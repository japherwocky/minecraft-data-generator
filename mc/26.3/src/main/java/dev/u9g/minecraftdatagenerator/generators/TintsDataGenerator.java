package dev.u9g.minecraftdatagenerator.generators;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.u9g.minecraftdatagenerator.util.DGU;
import dev.u9g.minecraftdatagenerator.util.EmptyRenderBlockView;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.FoliageColor;
import net.minecraft.world.level.GrassColor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneWireBlock;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.*;

public class TintsDataGenerator implements IDataGenerator {
    public static BiomeTintColors generateBiomeTintColors(Registry<Biome> biomeRegistry) {
        BiomeTintColors colors = new BiomeTintColors();

        loadColormaps();
        biomeRegistry.forEach(biome -> {
            int biomeGrassColor = rgb(biome.getGrassColor(0.0, 0.0));
            int biomeFoliageColor = rgb(biome.getFoliageColor());
            int biomeWaterColor = rgb(biome.getWaterColor());

            colors.grassColoursMap.computeIfAbsent(biomeGrassColor, k -> new ArrayList<>()).add(biome);
            colors.foliageColoursMap.computeIfAbsent(biomeFoliageColor, k -> new ArrayList<>()).add(biome);
            colors.waterColourMap.computeIfAbsent(biomeWaterColor, k -> new ArrayList<>()).add(biome);
        });
        return colors;
    }

    public static Map<Integer, Integer> generateRedstoneTintColors() {
        Map<Integer, Integer> resultColors = new LinkedHashMap<>();

        for (int redstoneLevel : RedstoneWireBlock.POWER.getPossibleValues()) {
            // Remove the unintended alpha channel from the redstone tint color
            int color = removeAlphaChannel(RedstoneWireBlock.getColorForPower(redstoneLevel));
            resultColors.put(redstoneLevel, color);
        }
        return resultColors;
    }

    private static int removeAlphaChannel(int color) {
        float r = (float) (color >> 16 & 0xFF) / 255;
        float g = (float) (color >> 8 & 0xFF) / 255;
        float b = (float) (color & 0xFF) / 255;
        return ((int)(r * 255) << 16) | ((int)(g * 255) << 8) | (int)(b * 255);
    }

    // Since 1.21.11 biome and block colours come back as ARGB with a full alpha
    // byte, so they were written as negative numbers -- every water colour among
    // them. tints.json has always held plain RGB, as the redstone colours still do.
    private static int rgb(int argb) {
        return argb & 0xFFFFFF;
    }

    // A biome that does not override its grass or foliage colour takes it from a
    // colormap texture, and only the client loads those (GrassColorReloadListener,
    // FoliageColorReloadListener). On the dedicated server this runs on, the pixel
    // arrays are empty, so since 1.19.4 every such biome read as 0. The textures
    // are on the classpath here, so load them the same way.
    private static void loadColormaps() {
        GrassColor.init(readColormap("grass"));
        FoliageColor.init(readColormap("foliage"));
    }

    private static int[] readColormap(String name) {
        String path = "/assets/minecraft/textures/colormap/" + name + ".png";
        try (InputStream in = GrassColor.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("No colormap on the classpath at " + path);
            }
            BufferedImage image = ImageIO.read(in);
            return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static int getBlockColor(Block block) {
        return BlockColors.createDefault().getTintSource(block.defaultBlockState(), 0).colorInWorld(block.defaultBlockState(), EmptyRenderBlockView.INSTANCE, BlockPos.ZERO);
    }

    public static Map<Block, Integer> generateConstantTintColors() {
        Map<Block, Integer> resultColors = new LinkedHashMap<>();

        resultColors.put(Blocks.BIRCH_LEAVES, rgb(FoliageColor.FOLIAGE_BIRCH));
        resultColors.put(Blocks.SPRUCE_LEAVES, rgb(FoliageColor.FOLIAGE_EVERGREEN));

        resultColors.put(Blocks.LILY_PAD, rgb(getBlockColor(Blocks.LILY_PAD)));
        resultColors.put(Blocks.ATTACHED_MELON_STEM, rgb(getBlockColor(Blocks.ATTACHED_MELON_STEM)));
        resultColors.put(Blocks.ATTACHED_PUMPKIN_STEM, rgb(getBlockColor(Blocks.ATTACHED_PUMPKIN_STEM)));

        //not really constant, depend on the block age, but kinda have to be handled since textures are literally white without them
        resultColors.put(Blocks.MELON_STEM, rgb(getBlockColor(Blocks.MELON_STEM)));
        resultColors.put(Blocks.PUMPKIN_STEM, rgb(getBlockColor(Blocks.PUMPKIN_STEM)));

        return resultColors;
    }

    private static JsonObject encodeBiomeColorMap(Registry<Biome> biomeRegistry, Map<Integer, List<Biome>> colorsMap) {
        JsonArray resultColorsArray = new JsonArray();
        for (var entry : colorsMap.entrySet()) {
            JsonObject entryObject = new JsonObject();

            JsonArray keysArray = new JsonArray();
            for (Biome biome : entry.getValue()) {
                Identifier registryKey = biomeRegistry.getKey(biome);
                keysArray.add(registryKey.getPath());
            }

            entryObject.add("keys", keysArray);
            entryObject.addProperty("color", entry.getKey());
            resultColorsArray.add(entryObject);
        }

        JsonObject resultObject = new JsonObject();
        resultObject.add("data", resultColorsArray);
        return resultObject;
    }

    private static JsonObject encodeRedstoneColorMap(Map<Integer, Integer> colorsMap) {
        JsonArray resultColorsArray = new JsonArray();
        for (var entry : colorsMap.entrySet()) {
            JsonObject entryObject = new JsonObject();

            JsonArray keysArray = new JsonArray();
            keysArray.add(entry.getKey());

            entryObject.add("keys", keysArray);
            entryObject.addProperty("color", entry.getValue());
            resultColorsArray.add(entryObject);
        }

        JsonObject resultObject = new JsonObject();
        resultObject.add("data", resultColorsArray);
        return resultObject;
    }

    private static JsonObject encodeBlocksColorMap(Registry<Block> blockRegistry, Map<Block, Integer> colorsMap) {
        JsonArray resultColorsArray = new JsonArray();
        for (var entry : colorsMap.entrySet()) {
            JsonObject entryObject = new JsonObject();

            JsonArray keysArray = new JsonArray();
            Identifier registryKey = blockRegistry.getKey(entry.getKey());
            keysArray.add(registryKey.getPath());

            entryObject.add("keys", keysArray);
            entryObject.addProperty("color", entry.getValue());
            resultColorsArray.add(entryObject);
        }

        JsonObject resultObject = new JsonObject();
        resultObject.add("data", resultColorsArray);
        return resultObject;
    }

    @Override
    public String getDataName() {
        return "tints";
    }

    @Override
    public JsonObject generateDataJson() {
        RegistryAccess registryManager = DGU.getWorld().registryAccess();
        Registry<Biome> biomeRegistry = registryManager.lookupOrThrow(Registries.BIOME);
        Registry<Block> blockRegistry = registryManager.lookupOrThrow(Registries.BLOCK);

        BiomeTintColors biomeTintColors = generateBiomeTintColors(biomeRegistry);
        Map<Integer, Integer> redstoneColors = generateRedstoneTintColors();
        Map<Block, Integer> constantTintColors = generateConstantTintColors();

        JsonObject resultObject = new JsonObject();

        resultObject.add("grass", encodeBiomeColorMap(biomeRegistry, biomeTintColors.grassColoursMap));
        resultObject.add("foliage", encodeBiomeColorMap(biomeRegistry, biomeTintColors.foliageColoursMap));
        resultObject.add("water", encodeBiomeColorMap(biomeRegistry, biomeTintColors.waterColourMap));

        resultObject.add("redstone", encodeRedstoneColorMap(redstoneColors));
        resultObject.add("constant", encodeBlocksColorMap(blockRegistry, constantTintColors));

        return resultObject;
    }

    public static class BiomeTintColors {
        final Map<Integer, List<Biome>> grassColoursMap = new LinkedHashMap<>();
        final Map<Integer, List<Biome>> foliageColoursMap = new LinkedHashMap<>();
        final Map<Integer, List<Biome>> waterColourMap = new LinkedHashMap<>();
    }
}
