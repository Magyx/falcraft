package com.falcraft.util;

import net.minecraft.core.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
     * Helper class to store triangle data with UV coordinates
     */
    private static class Triangle {
        float x0, y0, z0, x1, y1, z1, x2, y2, z2;
        float u0, v0, u1, v1, u2, v2;
        int color0, color1, color2;
        
        Triangle(float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2,
                float u0, float v0, float u1, float v1, float u2, float v2,
                int color0, int color1, int color2) {
            this.x0 = x0; this.y0 = y0; this.z0 = z0;
            this.x1 = x1; this.y1 = y1; this.z1 = z1;
            this.x2 = x2; this.y2 = y2; this.z2 = z2;
            this.u0 = u0; this.v0 = v0;
            this.u1 = u1; this.v1 = v1;
            this.u2 = u2; this.v2 = v2;
            this.color0 = color0; this.color1 = color1; this.color2 = color2;
        }
        
        /**
         * Interpolates UV coordinates using barycentric coordinates
         */
        float[] interpolateUV(float bary0, float bary1, float bary2) {
            float u = u0 * bary0 + u1 * bary1 + u2 * bary2;
            float v = v0 * bary0 + v1 * bary1 + v2 * bary2;
            return new float[] { u, v };
        }
        
        /**
         * Interpolates color using barycentric coordinates (fallback when no texture)
         */
        int interpolateColor(float bary0, float bary1, float bary2) {
            // Extract RGB components
            int r0 = (color0 >> 16) & 0xFF, g0 = (color0 >> 8) & 0xFF, b0 = color0 & 0xFF;
            int r1 = (color1 >> 16) & 0xFF, g1 = (color1 >> 8) & 0xFF, b1 = color1 & 0xFF;
            int r2 = (color2 >> 16) & 0xFF, g2 = (color2 >> 8) & 0xFF, b2 = color2 & 0xFF;
            
            // Interpolate
            int r = (int) (r0 * bary0 + r1 * bary1 + r2 * bary2);
            int g = (int) (g0 * bary0 + g1 * bary1 + g2 * bary2);
            int b = (int) (b0 * bary0 + b1 * bary1 + b2 * bary2);
            
            return (r << 16) | (g << 8) | b;
        }
        
        /**
         * Checks if this triangle has valid UV coordinates
         */
        boolean hasValidUVs() {
            // Check if at least one UV is non-zero (indicating valid texture mapping)
            return (u0 != 0 || v0 != 0 || u1 != 0 || v1 != 0 || u2 != 0 || v2 != 0);
        }
    }
    
    /**
     * Voxelizes a mesh into a 3D grid (legacy method without texture sampler)
     * @param mesh The mesh data to voxelize
     * @param resolution The resolution of the voxel grid (e.g., 32 = 32x32x32)
     * @return A voxel grid
     */
    public static VoxelGrid voxelize(GLBParser.MeshData mesh, int resolution) {
        return voxelize(mesh, resolution, null);
    }
    
    /**
     * Voxelizes a mesh into a 3D grid with proper per-voxel texture sampling
     * @param mesh The mesh data to voxelize
     * @param resolution The resolution of the voxel grid (e.g., 32 = 32x32x32)
     * @param textureSampler Optional texture sampler for per-voxel color sampling
     * @return A voxel grid
     */
    public static VoxelGrid voxelize(GLBParser.MeshData mesh, int resolution, TextureSampler textureSampler) {
        LOGGER.info("Voxelizing mesh with resolution {}x{}x{}", resolution, resolution, resolution);
        
        float[] vertices = mesh.vertices();
        int[] indices = mesh.indices();
        int[] colors = mesh.colors();
        float[] uvs = mesh.uvs();
        
        boolean hasUVs = uvs != null && uvs.length > 0;
        boolean hasTexture = textureSampler != null && hasUVs;
        
        LOGGER.info("Voxelization mode: {} (hasUVs={}, hasTexture={})", 
            hasTexture ? "PER-VOXEL TEXTURE SAMPLING" : "VERTEX COLOR INTERPOLATION",
            hasUVs, textureSampler != null);
        
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
        
        // Create voxel grid and distance tracking for "closest triangle wins"
        Map<BlockPos, Integer> voxels = new HashMap<>();
        Map<BlockPos, Float> voxelDistances = new HashMap<>(); // Track distance for each voxel
        
        // Build list of triangles with UV coordinates
        List<Triangle> triangles = new ArrayList<>();
        for (int i = 0; i < indices.length; i += 3) {
            int idx0 = indices[i];
            int idx1 = indices[i + 1];
            int idx2 = indices[i + 2];
            
            // Get UV coordinates for each vertex (if available)
            float u0 = hasUVs ? uvs[idx0 * 2] : 0;
            float v0 = hasUVs ? uvs[idx0 * 2 + 1] : 0;
            float u1 = hasUVs ? uvs[idx1 * 2] : 0;
            float v1 = hasUVs ? uvs[idx1 * 2 + 1] : 0;
            float u2 = hasUVs ? uvs[idx2 * 2] : 0;
            float v2 = hasUVs ? uvs[idx2 * 2 + 1] : 0;
            
            triangles.add(new Triangle(
                vertices[idx0 * 3], vertices[idx0 * 3 + 1], vertices[idx0 * 3 + 2],
                vertices[idx1 * 3], vertices[idx1 * 3 + 1], vertices[idx1 * 3 + 2],
                vertices[idx2 * 3], vertices[idx2 * 3 + 1], vertices[idx2 * 3 + 2],
                u0, v0, u1, v1, u2, v2,
                colors[idx0], colors[idx1], colors[idx2]
            ));
        }
        
        LOGGER.info("Voxelizing {} triangles using surface rasterization (closest-triangle-wins)", triangles.size());
        
        // Track texture sampling stats
        int textureSamples = 0;
        int colorInterpolations = 0;
        int closerTriangleUpdates = 0;
        
        // Surface voxelization: rasterize each triangle's surface
        for (Triangle tri : triangles) {
            // Transform triangle to voxel space
            int vx0 = (int) ((tri.x0 - minX) * scale);
            int vy0 = (int) ((tri.y0 - minY) * scale);
            int vz0 = (int) ((tri.z0 - minZ) * scale);
            
            int vx1 = (int) ((tri.x1 - minX) * scale);
            int vy1 = (int) ((tri.y1 - minY) * scale);
            int vz1 = (int) ((tri.z1 - minZ) * scale);
            
            int vx2 = (int) ((tri.x2 - minX) * scale);
            int vy2 = (int) ((tri.y2 - minY) * scale);
            int vz2 = (int) ((tri.z2 - minZ) * scale);
            
            // Get bounding box of triangle in voxel space
            int minVX = Math.max(0, Math.min(Math.min(vx0, vx1), vx2));
            int maxVX = Math.min(resolution - 1, Math.max(Math.max(vx0, vx1), vx2));
            int minVY = Math.max(0, Math.min(Math.min(vy0, vy1), vy2));
            int maxVY = Math.min(resolution - 1, Math.max(Math.max(vy0, vy1), vy2));
            int minVZ = Math.max(0, Math.min(Math.min(vz0, vz1), vz2));
            int maxVZ = Math.min(resolution - 1, Math.max(Math.max(vz0, vz1), vz2));
            
            // Rasterize triangle within bounding box
            for (int vx = minVX; vx <= maxVX; vx++) {
                for (int vy = minVY; vy <= maxVY; vy++) {
                    for (int vz = minVZ; vz <= maxVZ; vz++) {
                        // Calculate voxel center in world space
                        float worldX = minX + (vx + 0.5f) / scale;
                        float worldY = minY + (vy + 0.5f) / scale;
                        float worldZ = minZ + (vz + 0.5f) / scale;
                        
                        // Calculate barycentric coordinates
                        float[] bary = new float[3];
                        float dist = distanceToTriangle(worldX, worldY, worldZ, tri, bary);
                        
                        // Only fill if very close to surface (tight threshold for sharp features)
                        float voxelSize = 1.0f / scale;
                        if (dist < voxelSize * 0.866f) { // sqrt(3)/2 ≈ 0.866 (half voxel diagonal)
                            BlockPos pos = new BlockPos(vx, vy, vz);
                            
                            // CLOSEST TRIANGLE WINS: Only update if this triangle is closer
                            float currentDist = voxelDistances.getOrDefault(pos, Float.MAX_VALUE);
                            if (dist < currentDist) {
                                // Track if this is an update (closer triangle replaced existing)
                                if (currentDist < Float.MAX_VALUE) {
                                    closerTriangleUpdates++;
                                }
                                
                                int color;
                                
                                // Sample texture at interpolated UV coordinates
                                if (hasTexture && tri.hasValidUVs()) {
                                    // Interpolate UV coordinates using barycentric coords
                                    float[] uv = tri.interpolateUV(bary[0], bary[1], bary[2]);
                                    // Sample texture at this specific UV position
                                    color = textureSampler.sample(uv[0], uv[1]);
                                    textureSamples++;
                                } else {
                                    // Fallback: interpolate vertex colors
                                    color = tri.interpolateColor(bary[0], bary[1], bary[2]);
                                    colorInterpolations++;
                                }
                                
                                voxels.put(pos, color);
                                voxelDistances.put(pos, dist);
                            }
                        }
                    }
                }
            }
        }
        
        LOGGER.info("Voxelized mesh: {} voxels (texture samples: {}, color interpolations: {}, closer-triangle updates: {})", 
            voxels.size(), textureSamples, colorInterpolations, closerTriangleUpdates);
        
        return new VoxelGrid(voxels, resolution);
    }
    
    /**
     * Calculates the distance from a point to a triangle and returns barycentric coordinates
     * @param px Point X coordinate
     * @param py Point Y coordinate
     * @param pz Point Z coordinate
     * @param tri Triangle
     * @param bary Output array for barycentric coordinates [u, v, w]
     * @return Distance from point to triangle
     */
    private static float distanceToTriangle(float px, float py, float pz, Triangle tri, float[] bary) {
        // Vector from vertex 0 to point
        float dx = px - tri.x0;
        float dy = py - tri.y0;
        float dz = pz - tri.z0;
        
        // Triangle edges
        float e1x = tri.x1 - tri.x0;
        float e1y = tri.y1 - tri.y0;
        float e1z = tri.z1 - tri.z0;
        
        float e2x = tri.x2 - tri.x0;
        float e2y = tri.y2 - tri.y0;
        float e2z = tri.z2 - tri.z0;
        
        // Calculate barycentric coordinates
        float d00 = e1x * e1x + e1y * e1y + e1z * e1z;
        float d01 = e1x * e2x + e1y * e2y + e1z * e2z;
        float d11 = e2x * e2x + e2y * e2y + e2z * e2z;
        float d20 = dx * e1x + dy * e1y + dz * e1z;
        float d21 = dx * e2x + dy * e2y + dz * e2z;
        
        float denom = d00 * d11 - d01 * d01;
        if (Math.abs(denom) < 1e-8f) {
            // Degenerate triangle
            bary[0] = 1.0f;
            bary[1] = 0.0f;
            bary[2] = 0.0f;
            return Float.MAX_VALUE;
        }
        
        float v = (d11 * d20 - d01 * d21) / denom;
        float w = (d00 * d21 - d01 * d20) / denom;
        float u = 1.0f - v - w;
        
        // Clamp barycentric coordinates to triangle
        bary[0] = Math.max(0, Math.min(1, u));
        bary[1] = Math.max(0, Math.min(1, v));
        bary[2] = Math.max(0, Math.min(1, w));
        
        // Normalize if outside triangle
        float sum = bary[0] + bary[1] + bary[2];
        if (sum > 0) {
            bary[0] /= sum;
            bary[1] /= sum;
            bary[2] /= sum;
        }
        
        // Calculate closest point on triangle
        float closestX = tri.x0 * bary[0] + tri.x1 * bary[1] + tri.x2 * bary[2];
        float closestY = tri.y0 * bary[0] + tri.y1 * bary[1] + tri.y2 * bary[2];
        float closestZ = tri.z0 * bary[0] + tri.z1 * bary[1] + tri.z2 * bary[2];
        
        // Distance from point to closest point
        float distX = px - closestX;
        float distY = py - closestY;
        float distZ = pz - closestZ;
        
        return (float) Math.sqrt(distX * distX + distY * distY + distZ * distZ);
    }
    
}
