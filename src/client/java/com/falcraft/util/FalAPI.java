package com.falcraft.util;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

public class FalAPI {
    private static final Logger LOGGER = LoggerFactory.getLogger("FalAPI");
    private static final String FAL_QUEUE_SUBMIT = "https://queue.fal.run/fal-ai/nano-banana/edit";
    private static final String FAL_3D_QUEUE_SUBMIT = "https://queue.fal.run/fal-ai/meshy/v6-preview/text-to-3d";
    private static final Gson GSON = new Gson();
    private final HttpClient httpClient;
    private final String apiKey;
    
    /**
     * Result containing both the GLB model data and optional texture URL
     */
    public record ModelResult(byte[] glbData, String textureUrl) {}

    public FalAPI() {
        this.httpClient = HttpClient.newHttpClient();
        this.apiKey = loadApiKey();
        
        if (apiKey == null || apiKey.isEmpty()) {
            LOGGER.error("FAL_API_KEY not found! Please set it in .env file or as environment variable.");
            throw new IllegalStateException("FAL_API_KEY is required. Create a .env file in the Minecraft directory with: FAL_API_KEY=your-key-here");
        }
        
        LOGGER.info("fal API key loaded successfully");
    }
    
    /**
     * Loads the API key from .env file or environment variable
     * Priority: .env file in game directory > .env file in config directory > environment variable
     */
    private String loadApiKey() {
        // Try loading from .env file in game directory
        File gameDir = Minecraft.getInstance().gameDirectory;
        Path envFile = gameDir.toPath().resolve(".env");
        
        LOGGER.info("Looking for .env file at: {}", envFile.toAbsolutePath());
        LOGGER.info("File exists: {}", Files.exists(envFile));
        
        if (Files.exists(envFile)) {
            try {
                String key = readApiKeyFromEnvFile(envFile);
                if (key != null && !key.isEmpty()) {
                    LOGGER.info("✓ Loaded API key from .env file: {}", envFile);
                    return key;
                }
                LOGGER.warn("✗ .env file exists but FAL_API_KEY not found or empty");
            } catch (IOException e) {
                LOGGER.warn("Failed to read .env file: {}", envFile, e);
            }
        } else {
            LOGGER.warn("✗ .env file not found at: {}", envFile.toAbsolutePath());
        }
        
        // Try loading from .env file in config directory
        Path configEnvFile = gameDir.toPath().resolve("config").resolve("falcraft").resolve(".env");
        if (Files.exists(configEnvFile)) {
            try {
                String key = readApiKeyFromEnvFile(configEnvFile);
                if (key != null && !key.isEmpty()) {
                    LOGGER.info("Loaded API key from config .env file: {}", configEnvFile);
                    return key;
                }
            } catch (IOException e) {
                LOGGER.warn("Failed to read config .env file: {}", configEnvFile, e);
            }
        }
        
        // Fall back to environment variable
        String envKey = System.getenv("FAL_API_KEY");
        if (envKey != null && !envKey.isEmpty()) {
            LOGGER.info("Loaded API key from environment variable");
            return envKey;
        }
        
        return null;
    }
    
    /**
     * Reads the FAL_API_KEY from a .env file
     * Supports formats: FAL_API_KEY=value or FAL_API_KEY="value"
     */
    private String readApiKeyFromEnvFile(Path envFile) throws IOException {
        String content = Files.readString(envFile);
        
        for (String line : content.split("\\r?\\n")) {
            line = line.trim();
            
            // Skip comments and empty lines
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            
            // Look for FAL_API_KEY=value
            if (line.startsWith("FAL_API_KEY=")) {
                String value = line.substring("FAL_API_KEY=".length()).trim();
                
                // Remove surrounding quotes if present
                if (value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                } else if (value.startsWith("'") && value.endsWith("'")) {
                    value = value.substring(1, value.length() - 1);
                }
                
                return value;
            }
        }
        
        return null;
    }

    /**
     * Uploads a texture PNG file and remixes it using the fal nano-banana/edit endpoint
     * @param textureFile The PNG file to remix
     * @param prompt The text prompt describing the desired edits
     * @return The remixed PNG as a byte array
     * @throws IOException If network or file operations fail
     */
    public byte[] remixTexture(File textureFile, String prompt) throws IOException, InterruptedException {
        LOGGER.info("Starting texture remix with prompt: {}", prompt);
        
        // Step 1: Convert the file to base64 data URI
        byte[] fileBytes = Files.readAllBytes(textureFile.toPath());
        String base64Data = Base64.getEncoder().encodeToString(fileBytes);
        String dataUri = "data:image/png;base64," + base64Data;
        
        LOGGER.info("Converted texture to data URI ({} bytes)", fileBytes.length);
        
        // Step 2: Submit the request to the queue
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("prompt", prompt);
        
        JsonArray imageUrls = new JsonArray();
        imageUrls.add(dataUri);
        requestBody.add("image_urls", imageUrls);
        
        requestBody.addProperty("num_images", 1);
        requestBody.addProperty("output_format", "png");
        
        String requestBodyJson = GSON.toJson(requestBody);        
        LOGGER.info("Submitting request to fal queue...");
        
        HttpRequest submitRequest = HttpRequest.newBuilder()
                .uri(URI.create(FAL_QUEUE_SUBMIT))
                .header("Authorization", "Key " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBodyJson))
                .build();
        
        HttpResponse<String> submitResponse = httpClient.send(submitRequest, HttpResponse.BodyHandlers.ofString());
        
        if (submitResponse.statusCode() != 200) {
            LOGGER.error("fal queue submit error: {} - {}", submitResponse.statusCode(), submitResponse.body());
            throw new IOException("Failed to submit request to fal queue: " + submitResponse.statusCode());
        }
        
        JsonObject submitJson = GSON.fromJson(submitResponse.body(), JsonObject.class);
        String requestId = submitJson.get("request_id").getAsString();
        String responseUrl = submitJson.get("response_url").getAsString();
        String statusUrl = submitJson.get("status_url").getAsString();
        
        LOGGER.info("Request submitted with ID: {}", requestId);
        LOGGER.info("Status URL: {}", statusUrl);
        LOGGER.info("Response URL: {}", responseUrl);
        
        // Step 3: Poll for completion
        LOGGER.info("Polling for completion...");
        
        boolean completed = false;
        int attempts = 0;
        int maxAttempts = 60; // 60 attempts * 2 seconds = 2 minutes max
        
        while (!completed && attempts < maxAttempts) {
            Thread.sleep(2000); // Wait 2 seconds between polls
            attempts++;
            
            HttpRequest statusRequest = HttpRequest.newBuilder()
                    .uri(URI.create(statusUrl))
                    .header("Authorization", "Key " + apiKey)
                    .GET()
                    .build();
            
            HttpResponse<String> statusResponse = httpClient.send(statusRequest, HttpResponse.BodyHandlers.ofString());
            
            // 202 = IN_PROGRESS, 200 = COMPLETED
            if (statusResponse.statusCode() == 200 || statusResponse.statusCode() == 202) {
                JsonObject statusJson = GSON.fromJson(statusResponse.body(), JsonObject.class);
                String status = statusJson.get("status").getAsString();
                
                LOGGER.info("Status check {}/{}: {}", attempts, maxAttempts, status);
                
                if ("COMPLETED".equals(status)) {
                    completed = true;
                } else if ("FAILED".equals(status)) {
                    throw new IOException("fal request failed");
                }
            } else {
                LOGGER.warn("Unexpected status code {}, continuing to poll...", statusResponse.statusCode());
            }
        }
        
        if (!completed) {
            throw new IOException("Request timed out after " + maxAttempts + " attempts");
        }
        
        // Step 4: Get the result using the response_url from submit
        LOGGER.info("Fetching result from response URL...");
        
        HttpRequest resultRequest = HttpRequest.newBuilder()
                .uri(URI.create(responseUrl))
                .header("Authorization", "Key " + apiKey)
                .GET()
                .build();
        
        HttpResponse<String> resultResponse = httpClient.send(resultRequest, HttpResponse.BodyHandlers.ofString());
        
        if (resultResponse.statusCode() != 200) {
            LOGGER.error("Failed to get result: {} - {}", resultResponse.statusCode(), resultResponse.body());
            throw new IOException("Failed to get result from fal");
        }
        
        LOGGER.info("Got result, parsing...");
        JsonObject resultJson = GSON.fromJson(resultResponse.body(), JsonObject.class);
        String imageUrl = resultJson.getAsJsonArray("images")
                .get(0).getAsJsonObject()
                .get("url").getAsString();
        
        LOGGER.info("Downloading result image from: {}", imageUrl);
        
        // Step 5: Download the result image
        return downloadImage(imageUrl);
    }

    /**
     * Generates a 3D model from a text prompt using the fal Meshy v6 endpoint
     * @param prompt The text prompt describing the desired 3D model
     * @return ModelResult containing GLB data and texture URL
     * @throws IOException If network operations fail
     * @throws InterruptedException If the thread is interrupted during polling
     */
    public ModelResult generateModel(String prompt) throws IOException, InterruptedException {
        LOGGER.info("Starting 3D model generation with prompt: {}", prompt);
        
        // Step 1: Submit the request to the queue
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("prompt", prompt);
        requestBody.addProperty("mode", "full"); // Use full mode (textured model with proper colors)
        requestBody.addProperty("topology", "quad"); // Quad topology for cleaner UV layouts
        
        String requestBodyJson = GSON.toJson(requestBody);
        LOGGER.info("Submitting 3D generation request to fal queue...");
        
        HttpRequest submitRequest = HttpRequest.newBuilder()
                .uri(URI.create(FAL_3D_QUEUE_SUBMIT))
                .header("Authorization", "Key " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBodyJson))
                .build();
        
        HttpResponse<String> submitResponse = httpClient.send(submitRequest, HttpResponse.BodyHandlers.ofString());
        
        if (submitResponse.statusCode() != 200) {
            LOGGER.error("fal 3D queue submit error: {} - {}", submitResponse.statusCode(), submitResponse.body());
            throw new IOException("Failed to submit 3D generation request to fal queue: " + submitResponse.statusCode());
        }
        
        JsonObject submitJson = GSON.fromJson(submitResponse.body(), JsonObject.class);
        String requestId = submitJson.get("request_id").getAsString();
        String responseUrl = submitJson.get("response_url").getAsString();
        String statusUrl = submitJson.get("status_url").getAsString();
        
        LOGGER.info("3D generation request submitted with ID: {}", requestId);
        LOGGER.info("Status URL: {}", statusUrl);
        LOGGER.info("Response URL: {}", responseUrl);
        
        // Step 2: Poll for completion (3D generation takes longer, so increase timeout)
        LOGGER.info("Polling for 3D generation completion...");
        
        boolean completed = false;
        int attempts = 0;
        int maxAttempts = 160; // 160 attempts * 5 seconds = 13.3 minutes max (3D takes longer)
        
        while (!completed && attempts < maxAttempts) {
            Thread.sleep(5000); // Wait 5 seconds between polls (longer for 3D)
            attempts++;
            
            HttpRequest statusRequest = HttpRequest.newBuilder()
                    .uri(URI.create(statusUrl))
                    .header("Authorization", "Key " + apiKey)
                    .GET()
                    .build();
            
            HttpResponse<String> statusResponse = httpClient.send(statusRequest, HttpResponse.BodyHandlers.ofString());
            
            // 202 = IN_PROGRESS, 200 = COMPLETED
            if (statusResponse.statusCode() == 200 || statusResponse.statusCode() == 202) {
                JsonObject statusJson = GSON.fromJson(statusResponse.body(), JsonObject.class);
                String status = statusJson.get("status").getAsString();
                
                LOGGER.info("Status check {}/{}: {}", attempts, maxAttempts, status);
                
                if ("COMPLETED".equals(status)) {
                    completed = true;
                } else if ("FAILED".equals(status)) {
                    throw new IOException("fal 3D generation request failed");
                }
            } else {
                LOGGER.warn("Unexpected status code {}, continuing to poll...", statusResponse.statusCode());
            }
        }
        
        if (!completed) {
            throw new IOException("3D generation request timed out after " + maxAttempts + " attempts");
        }
        
        // Step 3: Get the result using the response_url from submit
        LOGGER.info("Fetching 3D model result from response URL...");
        
        HttpRequest resultRequest = HttpRequest.newBuilder()
                .uri(URI.create(responseUrl))
                .header("Authorization", "Key " + apiKey)
                .GET()
                .build();
        
        HttpResponse<String> resultResponse = httpClient.send(resultRequest, HttpResponse.BodyHandlers.ofString());
        
        if (resultResponse.statusCode() != 200) {
            LOGGER.error("Failed to get 3D result: {} - {}", resultResponse.statusCode(), resultResponse.body());
            throw new IOException("Failed to get 3D result from fal");
        }
        
        LOGGER.info("Got 3D result, parsing...");
        JsonObject resultJson = GSON.fromJson(resultResponse.body(), JsonObject.class);
        String glbUrl = resultJson.getAsJsonObject("model_glb")
                .get("url").getAsString();
        
        // Extract texture URL if available
        String textureUrl = null;
        if (resultJson.has("texture_urls")) {
            JsonArray textureUrls = resultJson.getAsJsonArray("texture_urls");
            if (!textureUrls.isEmpty()) {
                JsonObject firstTexture = textureUrls.get(0).getAsJsonObject();
                if (firstTexture.has("base_color")) {
                    textureUrl = firstTexture.getAsJsonObject("base_color")
                            .get("url").getAsString();
                    LOGGER.info("Found texture URL: {}", textureUrl);
                }
            }
        }
        
        LOGGER.info("Downloading GLB model from: {}", glbUrl);
        
        // Step 4: Download the GLB file
        byte[] glbData = downloadFile(glbUrl);
        
        return new ModelResult(glbData, textureUrl);
    }

    /**
     * Downloads an image from a URL
     * @param imageUrl The URL of the image
     * @return The image as a byte array
     */
    private byte[] downloadImage(String imageUrl) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(imageUrl))
                .GET()
                .build();
        
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        
        if (response.statusCode() != 200) {
            throw new IOException("Failed to download image from: " + imageUrl);
        }
        
        return response.body();
    }

    /**
     * Downloads a file from a URL
     * @param fileUrl The URL of the file
     * @return The file as a byte array
     */
    public byte[] downloadFile(String fileUrl) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(fileUrl))
                .GET()
                .build();
        
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        
        if (response.statusCode() != 200) {
            throw new IOException("Failed to download file from: " + fileUrl);
        }
        
        LOGGER.info("Downloaded file: {} bytes", response.body().length);
        return response.body();
    }
}

