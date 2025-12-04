package com.falcraft.util;

import net.minecraft.core.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts 3D mesh data to a voxel grid using the obj2voxel approach:
 * Triangle-voxel clipping with area-based "max wins" strategy.
 * 
 * This is the mathematically correct approach used by professional voxelizers.
 * Reference: https://github.com/eisenwave/obj2voxel
 * 
 * Algorithm:
 * 1. For each triangle, iterate over voxels in its bounding box
 * 2. Clip the triangle against the 6 faces of each voxel (Sutherland-Hodgman)
 * 3. Compute the area of the clipped polygon
 * 4. The triangle with the largest area inside a voxel determines its color
 */
public class Voxelizer {
    private static final Logger LOGGER = LoggerFactory.getLogger("Voxelizer");
    
    /**
     * Represents a voxelized 3D model
     */
    public record VoxelGrid(Map<BlockPos, Integer> voxels, int size) {}
    
    /**
     * 3D Vector for geometry calculations
     */
    private static class Vec3 {
        double x, y, z;
        
        Vec3(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
        
        Vec3 copy() {
            return new Vec3(x, y, z);
        }
        
        static Vec3 sub(Vec3 a, Vec3 b) {
            return new Vec3(a.x - b.x, a.y - b.y, a.z - b.z);
        }
        
        static Vec3 add(Vec3 a, Vec3 b) {
            return new Vec3(a.x + b.x, a.y + b.y, a.z + b.z);
        }
        
        static Vec3 scale(Vec3 v, double s) {
            return new Vec3(v.x * s, v.y * s, v.z * s);
        }
        
        static Vec3 cross(Vec3 a, Vec3 b) {
            return new Vec3(
                a.y * b.z - a.z * b.y,
                a.z * b.x - a.x * b.z,
                a.x * b.y - a.y * b.x
            );
        }
        
        static double dot(Vec3 a, Vec3 b) {
            return a.x * b.x + a.y * b.y + a.z * b.z;
        }
        
        double length() {
            return Math.sqrt(x * x + y * y + z * z);
        }
        
        /**
         * Linear interpolation between two vectors
         */
        static Vec3 lerp(Vec3 a, Vec3 b, double t) {
            return new Vec3(
                a.x + (b.x - a.x) * t,
                a.y + (b.y - a.y) * t,
                a.z + (b.z - a.z) * t
            );
        }
    }
    
    /**
     * A vertex with position and UV coordinates
     */
    private static class Vertex {
        Vec3 pos;
        double u, v;
        
        Vertex(Vec3 pos, double u, double v) {
            this.pos = pos;
            this.u = u;
            this.v = v;
        }
        
        Vertex copy() {
            return new Vertex(pos.copy(), u, v);
        }
        
        /**
         * Linear interpolation between two vertices (position and UVs)
         */
        static Vertex lerp(Vertex a, Vertex b, double t) {
            return new Vertex(
                Vec3.lerp(a.pos, b.pos, t),
                a.u + (b.u - a.u) * t,
                a.v + (b.v - a.v) * t
            );
        }
    }
    
    /**
     * Represents a polygon (triangle or clipped result) with vertices and UVs
     */
    private static class Polygon {
        List<Vertex> vertices;
        
        Polygon() {
            this.vertices = new ArrayList<>();
        }
        
        Polygon(Vertex v0, Vertex v1, Vertex v2) {
            this.vertices = new ArrayList<>();
            vertices.add(v0);
            vertices.add(v1);
            vertices.add(v2);
        }
        
        void addVertex(Vertex v) {
            vertices.add(v);
        }
        
        int size() {
            return vertices.size();
        }
        
        boolean isEmpty() {
            return vertices.size() < 3;
        }
        
        /**
         * Computes the area of this 3D polygon using the cross product method
         */
        double computeArea() {
            if (vertices.size() < 3) return 0;
            
            // Use the Newell method for 3D polygon area
            // Sum cross products of edges from first vertex
            Vec3 sum = new Vec3(0, 0, 0);
            Vertex v0 = vertices.get(0);
            
            for (int i = 1; i < vertices.size() - 1; i++) {
                Vertex v1 = vertices.get(i);
                Vertex v2 = vertices.get(i + 1);
                
                Vec3 e1 = Vec3.sub(v1.pos, v0.pos);
                Vec3 e2 = Vec3.sub(v2.pos, v0.pos);
                Vec3 cross = Vec3.cross(e1, e2);
                
                sum.x += cross.x;
                sum.y += cross.y;
                sum.z += cross.z;
            }
            
            return sum.length() * 0.5;
        }
        
        /**
         * Computes the centroid UV coordinates of this polygon
         */
        double[] computeCentroidUV() {
            if (vertices.isEmpty()) return new double[] {0, 0};
            
            double sumU = 0, sumV = 0;
            for (Vertex v : vertices) {
                sumU += v.u;
                sumV += v.v;
            }
            return new double[] {sumU / vertices.size(), sumV / vertices.size()};
        }
    }
    
    /**
     * Clips a polygon against a plane using Sutherland-Hodgman algorithm.
     * 
     * @param polygon The polygon to clip
     * @param planeNormal The plane normal (points to the "inside" half-space)
     * @param planeD The plane distance (plane equation: dot(normal, point) + d = 0)
     * @return The clipped polygon (may be empty if entirely outside)
     */
    private static Polygon clipPolygonAgainstPlane(Polygon polygon, Vec3 planeNormal, double planeD) {
        if (polygon.isEmpty()) return polygon;
        
        Polygon result = new Polygon();
        int n = polygon.size();
        
        for (int i = 0; i < n; i++) {
            Vertex current = polygon.vertices.get(i);
            Vertex next = polygon.vertices.get((i + 1) % n);
            
            // Signed distance to plane (positive = inside, negative = outside)
            double distCurrent = Vec3.dot(planeNormal, current.pos) + planeD;
            double distNext = Vec3.dot(planeNormal, next.pos) + planeD;
            
            boolean currentInside = distCurrent >= 0;
            boolean nextInside = distNext >= 0;
            
            if (currentInside) {
                // Current vertex is inside, add it
                result.addVertex(current.copy());
            }
            
            // Check if edge crosses the plane
            if (currentInside != nextInside) {
                // Compute intersection point
                double t = distCurrent / (distCurrent - distNext);
                t = Math.max(0, Math.min(1, t)); // Clamp for numerical stability
                Vertex intersection = Vertex.lerp(current, next, t);
                result.addVertex(intersection);
            }
        }
        
        return result;
    }
    
    /**
     * Clips a polygon against a voxel's 6 faces.
     * Returns the portion of the polygon that lies inside the voxel.
     * 
     * @param polygon The polygon to clip
     * @param voxelMin Minimum corner of the voxel (x, y, z)
     * @param voxelMax Maximum corner of the voxel (x+1, y+1, z+1)
     * @return The clipped polygon (empty if triangle doesn't intersect voxel)
     */
    private static Polygon clipPolygonAgainstVoxel(Polygon polygon, Vec3 voxelMin, Vec3 voxelMax) {
        // Clip against all 6 faces of the voxel
        // Each face is defined by a normal pointing inward and a distance
        
        // -X face (normal = +X, keeps points where x >= voxelMin.x)
        polygon = clipPolygonAgainstPlane(polygon, new Vec3(1, 0, 0), -voxelMin.x);
        if (polygon.isEmpty()) return polygon;
        
        // +X face (normal = -X, keeps points where x <= voxelMax.x)
        polygon = clipPolygonAgainstPlane(polygon, new Vec3(-1, 0, 0), voxelMax.x);
        if (polygon.isEmpty()) return polygon;
        
        // -Y face (normal = +Y, keeps points where y >= voxelMin.y)
        polygon = clipPolygonAgainstPlane(polygon, new Vec3(0, 1, 0), -voxelMin.y);
        if (polygon.isEmpty()) return polygon;
        
        // +Y face (normal = -Y, keeps points where y <= voxelMax.y)
        polygon = clipPolygonAgainstPlane(polygon, new Vec3(0, -1, 0), voxelMax.y);
        if (polygon.isEmpty()) return polygon;
        
        // -Z face (normal = +Z, keeps points where z >= voxelMin.z)
        polygon = clipPolygonAgainstPlane(polygon, new Vec3(0, 0, 1), -voxelMin.z);
        if (polygon.isEmpty()) return polygon;
        
        // +Z face (normal = -Z, keeps points where z <= voxelMax.z)
        polygon = clipPolygonAgainstPlane(polygon, new Vec3(0, 0, -1), voxelMax.z);
        
        return polygon;
    }
    
    /**
     * Stores the best (largest area) triangle contribution for each voxel
     */
    private static class VoxelCandidate {
        double area;
        int color;
        
        VoxelCandidate(double area, int color) {
            this.area = area;
            this.color = color;
        }
    }
    
    /**
     * Triangle data with UV coordinates
     */
    private static class Triangle {
        Vertex v0, v1, v2;
        int color0, color1, color2; // Fallback vertex colors
        
        Triangle(Vertex v0, Vertex v1, Vertex v2, int color0, int color1, int color2) {
            this.v0 = v0;
            this.v1 = v1;
            this.v2 = v2;
            this.color0 = color0;
            this.color1 = color1;
            this.color2 = color2;
        }
        
        Polygon toPolygon() {
            return new Polygon(v0.copy(), v1.copy(), v2.copy());
        }
        
        boolean hasValidUVs() {
            return (v0.u != 0 || v0.v != 0 || v1.u != 0 || v1.v != 0 || v2.u != 0 || v2.v != 0);
        }
    }
    
    // Legacy method for compatibility
    public static VoxelGrid voxelize(GLBParser.MeshData mesh, int resolution) {
        return voxelize(mesh, resolution, null);
    }
    
    /**
     * Voxelizes a mesh using triangle-voxel clipping with "max area wins" strategy.
     * This is the obj2voxel approach - mathematically correct voxelization.
     */
    public static VoxelGrid voxelize(GLBParser.MeshData mesh, int resolution, TextureSampler textureSampler) {
        LOGGER.info("Voxelizing mesh with resolution {}x{}x{} using AREA-BASED clipping (obj2voxel approach)", 
            resolution, resolution, resolution);
        
        float[] vertices = mesh.vertices();
        int[] indices = mesh.indices();
        int[] colors = mesh.colors();
        float[] uvs = mesh.uvs();
        
        boolean hasUVs = uvs != null && uvs.length > 0;
        boolean hasTexture = textureSampler != null && hasUVs;
        
        LOGGER.info("Mode: {} (hasUVs={}, hasTexture={})", 
            hasTexture ? "TEXTURE SAMPLING" : "VERTEX COLORS", hasUVs, hasTexture);
        
        if (vertices.length == 0) {
            return new VoxelGrid(new HashMap<>(), resolution);
        }
        
        // Calculate bounding box
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        
        for (int i = 0; i < vertices.length; i += 3) {
            minX = Math.min(minX, vertices[i]);
            minY = Math.min(minY, vertices[i + 1]);
            minZ = Math.min(minZ, vertices[i + 2]);
            maxX = Math.max(maxX, vertices[i]);
            maxY = Math.max(maxY, vertices[i + 1]);
            maxZ = Math.max(maxZ, vertices[i + 2]);
        }
        
        double sizeX = maxX - minX;
        double sizeY = maxY - minY;
        double sizeZ = maxZ - minZ;
        double maxSize = Math.max(Math.max(sizeX, sizeY), sizeZ);
        
        if (maxSize == 0) {
            return new VoxelGrid(new HashMap<>(), resolution);
        }
        
        // Scale to fit in resolution grid with 1 voxel padding
        double scale = (resolution - 2) / maxSize;
        double offsetX = -minX * scale + 1;
        double offsetY = -minY * scale + 1;
        double offsetZ = -minZ * scale + 1;
        
        LOGGER.info("Bounds: ({},{},{}) to ({},{},{}), scale: {}", 
            minX, minY, minZ, maxX, maxY, maxZ, scale);
        
        // Build triangles list
        List<Triangle> triangles = new ArrayList<>();
        for (int i = 0; i < indices.length; i += 3) {
            int idx0 = indices[i];
            int idx1 = indices[i + 1];
            int idx2 = indices[i + 2];
            
            // Transform vertices to voxel grid space
            Vec3 p0 = new Vec3(
                vertices[idx0 * 3] * scale + offsetX,
                vertices[idx0 * 3 + 1] * scale + offsetY,
                vertices[idx0 * 3 + 2] * scale + offsetZ
            );
            Vec3 p1 = new Vec3(
                vertices[idx1 * 3] * scale + offsetX,
                vertices[idx1 * 3 + 1] * scale + offsetY,
                vertices[idx1 * 3 + 2] * scale + offsetZ
            );
            Vec3 p2 = new Vec3(
                vertices[idx2 * 3] * scale + offsetX,
                vertices[idx2 * 3 + 1] * scale + offsetY,
                vertices[idx2 * 3 + 2] * scale + offsetZ
            );
            
            // Get UVs
            double u0 = hasUVs ? uvs[idx0 * 2] : 0;
            double v0 = hasUVs ? uvs[idx0 * 2 + 1] : 0;
            double u1 = hasUVs ? uvs[idx1 * 2] : 0;
            double v1 = hasUVs ? uvs[idx1 * 2 + 1] : 0;
            double u2 = hasUVs ? uvs[idx2 * 2] : 0;
            double v2 = hasUVs ? uvs[idx2 * 2 + 1] : 0;
            
            Vertex vert0 = new Vertex(p0, u0, v0);
            Vertex vert1 = new Vertex(p1, u1, v1);
            Vertex vert2 = new Vertex(p2, u2, v2);
            
            triangles.add(new Triangle(vert0, vert1, vert2, colors[idx0], colors[idx1], colors[idx2]));
        }
        
        LOGGER.info("Processing {} triangles with area-based clipping...", triangles.size());
        
        // Track best candidate for each voxel (MAX AREA WINS)
        Map<BlockPos, VoxelCandidate> candidates = new HashMap<>();
        
        // Stats
        int trianglesProcessed = 0;
        int voxelsUpdated = 0;
        int clipsPerformed = 0;
        
        // Process each triangle
        for (Triangle tri : triangles) {
            trianglesProcessed++;
            
            // Get bounding box in voxel coordinates
            int minVX = (int) Math.floor(Math.min(tri.v0.pos.x, Math.min(tri.v1.pos.x, tri.v2.pos.x)));
            int minVY = (int) Math.floor(Math.min(tri.v0.pos.y, Math.min(tri.v1.pos.y, tri.v2.pos.y)));
            int minVZ = (int) Math.floor(Math.min(tri.v0.pos.z, Math.min(tri.v1.pos.z, tri.v2.pos.z)));
            int maxVX = (int) Math.ceil(Math.max(tri.v0.pos.x, Math.max(tri.v1.pos.x, tri.v2.pos.x)));
            int maxVY = (int) Math.ceil(Math.max(tri.v0.pos.y, Math.max(tri.v1.pos.y, tri.v2.pos.y)));
            int maxVZ = (int) Math.ceil(Math.max(tri.v0.pos.z, Math.max(tri.v1.pos.z, tri.v2.pos.z)));
            
            // Clamp to grid
            minVX = Math.max(0, minVX);
            minVY = Math.max(0, minVY);
            minVZ = Math.max(0, minVZ);
            maxVX = Math.min(resolution - 1, maxVX);
            maxVY = Math.min(resolution - 1, maxVY);
            maxVZ = Math.min(resolution - 1, maxVZ);
            
            // Process each voxel in the bounding box
            for (int vx = minVX; vx <= maxVX; vx++) {
                for (int vy = minVY; vy <= maxVY; vy++) {
                    for (int vz = minVZ; vz <= maxVZ; vz++) {
                        // Clip triangle against this voxel
                        Polygon clipped = clipPolygonAgainstVoxel(
                            tri.toPolygon(),
                            new Vec3(vx, vy, vz),
                            new Vec3(vx + 1, vy + 1, vz + 1)
                        );
                        clipsPerformed++;
                        
                        if (clipped.isEmpty()) continue;
                        
                        // Compute area of clipped polygon
                        double area = clipped.computeArea();
                        if (area < 1e-10) continue;
                        
                        // Sample color at centroid of clipped polygon
                        int color;
                        if (hasTexture && tri.hasValidUVs()) {
                            double[] centroidUV = clipped.computeCentroidUV();
                            color = textureSampler.sample((float) centroidUV[0], (float) centroidUV[1]);
                        } else {
                            // Fallback: average vertex colors
                            int r = ((tri.color0 >> 16) & 0xFF + (tri.color1 >> 16) & 0xFF + (tri.color2 >> 16) & 0xFF) / 3;
                            int g = ((tri.color0 >> 8) & 0xFF + (tri.color1 >> 8) & 0xFF + (tri.color2 >> 8) & 0xFF) / 3;
                            int b = ((tri.color0) & 0xFF + (tri.color1) & 0xFF + (tri.color2) & 0xFF) / 3;
                            color = (r << 16) | (g << 8) | b;
                        }
                        
                        // MAX AREA WINS: Only update if this triangle has larger area in this voxel
                        BlockPos pos = new BlockPos(vx, vy, vz);
                        VoxelCandidate existing = candidates.get(pos);
                        
                        if (existing == null || area > existing.area) {
                            candidates.put(pos, new VoxelCandidate(area, color));
                            voxelsUpdated++;
                        }
                    }
                }
            }
            
            // Progress logging
            if (trianglesProcessed % 10000 == 0) {
                LOGGER.info("Progress: {}/{} triangles, {} voxels", trianglesProcessed, triangles.size(), candidates.size());
            }
        }
        
        // Convert candidates to final voxel map
        Map<BlockPos, Integer> voxels = new HashMap<>();
        for (Map.Entry<BlockPos, VoxelCandidate> entry : candidates.entrySet()) {
            voxels.put(entry.getKey(), entry.getValue().color);
        }
        
        LOGGER.info("Voxelization complete: {} voxels from {} triangles", voxels.size(), triangles.size());
        LOGGER.info("Stats: {} clips performed, {} voxel updates", clipsPerformed, voxelsUpdated);
        
        return new VoxelGrid(voxels, resolution);
    }
}
