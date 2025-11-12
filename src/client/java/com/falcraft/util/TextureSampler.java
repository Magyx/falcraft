package com.falcraft.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Samples colors from texture images
 */
public class TextureSampler {
    private static final Logger LOGGER = LoggerFactory.getLogger("TextureSampler");
    
    private final BufferedImage image;
    private final int width;
    private final int height;
    
    /**
     * Creates a texture sampler from PNG data
     * @param pngData The PNG file as a byte array
     * @throws IOException If the image cannot be loaded
     */
    public TextureSampler(byte[] pngData) throws IOException {
        this.image = ImageIO.read(new ByteArrayInputStream(pngData));
        if (this.image == null) {
            throw new IOException("Failed to load texture image");
        }
        this.width = image.getWidth();
        this.height = image.getHeight();
        LOGGER.info("Loaded texture: {}x{}", width, height);
        
        // Analyze texture color distribution
        analyzeTexture();
    }
    
    /**
     * Analyzes the texture to see what colors it contains
     */
    private void analyzeTexture() {
        int whiteCount = 0, lightCount = 0, darkCount = 0, blackCount = 0;
        int maxR = 0, maxG = 0, maxB = 0;
        int minR = 255, minG = 255, minB = 255;
        
        // Sample every 10th pixel for performance
        for (int y = 0; y < height; y += 10) {
            for (int x = 0; x < width; x += 10) {
                int rgb = image.getRGB(x, y) & 0xFFFFFF;
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                
                maxR = Math.max(maxR, r);
                maxG = Math.max(maxG, g);
                maxB = Math.max(maxB, b);
                minR = Math.min(minR, r);
                minG = Math.min(minG, g);
                minB = Math.min(minB, b);
                
                int avg = (r + g + b) / 3;
                if (r > 240 && g > 240 && b > 240) whiteCount++;
                else if (avg > 180) lightCount++;
                else if (avg < 50) blackCount++;
                else darkCount++;
            }
        }
        
        LOGGER.info("Texture analysis - White pixels: {}, Light: {}, Dark: {}, Black: {}", 
            whiteCount, lightCount, darkCount, blackCount);
        LOGGER.info("Texture color range - RGB min: ({},{},{}) max: ({},{},{})", 
            minR, minG, minB, maxR, maxG, maxB);
    }
    
    /**
     * Samples a color from the texture at UV coordinates
     * @param u Horizontal texture coordinate (0.0 to 1.0)
     * @param v Vertical texture coordinate (0.0 to 1.0)
     * @return RGB color as an integer
     */
    public int sample(float u, float v) {
        // Clamp UV coordinates to [0, 1]
        u = Math.max(0.0f, Math.min(1.0f, u));
        v = Math.max(0.0f, Math.min(1.0f, v));
        
        // Convert UV to pixel coordinates
        // Note: V is flipped (1.0 - v) because image Y axis is inverted
        int x = (int) (u * (width - 1));
        int y = (int) ((1.0f - v) * (height - 1));
        
        // Get RGB from image (removing alpha channel)
        int rgb = image.getRGB(x, y) & 0xFFFFFF;
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        
        // Apply brightness correction to remove baked-in shadows
        // Meshy textures have heavy shadows that make everything dark
        // Use a more moderate brightness boost with better color preservation
        float brightness = 1.8f; // Moderate brightness boost
        float gamma = 0.7f; // Slightly less aggressive gamma to preserve color saturation
        
        // Apply gamma correction to lift shadows while preserving color ratios
        r = (int) (Math.pow(r / 255.0f, gamma) * 255 * brightness);
        g = (int) (Math.pow(g / 255.0f, gamma) * 255 * brightness);
        b = (int) (Math.pow(b / 255.0f, gamma) * 255 * brightness);
        
        // Preserve color saturation for reds - if it's a red-ish color, boost red channel more
        int avg = (r + g + b) / 3;
        if (r > g && r > b && avg < 100) {
            // This is a dark red - boost it more aggressively
            r = (int) (r * 1.3f);
            r = Math.min(255, r);
        }
        
        // Clamp to valid range
        r = Math.min(255, Math.max(0, r));
        g = Math.min(255, Math.max(0, g));
        b = Math.min(255, Math.max(0, b));
        
        return (r << 16) | (g << 8) | b;
    }
}

