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

/**
 * Command to generate 3D models from text prompts using fal AI
 * Usage: /fal generate <size> <prompt>
 * Size: 16-128 (recommended: 32=fast, 48=balanced, 64=detailed)
 */
public class GenerateCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger("GenerateCommand");
    
    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("fal")
                .then(literal("generate")
                        .then(argument("size", IntegerArgumentType.integer(16, 128))
                                .then(argument("prompt", StringArgumentType.greedyString())
                                        .executes(GenerateCommand::execute)))));
    }
    
    private static int execute(CommandContext<FabricClientCommandSource> context) {
        int size = IntegerArgumentType.getInteger(context, "size");
        String prompt = StringArgumentType.getString(context, "prompt");
        FabricClientCommandSource source = context.getSource();
        
        // Send initial feedback
        source.sendFeedback(Component.literal("§e[fal] Starting 3D model generation (" + size + "x" + size + "x" + size + ")"));
        source.sendFeedback(Component.literal("§e[fal] Prompt: \"" + prompt + "\""));
        source.sendFeedback(Component.literal("§e[fal] This may take several minutes..."));
        
        // Run the generation process asynchronously to avoid blocking the game thread
        new Thread(() -> {
            try {
                LOGGER.info("Starting 3D model generation process...");
                
                // Step 1: Call fal API to generate 3D model
                Minecraft.getInstance().execute(() -> 
                    source.sendFeedback(Component.literal("§e[fal] Generating 3D model with AI...")));
                
                FalAPI falApi = new FalAPI();
                FalAPI.ModelResult modelResult = falApi.generateModel(prompt);
                
                LOGGER.info("Received GLB model from fal API ({} bytes)", modelResult.glbData().length);
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
                        LOGGER.info("Loaded embedded texture from GLB: {} bytes", embeddedTexture.length);
                        
                        // DEBUG: Save embedded texture to disk for inspection
                        try {
                            Path debugPath = Paths.get("debug_texture_embedded.jpg");
                            Files.write(debugPath, embeddedTexture);
                            LOGGER.info("DEBUG: Saved embedded texture to: {}", debugPath.toAbsolutePath());
                        } catch (Exception ex) {
                            LOGGER.warn("Could not save debug texture: {}", ex.getMessage());
                        }
                        
                        Minecraft.getInstance().execute(() ->
                            source.sendFeedback(Component.literal("§e[fal] Embedded texture extracted!")));
                    } else {
                        LOGGER.warn("No embedded texture found in GLB");
                        Minecraft.getInstance().execute(() ->
                            source.sendFeedback(Component.literal("§6[fal] No embedded texture, will use vertex colors")));
                    }
                } catch (Exception e) {
                    LOGGER.error("Failed to extract embedded texture: {}", e.getMessage(), e);
                    Minecraft.getInstance().execute(() ->
                        source.sendFeedback(Component.literal("§6[fal] Could not extract texture")));
                }
                
                // DEBUG: Also download and save external texture for comparison
                if (modelResult.textureUrl() != null) {
                    try {
                        LOGGER.info("DEBUG: Downloading external texture from texture_urls for comparison...");
                        byte[] externalTexture = falApi.downloadFile(modelResult.textureUrl());
                        Path externalPath = Paths.get("debug_texture_external.png");
                        Files.write(externalPath, externalTexture);
                        LOGGER.info("DEBUG: Saved external texture to: {}", externalPath.toAbsolutePath());
                    } catch (Exception ex) {
                        LOGGER.warn("Could not save external debug texture: {}", ex.getMessage());
                    }
                }
                
                // Step 3: Parse GLB file with texture sampling
                Minecraft.getInstance().execute(() ->
                    source.sendFeedback(Component.literal("§e[fal] Parsing 3D model...")));
                
                GLBParser.MeshData meshData = GLBParser.parse(modelResult.glbData(), textureSampler);
                LOGGER.info("Parsed GLB: {} vertices, {} indices", 
                        meshData.vertices().length / 3, meshData.indices().length);
                
                // Step 4: Voxelize the mesh
                Minecraft.getInstance().execute(() ->
                    source.sendFeedback(Component.literal("§e[fal] Converting to voxels (" + 
                            size + "x" + size + "x" + size + ")...")));
                
                Voxelizer.VoxelGrid voxelGrid = Voxelizer.voxelize(meshData, size);
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
                                "§a[fal] ✓ 3D model generated successfully! " + voxelGrid.voxels().size() + " blocks ready."));
                        source.sendFeedback(Component.literal(
                                "§e[fal] Right-click to place the structure!"));
                        LOGGER.info("3D generation process completed, entering placement preview mode");
                        
                    } catch (Exception e) {
                        String errorMsg = e.getMessage();
                        source.sendError(Component.literal("§c[fal] Error preparing placement: " + errorMsg));
                        LOGGER.error("Error preparing placement", e);
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

