package com.falcraft.commands;

import com.falcraft.util.BlockPlacer;
import com.falcraft.util.FalAPI;
import com.falcraft.util.GLBParser;
import com.falcraft.util.PlacementPreview;
import com.falcraft.util.TextureSampler;
import com.falcraft.util.Voxelizer;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

import com.mojang.brigadier.suggestion.SuggestionProvider;

/**
 * Command to generate 3D models from text prompts using fal AI
 * Usage: /fal generate <size> <prompt>          - Z-Image + SAM-3D (~30 seconds, default)
 *        /fal generate legacy <size> <prompt>   - Meshy-6 (~7 minutes, original method)
 * Size: 16-128 (recommended: 32=fast, 48=balanced, 64=detailed)
 */
public class GenerateCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger("GenerateCommand");
    
    // Suggest all size values (16-128 in increments of 16) so they appear before "legacy" in autocomplete
    private static final SuggestionProvider<FabricClientCommandSource> SIZE_SUGGESTIONS = (context, builder) -> {
        builder.suggest(16, Component.literal("Tiny"));
        builder.suggest(32, Component.literal("Small"));
        builder.suggest(48, Component.literal("Medium"));
        builder.suggest(64, Component.literal("Large"));
        builder.suggest(80, Component.literal("Extra Large"));
        builder.suggest(96, Component.literal("Huge"));
        builder.suggest(112, Component.literal("Massive"));
        builder.suggest(128, Component.literal("Maximum"));
        return builder.buildFuture();
    };
    
    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("fal")
                .then(literal("generate")
                        // Default mode: /fal generate <size> <prompt> (Z-Image + SAM-3D)
                        .then(argument("size", IntegerArgumentType.integer(16, 128))
                                .suggests(SIZE_SUGGESTIONS)
                                .then(argument("prompt", StringArgumentType.greedyString())
                                        .executes(GenerateCommand::execute)))
                        // Legacy mode: /fal generate legacy <size> <prompt> (Meshy-6)
                        .then(literal("legacy")
                                .then(argument("size", IntegerArgumentType.integer(16, 128))
                                        .suggests(SIZE_SUGGESTIONS)
                                        .then(argument("prompt", StringArgumentType.greedyString())
                                                .executes(GenerateCommand::executeLegacy))))));
    }
    
    /**
     * Legacy generation mode using Meshy-6 (original method)
     * Slower but uses UV-mapped textures (~7 minutes)
     */
    private static int executeLegacy(CommandContext<FabricClientCommandSource> context) {
        int size = IntegerArgumentType.getInteger(context, "size");
        String prompt = StringArgumentType.getString(context, "prompt");
        FabricClientCommandSource source = context.getSource();
        
        // Send initial feedback
        source.sendFeedback(Component.literal("§e[fal] Starting §7LEGACY§e 3D generation (" + size + "x" + size + "x" + size + ")"));
        source.sendFeedback(Component.literal("§e[fal] Prompt: \"" + prompt + "\""));
        source.sendFeedback(Component.literal("§e[fal] Using Meshy-6 pipeline (about 7 minutes)"));
        
        // Run the generation process asynchronously to avoid blocking the game thread
        new Thread(() -> {
            try {
                // Step 1: Call fal API to generate 3D model
                Minecraft.getInstance().execute(() -> 
                    source.sendFeedback(Component.literal("§e[fal] Generating 3D model with AI...")));
                
                FalAPI falApi = new FalAPI();
                FalAPI.ModelResult modelResult = falApi.generateModel(prompt);
                
                Minecraft.getInstance().execute(() ->
                    source.sendFeedback(Component.literal("§e[fal] Model generated! Processing...")));
                
                // Step 2: Extract embedded texture from GLB (more reliable than external texture_urls)
                TextureSampler textureSampler = null;
                try {
                    Minecraft.getInstance().execute(() ->
                        source.sendFeedback(Component.literal("§e[fal] Extracting texture from GLB...")));
                    
                    byte[] embeddedTexture = GLBParser.extractEmbeddedTexture(modelResult.glbData());
                    if (embeddedTexture != null) {
                        textureSampler = new TextureSampler(embeddedTexture);
                        Minecraft.getInstance().execute(() ->
                            source.sendFeedback(Component.literal("§e[fal] Embedded texture extracted!")));
                    } else {
                        Minecraft.getInstance().execute(() ->
                            source.sendFeedback(Component.literal("§6[fal] No embedded texture, will use vertex colors")));
                    }
                } catch (Exception e) {
                    LOGGER.error("Failed to extract embedded texture: {}", e.getMessage(), e);
                    Minecraft.getInstance().execute(() ->
                        source.sendFeedback(Component.literal("§6[fal] Could not extract texture")));
                }
                
                // Step 3: Parse GLB file with texture sampling
                Minecraft.getInstance().execute(() ->
                    source.sendFeedback(Component.literal("§e[fal] Parsing 3D model...")));
                
                GLBParser.MeshData meshData = GLBParser.parse(modelResult.glbData(), textureSampler);
                
                // Step 4: Voxelize the mesh with per-voxel texture sampling
                final TextureSampler finalTextureSampler = textureSampler;
                Minecraft.getInstance().execute(() ->
                    source.sendFeedback(Component.literal("§e[fal] Converting to voxels (" + 
                            size + "x" + size + "x" + size + ")...")));
                
                // Pass the texture sampler to enable per-voxel UV-based color sampling
                Voxelizer.VoxelGrid voxelGrid = Voxelizer.voxelize(meshData, size, finalTextureSampler);
                LOGGER.info("Voxelized mesh: {} voxels", voxelGrid.voxels().size());
                
                if (voxelGrid.voxels().isEmpty()) {
                    Minecraft.getInstance().execute(() ->
                        source.sendError(Component.literal("§c[fal] Error: Generated model has no voxels!")));
                    return;
                }
                
                // Step 5: Enter placement preview mode (MUST run on main thread)
                Minecraft.getInstance().execute(() -> {
                    try {
                        PlacementPreview.startPlacement(voxelGrid);
                        
                        source.sendFeedback(Component.literal(
                                "§a[fal] ✓ §7LEGACY§a generation complete! " + voxelGrid.voxels().size() + " blocks ready."));
                        source.sendFeedback(Component.literal(
                                "§e[fal] Right-click to place, G to rotate!"));
                    } catch (Exception e) {
                        String errorMsg = e.getMessage();
                        source.sendError(Component.literal("§c[fal] Error preparing placement: " + errorMsg));
                        LOGGER.error("Error preparing placement", e);
                    }
                });
                
            } catch (IllegalStateException e) {
                // Handle missing API key
                Minecraft.getInstance().execute(() ->
                    source.sendError(Component.literal("§c[fal] Error: API key not configured. Use /fal setkey <key>")));
                LOGGER.error("API key not configured", e);
            } catch (Exception e) {
                String errorMsg = e.getMessage();
                Minecraft.getInstance().execute(() ->
                    source.sendError(Component.literal("§c[fal] Error during legacy generation: " + errorMsg)));
                LOGGER.error("Error during LEGACY 3D generation process", e);
            }
        }, "fal-LegacyGenerate-Thread").start();
        
        return 1;
    }
    
    /**
     * Default generation mode using Z-Image Turbo + SAM-3D pipeline
     * Fast and high quality (~30 seconds)
     */
    private static int execute(CommandContext<FabricClientCommandSource> context) {
        int size = IntegerArgumentType.getInteger(context, "size");
        String prompt = StringArgumentType.getString(context, "prompt");
        FabricClientCommandSource source = context.getSource();
        
        // Send initial feedback
        source.sendFeedback(Component.literal("§e[fal] Starting 3D generation (" + size + "x" + size + "x" + size + ")"));
        source.sendFeedback(Component.literal("§e[fal] Prompt: \"" + prompt + "\""));
        source.sendFeedback(Component.literal("§e[fal] Using Z-Image + SAM-3D (about 30 seconds)"));
        
        // Run the generation process asynchronously
        new Thread(() -> {
            try {
                LOGGER.info("Starting 3D model generation process...");
                
                // Step 1: Generate image and convert to 3D
                Minecraft.getInstance().execute(() -> 
                    source.sendFeedback(Component.literal("§e[fal] [1/4] Generating 2D image...")));
                
                FalAPI falApi = new FalAPI();
                
                // This chains Z-Image (text→image) + SAM-3 (image→3D)
                // The generateModelFast method handles both steps internally
                FalAPI.ModelResult modelResult = falApi.generateModelFast(prompt);
                
                LOGGER.info("Received GLB model ({} bytes)", modelResult.glbData().length);
                Minecraft.getInstance().execute(() ->
                    source.sendFeedback(Component.literal("§e[fal] [2/4] 3D model generated! Processing...")));
                
                // Step 2: Extract embedded texture from GLB
                TextureSampler textureSampler = null;
                try {
                    byte[] embeddedTexture = GLBParser.extractEmbeddedTexture(modelResult.glbData());
                    if (embeddedTexture != null) {
                        textureSampler = new TextureSampler(embeddedTexture);
                    }
                } catch (Exception e) {
                    LOGGER.error("Failed to extract embedded texture: {}", e.getMessage(), e);
                }
                
                // Step 3: Parse GLB file
                Minecraft.getInstance().execute(() ->
                    source.sendFeedback(Component.literal("§e[fal] [3/4] Parsing 3D model...")));
                
                GLBParser.MeshData meshData = GLBParser.parse(modelResult.glbData(), textureSampler);
                
                // Step 4: Voxelize the mesh
                final TextureSampler finalTextureSampler = textureSampler;
                Minecraft.getInstance().execute(() ->
                    source.sendFeedback(Component.literal("§e[fal] [4/4] Converting to voxels (" + 
                            size + "x" + size + "x" + size + ")...")));
                
                Voxelizer.VoxelGrid voxelGrid = Voxelizer.voxelize(meshData, size, finalTextureSampler);
                
                if (voxelGrid.voxels().isEmpty()) {
                    Minecraft.getInstance().execute(() ->
                        source.sendError(Component.literal("§c[fal] Error: Generated model has no voxels!")));
                    return;
                }
                
                // Step 5: Enter placement preview mode
                Minecraft.getInstance().execute(() -> {
                    try {
                        PlacementPreview.startPlacement(voxelGrid);
                        
                        source.sendFeedback(Component.literal(
                                "§a[fal] ✓ Generation complete! " + voxelGrid.voxels().size() + " blocks ready."));
                        source.sendFeedback(Component.literal(
                                "§e[fal] Right-click to place, G to rotate!"));
                    } catch (Exception e) {
                        String errorMsg = e.getMessage();
                        source.sendError(Component.literal("§c[fal] Error preparing placement: " + errorMsg));
                        LOGGER.error("Error preparing placement", e);
                    }
                });
                
            } catch (IllegalStateException e) {
                Minecraft.getInstance().execute(() ->
                    source.sendError(Component.literal("§c[fal] Error: API key not configured. Use /fal setkey <key>")));
                LOGGER.error("API key not configured", e);
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

