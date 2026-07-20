package com.windykindy49.mirroredorigin.util;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;

/**
 * Turtle eggs are plain blocks with no block entity, so there is nowhere on the block
 * itself to remember which phase the mother turtle belonged to. This world-saved state
 * remembers the positions of eggs laid in the mirror phase; hatchlings from those
 * positions are then spawned into the mirror phase as well.
 *
 * Only mirror-phase eggs are stored - an absent position simply means "overworld phase",
 * which keeps the saved data small and makes the default behaviour correct.
 */
public class TurtleEggPhaseState extends PersistentState {
    private static final String KEY = "mirroredorigin_turtle_eggs";
    private static final String NBT_POSITIONS = "MirrorEggs";

    private final Set<Long> mirrorEggs = new HashSet<>();

    public TurtleEggPhaseState() {
    }

    public static TurtleEggPhaseState fromNbt(NbtCompound nbt) {
        TurtleEggPhaseState state = new TurtleEggPhaseState();
        for (long packed : nbt.getLongArray(NBT_POSITIONS)) {
            state.mirrorEggs.add(packed);
        }
        return state;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        long[] packed = new long[this.mirrorEggs.size()];
        int i = 0;
        for (Long value : this.mirrorEggs) {
            packed[i++] = value;
        }
        nbt.putLongArray(NBT_POSITIONS, packed);
        return nbt;
    }

    public static TurtleEggPhaseState get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(
            TurtleEggPhaseState::fromNbt, TurtleEggPhaseState::new, KEY);
    }

    public void setPhase(BlockPos pos, boolean inMirror) {
        long packed = pos.asLong();
        boolean changed = inMirror ? this.mirrorEggs.add(packed) : this.mirrorEggs.remove(packed);
        if (changed) {
            this.markDirty();
        }
    }

    public boolean isMirror(BlockPos pos) {
        return this.mirrorEggs.contains(pos.asLong());
    }

    public void clear(BlockPos pos) {
        if (this.mirrorEggs.remove(pos.asLong())) {
            this.markDirty();
        }
    }
}
