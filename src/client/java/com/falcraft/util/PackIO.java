package com.falcraft.util;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

public class PackIO {
    private static final Logger LOGGER = LoggerFactory.getLogger("PackIO");
    private static final String PACK_NAME = "falcraft_generated";
    private static final Gson GSON = new Gson();
    
    private final Path packRootPath;
    private final Path assetsPath;

    public PackIO() {
        // Get the Minecraft game directory
        File gameDir = Minecraft.getInstance().gameDirectory;
        
        // Create the resource pack path
        this.packRootPath = gameDir.toPath().resolve("resourcepacks").resolve(PACK_NAME);
        this.assetsPath = packRootPath.resolve("assets").resolve("minecraft").resolve("textures").resolve("block");
        
        try {
            initializeResourcePack();
        } catch (IOException e) {
            LOGGER.error("Failed to initialize resource pack", e);
        }
    }

    /**
     * Initializes the resource pack directory structure and pack.mcmeta file
     */
    private void initializeResourcePack() throws IOException {
        // Create directories
        Files.createDirectories(assetsPath);
        
        // Create pack.mcmeta file
        Path packMetaPath = packRootPath.resolve("pack.mcmeta");
        
        if (!Files.exists(packMetaPath)) {
            JsonObject packMeta = new JsonObject();
            JsonObject pack = new JsonObject();
            pack.addProperty("pack_format", 48); // Minecraft 1.21.10 format
            pack.addProperty("description", "Fal AI Generated Textures");
            packMeta.add("pack", pack);
            
            String packMetaJson = GSON.toJson(packMeta);
            Files.writeString(packMetaPath, packMetaJson, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            
            LOGGER.info("Created resource pack at: {}", packRootPath);
        }
    }

    /**
     * Writes a texture PNG to the resource pack
     * @param blockId The block identifier (e.g., "cobblestone", "stone", "dirt")
     * @param pngBytes The PNG image data
     */
    public void writeTexture(String blockId, byte[] pngBytes) throws IOException {
        // Extract just the block name if a full identifier is provided
        String blockName = blockId.contains(":") ? blockId.split(":")[1] : blockId;
        
        Path texturePath = assetsPath.resolve(blockName + ".png");
        Files.write(texturePath, pngBytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        
        LOGGER.info("Wrote texture to: {}", texturePath);
    }

    /**
     * Reloads resource packs to apply the new texture
     */
    public void reloadResourcePacks() {
        Minecraft minecraft = Minecraft.getInstance();
        
        // Run on the main thread
        minecraft.execute(() -> {
            try {
                LOGGER.info("Reloading resource packs...");
                
                PackRepository packRepository = minecraft.getResourcePackRepository();
                
                // Ensure our pack is enabled
                List<String> enabledPacks = new ArrayList<>(packRepository.getSelectedIds());
                String packId = "file/" + PACK_NAME;
                
                if (!enabledPacks.contains(packId)) {
                    enabledPacks.add(packId);
                    LOGGER.info("Enabling pack: {}", packId);
                }
                
                // Reload the pack repository
                packRepository.reload();
                
                // Apply the selected packs
                List<String> finalPacks = new ArrayList<>();
                for (String id : enabledPacks) {
                    Pack pack = packRepository.getPack(id);
                    if (pack != null) {
                        finalPacks.add(id);
                    }
                }
                
                // Set selected packs
                packRepository.setSelected(finalPacks);
                
                // Reload resources
                minecraft.reloadResourcePacks();
                
                LOGGER.info("Resource pack reload complete!");
            } catch (Exception e) {
                LOGGER.error("Failed to reload resource packs", e);
            }
        });
    }

    /**
     * Gets the root path of the generated resource pack
     */
    public Path getPackRootPath() {
        return packRootPath;
    }
}

