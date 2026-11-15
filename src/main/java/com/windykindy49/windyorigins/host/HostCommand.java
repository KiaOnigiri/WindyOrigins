package com.windykindy49.windyorigins.host;

import java.util.Locale;

/**
 * Everything Volta can be asked to do. The first twenty-four fill the three pages of the command wheel
 * (the last two of those only explain themselves there); the rest are reached by talking to her. Heat is out of 100: three or four requests in a row is
 * all the chip can take.
 */
public enum HostCommand {
    // Page one: things done to an enemy.
    OVERLOAD(11, 160, true),
    VIRUS(12, 400, true),
    BLIND(9, 320, true),
    IMPULSE(7, 160, true),
    PUPPET(18, 900, true),
    SIPHON(13, 500, true),
    SWAP(10, 400, true),
    GRAVITY(14, 600, false),
    // Page two: tricks with space, time and the Host's own skin.
    DECOY(11, 600, false),
    TURRET(16, 900, false),
    SHIELD(12, 600, false),
    TIMEWARP(16, 1200, false),
    REWIND(14, 800, false),
    BLINK(11, 240, false),
    REPAIR(15, 1500, false),
    SCAN(7, 400, false),
    // Page three: chores.
    MAGNET(5, 240, false),
    TOGGLE(3, 20, false),
    LIGHT(7, 400, false),
    DIG(11, 400, false),
    BUILD(11, 400, false),
    HARVEST(13, 500, false),
    FIND(6, 160, false),
    FETCH(8, 300, false),
    QUIET(0, 0, false),
    STOP(0, 0, false),
    STATUS(0, 0, false),
    HELP(0, 0, false),
    PVP(0, 0, false),
    // The body itself: she takes it and waits for orders, or gives it back.
    PILOT(5, 0, false),
    CHAT(0, 0, false);

    public static final int PAGE = 8;
    /** How many of the requests have a place on the wheel: everything but small talk. */
    public static final int WHEEL = 30;
    public final int heat;
    public final int cooldown;
    public final boolean targeted;

    private HostCommand(int heat, int cooldown, boolean targeted) {
        this.heat = heat;
        this.cooldown = cooldown;
        this.targeted = targeted;
    }

    public String key() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    public boolean chatOnly() {
        return this == FIND || this == FETCH || this.free();
    }

    /** Requests that cost nothing and work even when the chip is cooking. */
    public boolean free() {
        return this == QUIET || this == STOP || this == STATUS || this == HELP || this == PVP || this == CHAT;
    }

    /** The request in that place on the wheel, or null where the wheel is empty. */
    public static HostCommand onWheel(int id) {
        return id < WHEEL ? byId(id) : null;
    }

    public static HostCommand byId(int id) {
        HostCommand[] all = values();
        return id >= 0 && id < all.length ? all[id] : null;
    }
}
