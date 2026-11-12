package com.windykindy49.windyorigins.util;

import java.util.ArrayList;
import java.util.List;

public final class PhaseScope {
    private static final int MAX_DEPTH = 64;
    private static final ThreadLocal<List<Boolean>> SAVED = ThreadLocal.withInitial(ArrayList::new);

    private PhaseScope() {
    }

    public static void enter(boolean var0) {
        List var1 = SAVED.get();
        if (var1.size() >= 64) {
            var1.clear();
        }

        var1.add(MirrorContext.get());
        MirrorContext.set(var0);
    }

    public static void enterOrKeep(Boolean var0) {
        List var1 = SAVED.get();
        if (var1.size() >= 64) {
            var1.clear();
        }

        var1.add(MirrorContext.get());
        if (var0 != null) {
            MirrorContext.set(var0);
        }
    }

    public static void exit() {
        List var0 = SAVED.get();
        if (var0.isEmpty()) {
            MirrorContext.clear();
        } else {
            Boolean var1 = (Boolean)var0.remove(var0.size() - 1);
            if (var1 == null) {
                MirrorContext.clear();
            } else {
                MirrorContext.set(var1);
            }
        }
    }

    public static void reset() {
        List var0 = SAVED.get();
        if (!var0.isEmpty()) {
            var0.clear();
            MirrorContext.clear();
        }
    }
}
