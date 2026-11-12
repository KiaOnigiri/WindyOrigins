package com.windykindy49.windyorigins.dream;

import com.windykindy49.windyorigins.common.MobGroups;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.inventory.Inventory;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.structure.processor.BlockIgnoreStructureProcessor;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.math.noise.SimplexNoiseSampler;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.feature.ConfiguredFeature;

public final class DreamArena {
    public static final int TOP_Y = 120;
    private static final int CLEAR = 44;

    private DreamArena() {
    }

    private static DreamArena.Palette palette(MobGroups.Group var0, long var1) {
        return switch (var0) {
            case WILD -> new DreamArena.Palette(
            Blocks.PODZOL, Blocks.COARSE_DIRT, Blocks.DARK_OAK_LOG, Blocks.SHROOMLIGHT, Blocks.MOSSY_COBBLESTONE, true, Blocks.DARK_OAK_LEAVES
        );
            case UNDEAD -> new DreamArena.Palette(
            Blocks.SOUL_SOIL, Blocks.DEEPSLATE_TILES, Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.SOUL_LANTERN, Blocks.CRACKED_DEEPSLATE_TILES, false, null
        );
            case ARTHROPOD -> new DreamArena.Palette(Blocks.COARSE_DIRT, Blocks.MUD, Blocks.BONE_BLOCK, Blocks.OCHRE_FROGLIGHT, Blocks.COBWEB, false, null);
            case AQUATIC -> new DreamArena.Palette(
            Blocks.PRISMARINE, Blocks.DARK_PRISMARINE, Blocks.PRISMARINE_BRICKS, Blocks.SEA_LANTERN, Blocks.DEAD_TUBE_CORAL_BLOCK, false, null
        );
            case AERIAL -> new DreamArena.Palette(
            Blocks.WHITE_WOOL, Blocks.SNOW_BLOCK, Blocks.QUARTZ_PILLAR, Blocks.PEARLESCENT_FROGLIGHT, Blocks.WHITE_STAINED_GLASS, false, null
        );
            case INFERNAL -> new DreamArena.Palette(
            Blocks.CRIMSON_NYLIUM, Blocks.NETHERRACK, Blocks.POLISHED_BLACKSTONE, Blocks.SHROOMLIGHT, Blocks.MAGMA_BLOCK, true, Blocks.NETHER_WART_BLOCK
        );
            case ILLAGER -> new DreamArena.Palette(
            Blocks.DARK_OAK_PLANKS, Blocks.COBBLESTONE, Blocks.DARK_OAK_LOG, Blocks.LANTERN, Blocks.COBBLESTONE, false, null
        );
            case END -> new DreamArena.Palette(Blocks.END_STONE, Blocks.END_STONE_BRICKS, Blocks.PURPUR_PILLAR, Blocks.END_ROD, Blocks.OBSIDIAN, false, null);
            case VILLAGER -> new DreamArena.Palette(Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.OAK_LOG, Blocks.LANTERN, Blocks.HAY_BLOCK, true, Blocks.OAK_LEAVES);
            case PLAYER -> {
                DreamArena.Palette[] var3 = new DreamArena.Palette[]{
                    new DreamArena.Palette(
                        Blocks.CALCITE, Blocks.AMETHYST_BLOCK, Blocks.PURPUR_PILLAR, Blocks.PEARLESCENT_FROGLIGHT, Blocks.AMETHYST_BLOCK, false, null
                    ),
                    new DreamArena.Palette(
                        Blocks.MOSS_BLOCK, Blocks.CLAY, Blocks.CHERRY_LOG, Blocks.VERDANT_FROGLIGHT, Blocks.PINK_WOOL, true, Blocks.CHERRY_LEAVES
                    ),
                    new DreamArena.Palette(
                        Blocks.BLUE_ICE, Blocks.PACKED_ICE, Blocks.LIGHT_BLUE_CONCRETE, Blocks.SEA_LANTERN, Blocks.WHITE_STAINED_GLASS, false, null
                    ),
                    new DreamArena.Palette(
                        Blocks.BLACK_CONCRETE, Blocks.OBSIDIAN, Blocks.MAGENTA_CONCRETE, Blocks.CRYING_OBSIDIAN, Blocks.CYAN_STAINED_GLASS, false, null
                    )
                };
                yield var3[Math.floorMod(var1, var3.length)];
            }
            default -> new DreamArena.Palette(
            Blocks.MOSS_BLOCK, Blocks.DIRT, Blocks.BIRCH_LOG, Blocks.VERDANT_FROGLIGHT, Blocks.PINK_WOOL, true, Blocks.AZALEA_LEAVES
        );
        };
    }

    private static DreamArena.Nature nature(MobGroups.Group var0) {
        Block[] var1 = new Block[]{
            Blocks.COAL_ORE,
            Blocks.COAL_ORE,
            Blocks.IRON_ORE,
            Blocks.COPPER_ORE,
            Blocks.GOLD_ORE,
            Blocks.LAPIS_ORE,
            Blocks.REDSTONE_ORE,
            Blocks.DIAMOND_ORE,
            Blocks.EMERALD_ORE
        };
        Block[] var2 = new Block[]{
            Blocks.DEEPSLATE_COAL_ORE,
            Blocks.DEEPSLATE_IRON_ORE,
            Blocks.DEEPSLATE_GOLD_ORE,
            Blocks.DEEPSLATE_REDSTONE_ORE,
            Blocks.DEEPSLATE_LAPIS_ORE,
            Blocks.DEEPSLATE_DIAMOND_ORE
        };

        return switch (var0) {
            case WILD -> new DreamArena.Nature(
            new String[]{"spruce", "pine", "dark_oak", "huge_brown_mushroom"},
            new String[]{"patch_grass", "patch_berry_bush", "patch_large_fern", "patch_tall_grass"},
            var1,
            new Block[]{Blocks.MOSSY_COBBLESTONE}
        );
            case UNDEAD -> new DreamArena.Nature(
            new String[0], new String[0], var2, new Block[]{Blocks.COBWEB, Blocks.SKELETON_SKULL, Blocks.SOUL_LANTERN, Blocks.CANDLE}
        );
            case ARTHROPOD -> new DreamArena.Nature(
            new String[]{"huge_brown_mushroom", "huge_red_mushroom"},
            new String[]{"patch_grass"},
            var1,
            new Block[]{Blocks.COBWEB, Blocks.BROWN_MUSHROOM, Blocks.RED_MUSHROOM}
        );
            case AQUATIC -> new DreamArena.Nature(
            new String[0],
            new String[0],
            new Block[]{Blocks.COPPER_ORE, Blocks.LAPIS_ORE, Blocks.GOLD_ORE, Blocks.DIAMOND_ORE},
            new Block[]{Blocks.SEA_PICKLE, Blocks.DEAD_BRAIN_CORAL, Blocks.DEAD_TUBE_CORAL}
        );
            case AERIAL -> new DreamArena.Nature(
            new String[]{"cherry", "cherry", "azalea_tree"}, new String[]{"flower_cherry", "patch_grass"}, var1, new Block[]{Blocks.POWDER_SNOW}
        );
            case INFERNAL -> new DreamArena.Nature(
            new String[]{"crimson_fungus_planted", "crimson_fungus_planted"},
            new String[]{"crimson_forest_vegetation"},
            new Block[]{Blocks.NETHER_QUARTZ_ORE, Blocks.NETHER_QUARTZ_ORE, Blocks.NETHER_GOLD_ORE, Blocks.ANCIENT_DEBRIS},
            new Block[]{Blocks.MAGMA_BLOCK}
        );
            case ILLAGER -> new DreamArena.Nature(new String[]{"dark_oak", "oak"}, new String[]{"patch_grass"}, var1, new Block[]{Blocks.LANTERN});
            case END -> new DreamArena.Nature(
            new String[]{"chorus_plant", "chorus_plant"},
            new String[0],
            new Block[]{Blocks.CRYING_OBSIDIAN, Blocks.AMETHYST_BLOCK},
            new Block[]{Blocks.END_ROD}
        );
            case VILLAGER -> new DreamArena.Nature(
            new String[]{"oak", "fancy_oak", "birch"},
            new String[]{"patch_grass", "flower_plain", "patch_pumpkin", "patch_tall_grass"},
            var1,
            new Block[]{Blocks.HAY_BLOCK}
        );
            case PLAYER -> new DreamArena.Nature(
            new String[]{"cherry", "azalea_tree", "birch"},
            new String[]{"flower_cherry", "flower_meadow", "patch_grass"},
            var1,
            new Block[]{Blocks.AMETHYST_CLUSTER}
        );
            case PASTORAL -> new DreamArena.Nature(
            new String[]{"oak", "birch", "fancy_oak", "oak_bees_005"},
            new String[]{"patch_grass", "flower_default", "flower_meadow", "patch_tall_grass"},
            var1,
            new Block[0]
        );
            default -> new DreamArena.Nature(new String[]{"oak"}, new String[]{"patch_grass"}, var1, new Block[0]);
        };
    }

    public static DreamArena.Layout build(ServerWorld var0, int var1, MobGroups.Group var2, int var3, int var4, long var5) {
        Random var7 = Random.create(var5);
        DreamArena.Palette var8 = palette(var2, var5);
        DreamArena.Nature var9 = nature(var2);
        int var10 = 12 + var3 * 3;
        BlockPos var11 = new BlockPos(var1 * 512, 120, 0);

        for (int var12 = var11.getX() - 44 >> 4; var12 <= var11.getX() + 44 >> 4; var12++) {
            for (int var13 = -3; var13 <= 2; var13++) {
                var0.getChunk(var12, var13);
            }
        }

        byte var34 = 18;
        BlockState var35 = Blocks.AIR.getDefaultState();

        for (BlockPos var15 : BlockPos.iterate(var11.add(-44, -44, -44), var11.add(44, 24, 44))) {
            if (!var0.getBlockState(var15).isAir()) {
                var0.removeBlockEntity(var15);
                var0.setBlockState(var15, var35, var34);
            }
        }

        SimplexNoiseSampler var36 = new SimplexNoiseSampler(var7);
        int var37 = var10 * 2 + 1;
        int[][] var16 = new int[var37][var37];
        boolean[][] var17 = new boolean[var37][var37];

        for (int var18 = -var10; var18 <= var10; var18++) {
            for (int var19 = -var10; var19 <= var10; var19++) {
                double var20 = Math.sqrt((double)(var18 * var18 + var19 * var19));
                double var22 = (double)var10 - 1.0 + var36.sample((double)var18 * 0.17, (double)var19 * 0.17) * 1.4;
                if (!(var20 > var22)) {
                    double var24 = MathHelper.clamp(1.0 - var20 / var22, 0.0, 1.0);
                    double var26 = var36.sample((double)var18 * 0.07 + 31.0, (double)var19 * 0.07) * 2.4
                        + var36.sample((double)var18 * 0.19, (double)var19 * 0.19 + 57.0) * 0.9
                        + var24 * 1.8;
                    var26 *= Math.sqrt(var24);
                    var17[var18 + var10][var19 + var10] = true;
                    var16[var18 + var10][var19 + var10] = MathHelper.clamp((int)Math.round(var26), -1, 4);
                }
            }
        }

        ArrayList var38 = new ArrayList();
        double var39 = var7.nextDouble() * Math.PI * 2.0;

        for (int var21 = 0; var21 < var4; var21++) {
            double var41 = var39 + (double)var21 * Math.PI * 2.0 / (double)Math.max(1, var4) + (var7.nextDouble() - 0.5) * 0.5;
            double var49 = Math.max(5.0, (double)var10 * (0.5 + var7.nextDouble() * 0.2));
            var38.add(new int[]{(int)Math.round(Math.cos(var41) * var49), (int)Math.round(Math.sin(var41) * var49)});
        }

        List var40 = choosePieces(var0, var11, var10, var2, var2 == MobGroups.Group.PLAYER ? 3 : 2 + var3, var38, var7);

        for (DreamArena.Piece var23 : (Iterable<DreamArena.Piece>)(Object)(var40)) {
            int var50 = (var23.box.getMinX() + var23.box.getMaxX()) / 2 - var11.getX();
            int var25 = (var23.box.getMinZ() + var23.box.getMaxZ()) / 2 - var11.getZ();
            int var62 = MathHelper.clamp(var16[clamp(var50 + var10, 0, var37 - 1)][clamp(var25 + var10, 0, var37 - 1)], 0, 2);
            var23.level = var62;

            for (int var27 = var23.box.getMinX() - 3; var27 <= var23.box.getMaxX() + 3; var27++) {
                for (int var28 = var23.box.getMinZ() - 3; var28 <= var23.box.getMaxZ() + 3; var28++) {
                    int var29 = var27 - var11.getX() + var10;
                    int var30 = var28 - var11.getZ() + var10;
                    if (var29 >= 0 && var30 >= 0 && var29 < var37 && var30 < var37) {
                        int var31 = Math.max(
                            Math.max(var23.box.getMinX() - var27, var27 - var23.box.getMaxX()),
                            Math.max(var23.box.getMinZ() - var28, var28 - var23.box.getMaxZ())
                        );
                        if (var31 <= 0) {
                            var16[var29][var30] = var62;
                            var17[var29][var30] = true;
                        } else if (var17[var29][var30]) {
                            double var32 = (double)var31 / 4.0;
                            var16[var29][var30] = (int)Math.round((double)var62 + (double)(var16[var29][var30] - var62) * var32);
                        }
                    }
                }
            }
        }

        for (int var43 = -var10; var43 <= var10; var43++) {
            for (int var46 = -var10; var46 <= var10; var46++) {
                if (var17[var43 + var10][var46 + var10]) {
                    int var51 = var16[var43 + var10][var46 + var10];
                    double var54 = Math.sqrt((double)(var43 * var43 + var46 * var46));
                    int var70 = (int)((1.0 - var54 / ((double)var10 + 1.0)) * (double)var10 * 0.8) + 2 + var7.nextInt(2);

                    for (int var78 = var51; var78 > var51 - var70; var78--) {
                        BlockPos var84 = var11.add(var43, var78, var46);
                        Block var92 = var78 == var51 ? var8.top : var8.fill;
                        if (var78 < var51 - 1 && var9.ores.length > 0 && var7.nextFloat() < 0.035F) {
                            var92 = var9.ores[var7.nextInt(var9.ores.length)];
                        }

                        var0.setBlockState(var84, var92.getDefaultState(), var34);
                    }
                }
            }
        }

        for (DreamArena.Piece var47 : (Iterable<DreamArena.Piece>)(Object)(var40)) {
            BlockPos var52 = var47.origin.withY(var11.getY() + var47.level);
            var47.template.place(var0, var52, var52, var47.data, var7, var34);
            BlockBox var55 = var47.template.calculateBoundingBox(var47.data, var52);
            var47.placed = var55;
            dreamify(var0, var55, var8, var7, var34);
        }

        Registry var45 = var0.getRegistryManager().get(RegistryKeys.CONFIGURED_FEATURE);
        ChunkGenerator var48 = var0.getChunkManager().getChunkGenerator();
        int var53 = var9.trees.length == 0 ? 0 : 3 + var3 * 2 + var7.nextInt(3);
        int var56 = 0;

        for (int var63 = 0; var56 < var53 && var63 < var53 * 8; var63++) {
            int var71 = var7.nextInt(var37) - var10;
            int var79 = var7.nextInt(var37) - var10;
            if (free(var71, var79, var10 - 3, var17, var10, var40, var38, var11, 3)) {
                BlockPos var85 = var11.add(var71, var16[var71 + var10][var79 + var10] + 1, var79);
                if (feature(var45, var9.trees[var7.nextInt(var9.trees.length)], var0, var48, var7, var85)) {
                    var56++;
                }
            }
        }

        for (int var57 = 0; var57 < var9.plants.length * (2 + var3); var57++) {
            int var64 = var7.nextInt(var37) - var10;
            int var72 = var7.nextInt(var37) - var10;
            if (free(var64, var72, var10 - 2, var17, var10, var40, var38, var11, 2)) {
                feature(
                    var45, var9.plants[var7.nextInt(var9.plants.length)], var0, var48, var7, var11.add(var64, var16[var64 + var10][var72 + var10] + 1, var72)
                );
            }
        }

        for (int var58 = 0; var58 < var9.bits.length * (3 + var3); var58++) {
            int var65 = var7.nextInt(var37) - var10;
            int var73 = var7.nextInt(var37) - var10;
            if (free(var65, var73, var10 - 2, var17, var10, var40, var38, var11, 2)) {
                BlockPos var80 = var11.add(var65, var16[var65 + var10][var73 + var10] + 1, var73);
                BlockState var86 = var9.bits[var7.nextInt(var9.bits.length)].getDefaultState();
                if (var0.getBlockState(var80).isAir() && var86.canPlaceAt(var0, var80)) {
                    var0.setBlockState(var80, var86, var34);
                }
            }
        }

        for (int var59 = 0; var59 < 1 + var3; var59++) {
            int var66 = var7.nextInt(var37) - var10;
            int var74 = var7.nextInt(var37) - var10;
            if (free(var66, var74, var10 - 3, var17, var10, var40, var38, var11, 3)) {
                BlockPos var81 = var11.add(var66, var16[var66 + var10][var74 + var10] + 1, var74);
                int var87 = 2 + var7.nextInt(4);

                for (int var93 = 0; var93 < var87; var93++) {
                    var0.setBlockState(var81.up(var93), var8.accent.getDefaultState(), var34);
                }

                if (var7.nextFloat() < 0.6F) {
                    var0.setBlockState(var81.up(var87), var8.light.getDefaultState(), var34);
                }
            }
        }

        var56 = 3 + var7.nextInt(3) + var3;

        for (int var67 = 0; var67 < var56; var67++) {
            double var75 = var7.nextDouble() * Math.PI * 2.0;
            int var88 = 2 + var7.nextInt(4);
            double var94 = (double)(var10 + var88 + 5 + var7.nextInt(8));
            BlockPos var98 = var11.add((int)(Math.cos(var75) * var94), var7.nextInt(13) - 7, (int)(Math.sin(var75) * var94));
            islet(var0, var98, var88, var8, var9, var45, var48, var36, var7, var34);
        }

        for (int var68 = 0; var68 < 10 + var3 * 6; var68++) {
            double var76 = var7.nextDouble() * Math.PI * 2.0;
            double var89 = (double)(var10 + 4 + var7.nextInt(14));
            BlockPos var96 = var11.add((int)(Math.cos(var76) * var89), var7.nextInt(18) - 10, (int)(Math.sin(var76) * var89));
            if (var0.getBlockState(var96).isAir()) {
                var0.setBlockState(var96, var8.debris.getDefaultState(), var34);
            }
        }

        int var69 = var16[var10][var10];
        DreamArena.Layout var77 = new DreamArena.Layout(var11.add(0, var69 + 1, 0), var10);

        for (int[] var90 : (Iterable<int[]>)(Object)(var38)) {
            int var95 = var17[var90[0] + var10][var90[1] + var10] ? var16[var90[0] + var10][var90[1] + var10] : 0;
            BlockPos var97 = var11.add(var90[0], var95 + 1, var90[1]);

            for (BlockPos var33 : BlockPos.iterate(var97.add(-1, 0, -1), var97.add(1, 3, 1))) {
                var0.removeBlockEntity(var33);
                var0.setBlockState(var33, var35, var34);
            }

            var0.setBlockState(
                var97.down(),
                var8.light.getDefaultState().isFullCube(var0, var97.down()) ? var8.light.getDefaultState() : Blocks.CRYING_OBSIDIAN.getDefaultState(),
                var34
            );
            var77.spots.add(var97.toImmutable());
        }

        for (BlockPos var91 : BlockPos.iterate(var77.center.add(-1, 0, -1), var77.center.add(1, 2, 1))) {
            var0.removeBlockEntity(var91);
            var0.setBlockState(var91, var35, var34);
        }

        if (var0.getBlockState(var77.center.down()).isAir()) {
            var0.setBlockState(var77.center.down(), var8.top.getDefaultState(), var34);
        }

        return var77;
    }

    public static BlockPos ground(ServerWorld var0, BlockPos var1) {
        for (int var2 = var1.getY() + 8; var2 >= var1.getY() - 14; var2--) {
            BlockPos var3 = new BlockPos(var1.getX(), var2, var1.getZ());
            if (!var0.getBlockState(var3).getCollisionShape(var0, var3).isEmpty()
                && var0.getBlockState(var3.up()).getCollisionShape(var0, var3.up()).isEmpty()
                && var0.getBlockState(var3.up(2)).getCollisionShape(var0, var3.up(2)).isEmpty()) {
                return var3.up();
            }
        }

        return var1;
    }

    private static boolean feature(Registry<ConfiguredFeature<?, ?>> var0, String var1, ServerWorld var2, ChunkGenerator var3, Random var4, BlockPos var5) {
        ConfiguredFeature var6 = (ConfiguredFeature)var0.get(new Identifier("minecraft", var1));
        if (var6 == null) {
            return false;
        } else {
            try {
                return var6.generate(var2, var3, var4, var5);
            } catch (RuntimeException var8) {
                return false;
            }
        }
    }

    private static boolean free(
        int var0, int var1, int var2, boolean[][] var3, int var4, List<DreamArena.Piece> var5, List<int[]> var6, BlockPos var7, int var8
    ) {
        if (var0 * var0 + var1 * var1 <= var2 * var2 && var3[var0 + var4][var1 + var4] && var0 * var0 + var1 * var1 >= 9) {
            for (int[] var10 : var6) {
                if (Math.abs(var10[0] - var0) <= var8 && Math.abs(var10[1] - var1) <= var8) {
                    return false;
                }
            }

            int var14 = var7.getX() + var0;
            int var15 = var7.getZ() + var1;

            for (DreamArena.Piece var12 : var5) {
                BlockBox var13 = var12.box;
                if (var14 >= var13.getMinX() - var8 && var14 <= var13.getMaxX() + var8 && var15 >= var13.getMinZ() - var8 && var15 <= var13.getMaxZ() + var8) {
                    return false;
                }
            }

            return true;
        } else {
            return false;
        }
    }

    private static void islet(
        ServerWorld var0,
        BlockPos var1,
        int var2,
        DreamArena.Palette var3,
        DreamArena.Nature var4,
        Registry<ConfiguredFeature<?, ?>> var5,
        ChunkGenerator var6,
        SimplexNoiseSampler var7,
        Random var8,
        int var9
    ) {
        int var10 = var1.getY();

        for (int var11 = -var2; var11 <= var2; var11++) {
            for (int var12 = -var2; var12 <= var2; var12++) {
                double var13 = Math.sqrt((double)(var11 * var11 + var12 * var12));
                double var15 = (double)var2 + var7.sample((double)(var1.getX() + var11) * 0.3, (double)(var1.getZ() + var12) * 0.3) * 0.8;
                if (!(var13 > var15)) {
                    int var17 = var10 + (var13 < (double)var2 * 0.5 && var8.nextFloat() < 0.3F ? 1 : 0);
                    int var18 = (int)((1.0 - var13 / ((double)var2 + 1.0)) * (double)var2 * 1.3) + 1;

                    for (int var19 = 0; var19 < var18; var19++) {
                        BlockPos var20 = new BlockPos(var1.getX() + var11, var17 - var19, var1.getZ() + var12);
                        Block var21 = var19 == 0 ? var3.top : var3.fill;
                        if (var19 > 0 && var4.ores.length > 0 && var8.nextFloat() < 0.06F) {
                            var21 = var4.ores[var8.nextInt(var4.ores.length)];
                        }

                        var0.setBlockState(var20, var21.getDefaultState(), var9);
                    }
                }
            }
        }

        BlockPos var22 = ground(var0, var1.up(2));
        if (var2 >= 4 && var4.trees.length > 0 && var8.nextBoolean()) {
            feature(var5, var4.trees[var8.nextInt(var4.trees.length)], var0, var6, var8, var22);
        } else if (var4.plants.length > 0) {
            feature(var5, var4.plants[var8.nextInt(var4.plants.length)], var0, var6, var8, var22);
        } else if (var8.nextBoolean()) {
            var0.setBlockState(var22, var3.light.getDefaultState(), var9);
        }
    }

    private static List<DreamArena.Piece> choosePieces(ServerWorld var0, BlockPos var1, int var2, MobGroups.Group var3, int var4, List<int[]> var5, Random var6) {
        ArrayList var7 = new ArrayList();
        String[] var8 = DreamPieces.of(var3);
        if (var8.length == 0) {
            return var7;
        } else {
            StructureTemplateManager var9 = var0.getStructureTemplateManager();

            for (int var10 = 0; var7.size() < var4 && var10 < var4 * 14; var10++) {
                Identifier var11 = new Identifier("minecraft", var8[var6.nextInt(var8.length)]);
                StructureTemplate var12 = var9.getTemplate(var11).orElse(null);
                if (var12 != null) {
                    StructurePlacementData var13 = new StructurePlacementData()
                        .setRotation(BlockRotation.random(var6))
                        .setMirror(var6.nextBoolean() ? BlockMirror.NONE : BlockMirror.LEFT_RIGHT)
                        .setIgnoreEntities(true)
                        .addProcessor(BlockIgnoreStructureProcessor.IGNORE_STRUCTURE_BLOCKS)
                        .addProcessor(BlockIgnoreStructureProcessor.IGNORE_AIR_AND_STRUCTURE_BLOCKS);
                    Vec3i var14 = var12.getRotatedSize(var13.getRotation());
                    double var15 = Math.hypot((double)var14.getX(), (double)var14.getZ()) / 2.0;
                    double var17 = (double)var2 - 3.0 - var15;
                    if (!(var17 < 0.0)) {
                        double var19 = var6.nextDouble() * Math.PI * 2.0;
                        double var21 = var17 * (0.35 + 0.65 * var6.nextDouble());
                        BlockPos var23 = var1.add(
                            (int)Math.round(Math.cos(var19) * var21 - (double)var14.getX() / 2.0),
                            0,
                            (int)Math.round(Math.sin(var19) * var21 - (double)var14.getZ() / 2.0)
                        );
                        BlockBox var24 = var12.calculateBoundingBox(var13, var23);
                        if (onIsland(var24, var1, var2 - 3)) {
                            boolean var25 = var24.expand(2).contains(new Vec3i(var1.getX(), var24.getMinY(), var1.getZ()));

                            for (int[] var27 : var5) {
                                var25 |= var24.expand(2).contains(new Vec3i(var1.getX() + var27[0], var24.getMinY(), var1.getZ() + var27[1]));
                            }

                            for (DreamArena.Piece var29 : (Iterable<DreamArena.Piece>)(Object)(var7)) {
                                var25 |= var29.box.expand(2).intersects(var24);
                            }

                            if (!var25) {
                                var7.add(new DreamArena.Piece(var12, var13, var23, var24));
                            }
                        }
                    }
                }
            }

            return var7;
        }
    }

    private static boolean onIsland(BlockBox var0, BlockPos var1, int var2) {
        int[] var3 = new int[]{var0.getMinX(), var0.getMaxX()};
        int[] var4 = new int[]{var0.getMinZ(), var0.getMaxZ()};

        for (int var8 : var3) {
            for (int var12 : var4) {
                double var13 = (double)var8 + 0.5 - ((double)var1.getX() + 0.5);
                double var15 = (double)var12 + 0.5 - ((double)var1.getZ() + 0.5);
                if (var13 * var13 + var15 * var15 > (double)var2 * (double)var2) {
                    return false;
                }
            }
        }

        return true;
    }

    private static void dreamify(ServerWorld var0, BlockBox var1, DreamArena.Palette var2, Random var3, int var4) {
        BlockState var5 = Blocks.AIR.getDefaultState();
        int var6 = Math.max(1, var1.getBlockCountY());
        ArrayList var7 = new ArrayList();

        for (BlockPos var9 : BlockPos.iterate(var1.getMinX(), var1.getMinY(), var1.getMinZ(), var1.getMaxX(), var1.getMaxY(), var1.getMaxZ())) {
            BlockState var10 = var0.getBlockState(var9);
            if (!var10.isAir()) {
                if (var0.getBlockEntity(var9) instanceof Inventory
                    || var10.isOf(Blocks.SPAWNER)
                    || var10.isOf(Blocks.JIGSAW)
                    || var10.isOf(Blocks.STRUCTURE_BLOCK)
                    || var10.isOf(Blocks.WATER)
                    || var10.isOf(Blocks.LAVA)
                    || var10.isOf(Blocks.BUBBLE_COLUMN)
                    || var10.isOf(Blocks.KELP)
                    || var10.isOf(Blocks.KELP_PLANT)
                    || var10.isOf(Blocks.SEAGRASS)
                    || var10.isOf(Blocks.TALL_SEAGRASS)
                    || var10.isOf(Blocks.TNT)) {
                    var0.removeBlockEntity(var9);
                    var0.setBlockState(var9, var5, var4);
                } else if (var10.isOf(Blocks.SUSPICIOUS_SAND)) {
                    var0.removeBlockEntity(var9);
                    var0.setBlockState(var9, Blocks.SANDSTONE.getDefaultState(), var4);
                } else if (var10.isOf(Blocks.SUSPICIOUS_GRAVEL)) {
                    var0.removeBlockEntity(var9);
                    var0.setBlockState(var9, Blocks.TUFF.getDefaultState(), var4);
                } else {
                    if (var10.contains(Properties.WATERLOGGED) && var10.get(Properties.WATERLOGGED)) {
                        var10 = var10.with(Properties.WATERLOGGED, Boolean.valueOf(false));
                        var0.setBlockState(var9, var10, var4);
                    }

                    int var11 = var9.getY() - var1.getMinY();
                    float var12 = 0.04F + 0.18F * (float)var11 / (float)var6;
                    if (var11 > 0 && var3.nextFloat() < var12) {
                        if (var3.nextFloat() < 0.25F && var10.isFullCube(var0, var9)) {
                            var7.add(var9.toImmutable());
                        }

                        var0.setBlockState(var9, var5, var4);
                    } else if (var10.isFullCube(var0, var9) && var3.nextFloat() < 0.08F) {
                        var0.setBlockState(var9, (var3.nextBoolean() ? var2.debris : var2.accent).getDefaultState(), var4);
                    }
                }
            }
        }

        for (BlockPos var14 : (Iterable<BlockPos>)(Object)(var7)) {
            BlockPos var15 = var14.up(3 + var3.nextInt(5)).add(var3.nextInt(3) - 1, 0, var3.nextInt(3) - 1);
            if (var0.getBlockState(var15).isAir()) {
                var0.setBlockState(var15, var2.debris.getDefaultState(), var4);
            }
        }
    }

    public static int strengthOf(float var0) {
        return var0 < 20.0F ? 0 : (var0 <= 40.0F ? 1 : 2);
    }

    static int clamp(int var0, int var1, int var2) {
        return MathHelper.clamp(var0, var1, var2);
    }

    public static final class Layout {
        public final BlockPos center;
        public final List<BlockPos> spots = new ArrayList<>();
        public final int radius;

        Layout(BlockPos var1, int var2) {
            this.center = var1;
            this.radius = var2;
        }
    }

    private static record Nature(String[] trees, String[] plants, Block[] ores, Block[] bits) {
    }

    private static record Palette(Block top, Block fill, Block accent, Block light, Block debris, boolean trees, Block leaves) {
    }

    private static final class Piece {
        final StructureTemplate template;
        final StructurePlacementData data;
        final BlockPos origin;
        final BlockBox box;
        int level;
        BlockBox placed;

        Piece(StructureTemplate var1, StructurePlacementData var2, BlockPos var3, BlockBox var4) {
            this.template = var1;
            this.data = var2;
            this.origin = var3;
            this.box = var4;
        }
    }
}
