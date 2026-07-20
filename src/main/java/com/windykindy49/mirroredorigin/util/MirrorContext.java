package com.windykindy49.mirroredorigin.util;


public class MirrorContext {
    private static final ThreadLocal<Boolean> CURRENT_PHASE = new ThreadLocal();

    public static void set(boolean inMirror) {
        CURRENT_PHASE.set(inMirror);
    }

    public static Boolean get() {
        return CURRENT_PHASE.get();
    }

    public static void clear() {
        CURRENT_PHASE.remove();
    }
}

