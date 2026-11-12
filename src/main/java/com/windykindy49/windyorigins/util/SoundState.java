package com.windykindy49.windyorigins.util;

import net.minecraft.entity.Entity;

public class SoundState {
    public static final ThreadLocal<Entity> CURRENT_SOUND_SOURCE = new ThreadLocal<>();
    public static final ThreadLocal<Boolean> CROSS_PHASE = new ThreadLocal<>();

    public static void markCrossPhase() {
        CROSS_PHASE.set(Boolean.TRUE);
    }

    public static boolean isCrossPhase() {
        return Boolean.TRUE.equals(CROSS_PHASE.get());
    }

    public static void clearCrossPhase() {
        CROSS_PHASE.remove();
    }

    public static void reset() {
        CURRENT_SOUND_SOURCE.remove();
        CROSS_PHASE.remove();
    }
}
