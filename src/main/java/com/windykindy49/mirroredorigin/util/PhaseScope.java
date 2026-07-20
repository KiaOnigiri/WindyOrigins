package com.windykindy49.mirroredorigin.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Nest-safe save/restore for {@link MirrorContext}.
 *
 * MirrorContext is a single ThreadLocal value, so a plain set/clear pair destroys the value
 * belonging to an enclosing scope. Entity ticks genuinely nest (a passenger is ticked inside
 * its vehicle's tick), so the enclosing phase has to be put back rather than cleared.
 * ArrayList is used instead of ArrayDeque because the saved value may be null (= "no phase
 * active") and ArrayDeque rejects nulls.
 *
 * enter() and exit() live in separate mixin callbacks, so they cannot be wrapped in a
 * try/finally: if anything between them throws, the matching exit() never runs and the
 * saved entry is stranded. Two guards keep that from turning into a permanent, silent
 * mis-phasing of everything spawned afterwards:
 *
 *   - MAX_DEPTH bounds the damage. Real nesting is a vehicle plus its passengers, a handful
 *     of frames at most; anything beyond that is leaked state, so the stack is dropped.
 *   - reset() is called once per world tick, at a point where no entity tick is in progress
 *     and the depth is therefore known to be zero.
 */
public final class PhaseScope {
    private static final int MAX_DEPTH = 64;
    private static final ThreadLocal<List<Boolean>> SAVED = ThreadLocal.withInitial(ArrayList::new);

    private PhaseScope() {
    }

    public static void enter(boolean phase) {
        List<Boolean> saved = SAVED.get();
        if (saved.size() >= MAX_DEPTH) {
            // Leaked frames from an aborted tick. Drop them instead of growing forever.
            saved.clear();
        }
        saved.add(MirrorContext.get());
        MirrorContext.set(phase);
    }

    /**
     * Opens a scope that leaves the phase as it is. For call sites that only sometimes have
     * a phase to publish - an explosion with no source entity, a vibration nobody caused -
     * so that enter/exit stay paired without a flag on the mixin. Clearing unconditionally
     * instead would wipe the phase of the tick these things happen inside.
     */
    public static void enterOrKeep(Boolean phase) {
        List<Boolean> saved = SAVED.get();
        if (saved.size() >= MAX_DEPTH) {
            saved.clear();
        }
        saved.add(MirrorContext.get());
        if (phase != null) {
            MirrorContext.set(phase);
        }
    }

    public static void exit() {
        List<Boolean> saved = SAVED.get();
        if (saved.isEmpty()) {
            MirrorContext.clear();
            return;
        }
        Boolean previous = saved.remove(saved.size() - 1);
        if (previous == null) {
            MirrorContext.clear();
        } else {
            MirrorContext.set(previous);
        }
    }

    /**
     * Drops any state left behind by an aborted scope. Only safe to call where no scope is
     * legitimately open - currently the head of the world tick.
     */
    public static void reset() {
        List<Boolean> saved = SAVED.get();
        if (!saved.isEmpty()) {
            saved.clear();
            MirrorContext.clear();
        }
    }
}
