package com.dragoncare.client;

import com.dragoncare.DragonCare;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class DirtTextureBlender {

    private static final Logger LOGGER = LogManager.getLogger(DragonCare.MOD_ID);
    private static final Map<String, Identifier> BLENDED_CACHE = new ConcurrentHashMap<>();

    private DirtTextureBlender() {}

    /**
     * Retrieves or dynamically generates a dirt-blended texture.
     *
     * @param baseId    The original base texture ID of the dragon (from Ice & Fire).
     * @param dirtLevel The dirt level (1 to 5).
     * @return The Identifier of the registered blended DynamicTexture, or baseId if anything fails.
     */
    public static Identifier getOrCreateBlendedTexture(Identifier baseId, int dirtLevel) {
        if (dirtLevel <= 0 || baseId.getPath().startsWith("dynamic/")) {
            return baseId;
        }

        String cacheKey = baseId.toString() + "_dirt_" + dirtLevel;
        Identifier cached = BLENDED_CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        ResourceManager resourceManager = client.getResourceManager();

        // 1. Load the original dragon texture
        Optional<Resource> baseResource = resourceManager.getResource(baseId);
        if (baseResource.isEmpty()) {
            LOGGER.warn("Could not find base dragon texture resource: {}", baseId);
            return baseId;
        }

        // 2. Load the dirt overlay template
        Identifier overlayId = Identifier.of(DragonCare.MOD_ID, "textures/entity/overlay/dirt_" + dirtLevel + ".png");
        Optional<Resource> overlayResource = resourceManager.getResource(overlayId);
        if (overlayResource.isEmpty()) {
            LOGGER.warn("Could not find dirt overlay template resource: {}", overlayId);
            return baseId;
        }

        try {
            NativeImage baseImage;
            try (InputStream in = baseResource.get().getInputStream()) {
                baseImage = NativeImage.read(in);
            }

            NativeImage overlayImage;
            try (InputStream in = overlayResource.get().getInputStream()) {
                overlayImage = NativeImage.read(in);
            }

            int baseW = baseImage.getWidth();
            int baseH = baseImage.getHeight();
            int overlayW = overlayImage.getWidth();
            int overlayH = overlayImage.getHeight();

            // 3. Dynamic pixel-by-pixel alpha blending with coordinate scaling
            for (int y = 0; y < baseH; y++) {
                for (int x = 0; x < baseW; x++) {
                    // Map base coordinates to overlay template coordinates (handles stage scaling beautifully!)
                    int ox = (x * overlayW) / baseW;
                    int oy = (y * overlayH) / baseH;

                    int baseCol = baseImage.getColor(x, y);
                    int overlayCol = overlayImage.getColor(ox, oy);

                    int blendedCol = blendPixels(baseCol, overlayCol);
                    baseImage.setColor(x, y, blendedCol);
                }
            }

            // Close the overlay image as we are done with it
            overlayImage.close();

            // 4. Wrap base image in a NativeImageBackedTexture and register it
            NativeImageBackedTexture blendedTexture = new NativeImageBackedTexture(baseImage);
            
            // Generate a unique identifier for the registered texture
            String safePath = baseId.getPath().replace("/", "_").replace(".png", "");
            Identifier blendedId = Identifier.of(DragonCare.MOD_ID, "dynamic/blended_" + safePath + "_d" + dirtLevel);
            
            // Register texture with standard client TextureManager
            client.getTextureManager().registerTexture(blendedId, blendedTexture);
            
            BLENDED_CACHE.put(cacheKey, blendedId);
            LOGGER.info("Successfully generated and registered dynamic dirt texture: {}", blendedId);
            return blendedId;

        } catch (IOException e) {
            LOGGER.error("Failed to dynamically blend dirt texture for base {}: {}", baseId, e.getMessage(), e);
            return baseId;
        }
    }

    /**
     * Performs standard alpha blending between a base pixel and an overlay pixel in ABGR format.
     */
    private static int blendPixels(int baseColor, int overlayColor) {
        int aO = (overlayColor >> 24) & 0xFF;
        if (aO == 0) {
            return baseColor; // Overlay is transparent, keep base
        }
        if (aO == 255) {
            return overlayColor; // Overlay is fully opaque, override base
        }

        int aB = (baseColor >> 24) & 0xFF;
        int rB = baseColor & 0xFF;
        int gB = (baseColor >> 8) & 0xFF;
        int bB = (baseColor >> 16) & 0xFF;

        int rO = overlayColor & 0xFF;
        int gO = (overlayColor >> 8) & 0xFF;
        int bO = (overlayColor >> 16) & 0xFF;

        // Calculate blended alpha channel
        int aOut = aO + (aB * (255 - aO) / 255);
        if (aOut == 0) {
            return 0;
        }

        // Calculate blended color channels
        int rOut = (rO * aO + rB * aB * (255 - aO) / 255) / aOut;
        int gOut = (gO * aO + gB * aB * (255 - aO) / 255) / aOut;
        int bOut = (bO * aO + bB * aB * (255 - aO) / 255) / aOut;

        // Clamp just to be safe
        rOut = Math.min(255, Math.max(0, rOut));
        gOut = Math.min(255, Math.max(0, gOut));
        bOut = Math.min(255, Math.max(0, bOut));
        aOut = Math.min(255, Math.max(0, aOut));

        return (aOut << 24) | (bOut << 16) | (gOut << 8) | rOut;
    }

    public static void clearCache() {
        BLENDED_CACHE.clear();
    }
}
