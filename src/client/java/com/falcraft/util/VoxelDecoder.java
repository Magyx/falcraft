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
        
        // First pass: decode all voxels and track bounds for centering
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        
        // Temporary storage for positions and colors
        int[][] tempVoxels = new int[voxelCount][4]; // [gridX, gridY, gridZ, color]
        int validCount = 0;
        
        for (int i = 0; i < bytes.length; i += 6) {
            // Normalized coords (0-255)
            float xNorm = (bytes[i] & 0xFF) / 255f;
            float yNorm = (bytes[i + 1] & 0xFF) / 255f;
            float zNorm = (bytes[i + 2] & 0xFF) / 255f;
            
            // Denormalize to world space
            float worldX = boundsMin[0] + xNorm * rangeX;
            float worldY = boundsMin[1] + yNorm * rangeY;
            float worldZ = boundsMin[2] + zNorm * rangeZ;
            
            // Convert to grid coordinates
            // SAM-3D uses Z-up, Minecraft uses Y-up
            int gridX = Math.round((worldX - boundsMin[0]) * scale);
            int gridY = Math.round((worldZ - boundsMin[2]) * scale); // Z -> Y (up)
            int gridZ = Math.round((worldY - boundsMin[1]) * scale); // Y -> Z (forward)
            
            // RGB colors (0-255)
            int r = bytes[i + 3] & 0xFF;
            int g = bytes[i + 4] & 0xFF;
            int b = bytes[i + 5] & 0xFF;
            int color = (r << 16) | (g << 8) | b;
            
            // Track bounds
            minX = Math.min(minX, gridX);
            minY = Math.min(minY, gridY);
            minZ = Math.min(minZ, gridZ);
            maxX = Math.max(maxX, gridX);
            maxY = Math.max(maxY, gridY);
            maxZ = Math.max(maxZ, gridZ);
            
            tempVoxels[validCount][0] = gridX;
            tempVoxels[validCount][1] = gridY;
            tempVoxels[validCount][2] = gridZ;
            tempVoxels[validCount][3] = color;
            validCount++;
        }
        
        // Calculate centering offset
        int structureWidth = maxX - minX + 1;
        int structureHeight = maxY - minY + 1;
        int structureDepth = maxZ - minZ + 1;
        
        int offsetX = (gridSize - structureWidth) / 2 - minX;
        int offsetY = (gridSize - structureHeight) / 2 - minY;
        int offsetZ = (gridSize - structureDepth) / 2 - minZ;
        
        // Second pass: apply centering offset and store final positions
        for (int i = 0; i < validCount; i++) {
            int gridX = tempVoxels[i][0] + offsetX;
            int gridY = tempVoxels[i][1] + offsetY;
            int gridZ = tempVoxels[i][2] + offsetZ;
            int color = tempVoxels[i][3];
            
            // Clamp to grid bounds
            gridX = Math.max(0, Math.min(gridSize - 1, gridX));
            gridY = Math.max(0, Math.min(gridSize - 1, gridY));
            gridZ = Math.max(0, Math.min(gridSize - 1, gridZ));
            
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
        
        // First pass: decode and track bounds for centering
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        
        int[][] tempVoxels = new int[voxelCount][4];
        int validCount = 0;
        
        for (int i = 0; i < bytes.length; i += 6) {
            float xNorm = (bytes[i] & 0xFF) / 255f;
            float yNorm = (bytes[i + 1] & 0xFF) / 255f;
            float zNorm = (bytes[i + 2] & 0xFF) / 255f;
            
            float worldX = boundsMin[0] + xNorm * rangeX;
            float worldY = boundsMin[1] + yNorm * rangeY;
            float worldZ = boundsMin[2] + zNorm * rangeZ;
            
            int gridX = Math.round((worldX - boundsMin[0]) * scale);
            int gridY = Math.round((worldZ - boundsMin[2]) * scale);
            int gridZ = Math.round((worldY - boundsMin[1]) * scale);
            
            int color;
            if (useDefaultColor) {
                color = defaultColor;
            } else {
                int r = bytes[i + 3] & 0xFF;
                int g = bytes[i + 4] & 0xFF;
                int b = bytes[i + 5] & 0xFF;
                color = (r << 16) | (g << 8) | b;
            }
            
            minX = Math.min(minX, gridX);
            minY = Math.min(minY, gridY);
            minZ = Math.min(minZ, gridZ);
            maxX = Math.max(maxX, gridX);
            maxY = Math.max(maxY, gridY);
            maxZ = Math.max(maxZ, gridZ);
            
            tempVoxels[validCount][0] = gridX;
            tempVoxels[validCount][1] = gridY;
            tempVoxels[validCount][2] = gridZ;
            tempVoxels[validCount][3] = color;
            validCount++;
        }
        
        // Calculate centering offset
        int offsetX = (gridSize - (maxX - minX + 1)) / 2 - minX;
        int offsetY = (gridSize - (maxY - minY + 1)) / 2 - minY;
        int offsetZ = (gridSize - (maxZ - minZ + 1)) / 2 - minZ;
        
        // Second pass: apply centering
        for (int i = 0; i < validCount; i++) {
            int gridX = Math.max(0, Math.min(gridSize - 1, tempVoxels[i][0] + offsetX));
            int gridY = Math.max(0, Math.min(gridSize - 1, tempVoxels[i][1] + offsetY));
            int gridZ = Math.max(0, Math.min(gridSize - 1, tempVoxels[i][2] + offsetZ));
            
            BlockPos pos = new BlockPos(gridX, gridY, gridZ);
            voxels.put(pos, tempVoxels[i][3]);
        }
        
        return new DecodeResult(voxels, voxelCount, boundsMin, boundsMax);
    }
}
