package com.falcraft.util;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Maps RGB colors to the closest matching Minecraft blocks
 */
public class BlockMapper {
    private static final Logger LOGGER = LoggerFactory.getLogger("BlockMapper");
    
    // Color palette mapping block types to their approximate RGB colors
    private static final Map<Block, Integer> BLOCK_PALETTE = new HashMap<>();
    
    // Cache for color lookups
    private static final Map<Integer, BlockState> COLOR_CACHE = new HashMap<>();
    
    static {
        // Curated palette: Clean concrete for primary colors + stone/wood for natural tones
        // Using concrete as primary palette (clean, consistent colors)
        
        // Core 16 colors (Concrete - cleanest and most vibrant)
        BLOCK_PALETTE.put(Blocks.WHITE_CONCRETE, 0xF9FFFE);
        BLOCK_PALETTE.put(Blocks.LIGHT_GRAY_CONCRETE, 0x7D7D73);
        BLOCK_PALETTE.put(Blocks.GRAY_CONCRETE, 0x36393D);
        BLOCK_PALETTE.put(Blocks.BLACK_CONCRETE, 0x080A0F);
        BLOCK_PALETTE.put(Blocks.BROWN_CONCRETE, 0x5C3A24);
        BLOCK_PALETTE.put(Blocks.RED_CONCRETE, 0x8E2121);
        BLOCK_PALETTE.put(Blocks.ORANGE_CONCRETE, 0xE06101);
        BLOCK_PALETTE.put(Blocks.YELLOW_CONCRETE, 0xF1AF15);
        BLOCK_PALETTE.put(Blocks.LIME_CONCRETE, 0x5EA818);
        BLOCK_PALETTE.put(Blocks.GREEN_CONCRETE, 0x495B24);
        BLOCK_PALETTE.put(Blocks.CYAN_CONCRETE, 0x157788);
        BLOCK_PALETTE.put(Blocks.LIGHT_BLUE_CONCRETE, 0x2389C6);
        BLOCK_PALETTE.put(Blocks.BLUE_CONCRETE, 0x2C2E8F);
        BLOCK_PALETTE.put(Blocks.PURPLE_CONCRETE, 0x64209C);
        BLOCK_PALETTE.put(Blocks.MAGENTA_CONCRETE, 0xA9309F);
        BLOCK_PALETTE.put(Blocks.PINK_CONCRETE, 0xD5658F);
        
        // Essential stone blocks (for gray/tan range)
        BLOCK_PALETTE.put(Blocks.STONE, 0x808080);
        BLOCK_PALETTE.put(Blocks.COBBLESTONE, 0x7F7F7F);
        BLOCK_PALETTE.put(Blocks.STONE_BRICKS, 0x7A7A7A);
        BLOCK_PALETTE.put(Blocks.ANDESITE, 0x878787);
        BLOCK_PALETTE.put(Blocks.DIORITE, 0xE4E4E4);
        
        // Essential wood blocks (for brown/tan range)
        BLOCK_PALETTE.put(Blocks.OAK_PLANKS, 0xB18962);
        BLOCK_PALETTE.put(Blocks.SPRUCE_PLANKS, 0x6F5740);
        BLOCK_PALETTE.put(Blocks.BIRCH_PLANKS, 0xD2BC7C);
        BLOCK_PALETTE.put(Blocks.DARK_OAK_PLANKS, 0x44331C);
        
        // Terracotta for muted/earthy tones (fills gaps between concrete and stone)
        BLOCK_PALETTE.put(Blocks.WHITE_TERRACOTTA, 0xD1B1A1);
        BLOCK_PALETTE.put(Blocks.LIGHT_GRAY_TERRACOTTA, 0x876B62);
        BLOCK_PALETTE.put(Blocks.GRAY_TERRACOTTA, 0x392A23);
        
        // Special blocks for unique colors
        BLOCK_PALETTE.put(Blocks.COAL_BLOCK, 0x1A1919);  // Very dark
        BLOCK_PALETTE.put(Blocks.QUARTZ_BLOCK, 0xE8E5DD); // Bright white
        BLOCK_PALETTE.put(Blocks.SANDSTONE, 0xE3DBB0);    // Warm tan
        BLOCK_PALETTE.put(Blocks.RED_SANDSTONE, 0xBF6330); // Orange-brown
        
        LOGGER.info("Initialized curated block palette with {} blocks", BLOCK_PALETTE.size());
    }
    
    /**
     * Gets the closest matching Minecraft block for an RGB color
     * @param rgb The color as an RGB integer (0xRRGGBB)
     * @return The closest matching block state
     */
    public static BlockState getClosestBlock(int rgb) {
        // Check cache first
        if (COLOR_CACHE.containsKey(rgb)) {
            return COLOR_CACHE.get(rgb);
        }
        
        Block closestBlock = Blocks.WHITE_WOOL;
        double minDistance = Double.MAX_VALUE;
        
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        
        // Convert to LAB for perceptually uniform color distance
        double[] labTarget = rgbToLab(r, g, b);
        
        // Find closest color using perceptually-weighted distance
        for (Map.Entry<Block, Integer> entry : BLOCK_PALETTE.entrySet()) {
            int blockColor = entry.getValue();
            int br = (blockColor >> 16) & 0xFF;
            int bg = (blockColor >> 8) & 0xFF;
            int bb = blockColor & 0xFF;
            
            double[] labBlock = rgbToLab(br, bg, bb);
            
            // CIE76 Delta-E formula (perceptually uniform color difference)
            double distance = Math.sqrt(
                Math.pow(labTarget[0] - labBlock[0], 2) +
                Math.pow(labTarget[1] - labBlock[1], 2) +
                Math.pow(labTarget[2] - labBlock[2], 2)
            );
            
            if (distance < minDistance) {
                minDistance = distance;
                closestBlock = entry.getKey();
            }
        }
        
        BlockState blockState = closestBlock.defaultBlockState();
        COLOR_CACHE.put(rgb, blockState);
        
        return blockState;
    }
    
    /**
     * Converts RGB to LAB color space for perceptually uniform color matching
     * @param r Red component (0-255)
     * @param g Green component (0-255)
     * @param b Blue component (0-255)
     * @return LAB values [L, a, b]
     */
    private static double[] rgbToLab(int r, int g, int b) {
        // Convert RGB to XYZ
        double var_R = r / 255.0;
        double var_G = g / 255.0;
        double var_B = b / 255.0;
        
        // Apply gamma correction
        if (var_R > 0.04045) var_R = Math.pow((var_R + 0.055) / 1.055, 2.4);
        else var_R = var_R / 12.92;
        if (var_G > 0.04045) var_G = Math.pow((var_G + 0.055) / 1.055, 2.4);
        else var_G = var_G / 12.92;
        if (var_B > 0.04045) var_B = Math.pow((var_B + 0.055) / 1.055, 2.4);
        else var_B = var_B / 12.92;
        
        var_R = var_R * 100;
        var_G = var_G * 100;
        var_B = var_B * 100;
        
        // Observer = 2°, Illuminant = D65
        double X = var_R * 0.4124 + var_G * 0.3576 + var_B * 0.1805;
        double Y = var_R * 0.2126 + var_G * 0.7152 + var_B * 0.0722;
        double Z = var_R * 0.0193 + var_G * 0.1192 + var_B * 0.9505;
        
        // Convert XYZ to LAB
        double var_X = X / 95.047;
        double var_Y = Y / 100.000;
        double var_Z = Z / 108.883;
        
        if (var_X > 0.008856) var_X = Math.pow(var_X, 1.0 / 3.0);
        else var_X = (7.787 * var_X) + (16.0 / 116.0);
        if (var_Y > 0.008856) var_Y = Math.pow(var_Y, 1.0 / 3.0);
        else var_Y = (7.787 * var_Y) + (16.0 / 116.0);
        if (var_Z > 0.008856) var_Z = Math.pow(var_Z, 1.0 / 3.0);
        else var_Z = (7.787 * var_Z) + (16.0 / 116.0);
        
        double L = (116 * var_Y) - 16;
        double a = 500 * (var_X - var_Y);
        double b_lab = 200 * (var_Y - var_Z);
        
        return new double[]{L, a, b_lab};
    }
    
    /**
     * Clears the color cache
     */
    public static void clearCache() {
        COLOR_CACHE.clear();
    }
}

