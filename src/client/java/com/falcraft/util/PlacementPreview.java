package com.falcraft.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Manages the ghost block placement preview system
 * Allows players to see where a 3D structure will be placed before confirming
 * 
 * Supports both:
 * - Static mode: Show complete voxel grid after generation
 * - Streaming mode: Show live voxel updates during diffusion
 */
public class PlacementPreview {
    private static final Logger LOGGER = LoggerFactory.getLogger("PlacementPreview");
    
    private static Voxelizer.VoxelGrid pendingGrid = null;
    private static Map<BlockPos, Integer> surfaceVoxels = null; // Pre-computed surface for preview
    private static boolean isActive = false;
    private static int rotationIndex = 0; // 0=0°, 1=90°, 2=180°, 3=270° (clockwise around Y axis)
    
    // Streaming mode state
    private static boolean isStreaming = false;
    private static Map<BlockPos, Integer> streamingVoxels = null;
    private static int streamingGridSize = 0;
    private static String streamingStage = "";
    private static int streamingStep = 0;
    private static int streamingTotalSteps = 0;
    private static float streamingProgress = 0f;
    private static BlockPos streamingLockedOrigin = null; // Fixed position during streaming
    private static Map<BlockPos, Integer> noiseVoxels = null; // Random noise for "emerging from chaos" effect
    private static final Random NOISE_RANDOM = new Random();
    
    // Animated placement state
    private static boolean isAnimatingPlacement = false;
    private static List<List<Map.Entry<BlockPos, Integer>>> layersByY = null;
    private static int currentLayerIndex = 0;
    private static BlockPos placementOrigin = null;
    private static int placementRotation = 0; // Rotation to apply during placement
    private static int placementGridSize = 0; // Grid size for rotation calculation
    private static final int LAYERS_PER_TICK = 3; // Place 3 Y-levels per tick for smooth animation
    
    /**
     * Starts placement preview mode with the given voxel grid
     * @param grid The voxel grid to preview
     */
    public static void startPlacement(Voxelizer.VoxelGrid grid) {
        pendingGrid = grid;
        isActive = true;
        isStreaming = false;
        rotationIndex = 0; // Reset rotation
        
        // Pre-compute surface voxels for preview rendering
        surfaceVoxels = extractSurfaceVoxels(grid);
        LOGGER.info("Started placement preview mode with {} voxels, {} surface voxels", 
            grid.voxels().size(), surfaceVoxels.size());
    }
    
    // ==================== STREAMING MODE ====================
    
    /**
     * Starts streaming preview mode.
     * In this mode, voxels are updated incrementally as they stream in.
     * The position is LOCKED at start so you can watch the structure form in place.
     * 
     * Also generates random "noise" voxels that create the "emerging from chaos" effect.
     * @param gridSize The target grid size for coordinate scaling
     */
    public static void startStreaming(int gridSize) {
        isStreaming = true;
        isActive = true;
        streamingGridSize = gridSize;
        streamingVoxels = new HashMap<>();
        surfaceVoxels = new HashMap<>();
        pendingGrid = null;
        rotationIndex = 0;
        streamingStage = "starting";
        streamingStep = 0;
        streamingTotalSteps = 0;
        streamingProgress = 0f;
        
        // Lock the position at where player is looking RIGHT NOW
        // This lets them watch the structure form in a fixed spot
        streamingLockedOrigin = calculateStreamingOrigin(gridSize);
        
        // Generate initial noise voxels - random scattered voxels throughout the bounding box
        // These create the "chaos" that resolves into order as diffusion progresses
        noiseVoxels = generateNoiseVoxels(gridSize);
        surfaceVoxels = new HashMap<>(noiseVoxels); // Start with noise visible
        
        LOGGER.info("Started streaming preview mode with grid size {}, {} noise voxels, locked origin at {}", 
            gridSize, noiseVoxels.size(), streamingLockedOrigin);
    }
    
    /**
     * Generates dense noise voxels to create a "cloud" effect.
     * Uses gaussian distribution so noise is denser in the center.
     * This creates the initial "chaos" that will condense into the structure.
     */
    private static Map<BlockPos, Integer> generateNoiseVoxels(int gridSize) {
        Map<BlockPos, Integer> noise = new HashMap<>();
        
        // Generate DENSE noise - aim for ~25-35% fill of the bounding box
        // This creates a proper "cloud" effect rather than sparse floating cubes
        int totalVoxels = gridSize * gridSize * gridSize;
        int noiseCount = (int) (totalVoxels * 0.30); // 30% fill
        noiseCount = Math.min(noiseCount, 15000); // Cap for performance
        noiseCount = Math.max(noiseCount, 2000);  // Minimum for visual effect
        
        // Cyan-ish colors for the magical noise effect
        int[] noiseColors = {
            0x00FFFF, // Cyan
            0x40E0D0, // Turquoise
            0x7FFFD4, // Aquamarine
            0x00CED1, // Dark turquoise
            0x48D1CC, // Medium turquoise
            0x20B2AA, // Light sea green
            0x5F9EA0, // Cadet blue
            0x00BFFF, // Deep sky blue
            0x87CEEB, // Sky blue
        };
        
        float center = gridSize / 2.0f;
        float stdDev = gridSize / 3.0f; // Gaussian spread
        
        for (int i = 0; i < noiseCount; i++) {
            // Use gaussian distribution centered on the middle
            // This makes noise denser in the center where structure will form
            int x = (int) Math.round(center + NOISE_RANDOM.nextGaussian() * stdDev);
            int y = (int) Math.round(center + NOISE_RANDOM.nextGaussian() * stdDev);
            int z = (int) Math.round(center + NOISE_RANDOM.nextGaussian() * stdDev);
            
            // Clamp to grid bounds
            x = Math.max(0, Math.min(gridSize - 1, x));
            y = Math.max(0, Math.min(gridSize - 1, y));
            z = Math.max(0, Math.min(gridSize - 1, z));
            
            int color = noiseColors[NOISE_RANDOM.nextInt(noiseColors.length)];
            noise.put(new BlockPos(x, y, z), color);
        }
        
        return noise;
    }
    
    /**
     * Calculates the origin position for streaming mode (called once at start).
     * Similar to calculatePreviewOrigin but doesn't require pendingGrid.
     */
    private static BlockPos calculateStreamingOrigin(int gridSize) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        
        if (player == null) {
            return BlockPos.ZERO;
        }
        
        // Raycast to find what block the player is looking at
        Vec3 eyePos = player.getEyePosition(1.0f);
        Vec3 lookVec = player.getLookAngle();
        Vec3 endPos = eyePos.add(lookVec.scale(200.0));
        
        net.minecraft.world.level.ClipContext context = new net.minecraft.world.level.ClipContext(
            eyePos,
            endPos,
            net.minecraft.world.level.ClipContext.Block.OUTLINE,
            net.minecraft.world.level.ClipContext.Fluid.NONE,
            player
        );
        
        net.minecraft.world.phys.BlockHitResult hitResult = player.level().clip(context);
        
        BlockPos targetBlock;
        double minDistance = Math.max(gridSize * 1.2, 15.0);
        
        if (hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            Vec3 hitPos = hitResult.getLocation();
            double distanceToHit = eyePos.distanceTo(hitPos);
            
            if (distanceToHit < minDistance) {
                Vec3 targetPos = eyePos.add(lookVec.scale(minDistance));
                targetBlock = BlockPos.containing(targetPos);
                int groundY = findGroundFromAbove(player.level(), targetBlock, (int)eyePos.y);
                targetBlock = new BlockPos(targetBlock.getX(), groundY, targetBlock.getZ());
            } else {
                targetBlock = hitResult.getBlockPos().above();
            }
        } else {
            Vec3 targetPos = eyePos.add(lookVec.scale(minDistance));
            targetBlock = BlockPos.containing(targetPos);
            int groundY = findGroundFromAbove(player.level(), targetBlock, (int)eyePos.y);
            targetBlock = new BlockPos(targetBlock.getX(), groundY, targetBlock.getZ());
        }
        
        // Center the structure horizontally around the target block
        return new BlockPos(
            targetBlock.getX() - gridSize / 2,
            targetBlock.getY(),
            targetBlock.getZ() - gridSize / 2
        );
    }
    
    /**
     * Updates the streaming voxels with new data.
     * Called when new voxel data arrives from the SSE stream.
     * @param voxels The new voxel data
     * @param stage "geometry" or "appearance"
     * @param step Current diffusion step
     * @param totalSteps Total diffusion steps
     * @param progress Overall progress (0.0 - 1.0)
     */
    public static void updateStreamingVoxels(Map<BlockPos, Integer> voxels, String stage, 
            int step, int totalSteps, float progress) {
        if (!isStreaming) return;
        
        streamingVoxels = voxels;
        streamingStage = stage;
        streamingStep = step;
        streamingTotalSteps = totalSteps;
        streamingProgress = progress;
        
        // Blend noise voxels with real voxels for the "chaos → order" effect
        // As progress increases, more noise fades out and real structure emerges
        surfaceVoxels = blendNoiseWithVoxels(voxels, stage, progress);
        
        LOGGER.debug("Updated streaming voxels: {} voxels, stage={}, step={}/{}, blended={}", 
            voxels.size(), stage, step, totalSteps, surfaceVoxels.size());
    }
    
    /**
     * Blends noise voxels with real voxels using proximity-based condensation.
     * Creates a "noise condensing into structure" visual effect.
     * 
     * Key behaviors:
     * - Noise near real voxels stays longer (being "absorbed")
     * - Noise far from structure fades out quickly
     * - Creates visual of chaos condensing into order
     */
    private static Map<BlockPos, Integer> blendNoiseWithVoxels(Map<BlockPos, Integer> realVoxels, 
            String stage, float progress) {
        Map<BlockPos, Integer> blended = new HashMap<>();
        
        // Add real voxels with stage-appropriate coloring
        boolean isGeometry = "geometry".equals(stage);
        
        for (Map.Entry<BlockPos, Integer> entry : realVoxels.entrySet()) {
            int color = entry.getValue();
            
            if (isGeometry) {
                // Geometry phase: Apply cyan tint to all voxels
                color = applyCyanTint(color, 0.8f); // Strong cyan tint
            } else {
                // Appearance phase: Blend from cyan to real color based on progress
                // Progress 0.5 = start of appearance, 1.0 = end
                float appearanceProgress = Math.min(1.0f, (progress - 0.5f) * 2.0f);
                if (appearanceProgress < 1.0f) {
                    int cyanColor = applyCyanTint(color, 1.0f - appearanceProgress);
                    color = blendColors(cyanColor, color, appearanceProgress);
                }
            }
            
            blended.put(entry.getKey(), color);
        }
        
        // Add noise voxels with PROXIMITY-BASED condensation
        // Noise near real voxels stays, noise far away fades quickly
        if (noiseVoxels != null && !noiseVoxels.isEmpty() && progress < 0.85f) {
            
            // Calculate centroid of real voxels (where structure is forming)
            BlockPos centroid = calculateCentroid(realVoxels.keySet());
            
            for (Map.Entry<BlockPos, Integer> entry : noiseVoxels.entrySet()) {
                BlockPos noisePos = entry.getKey();
                
                // Skip if already covered by a real voxel
                if (blended.containsKey(noisePos)) continue;
                
                // Calculate distance to nearest real voxel (approximated by centroid for performance)
                // and distance to any nearby real voxels
                float minDistToReal = Float.MAX_VALUE;
                if (!realVoxels.isEmpty()) {
                    // Check distance to centroid first (fast approximation)
                    float distToCentroid = (float) Math.sqrt(noisePos.distSqr(centroid));
                    minDistToReal = distToCentroid;
                    
                    // For voxels close to centroid, check actual nearest neighbors
                    if (distToCentroid < streamingGridSize * 0.5f) {
                        for (BlockPos realPos : realVoxels.keySet()) {
                            float dist = (float) Math.sqrt(noisePos.distSqr(realPos));
                            if (dist < minDistToReal) {
                                minDistToReal = dist;
                                if (dist < 3) break; // Close enough, no need to check more
                            }
                        }
                    }
                }
                
                // Proximity-based survival probability
                // - Very close (0-4 blocks): High chance to stay (condensing effect)
                // - Medium (5-10 blocks): Medium chance, decreases with progress
                // - Far (11+ blocks): Low chance, fades quickly
                float survivalChance;
                
                if (minDistToReal < 4) {
                    // Close to structure - stays longer (being absorbed)
                    survivalChance = 0.95f - (progress * 0.5f);
                } else if (minDistToReal < 10) {
                    // Medium distance - moderate fade
                    survivalChance = 0.7f - (progress * 1.0f);
                } else {
                    // Far from structure - fades quickly
                    survivalChance = 0.4f - (progress * 1.2f);
                }
                
                // Apply global progress fade on top
                survivalChance *= (1.0f - progress * 0.8f);
                survivalChance = Math.max(0f, Math.min(1f, survivalChance));
                
                // Probabilistically keep this noise voxel
                if (NOISE_RANDOM.nextFloat() < survivalChance) {
                    blended.put(noisePos, entry.getValue());
                }
            }
        }
        
        return blended;
    }
    
    /**
     * Calculates the centroid (center point) of a set of positions.
     */
    private static BlockPos calculateCentroid(Set<BlockPos> positions) {
        if (positions.isEmpty()) {
            return new BlockPos(streamingGridSize / 2, streamingGridSize / 2, streamingGridSize / 2);
        }
        
        long sumX = 0, sumY = 0, sumZ = 0;
        for (BlockPos pos : positions) {
            sumX += pos.getX();
            sumY += pos.getY();
            sumZ += pos.getZ();
        }
        
        int count = positions.size();
        return new BlockPos(
            (int) (sumX / count),
            (int) (sumY / count),
            (int) (sumZ / count)
        );
    }
    
    /**
     * Applies a cyan tint to a color.
     * @param color Original RGB color
     * @param intensity Tint intensity (0.0 = no tint, 1.0 = full cyan)
     * @return Tinted color
     */
    private static int applyCyanTint(int color, float intensity) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        
        // Cyan is (0, 255, 255) - boost G and B, reduce R
        int cyanR = 64;  // Slight red for a more magical look
        int cyanG = 224;
        int cyanB = 255;
        
        r = (int) (r * (1 - intensity) + cyanR * intensity);
        g = (int) (g * (1 - intensity) + cyanG * intensity);
        b = (int) (b * (1 - intensity) + cyanB * intensity);
        
        return (r << 16) | (g << 8) | b;
    }
    
    /**
     * Blends two colors together.
     * @param c1 First color
     * @param c2 Second color
     * @param t Blend factor (0.0 = c1, 1.0 = c2)
     * @return Blended color
     */
    private static int blendColors(int c1, int c2, float t) {
        int r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        
        int r = (int) (r1 * (1 - t) + r2 * t);
        int g = (int) (g1 * (1 - t) + g2 * t);
        int b = (int) (b1 * (1 - t) + b2 * t);
        
        return (r << 16) | (g << 8) | b;
    }
    
    /**
     * Completes streaming mode and converts to normal placement mode.
     * @param finalVoxels The final voxel data
     */
    public static void completeStreaming(Map<BlockPos, Integer> finalVoxels) {
        if (!isStreaming) return;
        
        // Convert to VoxelGrid for placement
        pendingGrid = new Voxelizer.VoxelGrid(finalVoxels, streamingGridSize);
        
        // Re-extract surface voxels for final preview (with real colors, no noise)
        surfaceVoxels = extractSurfaceVoxels(pendingGrid);
        
        isStreaming = false;
        streamingStage = "complete";
        
        // Clear noise and unlock position
        noiseVoxels = null;
        streamingLockedOrigin = null;
        
        LOGGER.info("Streaming complete! {} voxels ready for placement", finalVoxels.size());
    }
    
    /**
     * Cancels streaming mode
     */
    public static void cancelStreaming() {
        if (isStreaming) {
            LOGGER.info("Cancelled streaming preview");
            isStreaming = false;
            isActive = false;
            streamingVoxels = null;
            surfaceVoxels = null;
            pendingGrid = null;
            streamingLockedOrigin = null;
            noiseVoxels = null;
        }
    }
    
    /**
     * @return true if currently in streaming mode
     */
    public static boolean isStreaming() {
        return isStreaming;
    }
    
    /**
     * @return The current streaming stage ("geometry", "appearance", etc.)
     */
    public static String getStreamingStage() {
        return streamingStage;
    }
    
    /**
     * @return The current streaming step
     */
    public static int getStreamingStep() {
        return streamingStep;
    }
    
    /**
     * @return The total streaming steps
     */
    public static int getStreamingTotalSteps() {
        return streamingTotalSteps;
    }
    
    /**
     * @return The streaming progress (0.0 - 1.0)
     */
    public static float getStreamingProgress() {
        return streamingProgress;
    }
    
    /**
     * @return The streaming grid size
     */
    public static int getStreamingGridSize() {
        return streamingGridSize;
    }
    
    /**
     * Rotates the structure 90 degrees clockwise (when viewed from above)
     */
    public static void rotate() {
        if (!isActive) return;
        rotationIndex = (rotationIndex + 1) % 4;
        LOGGER.info("Rotated structure to {} degrees", rotationIndex * 90);
    }
    
    /**
     * Gets the current rotation index (0=0°, 1=90°, 2=180°, 3=270°)
     */
    public static int getRotationIndex() {
        return rotationIndex;
    }
    
    /**
     * Transforms a voxel position based on current rotation.
     * Rotates around the Y axis (up), keeping the structure centered.
     * @param pos Original voxel position (in grid coordinates)
     * @param gridSize The size of the grid
     * @return Rotated position
     */
    public static BlockPos rotatePosition(BlockPos pos, int gridSize) {
        if (rotationIndex == 0) {
            return pos; // No rotation
        }
        
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        int s = gridSize - 1; // Max coordinate
        
        return switch (rotationIndex) {
            case 1 -> new BlockPos(s - z, y, x);      // 90° CW
            case 2 -> new BlockPos(s - x, y, s - z);  // 180°
            case 3 -> new BlockPos(z, y, s - x);      // 270° CW (90° CCW)
            default -> pos;
        };
    }
    
    /**
     * Extracts only the surface voxels (voxels with at least one exposed face)
     * This is much smaller than the full voxel set for preview rendering
     */
    private static Map<BlockPos, Integer> extractSurfaceVoxels(Voxelizer.VoxelGrid grid) {
        Map<BlockPos, Integer> surface = new HashMap<>();
        Set<BlockPos> allVoxels = grid.voxels().keySet();
        
        for (Map.Entry<BlockPos, Integer> entry : grid.voxels().entrySet()) {
            BlockPos pos = entry.getKey();
            
            // Check if any face is exposed (neighbor is not a voxel)
            boolean isExposed = 
                !allVoxels.contains(pos.above()) ||
                !allVoxels.contains(pos.below()) ||
                !allVoxels.contains(pos.north()) ||
                !allVoxels.contains(pos.south()) ||
                !allVoxels.contains(pos.east()) ||
                !allVoxels.contains(pos.west());
            
            if (isExposed) {
                surface.put(pos, entry.getValue());
            }
        }
        
        return surface;
    }
    
    /**
     * @return The surface voxels for preview rendering, or null if not available
     */
    public static Map<BlockPos, Integer> getSurfaceVoxels() {
        return surfaceVoxels;
    }
    
    /**
     * Confirms placement and starts animated bottom-up block placement
     */
    public static void confirmPlacement() {
        if (isActive && pendingGrid != null) {
            LOGGER.info("Starting animated placement of {} blocks (rotation: {}°)", 
                pendingGrid.voxels().size(), rotationIndex * 90);
            
            // Calculate placement origin and save rotation state
            placementOrigin = calculatePreviewOrigin();
            placementRotation = rotationIndex;
            placementGridSize = pendingGrid.size();
            
            // Sort voxels by Y coordinate (bottom to top)
            // Note: Y doesn't change with Y-axis rotation, so original Y is fine
            Map<Integer, List<Map.Entry<BlockPos, Integer>>> voxelsByY = new TreeMap<>();
            
            for (Map.Entry<BlockPos, Integer> entry : pendingGrid.voxels().entrySet()) {
                BlockPos voxelPos = entry.getKey();
                int y = voxelPos.getY();
                voxelsByY.computeIfAbsent(y, k -> new ArrayList<>()).add(entry);
            }
            
            // Convert to list of layers (sorted by Y)
            layersByY = new ArrayList<>(voxelsByY.values());
            currentLayerIndex = 0;
            isAnimatingPlacement = true;
            
            // Exit preview mode (but keep animating)
            isActive = false;
            pendingGrid = null;
            surfaceVoxels = null;
            
            LOGGER.info("Prepared {} layers for animated placement", layersByY.size());
        }
    }
    
    /**
     * Cancels placement preview without placing blocks
     */
    public static void cancelPlacement() {
        if (isActive) {
            LOGGER.info("Cancelled placement preview");
            isActive = false;
            pendingGrid = null;
            surfaceVoxels = null;
            
            // Also cancel streaming if active
            if (isStreaming) {
                isStreaming = false;
                streamingVoxels = null;
            }
        }
    }
    
    /**
     * @return true if placement preview is currently active
     */
    public static boolean isPlacementActive() {
        return isActive;
    }
    
    /**
     * @return The pending voxel grid, or null if not in placement mode
     */
    public static Voxelizer.VoxelGrid getPendingGrid() {
        return pendingGrid;
    }
    
    /**
     * Calculates the current preview origin based on what block the player is looking at
     * Uses raycasting to find the target block, then centers the structure on top of it
     * 
     * During streaming mode, returns the LOCKED origin (fixed at stream start).
     * @return The origin position for the preview
     */
    public static BlockPos calculatePreviewOrigin() {
        if (!isActive) {
            return BlockPos.ZERO;
        }
        
        // During streaming, return the locked origin (fixed position)
        if (isStreaming && streamingLockedOrigin != null) {
            return streamingLockedOrigin;
        }
        
        // Support both streaming mode and normal mode
        if (pendingGrid == null && !isStreaming) {
            return BlockPos.ZERO;
        }
        
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        
        if (player == null) {
            return BlockPos.ZERO;
        }
        
        int gridSize = isStreaming ? streamingGridSize : pendingGrid.size();
        
        // Raycast to find what block the player is looking at
        // Use a longer distance so it works from far away (up to 200 blocks)
        Vec3 eyePos = player.getEyePosition(1.0f);
        Vec3 lookVec = player.getLookAngle();
        Vec3 endPos = eyePos.add(lookVec.scale(200.0)); // 200 block reach
        
        net.minecraft.world.level.ClipContext context = new net.minecraft.world.level.ClipContext(
            eyePos,
            endPos,
            net.minecraft.world.level.ClipContext.Block.OUTLINE,
            net.minecraft.world.level.ClipContext.Fluid.NONE,
            player
        );
        
        net.minecraft.world.phys.BlockHitResult hitResult = player.level().clip(context);
        
        BlockPos targetBlock;
        double minDistance = Math.max(gridSize * 1.2, 15.0); // Minimum comfortable distance
        
        if (hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            Vec3 hitPos = hitResult.getLocation();
            double distanceToHit = eyePos.distanceTo(hitPos);
            
            // If the hit block is too close, place further away instead
            if (distanceToHit < minDistance) {
                Vec3 targetPos = eyePos.add(lookVec.scale(minDistance));
                targetBlock = BlockPos.containing(targetPos);
                
                // Search down from eye level to find ground (up to 100 blocks down)
                int groundY = findGroundFromAbove(player.level(), targetBlock, (int)eyePos.y);
                targetBlock = new BlockPos(targetBlock.getX(), groundY, targetBlock.getZ());
            } else {
                // Hit block is at good distance - place on top of it
                targetBlock = hitResult.getBlockPos().above();
            }
        } else {
            // Not looking at any block - place in front of player
            Vec3 targetPos = eyePos.add(lookVec.scale(minDistance));
            targetBlock = BlockPos.containing(targetPos);
            
            // Search down from eye level to find ground (up to 100 blocks down)
            int groundY = findGroundFromAbove(player.level(), targetBlock, (int)eyePos.y);
            targetBlock = new BlockPos(targetBlock.getX(), groundY, targetBlock.getZ());
        }
        
        // Center the structure horizontally around the target block
        return new BlockPos(
            targetBlock.getX() - gridSize / 2,
            targetBlock.getY(),
            targetBlock.getZ() - gridSize / 2
        );
    }
    
    /**
     * Finds ground by searching downward from a high position
     * Used when placing structures in the air or looking at sky
     */
    private static int findGroundFromAbove(net.minecraft.world.level.Level level, BlockPos startPos, int startY) {
        // Search down from start position (up to 100 blocks)
        for (int y = startY; y >= startY - 100; y--) {
            BlockPos checkPos = new BlockPos(startPos.getX(), y, startPos.getZ());
            net.minecraft.world.level.block.state.BlockState blockState = level.getBlockState(checkPos);
            net.minecraft.world.level.block.state.BlockState aboveState = level.getBlockState(checkPos.above());
            
            // Found solid ground with air above
            if (!blockState.isAir() && aboveState.isAir()) {
                return y + 1; // Place on top
            }
        }
        
        // If no ground found within 100 blocks, place at world bottom + some height
        return Math.max(level.getMinBuildHeight() + 5, startY - 100);
    }
    
    /**
     * @return true if animated placement is currently in progress
     */
    public static boolean isAnimatingPlacement() {
        return isAnimatingPlacement;
    }
    
    /**
     * Advances the animated placement by placing the next batch of layers
     * Call this from a client tick event
     */
    public static void tickAnimatedPlacement() {
        if (!isAnimatingPlacement || layersByY == null) {
            return;
        }
        
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        
        if (player == null) {
            LOGGER.error("Player is null during animated placement!");
            cancelAnimatedPlacement();
            return;
        }
        
        // Get the appropriate level for block placement
        Level level = getPlacementLevel(minecraft);
        if (level == null) {
            LOGGER.error("Level is null during animated placement!");
            cancelAnimatedPlacement();
            return;
        }
        
        // Place the next batch of layers
        int layersPlaced = 0;
        int blocksPlaced = 0;
        
        while (currentLayerIndex < layersByY.size() && layersPlaced < LAYERS_PER_TICK) {
            List<Map.Entry<BlockPos, Integer>> layer = layersByY.get(currentLayerIndex);
            
            // Place all blocks in this layer
            for (Map.Entry<BlockPos, Integer> entry : layer) {
                BlockPos voxelPos = entry.getKey();
                int color = entry.getValue();
                
                // Apply rotation to the voxel position
                BlockPos rotatedPos = applyPlacementRotation(voxelPos);
                
                // Calculate world position
                BlockPos worldPos = placementOrigin.offset(rotatedPos);
                
                // Get the closest matching block
                BlockState blockState = BlockMapper.getClosestBlock(color);
                
                // Place the block
                level.setBlock(worldPos, blockState, 3);
                blocksPlaced++;
            }
            
            currentLayerIndex++;
            layersPlaced++;
        }
        
        // Check if we're done
        if (currentLayerIndex >= layersByY.size()) {
            LOGGER.info("Animated placement complete! Placed {} blocks", getTotalBlockCount());
            isAnimatingPlacement = false;
            layersByY = null;
            placementOrigin = null;
            currentLayerIndex = 0;
        }
    }
    
    /**
     * Cancels the animated placement
     */
    private static void cancelAnimatedPlacement() {
        isAnimatingPlacement = false;
        layersByY = null;
        placementOrigin = null;
        placementRotation = 0;
        placementGridSize = 0;
        currentLayerIndex = 0;
    }
    
    /**
     * Applies the saved placement rotation to a voxel position
     */
    private static BlockPos applyPlacementRotation(BlockPos pos) {
        if (placementRotation == 0) {
            return pos;
        }
        
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        int s = placementGridSize - 1;
        
        return switch (placementRotation) {
            case 1 -> new BlockPos(s - z, y, x);      // 90° CW
            case 2 -> new BlockPos(s - x, y, s - z);  // 180°
            case 3 -> new BlockPos(z, y, s - x);      // 270° CW
            default -> pos;
        };
    }
    
    /**
     * Gets the total number of blocks to place
     */
    private static int getTotalBlockCount() {
        if (layersByY == null) return 0;
        return layersByY.stream().mapToInt(List::size).sum();
    }
    
    /**
     * Gets the appropriate level for block placement
     */
    private static Level getPlacementLevel(Minecraft minecraft) {
        IntegratedServer server = minecraft.getSingleplayerServer();
        
        if (server != null) {
            // Single-player: use the integrated server's level for proper persistence
            ClientLevel clientLevel = minecraft.level;
            if (clientLevel == null) {
                return null;
            }
            
            // Get the server-side level with the same dimension as the client
            ServerLevel serverLevel = server.getLevel(clientLevel.dimension());
            if (serverLevel != null) {
                return serverLevel;
            } else {
                LOGGER.warn("Could not get server level, falling back to client level");
                return clientLevel;
            }
        } else {
            // Multiplayer: we can only place on client side (won't persist)
            return minecraft.level;
        }
    }
}

