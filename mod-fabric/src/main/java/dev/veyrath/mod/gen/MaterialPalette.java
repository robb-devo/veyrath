package dev.veyrath.mod.gen;

import dev.veyrath.engine.surface.Material;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * Maps engine materials to block states.
 *
 * <p>Chapter 01 phase A palette: vanilla blocks chosen for colour and shader response. Phase B
 * swaps entries to VEYRATH blocks (custom textures/PBR) here only; the engine is unaffected.
 */
public final class MaterialPalette {
    private static final BlockState[] STATES = new BlockState[Material.values().length];

    static {
        put(Material.AIR, Blocks.AIR);
        put(Material.WATER, Blocks.WATER);
        // Level 8 is Minecraft's falling water: a waterfall column that does not spread sideways.
        put(Material.WATERFALL, Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 8));
        put(Material.LAVA, Blocks.LAVA);
        put(Material.BEDROCK, Blocks.BEDROCK);
        put(Material.STONE, Blocks.STONE);
        put(Material.DEEPSLATE, Blocks.DEEPSLATE);
        put(Material.ANDESITE, Blocks.ANDESITE);
        put(Material.DIORITE, Blocks.DIORITE);
        put(Material.GRANITE, Blocks.GRANITE);
        put(Material.TUFF, Blocks.TUFF);
        put(Material.CALCITE, Blocks.CALCITE);
        put(Material.BASALT, Blocks.BASALT);
        put(Material.BLACKSTONE, Blocks.BLACKSTONE);
        put(Material.OBSIDIAN, Blocks.OBSIDIAN);
        put(Material.MAGMA, Blocks.MAGMA_BLOCK);
        put(Material.STRATA_RED, Blocks.DYED_TERRACOTTA.red());
        put(Material.STRATA_ORANGE, Blocks.DYED_TERRACOTTA.orange());
        put(Material.STRATA_OCHRE, Blocks.DYED_TERRACOTTA.yellow());
        put(Material.STRATA_PALE, Blocks.DYED_TERRACOTTA.white());
        put(Material.STRATA_BROWN, Blocks.DYED_TERRACOTTA.brown());
        put(Material.STRATA_UMBER, Blocks.TERRACOTTA);
        put(Material.SANDSTONE_RED, Blocks.RED_SANDSTONE);
        put(Material.DIRT, Blocks.DIRT);
        put(Material.COARSE_DIRT, Blocks.COARSE_DIRT);
        put(Material.ROOTED_DIRT, Blocks.ROOTED_DIRT);
        put(Material.GRASS, Blocks.GRASS_BLOCK);
        put(Material.PODZOL, Blocks.PODZOL);
        put(Material.MOSS, Blocks.MOSS_BLOCK);
        put(Material.MUD, Blocks.MUD);
        put(Material.CLAY, Blocks.CLAY);
        put(Material.SAND, Blocks.SAND);
        put(Material.RED_SAND, Blocks.RED_SAND);
        put(Material.GRAVEL, Blocks.GRAVEL);
        put(Material.ASH, Blocks.SMOOTH_BASALT);
        put(Material.PACKED_MUD, Blocks.PACKED_MUD);
        put(Material.SNOW, Blocks.SNOW_BLOCK);
        put(Material.PACKED_ICE, Blocks.PACKED_ICE);
        put(Material.BLUE_ICE, Blocks.BLUE_ICE);
        put(Material.ICE, Blocks.ICE);
        put(Material.AMETHYST, Blocks.AMETHYST_BLOCK);
        put(Material.CRYSTAL_STONE, Blocks.SMOOTH_QUARTZ);
        // Vegetation (phase A: vanilla wood and leaves; leaf colour comes from the biome foliage colour)
        put(Material.ELDER_WOOD, Blocks.DARK_OAK_WOOD);
        leaves(Material.ELDER_LEAVES, Blocks.DARK_OAK_LEAVES);
        put(Material.BROADLEAF_WOOD, Blocks.OAK_WOOD);
        leaves(Material.BROADLEAF_LEAVES, Blocks.OAK_LEAVES);
        put(Material.CONIFER_WOOD, Blocks.SPRUCE_WOOD);
        leaves(Material.CONIFER_LEAVES, Blocks.SPRUCE_LEAVES);
        put(Material.PALE_WOOD, Blocks.BIRCH_WOOD);
        leaves(Material.BLOOM_LEAVES, Blocks.OAK_LEAVES);
        put(Material.EMBER_WOOD, Blocks.ACACIA_WOOD);
        leaves(Material.EMBER_LEAVES, Blocks.ACACIA_LEAVES);
        put(Material.FEN_WOOD, Blocks.MANGROVE_WOOD);
        leaves(Material.FEN_LEAVES, Blocks.MANGROVE_LEAVES);
        put(Material.CHAR_WOOD, Blocks.STRIPPED_DARK_OAK_WOOD);
        put(Material.GRASS_TUFT, Blocks.SHORT_GRASS);
        put(Material.FERN, Blocks.FERN);
        put(Material.FLOWER_YELLOW, Blocks.DANDELION);
        put(Material.FLOWER_RED, Blocks.POPPY);
        put(Material.FLOWER_BLUE, Blocks.CORNFLOWER);
        put(Material.FLOWER_WHITE, Blocks.OXEYE_DAISY);
        put(Material.FLOWER_VIOLET, Blocks.ALLIUM);
        put(Material.DRY_SHRUB, Blocks.DEAD_BUSH);
        put(Material.MOSS_CARPET, Blocks.MOSS_CARPET);
        put(Material.TALL_GRASS, Blocks.TALL_GRASS);
        put(Material.TALL_GRASS_TOP, Blocks.TALL_GRASS.defaultBlockState().setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
        put(Material.LARGE_FERN, Blocks.LARGE_FERN);
        put(Material.LARGE_FERN_TOP, Blocks.LARGE_FERN.defaultBlockState().setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
        put(Material.REEDS, Blocks.SUGAR_CANE);
        put(Material.AMETHYST_CLUSTER, Blocks.AMETHYST_CLUSTER);
        put(Material.AMETHYST_BUD, Blocks.MEDIUM_AMETHYST_BUD);
        put(Material.MUSHROOM_BROWN, Blocks.BROWN_MUSHROOM);
        put(Material.MUSHROOM_RED, Blocks.RED_MUSHROOM);
        put(Material.HANGING_ROOTS, Blocks.HANGING_ROOTS);
        put(Material.HANGING_VINE, Blocks.CAVE_VINES_PLANT);
        put(Material.HANGING_VINE_LIT, Blocks.CAVE_VINES_PLANT.defaultBlockState().setValue(BlockStateProperties.BERRIES, true));
        put(Material.HANGING_VINE_TIP, Blocks.CAVE_VINES);
        for (Material m : Material.values()) {
            if (STATES[m.ordinal()] == null) throw new IllegalStateException("No block for material " + m);
        }
    }

    private static void put(Material m, Block b) {
        STATES[m.ordinal()] = b.defaultBlockState();
    }

    private static void put(Material m, BlockState state) {
        STATES[m.ordinal()] = state;
    }

    /** Leaves placed by the generator are persistent: they are not attached to vanilla log distances. */
    private static void leaves(Material m, Block b) {
        BlockState state = b.defaultBlockState();
        if (state.hasProperty(LeavesBlock.PERSISTENT)) state = state.setValue(LeavesBlock.PERSISTENT, true);
        STATES[m.ordinal()] = state;
    }

    private MaterialPalette() {}

    public static BlockState state(int materialOrdinal) {
        return STATES[materialOrdinal];
    }
}
