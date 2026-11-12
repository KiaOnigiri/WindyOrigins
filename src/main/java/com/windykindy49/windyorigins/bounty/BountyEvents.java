package com.windykindy49.windyorigins.bounty;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.network.ServerPlayerEntity;

public final class BountyEvents {
    private BountyEvents() {
    }

    public static void register() {
        BountySounds.init();
        ServerTickEvents.END_SERVER_TICK.register(BountyManager::tick);
        ServerLivingEntityEvents.AFTER_DEATH.register((var0, var1) -> {
            if (!var0.getWorld().isClient()) {
                if (var0.getServer() != null) {
                    BountyManager.onTargetDied(var0.getServer(), var0);
                }
            }
        });
        ServerTickEvents.START_SERVER_TICK.register(var0 -> {
            for (ServerPlayerEntity var2 : var0.getPlayerManager().getPlayerList()) {
                if (var2.age % 20 == 0) {
                    BountyMarkState.Entry var3 = BountyMarkState.get(var0.getOverworld()).peek(var2.getUuid());
                    if (var3 != null && var3.target == null) {
                        BountyNetworking.syncHunter(var2, -1, 0, var3.cooldownTicks);
                    }

                    BountyNetworking.syncMarked(var2, BountyManager.isMarkedByAnyone(var0, var2));
                }
            }
        });
    }
}
