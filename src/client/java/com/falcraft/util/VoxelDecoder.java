package com.falcraft.util;

import net.minecraft.core.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Decodes binary voxel data from SAM-3D streaming endpoint.
 * 
 * The streaming endpoint returns voxels as base64-encoded binary data where each voxel
 * is 6 bytes: [x_norm, y_norm, z_norm, r, g, b] (all uint8).
 * 
 * Positions are normalized 0-255 and must be denormalized using bounds_min/bounds_max.
 * Colors are already in 0-255 RGB format.
 */
public class VoxelDecoder {
    private static final Logger LOGGER = LoggerFactory.getLogger("VoxelDecoder");
    
    /**
     * Represents a decoded voxel with world-space position and color
     */
    public record Voxel(float x, float y, float z, int r, int g, int b) {
        /**
         * Returns the color as a packed RGB integer
         */
        public int packedColor() {
            return (r << 16) | (g << 8) | b;
        }
    }
    
    /**
     * Result of decoding voxel data from an SSE event
     */
    public record DecodeResult(
        Map<BlockPos, Integer> voxels,
        int voxelCount,
        float[] boundsMin,
        float[] boundsMax
    ) {}
    
    /**
     * Decodes base64-encoded binary voxel data into a voxel grid.
     * 
     * @param base64Data The base64-encoded binary voxel data
     * @param boundsMin The minimum bounds [x, y, z] in world space
     * @param boundsMax The maximum bounds [x, y, z] in world space
     * @param gridSize The target grid size for Minecraft blocks
     * @return A map of BlockPos to packed RGB color
     */
    public static DecodeResult decode(String base64Data, float[] boundsMin, float[] boundsMax, int gridSize) {
        if (base64Data == null || base64Data.isEmpty()) {
            return new DecodeResult(new HashMap<>(), 0, boundsMin, boundsMax);
        }
        
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64Data);
        } catch (IllegalArgumentException e) {
            LOGGER.error("Failed to decode base64 voxel data: {}", e.getMessage());
            return new DecodeResult(new HashMap<>(), 0, boundsMin, boundsMax);
        }
        
        if (bytes.length % 6 != 0) {
            LOGGER.warn("Voxel data length {} is not divisible by 6", bytes.length);
        }
        
        int voxelCount = bytes.length / 6;
        Map<BlockPos, Integer> voxels = new HashMap<>();
        
        // Calculate the range for denormalization
        float rangeX = boundsMax[0] - boundsMin[0];
        float rangeY = boundsMax[1] - boundsMin[1];
        float rangeZ = boundsMax[2] - boundsMin[2];
        
        // Avoid division by zero
        if (rangeX == 0) rangeX = 1;
        if (rangeY == 0) rangeY = 1;
        if (rangeZ == 0) rangeZ = 1;
        
        // Calculate scale to fit voxels into the grid
        float maxRange = Math.max(Math.max(rangeX, rangeY), rangeZ);
        float scale = (gridSize - 1) / maxRange;
        
        for (int i = 0; i < bytes.length; i += 6) {
            // Normalized coords (0-255)
            float xNorm = (bytes[i] & 0xFF) / 255f;
            float yNorm = (bytes[i + 1] & 0xFF) / 255f;
            float zNorm = (bytes[i + 2] & 0xFF) / 255f;
            
            // Denormalize to world space
            float worldX = boundsMin[0] + xNorm * rangeX;
            float worldY = boundsMin[1] + yNorm * rangeY;
            float worldZ = boundsMin[2] + zNorm * rangeZ;
            
            // Convert to grid coordinates (center and scale)
            // SAM-3D uses Z-up, Minecraft uses Y-up
            // SAM-3D: X right, Y forward, Z up
            // Minecraft: X right, Y up, Z forward (south)
            int gridX = Math.round((worldX - boundsMin[0]) * scale);
            int gridY = Math.round((worldZ - boundsMin[2]) * scale); // Z -> Y (up)
            int gridZ = Math.round((worldY - boundsMin[1]) * scale); // Y -> Z (forward)
            
            // Clamp to grid bounds
            gridX = Math.max(0, Math.min(gridSize - 1, gridX));
            gridY = Math.max(0, Math.min(gridSize - 1, gridY));
            gridZ = Math.max(0, Math.min(gridSize - 1, gridZ));
            
            // RGB colors (0-255)
            int r = bytes[i + 3] & 0xFF;
            int g = bytes[i + 4] & 0xFF;
            int b = bytes[i + 5] & 0xFF;
            
            // Pack color
            int color = (r << 16) | (g << 8) | b;
            
            // Handle gray/missing colors during geometry phase
            // If color is very dark gray (close to 128,128,128 which is the default),
            // we keep it as-is since the appearance phase will provide real colors
            
            BlockPos pos = new BlockPos(gridX, gridY, gridZ);
            voxels.put(pos, color);
        }
        
        return new DecodeResult(voxels, voxelCount, boundsMin, boundsMax);
    }
    
    /**
     * Decodes voxel data without coordinate transformation (for preview only).
     * Used during geometry phase when we just want to show the shape forming.
     * 
     * @param base64Data The base64-encoded binary voxel data
     * @param boundsMin The minimum bounds [x, y, z]
     * @param boundsMax The maximum bounds [x, y, z]
     * @param gridSize The target grid size
     * @param useDefaultColor Whether to use a default gray color (for geometry phase)
     * @return A map of BlockPos to packed RGB color
     */
    public static DecodeResult decodeWithOptions(
            String base64Data, 
            float[] boundsMin, 
            float[] boundsMax, 
            int gridSize,
            boolean useDefaultColor) {
        
        if (base64Data == null || base64Data.isEmpty()) {
            return new DecodeResult(new HashMap<>(), 0, boundsMin, boundsMax);
        }
        
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64Data);
        } catch (IllegalArgumentException e) {
            LOGGER.error("Failed to decode base64 voxel data: {}", e.getMessage());
            return new DecodeResult(new HashMap<>(), 0, boundsMin, boundsMax);
        }
        
        int voxelCount = bytes.length / 6;
        Map<BlockPos, Integer> voxels = new HashMap<>();
        
        float rangeX = boundsMax[0] - boundsMin[0];
        float rangeY = boundsMax[1] - boundsMin[1];
        float rangeZ = boundsMax[2] - boundsMin[2];
        
        if (rangeX == 0) rangeX = 1;
        if (rangeY == 0) rangeY = 1;
        if (rangeZ == 0) rangeZ = 1;
        
        float maxRange = Math.max(Math.max(rangeX, rangeY), rangeZ);
        float scale = (gridSize - 1) / maxRange;
        
        // Default translucent white for geometry phase
        int defaultColor = 0xCCCCCC;
        
        for (int i = 0; i < bytes.length; i += 6) {
            float xNorm = (bytes[i] & 0xFF) / 255f;
            float yNorm = (bytes[i + 1] & 0xFF) / 255f;
            float zNorm = (bytes[i + 2] & 0xFF) / 255f;
            
            float worldX = boundsMin[0] + xNorm * rangeX;
            float worldY = boundsMin[1] + yNorm * rangeY;
            float worldZ = boundsMin[2] + zNorm * rangeZ;
            
            // SAM-3D Z-up to Minecraft Y-up conversion
            int gridX = Math.round((worldX - boundsMin[0]) * scale);
            int gridY = Math.round((worldZ - boundsMin[2]) * scale);
            int gridZ = Math.round((worldY - boundsMin[1]) * scale);
            
            gridX = Math.max(0, Math.min(gridSize - 1, gridX));
            gridY = Math.max(0, Math.min(gridSize - 1, gridY));
            gridZ = Math.max(0, Math.min(gridSize - 1, gridZ));
            
            int color;
            if (useDefaultColor) {
                color = defaultColor;
            } else {
                int r = bytes[i + 3] & 0xFF;
                int g = bytes[i + 4] & 0xFF;
                int b = bytes[i + 5] & 0xFF;
                color = (r << 16) | (g << 8) | b;
            }
            
            BlockPos pos = new BlockPos(gridX, gridY, gridZ);
            voxels.put(pos, color);
        }
        
        return new DecodeResult(voxels, voxelCount, boundsMin, boundsMax);
    }
}
