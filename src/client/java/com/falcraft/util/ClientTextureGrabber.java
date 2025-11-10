package com.falcraft.util;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ClientTextureGrabber {
    private static final Logger LOGGER = LoggerFactory.getLogger("ClientTextureGrabber");
    
    /**
     * Result of a texture grab operation
     */
    public static class GrabResult {
        public final File textureFile;
        public final String blockId;
        
        public GrabResult(File textureFile, String blockId) {
            this.textureFile = textureFile;
            this.blockId = blockId;
        }
    }

    /**
     * Grabs the texture of the block the player is currently looking at
     * @return A GrabResult containing the texture file and block ID, or null if no block is targeted
     */
    public static GrabResult grabTargetedBlockTexture() throws IOException {
        Minecraft minecraft = Minecraft.getInstance();
        
        // Check if player is looking at a block
        HitResult hitResult = minecraft.hitResult;
        if (hitResult == null || hitResult.getType() != HitResult.Type.BLOCK) {
            LOGGER.warn("Player is not looking at a block");
            return null;
        }
        
        BlockHitResult blockHit = (BlockHitResult) hitResult;
        BlockPos pos = blockHit.getBlockPos();
        
        if (minecraft.level == null) {
            LOGGER.warn("World is null");
            return null;
        }
        
        BlockState blockState = minecraft.level.getBlockState(pos);
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(blockState.getBlock());
        
        LOGGER.info("Player is looking at block: {}", blockId);
        
        // Get the block's model and particle texture
        BlockModelShaper modelShaper = minecraft.getBlockRenderer().getBlockModelShaper();
        TextureAtlasSprite sprite = modelShaper.getParticleIcon(blockState);
        
        if (sprite == null) {
            LOGGER.error("Could not get texture sprite for block: {}", blockId);
            return null;
        }
        
        ResourceLocation spriteName = sprite.contents().name();
        LOGGER.info("Found texture sprite: {}", spriteName);
        
        // Extract the texture as a NativeImage
        NativeImage texture = extractSpriteTexture(sprite);
        
        if (texture == null) {
            LOGGER.error("Failed to extract texture for block: {}", blockId);
            return null;
        }
        
        // Save to a temporary file - use the actual sprite name, not the block name
        Path tempDir = Files.createTempDirectory("falcraft_textures");
        
        // Extract just the texture name from the sprite path (e.g., "minecraft:block/dirt" -> "dirt")
        String textureName = spriteName.getPath();
        if (textureName.contains("/")) {
            textureName = textureName.substring(textureName.lastIndexOf("/") + 1);
        }
        
        File outputFile = tempDir.resolve(textureName + ".png").toFile();
        
        texture.writeToFile(outputFile);
        texture.close();
        
        LOGGER.info("Saved texture to: {}", outputFile.getAbsolutePath());
        LOGGER.info("Block: {}, Actual texture: {}", blockId, textureName);
        
        // Return the actual texture name, not the block ID
        return new GrabResult(outputFile, textureName);
    }

    /**
     * Extracts a texture sprite as a NativeImage
     * @param sprite The texture atlas sprite
     * @return A NativeImage containing the sprite's texture
     */
    private static NativeImage extractSpriteTexture(TextureAtlasSprite sprite) {
        try {
            int width = sprite.contents().width();
            int height = sprite.contents().height();
            
            LOGGER.info("Extracting texture: {}x{}", width, height);
            
            // Create a new NativeImage with the sprite's dimensions
            NativeImage image = new NativeImage(width, height, false);
            
            // Access the sprite's animated texture contents to get the first frame
            // The sprite contents stores the full texture which we need to extract
            try {
                // Use reflection to access the mipmap data since it's private
                var field = sprite.contents().getClass().getDeclaredField("byMipLevel");
                field.setAccessible(true);
                NativeImage[] mipmaps = (NativeImage[]) field.get(sprite.contents());
                NativeImage sourceImage = mipmaps[0];
                
                // Copy pixel data from the sprite
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int color = sourceImage.getPixel(x, y);
                        image.setPixel(x, y, color);
                    }
                }
            } catch (NoSuchFieldException | IllegalAccessException e) {
                LOGGER.error("Failed to access sprite mipmap data", e);
                return null;
            }
            
            return image;
        } catch (Exception e) {
            LOGGER.error("Failed to extract sprite texture", e);
            return null;
        }
    }
}

