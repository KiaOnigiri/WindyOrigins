package com.windykindy49.windyorigins.common;

import com.windykindy49.windyorigins.revenant.RevenantManager;
import com.windykindy49.windyorigins.sanguine.SanguineManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;

public final class AttackHooks {
    private AttackHooks() {
    }

    public static boolean beforeAttack(PlayerEntity var0, Entity var1) {
        if (var0.hasStatusEffect(WindyEffects.ICE_COFFIN)) {
            return true;
        } else if (var0.hasStatusEffect(WindyEffects.BAT_FORM)) {
            return true;
        } else if (var0.hasStatusEffect(WindyEffects.VENGEANCE)) {
            if (var0 instanceof ServerPlayerEntity var3) {
                RevenantManager.ghostStrike(var3, var1);
            }

            return true;
        } else {
            if (var0 instanceof ServerPlayerEntity var2 && SanguineManager.onSwing(var2, var1)) {
                return true;
            }

            return false;
        }
    }
}
