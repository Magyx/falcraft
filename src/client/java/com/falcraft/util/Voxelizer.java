package com.falcraft.util;

import net.minecraft.core.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Converts 3D mesh data to a voxel grid
 */
public class Voxelizer {
    private static final Logger LOGGER = LoggerFactory.getLogger("Voxelizer");
    
    /**
     * Represents a voxelized 3D model
     * @param voxels Map of block positions to RGB colors
     * @param size The resolution of the voxel grid
     */
    public record VoxelGrid(Map<BlockPos, Integer> voxels, int size) {}
    
    /**
     * Voxelizes a mesh into a 3D grid
     * @param mesh The mesh data to voxelize
     * @param resolution The resolution of the voxel grid (e.g., 32 = 32x32x32)
     * @return A voxel grid
     */
    public static VoxelGrid voxelize(GLBParser.MeshData mesh, int resolution) {
        LOGGER.info("Voxelizing mesh with resolution {}x{}x{}", resolution, resolution, resolution);
        
        float[] vertices = mesh.vertices();
        int[] indices = mesh.indices();
        int[] colors = mesh.colors();
        
        if (vertices.length == 0) {
            LOGGER.warn("Empty mesh, returning empty voxel grid");
            return new VoxelGrid(new HashMap<>(), resolution);
        }
        
        // Calculate bounding box
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
        
        for (int i = 0; i < vertices.length; i += 3) {
            float x = vertices[i];
            float y = vertices[i + 1];
            float z = vertices[i + 2];
            
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
        }
        
        LOGGER.info("Bounding box: ({}, {}, {}) to ({}, {}, {})", minX, minY, minZ, maxX, maxY, maxZ);
        
        float sizeX = maxX - minX;
        float sizeY = maxY - minY;
        float sizeZ = maxZ - minZ;
        float maxSize = Math.max(Math.max(sizeX, sizeY), sizeZ);
        
        if (maxSize == 0) {
            LOGGER.warn("Zero-sized mesh, returning empty voxel grid");
            return new VoxelGrid(new HashMap<>(), resolution);
        }
        
        // Scale factor to fit mesh in voxel grid
        float scale = (resolution - 1) / maxSize;
        
        LOGGER.info("Scale factor: {}", scale);
        
        // Create voxel grid
        Map<BlockPos, Integer> voxels = new HashMap<>();
        
        // Voxelize using triangle rasterization
        for (int i = 0; i < indices.length; i += 3) {
            int idx0 = indices[i];
            int idx1 = indices[i + 1];
            int idx2 = indices[i + 2];
            
            // Get triangle vertices
            float x0 = vertices[idx0 * 3];
            float y0 = vertices[idx0 * 3 + 1];
            float z0 = vertices[idx0 * 3 + 2];
            
            float x1 = vertices[idx1 * 3];
            float y1 = vertices[idx1 * 3 + 1];
            float z1 = vertices[idx1 * 3 + 2];
            
            float x2 = vertices[idx2 * 3];
            float y2 = vertices[idx2 * 3 + 1];
            float z2 = vertices[idx2 * 3 + 2];
            
            // Transform to voxel space
            int vx0 = (int) ((x0 - minX) * scale);
            int vy0 = (int) ((y0 - minY) * scale);
            int vz0 = (int) ((z0 - minZ) * scale);
            
            int vx1 = (int) ((x1 - minX) * scale);
            int vy1 = (int) ((y1 - minY) * scale);
            int vz1 = (int) ((z1 - minZ) * scale);
            
            int vx2 = (int) ((x2 - minX) * scale);
            int vy2 = (int) ((y2 - minY) * scale);
            int vz2 = (int) ((z2 - minZ) * scale);
            
            // Get average color for this triangle
            int color0 = colors[idx0];
            int color1 = colors[idx1];
            int color2 = colors[idx2];
            int avgColor = averageColors(color0, color1, color2);
            
            // Rasterize triangle to voxels
            rasterizeTriangle(voxels, vx0, vy0, vz0, vx1, vy1, vz1, vx2, vy2, vz2, avgColor, resolution);
        }
        
        LOGGER.info("Voxelized mesh: {} voxels", voxels.size());
        
        return new VoxelGrid(voxels, resolution);
    }
    
    /**
     * Rasterizes a triangle into voxels using a simple scan-line approach
     */
    private static void rasterizeTriangle(Map<BlockPos, Integer> voxels,
                                         int x0, int y0, int z0,
                                         int x1, int y1, int z1,
                                         int x2, int y2, int z2,
                                         int color, int resolution) {
        // Simple approach: voxelize all points along the triangle edges
        voxelizeLine(voxels, x0, y0, z0, x1, y1, z1, color, resolution);
        voxelizeLine(voxels, x1, y1, z1, x2, y2, z2, color, resolution);
        voxelizeLine(voxels, x2, y2, z2, x0, y0, z0, color, resolution);
        
        // Fill interior using scanline
        fillTriangle(voxels, x0, y0, z0, x1, y1, z1, x2, y2, z2, color, resolution);
    }
    
    /**
     * Voxelizes a line using 3D Bresenham algorithm
     */
    private static void voxelizeLine(Map<BlockPos, Integer> voxels,
                                     int x0, int y0, int z0,
                                     int x1, int y1, int z1,
                                     int color, int resolution) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int dz = Math.abs(z1 - z0);
        
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int sz = z0 < z1 ? 1 : -1;
        
        int dm = Math.max(Math.max(dx, dy), dz);
        
        // Handle degenerate case where start and end are the same point
        if (dm == 0) {
            if (x0 >= 0 && x0 < resolution && y0 >= 0 && y0 < resolution && z0 >= 0 && z0 < resolution) {
                voxels.put(new BlockPos(x0, y0, z0), color);
            }
            return;
        }
        
        int x = x0, y = y0, z = z0;
        
        int err1 = dm / 2;
        int err2 = dm / 2;
        
        for (int i = 0; i <= dm; i++) {
            if (x >= 0 && x < resolution && y >= 0 && y < resolution && z >= 0 && z < resolution) {
                voxels.put(new BlockPos(x, y, z), color);
            }
            
            err1 -= dx;
            if (err1 < 0) {
                err1 += dm;
                x += sx;
            }
            
            err2 -= dy;
            if (err2 < 0) {
                err2 += dm;
                y += sy;
            }
            
            if (i * dz / dm > (i - 1) * dz / dm) {
                z += sz;
            }
        }
    }
    
    /**
     * Fills a triangle interior with voxels
     */
    private static void fillTriangle(Map<BlockPos, Integer> voxels,
                                     int x0, int y0, int z0,
                                     int x1, int y1, int z1,
                                     int x2, int y2, int z2,
                                     int color, int resolution) {
        // Calculate triangle center
        int cx = (x0 + x1 + x2) / 3;
        int cy = (y0 + y1 + y2) / 3;
        int cz = (z0 + z1 + z2) / 3;
        
        // Fill from center to edges (simple flood fill approach)
        // Draw lines from center to each vertex
        voxelizeLine(voxels, cx, cy, cz, x0, y0, z0, color, resolution);
        voxelizeLine(voxels, cx, cy, cz, x1, y1, z1, color, resolution);
        voxelizeLine(voxels, cx, cy, cz, x2, y2, z2, color, resolution);
        
        // Draw lines between midpoints
        int mx01 = (x0 + x1) / 2, my01 = (y0 + y1) / 2, mz01 = (z0 + z1) / 2;
        int mx12 = (x1 + x2) / 2, my12 = (y1 + y2) / 2, mz12 = (z1 + z2) / 2;
        int mx20 = (x2 + x0) / 2, my20 = (y2 + y0) / 2, mz20 = (z2 + z0) / 2;
        
        voxelizeLine(voxels, cx, cy, cz, mx01, my01, mz01, color, resolution);
        voxelizeLine(voxels, cx, cy, cz, mx12, my12, mz12, color, resolution);
        voxelizeLine(voxels, cx, cy, cz, mx20, my20, mz20, color, resolution);
    }
    
    /**
     * Averages three RGB colors
     */
    private static int averageColors(int c1, int c2, int c3) {
        int r1 = (c1 >> 16) & 0xFF;
        int g1 = (c1 >> 8) & 0xFF;
        int b1 = c1 & 0xFF;
        
        int r2 = (c2 >> 16) & 0xFF;
        int g2 = (c2 >> 8) & 0xFF;
        int b2 = c2 & 0xFF;
        
        int r3 = (c3 >> 16) & 0xFF;
        int g3 = (c3 >> 8) & 0xFF;
        int b3 = c3 & 0xFF;
        
        int r = (r1 + r2 + r3) / 3;
        int g = (g1 + g2 + g3) / 3;
        int b = (b1 + b2 + b3) / 3;
        
        return (r << 16) | (g << 8) | b;
    }
}

