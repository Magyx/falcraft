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

import java.util.Map;

/**
 * Places voxel grids as blocks in the Minecraft world
 */
public class BlockPlacer {
    private static final Logger LOGGER = LoggerFactory.getLogger("BlockPlacer");
    
    /**
     * Places a voxel grid in the Minecraft world
     * The grid is placed based on the player's look direction
     * @param grid The voxel grid to place
     * @return The number of blocks placed
     */
    public static int placeVoxelGrid(Voxelizer.VoxelGrid grid) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        
        if (player == null) {
            LOGGER.error("Player is null!");
            return 0;
        }
        
        // Get the appropriate level based on whether we're in single-player or multiplayer
        Level level;
        IntegratedServer server = minecraft.getSingleplayerServer();
        
        if (server != null) {
            // Single-player: use the integrated server's level for proper persistence
            ClientLevel clientLevel = minecraft.level;
            if (clientLevel == null) {
                LOGGER.error("Client level is null!");
                return 0;
            }
            
            // Get the server-side level with the same dimension as the client
            ServerLevel serverLevel = server.getLevel(clientLevel.dimension());
            if (serverLevel != null) {
                level = serverLevel;
                LOGGER.info("Using server-side level for block placement (single-player)");
            } else {
                LOGGER.warn("Could not get server level, falling back to client level");
                level = clientLevel;
            }
        } else {
            // Multiplayer: we can only place on client side (won't persist)
            level = minecraft.level;
            LOGGER.warn("Multiplayer detected - blocks may not persist! Consider using a server-side mod.");
        }
        
        if (level == null) {
            LOGGER.error("Level is null!");
            return 0;
        }
        
        // Calculate placement origin based on player look direction
        BlockPos origin = calculatePlacementOrigin(player, grid.size());
        
        LOGGER.info("Placing voxel grid at origin: {} (level side: {})", origin, level.isClientSide() ? "CLIENT" : "SERVER");
        
        int blocksPlaced = 0;
        
        // Place each voxel as a block
        for (Map.Entry<BlockPos, Integer> entry : grid.voxels().entrySet()) {
            BlockPos voxelPos = entry.getKey();
            int color = entry.getValue();
            
            // Calculate world position
            BlockPos worldPos = origin.offset(voxelPos);
            
            // Get the closest matching block
            BlockState blockState = BlockMapper.getClosestBlock(color);
            
            // Place the block with proper flags for server-side placement
            // Flags: 3 = UPDATE_NEIGHBORS | BLOCK_UPDATE (notify neighbors and update)
            level.setBlock(worldPos, blockState, 3);
            blocksPlaced++;
        }
        
        LOGGER.info("Placed {} blocks on {} side", blocksPlaced, level.isClientSide() ? "CLIENT" : "SERVER");
        
        return blocksPlaced;
    }
    
    /**
     * Calculates where to place the voxel grid based on player look direction
     * Places the model a few blocks away in the direction the player is looking
     */
    private static BlockPos calculatePlacementOrigin(LocalPlayer player, int gridSize) {
        Vec3 playerPos = player.position();
        Vec3 lookVec = player.getLookAngle();
        
        // Place the model at a distance based on its size
        // Larger models are placed further away
        double distance = Math.max(gridSize * 0.7, 5.0);
        
        // Calculate target position in look direction
        Vec3 targetPos = playerPos.add(
            lookVec.x * distance,
            lookVec.y * distance,
            lookVec.z * distance
        );
        
        // Center the grid at the target position
        // Offset by half the grid size to center it
        BlockPos origin = BlockPos.containing(
            targetPos.x - gridSize / 2.0,
            targetPos.y - gridSize / 2.0,
            targetPos.z - gridSize / 2.0
        );
        
        // Ensure the model doesn't spawn too low (at least at player's feet level)
        if (origin.getY() < player.getBlockY()) {
            origin = new BlockPos(origin.getX(), player.getBlockY(), origin.getZ());
        }
        
        return origin;
    }
}

