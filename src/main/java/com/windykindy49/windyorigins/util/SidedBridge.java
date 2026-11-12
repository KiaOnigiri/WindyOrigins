package com.windykindy49.windyorigins.util;

import java.util.function.Supplier;
import net.minecraft.entity.Entity;

public final class SidedBridge {
    private static Supplier<Entity> camera = () -> null;

    private SidedBridge() {
    }

    public static Entity camera() {
        return camera.get();
    }

    public static void setCamera(Supplier<Entity> var0) {
        camera = var0;
    }
}
