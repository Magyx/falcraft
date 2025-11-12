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
        // Initialize block palette with approximate colors
        // Wool blocks
        BLOCK_PALETTE.put(Blocks.WHITE_WOOL, 0xF9FFFE);
        BLOCK_PALETTE.put(Blocks.ORANGE_WOOL, 0xF9801D);
        BLOCK_PALETTE.put(Blocks.MAGENTA_WOOL, 0xC74EBD);
        BLOCK_PALETTE.put(Blocks.LIGHT_BLUE_WOOL, 0x3AB3DA);
        BLOCK_PALETTE.put(Blocks.YELLOW_WOOL, 0xFED83D);
        BLOCK_PALETTE.put(Blocks.LIME_WOOL, 0x80C71F);
        BLOCK_PALETTE.put(Blocks.PINK_WOOL, 0xF38BAA);
        BLOCK_PALETTE.put(Blocks.GRAY_WOOL, 0x474F52);
        BLOCK_PALETTE.put(Blocks.LIGHT_GRAY_WOOL, 0x9D9D97);
        BLOCK_PALETTE.put(Blocks.CYAN_WOOL, 0x169C9C);
        BLOCK_PALETTE.put(Blocks.PURPLE_WOOL, 0x8932B8);
        BLOCK_PALETTE.put(Blocks.BLUE_WOOL, 0x3C44AA);
        BLOCK_PALETTE.put(Blocks.BROWN_WOOL, 0x835432);
        BLOCK_PALETTE.put(Blocks.GREEN_WOOL, 0x5E7C16);
        BLOCK_PALETTE.put(Blocks.RED_WOOL, 0xB02E26);
        BLOCK_PALETTE.put(Blocks.BLACK_WOOL, 0x1D1D21);
        
        // Concrete blocks
        BLOCK_PALETTE.put(Blocks.WHITE_CONCRETE, 0xF9FFFE);
        BLOCK_PALETTE.put(Blocks.ORANGE_CONCRETE, 0xE06101);
        BLOCK_PALETTE.put(Blocks.MAGENTA_CONCRETE, 0xA9309F);
        BLOCK_PALETTE.put(Blocks.LIGHT_BLUE_CONCRETE, 0x2389C6);
        BLOCK_PALETTE.put(Blocks.YELLOW_CONCRETE, 0xF1AF15);
        BLOCK_PALETTE.put(Blocks.LIME_CONCRETE, 0x5EA818);
        BLOCK_PALETTE.put(Blocks.PINK_CONCRETE, 0xD5658F);
        BLOCK_PALETTE.put(Blocks.GRAY_CONCRETE, 0x36393D);
        BLOCK_PALETTE.put(Blocks.LIGHT_GRAY_CONCRETE, 0x7D7D73);
        BLOCK_PALETTE.put(Blocks.CYAN_CONCRETE, 0x157788);
        BLOCK_PALETTE.put(Blocks.PURPLE_CONCRETE, 0x64209C);
        BLOCK_PALETTE.put(Blocks.BLUE_CONCRETE, 0x2C2E8F);
        BLOCK_PALETTE.put(Blocks.BROWN_CONCRETE, 0x5C3A24);
        BLOCK_PALETTE.put(Blocks.GREEN_CONCRETE, 0x495B24);
        BLOCK_PALETTE.put(Blocks.RED_CONCRETE, 0x8E2121);
        BLOCK_PALETTE.put(Blocks.BLACK_CONCRETE, 0x080A0F);
        
        // Terracotta blocks
        BLOCK_PALETTE.put(Blocks.WHITE_TERRACOTTA, 0xD1B1A1);
        BLOCK_PALETTE.put(Blocks.ORANGE_TERRACOTTA, 0xA14E28);
        BLOCK_PALETTE.put(Blocks.MAGENTA_TERRACOTTA, 0x95576C);
        BLOCK_PALETTE.put(Blocks.LIGHT_BLUE_TERRACOTTA, 0x706C8A);
        BLOCK_PALETTE.put(Blocks.YELLOW_TERRACOTTA, 0xBA8524);
        BLOCK_PALETTE.put(Blocks.LIME_TERRACOTTA, 0x677534);
        BLOCK_PALETTE.put(Blocks.PINK_TERRACOTTA, 0xA04D4E);
        BLOCK_PALETTE.put(Blocks.GRAY_TERRACOTTA, 0x392A23);
        BLOCK_PALETTE.put(Blocks.LIGHT_GRAY_TERRACOTTA, 0x876B62);
        BLOCK_PALETTE.put(Blocks.CYAN_TERRACOTTA, 0x575C5C);
        BLOCK_PALETTE.put(Blocks.PURPLE_TERRACOTTA, 0x764656);
        BLOCK_PALETTE.put(Blocks.BLUE_TERRACOTTA, 0x4A3C5C);
        BLOCK_PALETTE.put(Blocks.BROWN_TERRACOTTA, 0x4D3223);
        BLOCK_PALETTE.put(Blocks.GREEN_TERRACOTTA, 0x4C532A);
        BLOCK_PALETTE.put(Blocks.RED_TERRACOTTA, 0x8E3C2E);
        BLOCK_PALETTE.put(Blocks.TERRACOTTA, 0x9A5A3A);
        
        // Wood blocks
        BLOCK_PALETTE.put(Blocks.OAK_PLANKS, 0xB18962);
        BLOCK_PALETTE.put(Blocks.SPRUCE_PLANKS, 0x6F5740);
        BLOCK_PALETTE.put(Blocks.BIRCH_PLANKS, 0xD2BC7C);
        BLOCK_PALETTE.put(Blocks.JUNGLE_PLANKS, 0xAC8261);
        BLOCK_PALETTE.put(Blocks.ACACIA_PLANKS, 0xB16643);
        BLOCK_PALETTE.put(Blocks.DARK_OAK_PLANKS, 0x44331C);
        BLOCK_PALETTE.put(Blocks.MANGROVE_PLANKS, 0x784840);
        BLOCK_PALETTE.put(Blocks.CHERRY_PLANKS, 0xE5BFBB);
        BLOCK_PALETTE.put(Blocks.BAMBOO_PLANKS, 0xBBA969);
        
        // Stone variants
        BLOCK_PALETTE.put(Blocks.STONE, 0x808080);
        BLOCK_PALETTE.put(Blocks.COBBLESTONE, 0x7F7F7F);
        BLOCK_PALETTE.put(Blocks.SMOOTH_STONE, 0xA0A0A0);
        BLOCK_PALETTE.put(Blocks.STONE_BRICKS, 0x7A7A7A);
        BLOCK_PALETTE.put(Blocks.ANDESITE, 0x878787);
        BLOCK_PALETTE.put(Blocks.DIORITE, 0xE4E4E4);
        BLOCK_PALETTE.put(Blocks.GRANITE, 0x9D6F5F);
        BLOCK_PALETTE.put(Blocks.SANDSTONE, 0xE3DBB0);
        BLOCK_PALETTE.put(Blocks.RED_SANDSTONE, 0xBF6330);
        
        // Metal blocks
        BLOCK_PALETTE.put(Blocks.IRON_BLOCK, 0xD8D8D8);
        BLOCK_PALETTE.put(Blocks.GOLD_BLOCK, 0xF4EC3C);
        BLOCK_PALETTE.put(Blocks.DIAMOND_BLOCK, 0x5CDBD5);
        BLOCK_PALETTE.put(Blocks.EMERALD_BLOCK, 0x17DD62);
        BLOCK_PALETTE.put(Blocks.LAPIS_BLOCK, 0x1F3DAD);
        BLOCK_PALETTE.put(Blocks.REDSTONE_BLOCK, 0xAE0C00);
        BLOCK_PALETTE.put(Blocks.COPPER_BLOCK, 0xC77154);
        BLOCK_PALETTE.put(Blocks.NETHERITE_BLOCK, 0x444244);
        
        // Ore blocks
        BLOCK_PALETTE.put(Blocks.COAL_BLOCK, 0x1A1919);
        BLOCK_PALETTE.put(Blocks.QUARTZ_BLOCK, 0xE8E5DD);
        
        // Misc blocks
        BLOCK_PALETTE.put(Blocks.GLASS, 0xC0F0FA);
        BLOCK_PALETTE.put(Blocks.OBSIDIAN, 0x100821);
        BLOCK_PALETTE.put(Blocks.GLOWSTONE, 0xF8F39A);
        BLOCK_PALETTE.put(Blocks.SEA_LANTERN, 0xACDFDB);
        BLOCK_PALETTE.put(Blocks.SNOW_BLOCK, 0xFFFEFE);
        BLOCK_PALETTE.put(Blocks.ICE, 0x919BFB);
        BLOCK_PALETTE.put(Blocks.PACKED_ICE, 0x7DA7F8);
        BLOCK_PALETTE.put(Blocks.SLIME_BLOCK, 0x72C555);
        BLOCK_PALETTE.put(Blocks.HONEY_BLOCK, 0xF29D28);
        BLOCK_PALETTE.put(Blocks.MOSS_BLOCK, 0x5A7241);
        
        // Nether blocks
        BLOCK_PALETTE.put(Blocks.NETHERRACK, 0x6B3838);
        BLOCK_PALETTE.put(Blocks.NETHER_BRICKS, 0x301F1F);
        BLOCK_PALETTE.put(Blocks.CRIMSON_PLANKS, 0x683C52);
        BLOCK_PALETTE.put(Blocks.WARPED_PLANKS, 0x3B6A6D);
        
        LOGGER.info("Initialized block palette with {} blocks", BLOCK_PALETTE.size());
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
        
        // Find closest color using Euclidean distance in RGB space
        for (Map.Entry<Block, Integer> entry : BLOCK_PALETTE.entrySet()) {
            int blockColor = entry.getValue();
            int br = (blockColor >> 16) & 0xFF;
            int bg = (blockColor >> 8) & 0xFF;
            int bb = blockColor & 0xFF;
            
            double distance = Math.sqrt(
                Math.pow(r - br, 2) +
                Math.pow(g - bg, 2) +
                Math.pow(b - bb, 2)
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
     * Clears the color cache
     */
    public static void clearCache() {
        COLOR_CACHE.clear();
    }
}

