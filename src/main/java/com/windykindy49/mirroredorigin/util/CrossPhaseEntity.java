package com.windykindy49.mirroredorigin.util;

/**
 * Marks an entity that belongs to no phase in particular and is therefore visible, audible
 * and effective in both of them at once.
 *
 * Only weather lightning uses this so far. A bolt out of a thunderstorm is not anyone's
 * doing - it is the sky behaving the same way over both worlds - so splitting it in two
 * would mean one of the worlds watching a storm that never strikes anything.
 */
public interface CrossPhaseEntity {
    boolean mirroredorigin$isCrossPhase();

    void mirroredorigin$setCrossPhase(boolean crossPhase);
}
