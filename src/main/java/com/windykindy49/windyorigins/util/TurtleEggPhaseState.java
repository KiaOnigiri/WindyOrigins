package com.windykindy49.windyorigins.util;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;

public class TurtleEggPhaseState extends PersistentState {
    private static final String KEY = "windyorigins_turtle_eggs";
    private static final String NBT_POSITIONS = "MirrorEggs";
    private final Set<Long> mirrorEggs = new HashSet<>();

    public static TurtleEggPhaseState fromNbt(NbtCompound var0) {
        TurtleEggPhaseState var1 = new TurtleEggPhaseState();

        for (long var5 : var0.getLongArray("MirrorEggs")) {
            var1.mirrorEggs.add(var5);
        }

        return var1;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound var1) {
        long[] var2 = new long[this.mirrorEggs.size()];
        int var3 = 0;

        for (Long var5 : this.mirrorEggs) {
            var2[var3++] = var5;
        }

        var1.putLongArray("MirrorEggs", var2);
        return var1;
    }

    public static TurtleEggPhaseState get(ServerWorld var0) {
        return var0.getPersistentStateManager().getOrCreate(TurtleEggPhaseState::fromNbt, TurtleEggPhaseState::new, "windyorigins_turtle_eggs");
    }

    public void setPhase(BlockPos var1, boolean var2) {
        long var3 = var1.asLong();
        boolean var5 = var2 ? this.mirrorEggs.add(var3) : this.mirrorEggs.remove(var3);
        if (var5) {
            this.markDirty();
        }
    }

    public boolean isMirror(BlockPos var1) {
        return this.mirrorEggs.contains(var1.asLong());
    }

    public void clear(BlockPos var1) {
        if (this.mirrorEggs.remove(var1.asLong())) {
            this.markDirty();
        }
    }
}
