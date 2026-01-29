package com.falcraft.commands;

import com.falcraft.util.PlacementPreview;
import com.falcraft.util.SAM3DStreamAPI;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

import com.mojang.brigadier.suggestion.SuggestionProvider;

/**
 * Command to stream 3D model generation with live diffusion preview.
 * Usage: /fal stream <size> <prompt>
 * 
 * Unlike /fal generate which waits for the full model before showing preview,
 * this command streams the diffusion process so you can watch the structure
 * emerge from noise in real-time!
 * 
 * Stages:
 * - geometry: Shape forms from noise (gray blocks)
 * - appearance: Colors appear on the shape
 * - complete: Ready for placement
 */
public class StreamCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger("StreamCommand");
    
    // Keep reference to current API instance for cancellation
    private static final AtomicReference<SAM3DStreamAPI> currentStreamAPI = new AtomicReference<>(null);
    private static Thread currentStreamThread = null;
    
    // Suggest size values
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
                .then(literal("stream")
                        .then(argument("size", IntegerArgumentType.integer(16, 128))
                                .suggests(SIZE_SUGGESTIONS)
                                .then(argument("prompt", StringArgumentType.greedyString())
                                        .executes(StreamCommand::execute)))
                        // Cancel subcommand
                        .then(literal("cancel")
                                .executes(StreamCommand::executeCancel))));
    }
    
    /**
     * Cancels the current streaming operation
     */
    private static int executeCancel(CommandContext<FabricClientCommandSource> context) {
        FabricClientCommandSource source = context.getSource();
        
        SAM3DStreamAPI api = currentStreamAPI.get();
        if (api != null) {
            api.cancel();
            PlacementPreview.cancelStreaming();
            source.sendFeedback(Component.literal("§e[fal] Streaming cancelled."));
        } else {
            source.sendFeedback(Component.literal("§7[fal] No active stream to cancel."));
        }
        
        return 1;
    }
    
    /**
     * Executes the streaming generation command
     */
    private static int execute(CommandContext<FabricClientCommandSource> context) {
        int size = IntegerArgumentType.getInteger(context, "size");
        String prompt = StringArgumentType.getString(context, "prompt");
        FabricClientCommandSource source = context.getSource();
        
        // Check for existing stream
        if (PlacementPreview.isStreaming()) {
            source.sendError(Component.literal("§c[fal] A stream is already in progress! Use /fal stream cancel to stop it."));
            return 0;
        }
        
        // Send initial feedback
        source.sendFeedback(Component.literal("§b[fal] ✨ Starting streaming 3D generation..."));
        source.sendFeedback(Component.literal("§b[fal] Prompt: \"" + prompt + "\""));
        source.sendFeedback(Component.literal("§7[fal] Watch the structure emerge from noise!"));
        
        // Start streaming preview mode
        Minecraft.getInstance().execute(() -> {
            PlacementPreview.startStreaming(size);
        });
        
        // Create the stream thread
        currentStreamThread = new Thread(() -> {
            try {
                SAM3DStreamAPI api = new SAM3DStreamAPI();
                currentStreamAPI.set(api);
                
                api.streamGenerate(prompt, size, new SAM3DStreamAPI.StreamCallback() {
                    private int lastReportedStep = -1;
                    
                    @Override
                    public void onVoxelUpdate(Map<BlockPos, Integer> voxels, String stage, 
                            int step, int totalSteps, float progress) {
                        
                        // Update preview on main thread
                        Minecraft.getInstance().execute(() -> {
                            PlacementPreview.updateStreamingVoxels(voxels, stage, step, totalSteps, progress);
                        });
                        
                        // Show progress in action bar (every 5 steps to avoid spam)
                        if (step != lastReportedStep && step % 5 == 0) {
                            lastReportedStep = step;
                            int percent = Math.round(progress * 100);
                            String stageIcon = "geometry".equals(stage) ? "🔷" : "🎨";
                            String progressBar = buildProgressBar(progress);
                            
                            Minecraft.getInstance().execute(() -> {
                                Minecraft.getInstance().player.displayClientMessage(
                                    Component.literal("§b" + stageIcon + " " + stage + " " + progressBar + " " + percent + "%"),
                                    true // Action bar
                                );
                            });
                        }
                    }
                    
                    @Override
                    public void onComplete(Map<BlockPos, Integer> finalVoxels) {
                        Minecraft.getInstance().execute(() -> {
                            PlacementPreview.completeStreaming(finalVoxels);
                            
                            source.sendFeedback(Component.literal(
                                    "§a[fal] ✓ Streaming complete! " + finalVoxels.size() + " blocks ready."));
                            source.sendFeedback(Component.literal(
                                    "§e[fal] Right-click to place, G to rotate!"));
                            
                            Minecraft.getInstance().player.displayClientMessage(
                                Component.literal("§a✓ Ready to place! Right-click to build"),
                                true
                            );
                        });
                        
                        currentStreamAPI.set(null);
                    }
                    
                    @Override
                    public void onError(String error) {
                        Minecraft.getInstance().execute(() -> {
                            PlacementPreview.cancelStreaming();
                            
                            if (!"Cancelled".equals(error)) {
                                source.sendError(Component.literal("§c[fal] Stream error: " + error));
                            }
                        });
                        
                        currentStreamAPI.set(null);
                    }
                    
                    @Override
                    public void onStatus(String message) {
                        Minecraft.getInstance().execute(() -> {
                            source.sendFeedback(Component.literal("§7[fal] " + message));
                        });
                    }
                });
                
            } catch (IllegalStateException e) {
                Minecraft.getInstance().execute(() -> {
                    PlacementPreview.cancelStreaming();
                    source.sendError(Component.literal("§c[fal] Error: API key not configured. Use /fal setkey <key>"));
                });
            } catch (Exception e) {
                LOGGER.error("Stream generation failed", e);
                Minecraft.getInstance().execute(() -> {
                    PlacementPreview.cancelStreaming();
                    source.sendError(Component.literal("§c[fal] Stream error: " + e.getMessage()));
                });
            } finally {
                currentStreamAPI.set(null);
            }
        }, "fal-Stream-Thread");
        
        currentStreamThread.start();
        
        return 1;
    }
    
    /**
     * Builds a simple progress bar string
     */
    private static String buildProgressBar(float progress) {
        int filled = Math.round(progress * 10);
        int empty = 10 - filled;
        return "§a" + "█".repeat(filled) + "§8" + "░".repeat(empty);
    }
}
