package com.falcraft.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/**
 * Commands for Falcraft configuration
 * Usage: /fal setkey <key> - Set the fal.ai API key
 *        /fal status       - Check if API key is configured
 */
public class ConfigCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger("ConfigCommand");
    
    /** Represents where the API key was loaded from */
    public enum KeySource {
        CONFIG_FILE("config file"),
        ENV_FILE(".env file"),
        ENVIRONMENT_VAR("environment variable"),
        NOT_FOUND("not configured");
        
        private final String displayName;
        KeySource(String displayName) { this.displayName = displayName; }
        public String getDisplayName() { return displayName; }
    }
    
    /** Result containing the API key and its source */
    public record KeyInfo(String key, KeySource source) {}
    
    /**
     * Gets the path to the API key config file
     */
    public static Path getApiKeyPath() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config")
                .resolve("falcraft")
                .resolve("api-key.txt");
    }
    
    /**
     * Checks if an API key is configured (either in config file, .env, or environment)
     */
    public static boolean isApiKeyConfigured() {
        return getApiKeyWithSource().key() != null;
    }
    
    /**
     * Gets the configured API key, or null if not configured
     */
    public static String getApiKey() {
        KeyInfo info = getApiKeyWithSource();
        return info.key();
    }
    
    /**
     * Gets the configured API key along with information about where it was loaded from
     */
    public static KeyInfo getApiKeyWithSource() {
        // Check config file first (highest priority)
        Path configPath = getApiKeyPath();
        if (Files.exists(configPath)) {
            try {
                String key = Files.readString(configPath).trim();
                if (!key.isEmpty()) {
                    return new KeyInfo(key, KeySource.CONFIG_FILE);
                }
            } catch (IOException ignored) {}
        }
        
        // Check .env file in game directory
        Path envFile = Minecraft.getInstance().gameDirectory.toPath().resolve(".env");
        if (Files.exists(envFile)) {
            try {
                String content = Files.readString(envFile);
                for (String line : content.split("\\r?\\n")) {
                    line = line.trim();
                    if (line.startsWith("FAL_API_KEY=")) {
                        String value = line.substring("FAL_API_KEY=".length()).trim();
                        // Remove quotes if present
                        if ((value.startsWith("\"") && value.endsWith("\"")) ||
                            (value.startsWith("'") && value.endsWith("'"))) {
                            value = value.substring(1, value.length() - 1);
                        }
                        if (!value.isEmpty()) {
                            return new KeyInfo(value, KeySource.ENV_FILE);
                        }
                    }
                }
            } catch (IOException ignored) {}
        }
        
        // Check environment variable
        String envKey = System.getenv("FAL_API_KEY");
        if (envKey != null && !envKey.isEmpty()) {
            return new KeyInfo(envKey, KeySource.ENVIRONMENT_VAR);
        }
        
        return new KeyInfo(null, KeySource.NOT_FOUND);
    }
    
    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(
            literal("fal")
                .then(literal("setkey")
                    .then(argument("key", StringArgumentType.string())
                        .executes(context -> executeSetKey(context.getSource(), 
                            StringArgumentType.getString(context, "key")))))
                .then(literal("status")
                    .executes(context -> executeStatus(context.getSource())))
        );
    }
    
    private static int executeSetKey(FabricClientCommandSource source, String key) {
        // Validate key format (basic check - fal keys typically start with certain patterns)
        if (key == null || key.trim().isEmpty()) {
            source.sendError(Component.literal("§c[fal] Error: API key cannot be empty!"));
            return 0;
        }
        
        key = key.trim();
        
        // Save the key to config file
        try {
            Path configPath = getApiKeyPath();
            
            // Create parent directories if they don't exist
            Files.createDirectories(configPath.getParent());
            
            // Write the key to file
            Files.writeString(configPath, key);
            
            source.sendFeedback(Component.literal("§a[fal] ✓ API key saved successfully!"));
            source.sendFeedback(Component.literal("§7[fal] Key stored in: config/falcraft/api-key.txt"));
            source.sendFeedback(Component.literal("§e[fal] You can now use /fal generate!"));
            
            LOGGER.info("API key saved to config file");
            return 1;
            
        } catch (IOException e) {
            source.sendError(Component.literal("§c[fal] Error saving API key: " + e.getMessage()));
            LOGGER.error("Failed to save API key", e);
            return 0;
        }
    }
    
    private static int executeStatus(FabricClientCommandSource source) {
        source.sendFeedback(Component.literal("§6[fal] §lFalcraft Status"));
        source.sendFeedback(Component.literal("§7-------------------"));
        
        // Check API key status and source
        KeyInfo keyInfo = getApiKeyWithSource();
        
        if (keyInfo.key() != null) {
            // Show masked key for security
            String key = keyInfo.key();
            String maskedKey = key.length() > 8 
                ? key.substring(0, 4) + "****" + key.substring(key.length() - 4)
                : "****";
            source.sendFeedback(Component.literal("§aAPI Key: §2Configured §7(" + maskedKey + ")"));
            source.sendFeedback(Component.literal("§7Source: §f" + keyInfo.source().getDisplayName()));
        } else {
            source.sendFeedback(Component.literal("§cAPI Key: §4Not configured"));
            source.sendFeedback(Component.literal("§7  Use: §e/fal setkey <your-key>"));
            source.sendFeedback(Component.literal("§7  Get a key at: §bhttps://fal.ai/dashboard/keys"));
        }
        
        return 1;
    }
}

