package com.falcraft.commands;

import com.falcraft.util.ClientTextureGrabber;
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

import java.io.File;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class RemixCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger("RemixCommand");

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
                
                // Step 1: Grab the texture of the block being looked at
                Minecraft.getInstance().execute(() -> 
                    source.sendFeedback(Component.literal("§e[fal] Extracting current texture...")));
                ClientTextureGrabber.GrabResult grabResult = ClientTextureGrabber.grabTargetedBlockTexture();
                
                if (grabResult == null) {
                    Minecraft.getInstance().execute(() ->
                        source.sendError(Component.literal("§c[fal] Error: You must be looking at a block!")));
                    return;
                }
                
                File textureFile = grabResult.textureFile;
                String blockId = grabResult.blockId;
                
                LOGGER.info("Grabbed texture for block: {}", blockId);
                Minecraft.getInstance().execute(() ->
                    source.sendFeedback(Component.literal("§e[fal] Extracted texture for: " + blockId)));
                
                // Step 2: Call fal API to remix the texture
                Minecraft.getInstance().execute(() ->
                    source.sendFeedback(Component.literal("§e[fal] Sending to fal for remixing...")));
                FalAPI falApi = new FalAPI();
                byte[] remixedTexture = falApi.remixTexture(textureFile, prompt);
                
                LOGGER.info("Received remixed texture from fal API");
                Minecraft.getInstance().execute(() ->
                    source.sendFeedback(Component.literal("§e[fal] Received remixed texture!")));
                
                // Clean up the temporary texture file
                textureFile.delete();
                textureFile.getParentFile().delete();
                
                // Step 3: Write the remixed texture to the resource pack
                Minecraft.getInstance().execute(() ->
                    source.sendFeedback(Component.literal("§e[fal] Writing texture to resource pack...")));
                PackIO packIO = new PackIO();
                packIO.writeTexture(blockId, remixedTexture);
                
                LOGGER.info("Wrote remixed texture to resource pack");
                
                // Step 4: Reload resource packs to apply the change
                Minecraft.getInstance().execute(() ->
                    source.sendFeedback(Component.literal("§e[fal] Reloading resource packs...")));
                packIO.reloadResourcePacks();
                
                // Success message
                Minecraft.getInstance().execute(() ->
                    source.sendFeedback(Component.literal("§a[fal] ✓ Texture remix complete! The new texture has been applied.")));
                LOGGER.info("Remix process completed successfully");
                
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

