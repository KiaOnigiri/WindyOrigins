package com.windykindy49.windyorigins.host;

import java.util.Locale;

/**
 * Everything Volta can be asked to do. The first twenty-four fill the three pages of the command wheel
 * (the last two of those only explain themselves there); the rest are reached by talking to her. Heat is out of 100: three or four requests in a row is
 * all the chip can take.
 */
public enum HostCommand {
    // Page one: things done to an enemy.
    OVERLOAD(28, 160, true),
    VIRUS(30, 400, true),
    BLIND(22, 320, true),
    IMPULSE(18, 160, true),
    PUPPET(45, 900, true),
    SIPHON(32, 500, true),
    SWAP(25, 400, true),
    GRAVITY(35, 600, false),
    // Page two: tricks with space, time and the Host's own skin.
    DECOY(28, 600, false),
    TURRET(40, 900, false),
    SHIELD(30, 600, false),
    TIMEWARP(40, 1200, false),
    REWIND(35, 800, false),
    BLINK(28, 240, false),
    REPAIR(38, 1500, false),
    SCAN(18, 400, false),
    // Page three: chores.
    MAGNET(12, 240, false),
    TOGGLE(8, 20, false),
    LIGHT(18, 400, false),
    DIG(28, 400, false),
    BUILD(28, 400, false),
    HARVEST(32, 500, false),
    FIND(15, 160, false),
    FETCH(20, 300, false),
    QUIET(0, 0, false),
    STOP(0, 0, false),
    STATUS(0, 0, false),
    HELP(0, 0, false),
    CHAT(0, 0, false);

    public static final int PAGE = 8;
    public static final int WHEEL = 24;
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
        return this == QUIET || this == STOP || this == STATUS || this == HELP || this == CHAT;
    }

    public static HostCommand byId(int id) {
        HostCommand[] all = values();
        return id >= 0 && id < all.length ? all[id] : null;
    }
}
