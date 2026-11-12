package com.windykindy49.windyorigins.client;

import com.windykindy49.windyorigins.common.Powers;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

public final class ClientOutlines {
    public static final Set<Integer> SERVER_MARKED = new HashSet<>();
    public static final Map<Integer, Long> TIMED = new HashMap<>();

    private ClientOutlines() {
    }

    public static boolean should(Entity var0) {
        if (SERVER_MARKED.contains(var0.getId())) {
            return true;
        } else {
            Long var1 = TIMED.get(var0.getId());
            if (var1 != null) {
                if (var1 > var0.getWorld().getTime()) {
                    return true;
                }

                TIMED.remove(var0.getId());
            }

            ClientPlayerEntity var2 = MinecraftClient.getInstance().player;
            if (var2 != null && var0 != var2 && var0 instanceof LivingEntity var3 && var3.isAlive()) {
                return var2.getHungerManager().getFoodLevel() <= 10 && Powers.has(var2, "blood_sense") && var0.squaredDistanceTo(var2) < 256.0;
            }

            return false;
        }
    }
}
