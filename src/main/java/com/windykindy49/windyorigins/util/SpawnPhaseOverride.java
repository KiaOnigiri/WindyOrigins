package com.windykindy49.windyorigins.util;

import java.util.ArrayList;
import java.util.List;

public class SpawnPhaseOverride {
    private static final int MAX_DEPTH = 64;
    private static final ThreadLocal<Boolean> PENDING = new ThreadLocal<>();
    private static final ThreadLocal<List<Boolean>> SAVED = ThreadLocal.withInitial(ArrayList::new);

    public static void enter(boolean var0) {
        List var1 = SAVED.get();
        if (var1.size() >= 64) {
            var1.clear();
        }

        var1.add(PENDING.get());
        PENDING.set(var0);
    }

    public static void exit() {
        List var0 = SAVED.get();
        if (var0.isEmpty()) {
            PENDING.remove();
        } else {
            restore((Boolean)var0.remove(var0.size() - 1));
        }
    }

    public static void reset() {
        List var0 = SAVED.get();
        if (!var0.isEmpty()) {
            var0.clear();
            PENDING.remove();
        }
    }

    public static Boolean push(boolean var0) {
        Boolean var1 = PENDING.get();
        PENDING.set(var0);
        return var1;
    }

    public static void restore(Boolean var0) {
        if (var0 == null) {
            PENDING.remove();
        } else {
            PENDING.set(var0);
        }
    }

    public static Boolean get() {
        return PENDING.get();
    }
}
