package com.falcraft.commands;

import com.falcraft.util.BlockPlacer;
import com.falcraft.util.FalAPI;
import com.falcraft.util.GLBParser;
import com.falcraft.util.Voxelizer;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/**
 * Command to generate 3D models from text prompts using fal AI
 * Usage: /fal generate <prompt>
 */
public class GenerateCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger("GenerateCommand");
    private static final int VOXEL_RESOLUTION = 32; // 32x32x32 voxel grid
    
    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("fal")
                .then(literal("generate")
                        .then(argument("prompt", StringArgumentType.greedyString())
                                .executes(GenerateCommand::execute))));
    }
    
    private static int execute(CommandContext<FabricClientCommandSource> context) {
        String prompt = StringArgumentType.getString(context, "prompt");
        FabricClientCommandSource source = context.getSource();
        
        // Send initial feedback
        source.sendFeedback(Component.literal("§e[fal] Starting 3D model generation with prompt: \"" + prompt + "\""));
        source.sendFeedback(Component.literal("§e[fal] This may take several minutes..."));
        
        // Run the generation process asynchronously to avoid blocking the game thread
        new Thread(() -> {
            try {
                LOGGER.info("Starting 3D model generation process...");
                
                // Step 1: Call fal API to generate 3D model
                Minecraft.getInstance().execute(() -> 
                    source.sendFeedback(Component.literal("§e[fal] Generating 3D model with AI...")));
                
                FalAPI falApi = new FalAPI();
                byte[] glbData = falApi.generateModel(prompt);
                
                LOGGER.info("Received GLB model from fal API ({} bytes)", glbData.length);
                Minecraft.getInstance().execute(() ->
                    source.sendFeedback(Component.literal("§e[fal] Model generated! Processing...")));
                
                // Step 2: Parse GLB file
                Minecraft.getInstance().execute(() ->
                    source.sendFeedback(Component.literal("§e[fal] Parsing 3D model...")));
                
                GLBParser.MeshData meshData = GLBParser.parse(glbData);
                LOGGER.info("Parsed GLB: {} vertices, {} indices", 
                        meshData.vertices().length / 3, meshData.indices().length);
                
                // Step 3: Voxelize the mesh
                Minecraft.getInstance().execute(() ->
                    source.sendFeedback(Component.literal("§e[fal] Converting to voxels (" + 
                            VOXEL_RESOLUTION + "x" + VOXEL_RESOLUTION + "x" + VOXEL_RESOLUTION + ")...")));
                
                Voxelizer.VoxelGrid voxelGrid = Voxelizer.voxelize(meshData, VOXEL_RESOLUTION);
                LOGGER.info("Voxelized mesh: {} voxels", voxelGrid.voxels().size());
                
                if (voxelGrid.voxels().isEmpty()) {
                    Minecraft.getInstance().execute(() ->
                        source.sendError(Component.literal("§c[fal] Error: Generated model has no voxels!")));
                    return;
                }
                
                // Step 4: Place blocks in the world (MUST run on main thread)
                Minecraft.getInstance().execute(() -> {
                    try {
                        source.sendFeedback(Component.literal("§e[fal] Placing blocks in world..."));
                        
                        int blocksPlaced = BlockPlacer.placeVoxelGrid(voxelGrid);
                        
                        source.sendFeedback(Component.literal(
                                "§a[fal] ✓ 3D model generated successfully! Placed " + blocksPlaced + " blocks."));
                        LOGGER.info("3D generation process completed successfully");
                        
                    } catch (Exception e) {
                        String errorMsg = e.getMessage();
                        source.sendError(Component.literal("§c[fal] Error placing blocks: " + errorMsg));
                        LOGGER.error("Error placing blocks", e);
                    }
                });
                
            } catch (IllegalStateException e) {
                // Handle missing API key
                Minecraft.getInstance().execute(() ->
                    source.sendError(Component.literal("§c[fal] Error: FAL_API_KEY not found in .env file!")));
                LOGGER.error("FAL_API_KEY not set", e);
            } catch (Exception e) {
                String errorMsg = e.getMessage();
                Minecraft.getInstance().execute(() ->
                    source.sendError(Component.literal("§c[fal] Error during generation: " + errorMsg)));
                LOGGER.error("Error during 3D generation process", e);
            }
        }, "fal-Generate-Thread").start();
        
        return 1;
    }
}

