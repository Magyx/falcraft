package com.falcraft.util;

import net.minecraft.core.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Professional-grade voxelizer using ray-casting with BVH acceleration.
 * Instead of iterating triangles and claiming voxels (push approach),
 * this iterates voxels and finds the closest surface (pull approach).
 * 
 * This produces cleaner results because each voxel gets its color from
 * the mathematically closest surface point, not whichever triangle
 * happened to process first.
 */
public class RayCastVoxelizer {
    private static final Logger LOGGER = LoggerFactory.getLogger("RayCastVoxelizer");
    
    /**
     * Represents a voxelized 3D model
     */
    public record VoxelGrid(Map<BlockPos, Integer> voxels, int size) {}
    
    // ==================== GEOMETRY PRIMITIVES ====================
    
    /**
     * 3D Vector for ray-casting math
     */
    private static class Vec3 {
        float x, y, z;
        
        Vec3(float x, float y, float z) {
            this.x = x; this.y = y; this.z = z;
        }
        
        Vec3 sub(Vec3 other) {
            return new Vec3(x - other.x, y - other.y, z - other.z);
        }
        
        Vec3 add(Vec3 other) {
            return new Vec3(x + other.x, y + other.y, z + other.z);
        }
        
        Vec3 scale(float s) {
            return new Vec3(x * s, y * s, z * s);
        }
        
        float dot(Vec3 other) {
            return x * other.x + y * other.y + z * other.z;
        }
        
        Vec3 cross(Vec3 other) {
            return new Vec3(
                y * other.z - z * other.y,
                z * other.x - x * other.z,
                x * other.y - y * other.x
            );
        }
        
        float length() {
            return (float) Math.sqrt(x * x + y * y + z * z);
        }
        
        Vec3 normalize() {
            float len = length();
            if (len > 0) {
                return new Vec3(x / len, y / len, z / len);
            }
            return new Vec3(0, 0, 0);
        }
    }
    
    /**
     * Axis-Aligned Bounding Box
     */
    private static class AABB {
        Vec3 min, max;
        
        AABB(Vec3 min, Vec3 max) {
            this.min = min;
            this.max = max;
        }
        
        AABB() {
            this.min = new Vec3(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE);
            this.max = new Vec3(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE);
        }
        
        void expand(Vec3 point) {
            min = new Vec3(Math.min(min.x, point.x), Math.min(min.y, point.y), Math.min(min.z, point.z));
            max = new Vec3(Math.max(max.x, point.x), Math.max(max.y, point.y), Math.max(max.z, point.z));
        }
        
        void expand(AABB other) {
            min = new Vec3(Math.min(min.x, other.min.x), Math.min(min.y, other.min.y), Math.min(min.z, other.min.z));
            max = new Vec3(Math.max(max.x, other.max.x), Math.max(max.y, other.max.y), Math.max(max.z, other.max.z));
        }
        
        Vec3 center() {
            return new Vec3((min.x + max.x) / 2, (min.y + max.y) / 2, (min.z + max.z) / 2);
        }
        
        /**
         * Ray-AABB intersection test (slab method)
         */
        boolean intersectsRay(Vec3 origin, Vec3 dirInv, float tMax) {
            float t1 = (min.x - origin.x) * dirInv.x;
            float t2 = (max.x - origin.x) * dirInv.x;
            float t3 = (min.y - origin.y) * dirInv.y;
            float t4 = (max.y - origin.y) * dirInv.y;
            float t5 = (min.z - origin.z) * dirInv.z;
            float t6 = (max.z - origin.z) * dirInv.z;
            
            float tmin = Math.max(Math.max(Math.min(t1, t2), Math.min(t3, t4)), Math.min(t5, t6));
            float tmax = Math.min(Math.min(Math.max(t1, t2), Math.max(t3, t4)), Math.max(t5, t6));
            
            return tmax >= 0 && tmin <= tmax && tmin <= tMax;
        }
    }
    
    /**
     * Triangle with UV coordinates for texture sampling
     */
    private static class Triangle {
        Vec3 v0, v1, v2;           // Vertices
        Vec3 normal;               // Face normal (for front/back face detection)
        float u0, v0uv, u1, v1uv, u2, v2uv;  // UV coordinates
        AABB bounds;
        
        Triangle(Vec3 v0, Vec3 v1, Vec3 v2, float u0, float v0uv, float u1, float v1uv, float u2, float v2uv) {
            this.v0 = v0; this.v1 = v1; this.v2 = v2;
            this.u0 = u0; this.v0uv = v0uv;
            this.u1 = u1; this.v1uv = v1uv;
            this.u2 = u2; this.v2uv = v2uv;
            
            // Compute face normal (cross product of edges)
            Vec3 edge1 = v1.sub(v0);
            Vec3 edge2 = v2.sub(v0);
            this.normal = edge1.cross(edge2).normalize();
            
            // Compute bounding box
            this.bounds = new AABB();
            bounds.expand(v0);
            bounds.expand(v1);
            bounds.expand(v2);
        }
        
        Vec3 centroid() {
            return new Vec3(
                (v0.x + v1.x + v2.x) / 3,
                (v0.y + v1.y + v2.y) / 3,
                (v0.z + v1.z + v2.z) / 3
            );
        }
        
        /**
         * Check if a ray is hitting the front face of this triangle
         * Front face = ray direction and normal point in opposite directions (dot < 0)
         */
        boolean isFrontFacing(Vec3 rayDir) {
            return rayDir.dot(normal) < 0;
        }
    }
    
    /**
     * Result of a ray-triangle intersection
     */
    private static class HitResult {
        float t;      // Distance along ray
        float u, v;   // Barycentric coordinates (for UV interpolation)
        Triangle tri; // Hit triangle
        
        HitResult(float t, float u, float v, Triangle tri) {
            this.t = t; this.u = u; this.v = v; this.tri = tri;
        }
        
        float[] getUV() {
            // Interpolate UV using barycentric coordinates
            float w = 1.0f - u - v;
            float texU = w * tri.u0 + u * tri.u1 + v * tri.u2;
            float texV = w * tri.v0uv + u * tri.v1uv + v * tri.v2uv;
            return new float[] { texU, texV };
        }
    }
    
    // ==================== BVH (Bounding Volume Hierarchy) ====================
    
    /**
     * BVH Node - either a leaf containing triangles, or an internal node with children
     */
    private static class BVHNode {
        AABB bounds;
        BVHNode left, right;
        List<Triangle> triangles; // Only set for leaf nodes
        
        boolean isLeaf() {
            return triangles != null;
        }
    }
    
    /**
     * Builds a BVH from a list of triangles
     */
    private static BVHNode buildBVH(List<Triangle> triangles, int depth) {
        BVHNode node = new BVHNode();
        
        // Compute bounds
        node.bounds = new AABB();
        for (Triangle tri : triangles) {
            node.bounds.expand(tri.bounds);
        }
        
        // Leaf node if few triangles or max depth reached
        if (triangles.size() <= 4 || depth > 20) {
            node.triangles = new ArrayList<>(triangles);
            return node;
        }
        
        // Find best split axis (longest dimension)
        Vec3 size = node.bounds.max.sub(node.bounds.min);
        int axis;
        if (size.x >= size.y && size.x >= size.z) axis = 0;
        else if (size.y >= size.z) axis = 1;
        else axis = 2;
        
        // Sort triangles by centroid along split axis
        final int splitAxis = axis;
        triangles.sort((a, b) -> {
            Vec3 ca = a.centroid();
            Vec3 cb = b.centroid();
            float va = (splitAxis == 0) ? ca.x : (splitAxis == 1) ? ca.y : ca.z;
            float vb = (splitAxis == 0) ? cb.x : (splitAxis == 1) ? cb.y : cb.z;
            return Float.compare(va, vb);
        });
        
        // Split in half
        int mid = triangles.size() / 2;
        node.left = buildBVH(triangles.subList(0, mid), depth + 1);
        node.right = buildBVH(triangles.subList(mid, triangles.size()), depth + 1);
        
        return node;
    }
    
    // ==================== RAY-TRIANGLE INTERSECTION ====================
    
    /**
     * Möller–Trumbore ray-triangle intersection algorithm
     * Returns HitResult if hit, null otherwise
     */
    private static HitResult rayTriangleIntersect(Vec3 origin, Vec3 dir, Triangle tri, float tMax) {
        final float EPSILON = 1e-7f;
        
        Vec3 edge1 = tri.v1.sub(tri.v0);
        Vec3 edge2 = tri.v2.sub(tri.v0);
        Vec3 h = dir.cross(edge2);
        float a = edge1.dot(h);
        
        if (a > -EPSILON && a < EPSILON) {
            return null; // Ray parallel to triangle
        }
        
        float f = 1.0f / a;
        Vec3 s = origin.sub(tri.v0);
        float u = f * s.dot(h);
        
        if (u < 0.0f || u > 1.0f) {
            return null;
        }
        
        Vec3 q = s.cross(edge1);
        float v = f * dir.dot(q);
        
        if (v < 0.0f || u + v > 1.0f) {
            return null;
        }
        
        float t = f * edge2.dot(q);
        
        if (t > EPSILON && t < tMax) {
            return new HitResult(t, u, v, tri);
        }
        
        return null;
    }
    
    /**
     * Find closest intersection by traversing BVH
     */
    private static HitResult findClosestHit(BVHNode node, Vec3 origin, Vec3 dir, Vec3 dirInv, float tMax) {
        if (!node.bounds.intersectsRay(origin, dirInv, tMax)) {
            return null;
        }
        
        if (node.isLeaf()) {
            HitResult closest = null;
            for (Triangle tri : node.triangles) {
                HitResult hit = rayTriangleIntersect(origin, dir, tri, tMax);
                if (hit != null && (closest == null || hit.t < closest.t)) {
                    closest = hit;
                    tMax = hit.t;
                }
            }
            return closest;
        }
        
        // Traverse children
        HitResult hitLeft = findClosestHit(node.left, origin, dir, dirInv, tMax);
        if (hitLeft != null) {
            tMax = hitLeft.t;
        }
        HitResult hitRight = findClosestHit(node.right, origin, dir, dirInv, tMax);
        
        if (hitRight != null && (hitLeft == null || hitRight.t < hitLeft.t)) {
            return hitRight;
        }
        return hitLeft;
    }
    
    // ==================== MAIN VOXELIZATION ====================
    
    /**
     * Voxelizes a mesh using ray-casting (pull approach)
     * For each voxel, finds the closest surface and samples its texture.
     */
    public static VoxelGrid voxelize(GLBParser.MeshData mesh, int resolution, TextureSampler textureSampler) {
        LOGGER.info("Ray-cast voxelizing mesh with resolution {}x{}x{}", resolution, resolution, resolution);
        
        float[] vertices = mesh.vertices();
        int[] indices = mesh.indices();
        float[] uvs = mesh.uvs();
        
        boolean hasUVs = uvs != null && uvs.length > 0;
        boolean hasTexture = textureSampler != null && hasUVs;
        
        LOGGER.info("Mode: RAY-CASTING with BVH (hasUVs={}, hasTexture={})", hasUVs, hasTexture);
        
        if (vertices.length == 0) {
            LOGGER.warn("Empty mesh, returning empty voxel grid");
            return new VoxelGrid(new HashMap<>(), resolution);
        }
        
        // Build triangles
        List<Triangle> triangles = new ArrayList<>();
        AABB meshBounds = new AABB();
        
        for (int i = 0; i < indices.length; i += 3) {
            int idx0 = indices[i];
            int idx1 = indices[i + 1];
            int idx2 = indices[i + 2];
            
            Vec3 v0 = new Vec3(vertices[idx0 * 3], vertices[idx0 * 3 + 1], vertices[idx0 * 3 + 2]);
            Vec3 v1 = new Vec3(vertices[idx1 * 3], vertices[idx1 * 3 + 1], vertices[idx1 * 3 + 2]);
            Vec3 v2 = new Vec3(vertices[idx2 * 3], vertices[idx2 * 3 + 1], vertices[idx2 * 3 + 2]);
            
            float u0 = hasUVs ? uvs[idx0 * 2] : 0;
            float uv0 = hasUVs ? uvs[idx0 * 2 + 1] : 0;
            float u1 = hasUVs ? uvs[idx1 * 2] : 0;
            float uv1 = hasUVs ? uvs[idx1 * 2 + 1] : 0;
            float u2 = hasUVs ? uvs[idx2 * 2] : 0;
            float uv2 = hasUVs ? uvs[idx2 * 2 + 1] : 0;
            
            Triangle tri = new Triangle(v0, v1, v2, u0, uv0, u1, uv1, u2, uv2);
            triangles.add(tri);
            
            meshBounds.expand(tri.bounds);
        }
        
        LOGGER.info("Built {} triangles", triangles.size());
        LOGGER.info("Mesh bounds: ({},{},{}) to ({},{},{})", 
            meshBounds.min.x, meshBounds.min.y, meshBounds.min.z,
            meshBounds.max.x, meshBounds.max.y, meshBounds.max.z);
        
        // Build BVH
        long bvhStart = System.currentTimeMillis();
        BVHNode bvh = buildBVH(new ArrayList<>(triangles), 0);
        LOGGER.info("Built BVH in {}ms", System.currentTimeMillis() - bvhStart);
        
        // Calculate voxel grid parameters
        Vec3 size = meshBounds.max.sub(meshBounds.min);
        float maxSize = Math.max(Math.max(size.x, size.y), size.z);
        float voxelSize = maxSize / (resolution - 1);
        
        LOGGER.info("Voxel size: {}", voxelSize);
        
        // Ray-cast from each potential voxel position
        Map<BlockPos, Integer> voxels = new HashMap<>();
        Vec3 meshCenter = meshBounds.center();
        
        long castStart = System.currentTimeMillis();
        int raysTotal = 0;
        int hitsTotal = 0;
        int backFaceRejections = 0;
        
        // For each voxel in the grid
        for (int vx = 0; vx < resolution; vx++) {
            for (int vy = 0; vy < resolution; vy++) {
                for (int vz = 0; vz < resolution; vz++) {
                    // Calculate voxel center in world space
                    float worldX = meshBounds.min.x + (vx + 0.5f) * voxelSize;
                    float worldY = meshBounds.min.y + (vy + 0.5f) * voxelSize;
                    float worldZ = meshBounds.min.z + (vz + 0.5f) * voxelSize;
                    Vec3 voxelCenter = new Vec3(worldX, worldY, worldZ);
                    
                    // Cast rays in 6 directions to find nearest FRONT-FACING surface
                    // Front-facing = we're hitting the surface from outside (ray · normal < 0)
                    HitResult closestHit = null;
                    float closestDist = Float.MAX_VALUE;
                    
                    // 6 axis-aligned directions
                    Vec3[] directions = {
                        new Vec3(1, 0, 0), new Vec3(-1, 0, 0),
                        new Vec3(0, 1, 0), new Vec3(0, -1, 0),
                        new Vec3(0, 0, 1), new Vec3(0, 0, -1)
                    };
                    
                    for (Vec3 dir : directions) {
                        raysTotal++;
                        Vec3 dirInv = new Vec3(
                            dir.x != 0 ? 1.0f / dir.x : Float.MAX_VALUE,
                            dir.y != 0 ? 1.0f / dir.y : Float.MAX_VALUE,
                            dir.z != 0 ? 1.0f / dir.z : Float.MAX_VALUE
                        );
                        
                        HitResult hit = findClosestHit(bvh, voxelCenter, dir, dirInv, Float.MAX_VALUE);
                        if (hit != null) {
                            // ONLY accept front-facing hits (hitting surface from outside)
                            // This prevents voxels inside the chest from sampling the cape behind them
                            if (hit.tri.isFrontFacing(dir)) {
                                if (hit.t < closestDist) {
                                    closestDist = hit.t;
                                    closestHit = hit;
                                }
                            } else {
                                backFaceRejections++;
                            }
                        }
                    }
                    
                    // If we hit a front-facing surface within threshold, this is a surface voxel
                    if (closestHit != null && closestDist < voxelSize * 0.75f) {
                        hitsTotal++;
                        
                        int color;
                        if (hasTexture) {
                            float[] uv = closestHit.getUV();
                            color = textureSampler.sample(uv[0], uv[1]);
                        } else {
                            // Fallback: gray based on position
                            int gray = (int) ((worldX * 50 + worldY * 50 + worldZ * 50) % 128) + 64;
                            color = (gray << 16) | (gray << 8) | gray;
                        }
                        
                        voxels.put(new BlockPos(vx, vy, vz), color);
                    }
                }
            }
        }
        
        LOGGER.info("Ray-casting complete in {}ms", System.currentTimeMillis() - castStart);
        LOGGER.info("Cast {} rays, {} surface voxels found, {} back-face rejections", raysTotal, hitsTotal, backFaceRejections);
        LOGGER.info("Voxelized mesh: {} voxels", voxels.size());
        
        return new VoxelGrid(voxels, resolution);
    }
}

