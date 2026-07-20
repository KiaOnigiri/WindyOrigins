package com.windykindy49.mirroredorigin.util;

import net.minecraft.entity.Entity;

/**
 * Per-thread state used to route sounds/particles by mirror phase.
 *
 * CURRENT_SOUND_SOURCE - entity currently emitting a sound (set by EntitySoundMixin /
 *                        PlayerEntitySoundMixin around Entity#playSound). Lets the packet
 *                        filter resolve phase from the true source rather than only from
 *                        the ambient tick context.
 *
 * CROSS_PHASE          - while true, sound/particle packets emitted in this window must
 *                        reach players in BOTH phases. Used by
 *                        mirroredorigin:cross_phase_effect (phase_shift transition and the
 *                        banishment "someone was teleported" feedback).
 */
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
