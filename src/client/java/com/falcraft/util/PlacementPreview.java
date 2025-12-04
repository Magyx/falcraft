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
 */
public class PlacementPreview {
    private static final Logger LOGGER = LoggerFactory.getLogger("PlacementPreview");
    
    private static Voxelizer.VoxelGrid pendingGrid = null;
    private static Map<BlockPos, Integer> surfaceVoxels = null; // Pre-computed surface for preview
    private static boolean isActive = false;
    private static int rotationIndex = 0; // 0=0°, 1=90°, 2=180°, 3=270° (clockwise around Y axis)
    
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
        rotationIndex = 0; // Reset rotation
        
        // Pre-compute surface voxels for preview rendering
        surfaceVoxels = extractSurfaceVoxels(grid);
        LOGGER.info("Started placement preview mode with {} voxels, {} surface voxels", 
            grid.voxels().size(), surfaceVoxels.size());
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
     * @return The origin position for the preview
     */
    public static BlockPos calculatePreviewOrigin() {
        if (!isActive || pendingGrid == null) {
            return BlockPos.ZERO;
        }
        
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        
        if (player == null) {
            return BlockPos.ZERO;
        }
        
        int gridSize = pendingGrid.size();
        
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

