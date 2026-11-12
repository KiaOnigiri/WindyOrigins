package com.windykindy49.windyorigins.dream;

import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;

public final class DreamRules {
    private static final String[] AWAKE = new String[]{
        "dream_",
        "sleep_anywhere",
        "phantom_kin",
        "glass_body",
        "water_phobia",
        "reality_toxicity",
        "phase_shift",
        "banishment_punch",
        "glass_skin",
        "spawn_in_mirror"
    };

    private DreamRules() {
    }

    public static boolean silenced(Entity var0, Identifier var1) {
        if (var0.getWorld() != null && var0.getWorld().getRegistryKey() == DreamManager.DREAMS) {
            if (!"windyorigins".equals(var1.getNamespace()) && !"origins".equals(var1.getNamespace())) {
                return false;
            } else {
                String var2 = var1.getPath();

                for (String var6 : AWAKE) {
                    if (var2.startsWith(var6)) {
                        return false;
                    }
                }

                return true;
            }
        } else {
            return false;
        }
    }
}
