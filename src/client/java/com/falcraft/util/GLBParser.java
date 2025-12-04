package com.falcraft.util;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser for GLB (GL Transmission Format Binary) files
 * GLB format structure:
 * - 12-byte header (magic, version, length)
 * - JSON chunk (scene structure, buffers, accessors)
 * - BIN chunk (binary mesh data)
 */
public class GLBParser {
    private static final Logger LOGGER = LoggerFactory.getLogger("GLBParser");
    private static final Gson GSON = new Gson();
    
    // GLB constants
    private static final int GLB_MAGIC = 0x46546C67; // "glTF" in hex
    private static final int JSON_CHUNK_TYPE = 0x4E4F534A; // "JSON"
    private static final int BIN_CHUNK_TYPE = 0x004E4942; // "BIN\0"
    
    /**
     * Represents parsed mesh data from a GLB file
     * @param vertices Array of vertex positions (x,y,z,x,y,z,...)
     * @param indices Array of triangle indices
     * @param colors Array of vertex colors (RGB as integers) - fallback when no texture
     * @param uvs Array of UV coordinates (u,v,u,v,...), null if no UVs
     */
    public record MeshData(float[] vertices, int[] indices, int[] colors, float[] uvs) {}
    
    /**
     * Extracts the embedded texture from a GLB file if present
     * @param glbData The GLB file as a byte array
     * @return The texture as a byte array (PNG or JPEG), or null if no embedded texture
     */
    public static byte[] extractEmbeddedTexture(byte[] glbData) throws IOException {
        LOGGER.info("Extracting embedded texture from GLB...");
        
        ByteBuffer buffer = ByteBuffer.wrap(glbData);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        
        // Parse GLB header
        int magic = buffer.getInt();
        if (magic != GLB_MAGIC) {
            return null;
        }
        buffer.getInt(); // version
        buffer.getInt(); // length
        
        // Parse JSON chunk
        int jsonChunkLength = buffer.getInt();
        buffer.getInt(); // chunk type
        
        byte[] jsonBytes = new byte[jsonChunkLength];
        buffer.get(jsonBytes);
        String jsonString = new String(jsonBytes);
        JsonObject gltf = GSON.fromJson(jsonString, JsonObject.class);
        
        // Parse BIN chunk
        int binChunkLength = buffer.getInt();
        buffer.getInt(); // chunk type
        
        byte[] binData = new byte[binChunkLength];
        buffer.get(binData);
        
        // Check for embedded images
        if (!gltf.has("images")) {
            LOGGER.info("No images found in GLB");
            return null;
        }
        
        JsonArray images = gltf.getAsJsonArray("images");
        if (images.isEmpty()) {
            LOGGER.info("Images array is empty");
            return null;
        }
        
        // Get first image
        JsonObject image = images.get(0).getAsJsonObject();
        LOGGER.info("Found image: {}", image);
        
        // Check if image is embedded (has bufferView) or external (has uri)
        if (!image.has("bufferView")) {
            LOGGER.info("Image is not embedded (no bufferView)");
            return null;
        }
        
        int bufferViewIndex = image.get("bufferView").getAsInt();
        String mimeType = image.has("mimeType") ? image.get("mimeType").getAsString() : "image/png";
        
        LOGGER.info("Image is embedded with bufferView {}, mimeType: {}", bufferViewIndex, mimeType);
        
        // Get buffer view
        JsonArray bufferViews = gltf.getAsJsonArray("bufferViews");
        JsonObject bufferView = bufferViews.get(bufferViewIndex).getAsJsonObject();
        
        int byteOffset = bufferView.has("byteOffset") ? bufferView.get("byteOffset").getAsInt() : 0;
        int byteLength = bufferView.get("byteLength").getAsInt();
        
        LOGGER.info("Extracting texture: offset={}, length={}", byteOffset, byteLength);
        
        // Extract texture data from binary buffer
        byte[] textureData = new byte[byteLength];
        System.arraycopy(binData, byteOffset, textureData, 0, byteLength);
        
        LOGGER.info("Successfully extracted embedded texture ({} bytes)", textureData.length);
        return textureData;
    }
    
    /**
     * Parses a GLB file and extracts mesh data
     * Note: textureSampler parameter is kept for API compatibility but not used.
     * Color sampling now happens in the Voxelizer for better accuracy.
     * @param glbData The GLB file as a byte array
     * @param textureSampler Unused (kept for API compatibility)
     * @return Parsed mesh data
     * @throws IOException If the GLB format is invalid
     */
    public static MeshData parse(byte[] glbData, TextureSampler textureSampler) throws IOException {
        LOGGER.info("Parsing GLB file ({} bytes)", glbData.length);
        
        ByteBuffer buffer = ByteBuffer.wrap(glbData);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        
        // Parse GLB header
        int magic = buffer.getInt();
        if (magic != GLB_MAGIC) {
            throw new IOException("Invalid GLB magic number: 0x" + Integer.toHexString(magic));
        }
        
        int version = buffer.getInt();
        int length = buffer.getInt();
        
        LOGGER.info("GLB version: {}, length: {}", version, length);
        
        // Parse JSON chunk
        int jsonChunkLength = buffer.getInt();
        int jsonChunkType = buffer.getInt();
        
        if (jsonChunkType != JSON_CHUNK_TYPE) {
            throw new IOException("Expected JSON chunk, got: 0x" + Integer.toHexString(jsonChunkType));
        }
        
        byte[] jsonBytes = new byte[jsonChunkLength];
        buffer.get(jsonBytes);
        String jsonString = new String(jsonBytes);
        
        JsonObject gltf = GSON.fromJson(jsonString, JsonObject.class);
        LOGGER.info("Parsed glTF JSON");
        
        // Parse BIN chunk
        int binChunkLength = buffer.getInt();
        int binChunkType = buffer.getInt();
        
        if (binChunkType != BIN_CHUNK_TYPE) {
            throw new IOException("Expected BIN chunk, got: 0x" + Integer.toHexString(binChunkType));
        }
        
        byte[] binData = new byte[binChunkLength];
        buffer.get(binData);
        
        LOGGER.info("Extracted binary data ({} bytes)", binData.length);
        
        // Extract mesh data from glTF structure
        return extractMeshData(gltf, binData);
    }
    
    /**
     * Extracts mesh data from glTF JSON structure and binary buffer
     * UV coordinates are passed through for per-voxel sampling in the Voxelizer
     */
    private static MeshData extractMeshData(JsonObject gltf, byte[] binData) throws IOException {
        LOGGER.info("Extracting mesh data from glTF structure");
        
        // Log texture/image information from GLB
        if (gltf.has("textures")) {
            LOGGER.info("GLB contains textures: {}", gltf.get("textures"));
        }
        if (gltf.has("images")) {
            LOGGER.info("GLB contains images: {}", gltf.get("images"));
        }
        if (gltf.has("materials")) {
            LOGGER.info("GLB contains materials: {}", gltf.get("materials"));
        }
        
        JsonArray meshes = gltf.getAsJsonArray("meshes");
        if (meshes == null || meshes.isEmpty()) {
            throw new IOException("No meshes found in glTF");
        }
        
        // Get first mesh (we'll merge all primitives)
        JsonObject mesh = meshes.get(0).getAsJsonObject();
        JsonArray primitives = mesh.getAsJsonArray("primitives");
        
        List<Float> allVertices = new ArrayList<>();
        List<Integer> allIndices = new ArrayList<>();
        List<Integer> allColors = new ArrayList<>();
        List<Float> allUVs = new ArrayList<>();
        
        int vertexOffset = 0;
        
        // Process each primitive (submesh)
        for (int i = 0; i < primitives.size(); i++) {
            JsonObject primitive = primitives.get(i).getAsJsonObject();
            JsonObject attributes = primitive.getAsJsonObject("attributes");
            
            // Extract vertex positions
            int positionAccessorIndex = attributes.get("POSITION").getAsInt();
            float[] positions = extractFloatArray(gltf, binData, positionAccessorIndex);
            
            LOGGER.info("Primitive {}: {} vertices", i, positions.length / 3);
            
            // Extract indices
            if (primitive.has("indices")) {
                int indicesAccessorIndex = primitive.get("indices").getAsInt();
                int[] indices = extractIntArray(gltf, binData, indicesAccessorIndex);
                
                // Add indices with offset
                for (int index : indices) {
                    allIndices.add(index + vertexOffset);
                }
            } else {
                // No indices - create them sequentially
                for (int j = 0; j < positions.length / 3; j++) {
                    allIndices.add(vertexOffset + j);
                }
            }
            
            // Extract UV coordinates (critical for per-voxel texture sampling!)
            float[] uvs = null;
            if (attributes.has("TEXCOORD_0")) {
                int uvAccessorIndex = attributes.get("TEXCOORD_0").getAsInt();
                uvs = extractFloatArray(gltf, binData, uvAccessorIndex);
                LOGGER.info("Primitive {}: Found TEXCOORD_0 with {} UVs", i, uvs.length / 2);
                
                // Log UV coordinate range for debugging
                float minU = Float.MAX_VALUE, maxU = Float.MIN_VALUE;
                float minV = Float.MAX_VALUE, maxV = Float.MIN_VALUE;
                for (int j = 0; j < uvs.length; j += 2) {
                    minU = Math.min(minU, uvs[j]);
                    maxU = Math.max(maxU, uvs[j]);
                    minV = Math.min(minV, uvs[j + 1]);
                    maxV = Math.max(maxV, uvs[j + 1]);
                }
                LOGGER.info("Primitive {}: UV range - U: [{}, {}], V: [{}, {}]", i, minU, maxU, minV, maxV);
            } else {
                LOGGER.warn("Primitive {}: No TEXCOORD_0 found - texture sampling will not work!", i);
            }
            
            // Generate fallback colors from vertex colors or position-based
            // These are only used if texture sampling fails
            int[] colors;
            if (attributes.has("COLOR_0")) {
                // Use vertex colors from GLB
                int colorAccessorIndex = attributes.get("COLOR_0").getAsInt();
                colors = extractColorArray(gltf, binData, colorAccessorIndex);
                LOGGER.info("Primitive {}: Found COLOR_0 attribute with {} colors", i, colors.length);
            } else {
                // Generate fallback colors based on vertex position
                LOGGER.info("Primitive {}: No COLOR_0, generating position-based fallback colors", i);
                colors = new int[positions.length / 3];
                for (int j = 0; j < colors.length; j++) {
                    // Generate varied colors based on position (normalized)
                    float x = positions[j * 3];
                    float y = positions[j * 3 + 1];
                    float z = positions[j * 3 + 2];
                    
                    // Create neutral gray colors as fallback
                    int gray = (int) ((Math.abs(x * 50) + Math.abs(y * 50) + Math.abs(z * 50)) % 128) + 64;
                    colors[j] = (gray << 16) | (gray << 8) | gray;
                }
            }
            
            // Add to combined lists
            for (float pos : positions) {
                allVertices.add(pos);
            }
            for (int color : colors) {
                allColors.add(color);
            }
            if (uvs != null) {
                for (float uv : uvs) {
                    allUVs.add(uv);
                }
            } else {
                // Add zero UVs as placeholder (will not be used for texture sampling)
                for (int j = 0; j < positions.length / 3; j++) {
                    allUVs.add(0.0f);
                    allUVs.add(0.0f);
                }
            }
            
            vertexOffset += positions.length / 3;
        }
        
        // Convert lists to arrays
        float[] vertices = new float[allVertices.size()];
        for (int i = 0; i < allVertices.size(); i++) {
            vertices[i] = allVertices.get(i);
        }
        
        int[] indices = allIndices.stream().mapToInt(Integer::intValue).toArray();
        int[] colors = allColors.stream().mapToInt(Integer::intValue).toArray();
        
        float[] uvs = new float[allUVs.size()];
        for (int i = 0; i < allUVs.size(); i++) {
            uvs[i] = allUVs.get(i);
        }
        
        LOGGER.info("Extracted mesh: {} vertices, {} indices, {} colors, {} UVs",
                vertices.length / 3, indices.length, colors.length, uvs.length / 2);
        
        return new MeshData(vertices, indices, colors, uvs);
    }
    
    /**
     * Extracts a float array from glTF accessor
     */
    private static float[] extractFloatArray(JsonObject gltf, byte[] binData, int accessorIndex) throws IOException {
        JsonObject accessor = gltf.getAsJsonArray("accessors").get(accessorIndex).getAsJsonObject();
        int bufferViewIndex = accessor.get("bufferView").getAsInt();
        int count = accessor.get("count").getAsInt();
        int componentType = accessor.get("componentType").getAsInt();
        String type = accessor.get("type").getAsString();
        
        int byteOffset = accessor.has("byteOffset") ? accessor.get("byteOffset").getAsInt() : 0;
        
        JsonObject bufferView = gltf.getAsJsonArray("bufferViews").get(bufferViewIndex).getAsJsonObject();
        int bufferViewOffset = bufferView.has("byteOffset") ? bufferView.get("byteOffset").getAsInt() : 0;
        
        int componentsPerElement = getComponentCount(type);
        int totalComponents = count * componentsPerElement;
        
        ByteBuffer buffer = ByteBuffer.wrap(binData, bufferViewOffset + byteOffset, totalComponents * 4);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        
        float[] result = new float[totalComponents];
        for (int i = 0; i < totalComponents; i++) {
            result[i] = buffer.getFloat();
        }
        
        return result;
    }
    
    /**
     * Extracts an integer array from glTF accessor (for indices)
     */
    private static int[] extractIntArray(JsonObject gltf, byte[] binData, int accessorIndex) throws IOException {
        JsonObject accessor = gltf.getAsJsonArray("accessors").get(accessorIndex).getAsJsonObject();
        int bufferViewIndex = accessor.get("bufferView").getAsInt();
        int count = accessor.get("count").getAsInt();
        int componentType = accessor.get("componentType").getAsInt();
        
        int byteOffset = accessor.has("byteOffset") ? accessor.get("byteOffset").getAsInt() : 0;
        
        JsonObject bufferView = gltf.getAsJsonArray("bufferViews").get(bufferViewIndex).getAsJsonObject();
        int bufferViewOffset = bufferView.has("byteOffset") ? bufferView.get("byteOffset").getAsInt() : 0;
        
        ByteBuffer buffer = ByteBuffer.wrap(binData);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.position(bufferViewOffset + byteOffset);
        
        int[] result = new int[count];
        
        // Component type: 5121=ubyte, 5123=ushort, 5125=uint
        for (int i = 0; i < count; i++) {
            if (componentType == 5121) { // UNSIGNED_BYTE
                result[i] = buffer.get() & 0xFF;
            } else if (componentType == 5123) { // UNSIGNED_SHORT
                result[i] = buffer.getShort() & 0xFFFF;
            } else if (componentType == 5125) { // UNSIGNED_INT
                result[i] = buffer.getInt();
            } else {
                throw new IOException("Unsupported index component type: " + componentType);
            }
        }
        
        return result;
    }
    
    /**
     * Extracts color data and converts to RGB integers
     */
    private static int[] extractColorArray(JsonObject gltf, byte[] binData, int accessorIndex) throws IOException {
        float[] colorFloats = extractFloatArray(gltf, binData, accessorIndex);
        int[] colors = new int[colorFloats.length / 3]; // Assuming RGB (3 components)
        
        for (int i = 0; i < colors.length; i++) {
            int r = (int) (colorFloats[i * 3] * 255) & 0xFF;
            int g = (int) (colorFloats[i * 3 + 1] * 255) & 0xFF;
            int b = (int) (colorFloats[i * 3 + 2] * 255) & 0xFF;
            colors[i] = (r << 16) | (g << 8) | b;
        }
        
        return colors;
    }
    
    /**
     * Gets the number of components for a glTF type
     */
    private static int getComponentCount(String type) {
        return switch (type) {
            case "SCALAR" -> 1;
            case "VEC2" -> 2;
            case "VEC3" -> 3;
            case "VEC4" -> 4;
            case "MAT2" -> 4;
            case "MAT3" -> 9;
            case "MAT4" -> 16;
            default -> throw new IllegalArgumentException("Unknown type: " + type);
        };
    }
}
