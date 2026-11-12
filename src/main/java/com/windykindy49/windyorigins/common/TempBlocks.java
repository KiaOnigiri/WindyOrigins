package com.windykindy49.windyorigins.common;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;

public class TempBlocks extends PersistentState {
    private static final String KEY = "windyorigins_temp_blocks";
    private final List<TempBlocks.Entry> entries = new ArrayList<>();

    public static TempBlocks get(MinecraftServer var0) {
        return var0.getOverworld().getPersistentStateManager().getOrCreate(new net.minecraft.world.PersistentState.Type<>(TempBlocks::new, TempBlocks::fromNbt, null), "windyorigins_temp_blocks");
    }

    public static TempBlocks fromNbt(NbtCompound var0) {
        TempBlocks var1 = new TempBlocks();
        NbtList var2 = var0.getList("Blocks", 10);

        for (int var3 = 0; var3 < var2.size(); var3++) {
            NbtCompound var4 = var2.getCompound(var3);
            RegistryKey var5 = RegistryKey.of(RegistryKeys.WORLD, new Identifier(var4.getString("World")));
            Block var6 = Registries.BLOCK.get(new Identifier(var4.getString("Block")));
            var1.entries.add(new TempBlocks.Entry(var5, BlockPos.fromLong(var4.getLong("Pos")), var6, var4.getLong("Expires")));
        }

        return var1;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound var1) {
        NbtList var2 = new NbtList();

        for (TempBlocks.Entry var4 : this.entries) {
            NbtCompound var5 = new NbtCompound();
            var5.putString("World", var4.world.getValue().toString());
            var5.putString("Block", Registries.BLOCK.getId(var4.block).toString());
            var5.putLong("Pos", var4.pos.asLong());
            var5.putLong("Expires", var4.expires);
            var2.add(var5);
        }

        var1.put("Blocks", var2);
        return var1;
    }

    public static boolean place(ServerWorld var0, BlockPos var1, BlockState var2, int var3) {
        if (!var0.setBlockState(var1, var2)) {
            return false;
        } else {
            TempBlocks var4 = get(var0.getServer());
            var4.entries
                .add(new TempBlocks.Entry(var0.getRegistryKey(), var1.toImmutable(), var2.getBlock(), var0.getServer().getOverworld().getTime() + (long)var3));
            var4.markDirty();
            return true;
        }
    }

    public static void tick(MinecraftServer var0) {
        TempBlocks var1 = get(var0);
        if (!var1.entries.isEmpty()) {
            long var2 = var0.getOverworld().getTime();
            boolean var4 = false;
            Iterator var5 = var1.entries.iterator();

            while (var5.hasNext()) {
                TempBlocks.Entry var6 = (TempBlocks.Entry)var5.next();
                if (var6.expires <= var2) {
                    ServerWorld var7 = var0.getWorld(var6.world);
                    if (var7 == null) {
                        var5.remove();
                        var4 = true;
                    } else if (!var7.isChunkLoaded(var6.pos)) {
                        var6.expires = var2 + 100L;
                    } else {
                        BlockState var8 = var7.getBlockState(var6.pos);
                        if (var8.isOf(var6.block)) {
                            var7.setBlockState(var6.pos, Blocks.AIR.getDefaultState());
                            var7.spawnParticles(
                                new BlockStateParticleEffect(ParticleTypes.BLOCK, var8),
                                (double)var6.pos.getX() + 0.5,
                                (double)var6.pos.getY() + 0.5,
                                (double)var6.pos.getZ() + 0.5,
                                8,
                                0.3,
                                0.3,
                                0.3,
                                0.0
                            );
                            BlockSoundGroup var9 = var8.getSoundGroup();
                            var7.playSound(null, var6.pos, var9.getBreakSound(), SoundCategory.BLOCKS, 0.4F, var9.getPitch() * 1.2F);
                        }

                        var5.remove();
                        var4 = true;
                    }
                }
            }

            if (var4) {
                var1.markDirty();
            }
        }
    }

    private static final class Entry {
        final RegistryKey<World> world;
        final BlockPos pos;
        final Block block;
        long expires;

        Entry(RegistryKey<World> var1, BlockPos var2, Block var3, long var4) {
            this.world = var1;
            this.pos = var2;
            this.block = var3;
            this.expires = var4;
        }
    }
}
