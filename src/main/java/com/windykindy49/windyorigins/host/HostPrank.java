package com.windykindy49.windyorigins.host;

import java.util.Locale;

public enum HostPrank {
    SLOT,
    HISS,
    HUD,
    HURT,
    STEPS,
    GLITCH,
    FISHEYE,
    GIFT,
    ECHO,
    DANCE,
    MIRROR,
    WINK,
    RENAME,
    TEASE,
    SPIN,
    JUMP,
    INVERT,
    SHUFFLE,
    SCREAM,
    FAKE_DEATH,
    RUBBERBAND,
    SHOVE,
    DROP,
    BLACKOUT,
    WHISTLE;

    public String key() {
        return this.name().toLowerCase(Locale.ROOT);
    }
}
