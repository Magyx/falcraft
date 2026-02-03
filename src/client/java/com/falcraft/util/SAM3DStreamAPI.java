package com.falcraft.util;

import com.falcraft.commands.ConfigCommand;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SSE client for the SAM-3D streaming endpoint.
 * 
 * Connects to rehan/sam-3d-stream on fal.ai and receives real-time voxel updates
 * as the 3D model is being diffused.
 * 
 * Event stages:
 * - geometry: Shape forming from noise (positions only, no real colors)
 * - appearance: Colors being applied to the shape
 * - mesh_preview: Final mesh decoded
 * - complete: Generation finished
 */
public class SAM3DStreamAPI {
    private static final Logger LOGGER = LoggerFactory.getLogger("SAM3DStreamAPI");
    
    // When running locally, point these endpoints at your own server.  The
    // streaming endpoint should emit SSE events with geometry/appearance updates.
    private static final String SAM3D_STREAM_ENDPOINT = "http://localhost:8000/sam3d-stream/stream";

    // Z-Image Turbo for initial image generation
    private static final String FAL_ZIMAGE_QUEUE_SUBMIT = "http://localhost:8000/z-image/turbo";
    
    private static final Gson GSON = new Gson();
    private final HttpClient httpClient;
    private final String apiKey;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    
    /**
     * Callback interface for streaming events
     */
    public interface StreamCallback {
        /**
         * Called when voxel data is received
         * @param voxels The current voxel grid
         * @param stage "geometry" or "appearance"
         * @param step Current diffusion step
         * @param totalSteps Total diffusion steps
         * @param progress Overall progress (0.0 - 1.0)
         */
        void onVoxelUpdate(Map<BlockPos, Integer> voxels, String stage, int step, int totalSteps, float progress);
        
        /**
         * Called when streaming completes successfully
         * @param finalVoxels The final voxel grid
         */
        void onComplete(Map<BlockPos, Integer> finalVoxels);
        
        /**
         * Called when an error occurs
         * @param error The error message
         */
        void onError(String error);
        
        /**
         * Called with status messages
         * @param message The status message
         */
        void onStatus(String message);
    }
    
    public SAM3DStreamAPI() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        // When using a local server the API key is optional.  We still read
        // whatever key has been configured for backwards compatibility but
        // default to an empty string if none is provided.
        String configuredKey = ConfigCommand.getApiKey();
        this.apiKey = configuredKey != null ? configuredKey : "";
    }
    
    /**
     * Cancels the current streaming operation
     */
    public void cancel() {
        cancelled.set(true);
    }
    
    /**
     * Generates an image using Z-Image Turbo and then streams 3D generation
     * 
     * @param prompt The text prompt
     * @param gridSize The target Minecraft grid size
     * @param callback Callback for streaming events
     */
    public void streamGenerate(String prompt, int gridSize, StreamCallback callback) {
        cancelled.set(false);
        
        try {
            // Step 1: Generate 2D image with Z-Image Turbo
            callback.onStatus("Generating 2D image...");
            String imageUrl = generateImageWithZImage(prompt);
            
            if (cancelled.get()) {
                callback.onError("Cancelled");
                return;
            }
            
            callback.onStatus("Image generated! Starting 3D streaming...");
            
            // Step 2: Stream 3D generation
            streamFrom3D(imageUrl, prompt, gridSize, callback);
            
        } catch (Exception e) {
            LOGGER.error("Stream generation failed", e);
            callback.onError(e.getMessage());
        }
    }
    
    /**
     * Generates an image using Z-Image Turbo
     */
    private String generateImageWithZImage(String prompt) throws IOException, InterruptedException {
        // Augment prompt for 3D-friendly output
        String augmentedPrompt = prompt + " image with plain white background, view from diagonally above";
        
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("prompt", augmentedPrompt);
        requestBody.addProperty("image_size", "square_hd");
        requestBody.addProperty("num_inference_steps", 8);
        requestBody.addProperty("num_images", 1);
        requestBody.addProperty("enable_safety_checker", true);
        requestBody.addProperty("output_format", "png");
        
        HttpRequest submitRequest = HttpRequest.newBuilder()
                .uri(URI.create(FAL_ZIMAGE_QUEUE_SUBMIT))
                .header("Authorization", "Key " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(requestBody)))
                .build();
        
        HttpResponse<String> submitResponse = httpClient.send(submitRequest, HttpResponse.BodyHandlers.ofString());
        
        if (submitResponse.statusCode() != 200) {
            throw new IOException("Z-Image submit failed: " + submitResponse.statusCode());
        }
        
        JsonObject submitJson = GSON.fromJson(submitResponse.body(), JsonObject.class);
        String responseUrl = submitJson.get("response_url").getAsString();
        String statusUrl = submitJson.get("status_url").getAsString();
        
        // Poll for completion
        boolean completed = false;
        int attempts = 0;
        int maxAttempts = 30;
        
        while (!completed && attempts < maxAttempts && !cancelled.get()) {
            Thread.sleep(1000);
            attempts++;
            
            HttpRequest statusRequest = HttpRequest.newBuilder()
                    .uri(URI.create(statusUrl))
                    .header("Authorization", "Key " + apiKey)
                    .GET()
                    .build();
            
            HttpResponse<String> statusResponse = httpClient.send(statusRequest, HttpResponse.BodyHandlers.ofString());
            
            if (statusResponse.statusCode() == 200 || statusResponse.statusCode() == 202) {
                JsonObject statusJson = GSON.fromJson(statusResponse.body(), JsonObject.class);
                String status = statusJson.get("status").getAsString();
                
                if ("COMPLETED".equals(status)) {
                    completed = true;
                } else if ("FAILED".equals(status)) {
                    throw new IOException("Z-Image generation failed");
                }
            }
        }
        
        if (!completed) {
            throw new IOException("Z-Image generation timed out");
        }
        
        // Get result
        HttpRequest resultRequest = HttpRequest.newBuilder()
                .uri(URI.create(responseUrl))
                .header("Authorization", "Key " + apiKey)
                .GET()
                .build();
        
        HttpResponse<String> resultResponse = httpClient.send(resultRequest, HttpResponse.BodyHandlers.ofString());
        
        if (resultResponse.statusCode() != 200) {
            throw new IOException("Failed to get Z-Image result");
        }
        
        JsonObject resultJson = GSON.fromJson(resultResponse.body(), JsonObject.class);
        return resultJson.getAsJsonArray("images")
                .get(0).getAsJsonObject()
                .get("url").getAsString();
    }
    
    /**
     * Streams 3D generation from an image using SAM-3D streaming endpoint
     */
    private void streamFrom3D(String imageUrl, String prompt, int gridSize, StreamCallback callback) 
            throws IOException, InterruptedException {
        
        // Build request body
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("image_url", imageUrl);
        requestBody.addProperty("prompt", prompt);
        requestBody.add("point_prompts", new JsonArray());
        requestBody.add("box_prompts", new JsonArray());
        requestBody.addProperty("stream_geometry_every", 2); // Emit every 2 steps for smooth preview
        requestBody.addProperty("stream_colors_every", 2);
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(SAM3D_STREAM_ENDPOINT))
                .header("Authorization", "Key " + apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(requestBody)))
                .timeout(Duration.ofMinutes(5))
                .build();
        
        // Use streaming body handler
        HttpResponse<java.io.InputStream> response = httpClient.send(request, 
                HttpResponse.BodyHandlers.ofInputStream());
        
        if (response.statusCode() != 200) {
            String errorBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
            throw new IOException("SAM-3D stream failed: " + response.statusCode() + " - " + errorBody);
        }
        
        // Parse SSE events
        Map<BlockPos, Integer> lastVoxels = null;
        java.io.InputStream bodyStream = response.body();
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(bodyStream, StandardCharsets.UTF_8))) {
            
            String line;
            StringBuilder eventData = new StringBuilder();
            
            while ((line = reader.readLine()) != null && !cancelled.get()) {
                if (line.startsWith("data: ")) {
                    eventData.append(line.substring(6));
                } else if (line.isEmpty() && eventData.length() > 0) {
                    // Process the event
                    String jsonStr = eventData.toString().trim();
                    eventData.setLength(0);
                    
                    if (jsonStr.isEmpty()) continue;
                    
                    try {
                        JsonObject event = GSON.fromJson(jsonStr, JsonObject.class);
                        
                        // Check for early exit BEFORE processing status messages
                        if (event.has("stage")) {
                            String stage = event.get("stage").getAsString();
                            
                            // EARLY EXIT: When final appearance step is received, we have all voxels!
                            // Skip waiting for mesh_preview, finalizing, and GLB generation (saves ~5-10 seconds)
                            if ("appearance".equals(stage) && event.has("step") && event.has("total_steps")) {
                                int step = event.get("step").getAsInt();
                                int totalSteps = event.get("total_steps").getAsInt();
                                
                                // Process this final voxel update first
                                lastVoxels = processSSEEvent(event, gridSize, callback, lastVoxels);
                                
                                if (step == totalSteps && lastVoxels != null && !lastVoxels.isEmpty()) {
                                    LOGGER.info("Early exit: Got final appearance step ({}/{}), {} voxels. Skipping GLB generation.",
                                            step, totalSteps, lastVoxels.size());
                                    callback.onComplete(lastVoxels);
                                    // Close the stream immediately to stop receiving more events
                                    bodyStream.close();
                                    return; // Exit immediately
                                }
                                continue; // Already processed, skip to next event
                            }
                            
                            // Normal processing for other stages
                            lastVoxels = processSSEEvent(event, gridSize, callback, lastVoxels);
                            
                            // Check for completion or error
                            if ("complete".equals(stage) || "error".equals(stage)) {
                                break;
                            }
                        } else {
                            lastVoxels = processSSEEvent(event, gridSize, callback, lastVoxels);
                        }
                    } catch (Exception e) {
                        LOGGER.warn("Failed to parse SSE event: {}", jsonStr, e);
                    }
                }
            }
        } catch (IOException e) {
            // Expected when we close the stream early
            if (!e.getMessage().contains("closed") && !e.getMessage().contains("Stream closed")) {
                throw e;
            }
        }
        
        if (cancelled.get()) {
            callback.onError("Cancelled");
        }
    }
    
    /**
     * Processes a single SSE event
     */
    private Map<BlockPos, Integer> processSSEEvent(JsonObject event, int gridSize, 
            StreamCallback callback, Map<BlockPos, Integer> previousVoxels) {
        
        // Check for heartbeat (ignore)
        if (event.has("heartbeat")) {
            return previousVoxels;
        }
        
        String stage = event.has("stage") ? event.get("stage").getAsString() : "unknown";
        
        switch (stage) {
            case "loading":
            case "preprocessing":
            case "geometry_start":
                callback.onStatus(event.has("message") ? event.get("message").getAsString() : "Processing...");
                break;
                
            case "geometry":
                return handleVoxelUpdate(event, gridSize, callback, true);
                
            case "appearance":
                return handleVoxelUpdate(event, gridSize, callback, false);
                
            case "mesh_preview":
                callback.onStatus("Mesh decoded!");
                break;
                
            case "complete":
                callback.onStatus("Generation complete!");
                if (previousVoxels != null) {
                    callback.onComplete(previousVoxels);
                }
                break;
                
            case "error":
                String error = event.has("error") ? event.get("error").getAsString() : "Unknown error";
                callback.onError(error);
                break;
                
            default:
                if (event.has("message")) {
                    callback.onStatus(event.get("message").getAsString());
                }
        }
        
        return previousVoxels;
    }
    
    /**
     * Handles a voxel update event (geometry or appearance stage)
     */
    private Map<BlockPos, Integer> handleVoxelUpdate(JsonObject event, int gridSize, 
            StreamCallback callback, boolean isGeometryPhase) {
        
        if (!event.has("voxel_data") || !event.has("bounds_min") || !event.has("bounds_max")) {
            return null;
        }
        
        String voxelData = event.get("voxel_data").getAsString();
        
        // Parse bounds
        float[] boundsMin = new float[3];
        float[] boundsMax = new float[3];
        
        var boundsMinArr = event.getAsJsonArray("bounds_min");
        var boundsMaxArr = event.getAsJsonArray("bounds_max");
        
        for (int i = 0; i < 3; i++) {
            boundsMin[i] = boundsMinArr.get(i).getAsFloat();
            boundsMax[i] = boundsMaxArr.get(i).getAsFloat();
        }
        
        // Decode voxels
        VoxelDecoder.DecodeResult result = VoxelDecoder.decodeWithOptions(
                voxelData, boundsMin, boundsMax, gridSize, isGeometryPhase);
        
        // Get step info
        int step = event.has("step") ? event.get("step").getAsInt() : 0;
        int totalSteps = event.has("total_steps") ? event.get("total_steps").getAsInt() : 30;
        float progress = event.has("progress") ? event.get("progress").getAsFloat() : 0f;
        
        String stage = isGeometryPhase ? "geometry" : "appearance";
        
        // Notify callback
        callback.onVoxelUpdate(result.voxels(), stage, step, totalSteps, progress);
        
        return result.voxels();
    }
}
