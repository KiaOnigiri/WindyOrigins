package com.windykindy49.mirroredorigin.util;

/**
 * Forces the mirror phase of every entity spawned inside a well-defined window, even
 * mobs, which ServerWorldSpawnMixin otherwise deliberately skips (mobs normally get their
 * phase from MobEntity#initialize, and natural spawns must stay in the overworld phase).
 *
 * Used where an entity is created by game logic rather than by a player action and must
 * inherit a parent's phase:
 *   - baby animals produced by breeding      (AnimalBreedMixin)
 *   - turtles hatching from eggs             (TurtleEggBlockMixin)
 *
 * Working through a ThreadLocal instead of injecting at the exact spawn call keeps this
 * independent of how vanilla happens to structure those methods (lambdas, helper calls,
 * ServerWorldAccess default methods, ...).
 */
public class SpawnPhaseOverride {
    private static final int MAX_DEPTH = 64;
    private static final ThreadLocal<Boolean> PENDING = new ThreadLocal<>();
    private static final ThreadLocal<java.util.List<Boolean>> SAVED =
        ThreadLocal.withInitial(java.util.ArrayList::new);

    /**
     * Opens an override scope without the caller having to park the previous value in a
     * field. That mattered for TurtleEggBlockMixin: its target is the single shared
     * TurtleEggBlock instance, not one object per clutch, so a field there was global state
     * for every clutch in the world at once.
     */
    public static void enter(boolean phase) {
        java.util.List<Boolean> saved = SAVED.get();
        if (saved.size() >= MAX_DEPTH) {
            saved.clear();
        }
        saved.add(PENDING.get());
        PENDING.set(phase);
    }

    public static void exit() {
        java.util.List<Boolean> saved = SAVED.get();
        if (saved.isEmpty()) {
            PENDING.remove();
            return;
        }
        restore(saved.remove(saved.size() - 1));
    }

    /**
     * Drops anything left behind by a scope that never closed. A stranded override is worse
     * than a stranded phase context: ServerWorldSpawnMixin consults it FIRST and applies it
     * to everything, mobs included, so one leak would keep stamping the wrong phase onto
     * every natural spawn, drop and projectile until the server restarted. Called once per
     * world tick, where no scope can legitimately be open.
     */
    public static void reset() {
        java.util.List<Boolean> saved = SAVED.get();
        if (!saved.isEmpty()) {
            saved.clear();
            PENDING.remove();
        }
    }

    /** @return the value that was active before, to be handed back to {@link #restore}. */
    public static Boolean push(boolean phase) {
        Boolean previous = PENDING.get();
        PENDING.set(phase);
        return previous;
    }

    public static void restore(Boolean previous) {
        if (previous == null) {
            PENDING.remove();
        } else {
            PENDING.set(previous);
        }
    }

    public static Boolean get() {
        return PENDING.get();
    }
}
