package com.windykindy49.windyorigins.util;

public class MirrorContext {
    private static final ThreadLocal<Boolean> CURRENT_PHASE = new ThreadLocal<>();

    public static void set(boolean var0) {
        CURRENT_PHASE.set(var0);
    }

    public static Boolean get() {
        return CURRENT_PHASE.get();
    }

    public static void clear() {
        CURRENT_PHASE.remove();
    }
}
