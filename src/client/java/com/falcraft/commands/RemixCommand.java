package com.falcraft.commands;

import com.falcraft.util.ClientTextureGrabber;
import com.falcraft.util.ClientTextureGrabber.MultiGrabResult;
import com.falcraft.util.ClientTextureGrabber.TextureEntry;
import com.falcraft.util.FalAPI;
import com.falcraft.util.PackIO;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class RemixCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger("RemixCommand");

    /**
     * Holds the result of a single texture remix operation
     */
    private static class RemixResult {
        final String textureName;
        final byte[] pngBytes;
        
        RemixResult(String textureName, byte[] pngBytes) {
            this.textureName = textureName;
            this.pngBytes = pngBytes;
        }
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("fal")
                .then(literal("remix")
                        .then(argument("prompt", StringArgumentType.greedyString())
                                .executes(RemixCommand::execute))));
    }

    private static int execute(CommandContext<FabricClientCommandSource> context) {
        String prompt = StringArgumentType.getString(context, "prompt");
        FabricClientCommandSource source = context.getSource();
        
        // Send initial feedback
        source.sendFeedback(Component.literal("§e[fal] Starting texture remix with prompt: \"" + prompt + "\""));
        
        // Run the remix process asynchronously to avoid blocking the game thread
        new Thread(() -> {
            try {
                LOGGER.info("Starting remix process...");
                
                // Step 1: Grab ALL textures from the block being looked at
                Minecraft.getInstance().execute(() -> 
                    source.sendFeedback(Component.literal("§e[fal] Extracting block textures...")));
                
                MultiGrabResult grabResult = ClientTextureGrabber.grabAllBlockTextures();
                
                if (grabResult == null) {
                    Minecraft.getInstance().execute(() ->
                        source.sendError(Component.literal("§c[fal] Error: You must be looking at a block!")));
                    return;
                }
                
                int textureCount = grabResult.textures.size();
                LOGGER.info("Found {} unique textures for block: {}", textureCount, grabResult.blockId);
                
                // Build list of texture names for display
                StringBuilder textureList = new StringBuilder();
                for (int i = 0; i < grabResult.textures.size(); i++) {
                    if (i > 0) textureList.append(", ");
                    textureList.append(grabResult.textures.get(i).textureName);
                }
                
                final String textureNames = textureList.toString();
                Minecraft.getInstance().execute(() ->
                    source.sendFeedback(Component.literal("§e[fal] Found " + textureCount + " texture(s): " + textureNames)));
                
                // Step 2: Send parallel API requests for each unique texture
                Minecraft.getInstance().execute(() ->
                    source.sendFeedback(Component.literal("§e[fal] Sending " + textureCount + " texture(s) to fal for remixing...")));
                
                FalAPI falApi = new FalAPI();
                List<CompletableFuture<RemixResult>> futures = new ArrayList<>();
                AtomicInteger completedCount = new AtomicInteger(0);
                
                for (TextureEntry texture : grabResult.textures) {
                    CompletableFuture<RemixResult> future = CompletableFuture.supplyAsync(() -> {
                        try {
                            LOGGER.info("Remixing texture: {}", texture.textureName);
                            byte[] remixed = falApi.remixTexture(texture.textureFile, prompt);
                            
                            int done = completedCount.incrementAndGet();
                            Minecraft.getInstance().execute(() ->
                                source.sendFeedback(Component.literal("§e[fal] ✓ Remixed " + texture.textureName + " (" + done + "/" + textureCount + ")")));
                            
                            return new RemixResult(texture.textureName, remixed);
                        } catch (Exception e) {
                            LOGGER.error("Failed to remix texture: {}", texture.textureName, e);
                            throw new RuntimeException("Failed to remix " + texture.textureName + ": " + e.getMessage(), e);
                        }
                    });
                    futures.add(future);
                }
                
                // Wait for all API calls to complete
                LOGGER.info("Waiting for {} API calls to complete...", futures.size());
                CompletableFuture<Void> allFutures = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
                
                try {
                    allFutures.join();
                } catch (Exception e) {
                    // One or more futures failed
                    Minecraft.getInstance().execute(() ->
                        source.sendError(Component.literal("§c[fal] Error: Some textures failed to remix. Check logs.")));
                    grabResult.cleanup();
                    return;
                }
                
                // Clean up the temporary texture files
                grabResult.cleanup();
                
                // Step 3: Write all remixed textures to the resource pack
                Minecraft.getInstance().execute(() ->
                    source.sendFeedback(Component.literal("§e[fal] Writing " + textureCount + " texture(s) to resource pack...")));
                
                PackIO packIO = new PackIO();
                int writtenCount = 0;
                
                for (CompletableFuture<RemixResult> future : futures) {
                    try {
                        RemixResult result = future.get();
                        packIO.writeTexture(result.textureName, result.pngBytes);
                        writtenCount++;
                        LOGGER.info("Wrote remixed texture: {}", result.textureName);
                    } catch (Exception e) {
                        LOGGER.error("Failed to write texture", e);
                    }
                }
                
                if (writtenCount == 0) {
                    Minecraft.getInstance().execute(() ->
                        source.sendError(Component.literal("§c[fal] Error: No textures were written!")));
                    return;
                }
                
                // Step 4: Single reload to apply all textures at once
                Minecraft.getInstance().execute(() ->
                    source.sendFeedback(Component.literal("§e[fal] Reloading resource packs...")));
                packIO.reloadResourcePacks();
                
                // Success message
                final int finalWrittenCount = writtenCount;
                Minecraft.getInstance().execute(() ->
                    source.sendFeedback(Component.literal("§a[fal] ✓ Texture remix complete! Applied " + finalWrittenCount + " new texture(s).")));
                LOGGER.info("Remix process completed successfully - {} textures applied", writtenCount);
                
            } catch (IllegalStateException e) {
                // Handle missing API key
                Minecraft.getInstance().execute(() ->
                    source.sendError(Component.literal("§c[fal] Error: FAL_API_KEY not found in .env file!")));
                LOGGER.error("FAL_API_KEY not set", e);
            } catch (Exception e) {
                String errorMsg = e.getMessage();
                Minecraft.getInstance().execute(() ->
                    source.sendError(Component.literal("§c[fal] Error during remix: " + errorMsg)));
                LOGGER.error("Error during remix process", e);
            }
        }, "fal-Remix-Thread").start();
        
        return 1;
    }
}
