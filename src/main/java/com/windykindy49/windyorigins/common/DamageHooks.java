package com.windykindy49.windyorigins.common;

import com.windykindy49.windyorigins.dream.DreamManager;
import com.windykindy49.windyorigins.host.HostManager;
import com.windykindy49.windyorigins.sanguine.SanguineManager;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.tag.DamageTypeTags;

public final class DamageHooks {
    private DamageHooks() {
    }

    public static boolean refuse(LivingEntity var0, DamageSource var1) {
        HostManager.beforeHurt(var0);
        if (var1.isIn(DamageTypeTags.IS_FALL) && com.windykindy49.windyorigins.revenant.RevenantManager.isEjected(var0)) {
            return true;
        }

        if (!var1.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY) && HostManager.absorb(var0, var1)) {
            return true;
        }

        return var1.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)
            ? false
            : var0.hasStatusEffect(WindyEffects.ICE_COFFIN) || var0.hasStatusEffect(WindyEffects.FOLDED) || var0.hasStatusEffect(WindyEffects.VENGEANCE);
    }

    public static float scale(LivingEntity var0, DamageSource var1, float var2) {
        if (var1.getAttacker() instanceof LivingEntity var3
            && var1.getSource() == var3
            && var3.getMainHandStack().isIn(SanguineManager.ASPEN)
            && Powers.has(var0, "aspen")) {
            var2 *= 3.0F;
        }

        if (var0.getWorld().getRegistryKey() == DreamManager.DREAMS) {
            if (var1.getAttacker() instanceof PlayerEntity var5 && var1.getSource() == var5 && var5.getMainHandStack().isEmpty()) {
                var2 = Math.max(var2, 3.0F);
            }

            if (var1.getAttacker() instanceof PlayerEntity var6 && Powers.has(var6, "dream_touch")) {
                var2 *= 1.5F;
            }

            if (var0 instanceof PlayerEntity && Powers.has(var0, "dream_touch")) {
                var2 *= 0.8F;
            }
        }

        var2 = com.windykindy49.windyorigins.mirror.MirrorManager.scaleIncoming(var0, var2);
        if (var0.hasStatusEffect(WindyEffects.CYBERPSYCHOSIS)) {
            var2 *= 1.5F;
        }

        return var2;
    }

    public static void after(LivingEntity var0, DamageSource var1, float var2) {
        if (!var0.getWorld().isClient()) {
            HostManager.onHurt(var0, var1, var2);
            DreamManager.onHurt(var0, var1);
            com.windykindy49.windyorigins.mirror.MirrorManager.afterDamage(var0, var1, var2);
            if (var1.getAttacker() instanceof PlayerEntity var3 && var1.getSource() == var3 && var3 != var0) {
                SanguineManager.lifesteal(var3, var2);
            }
        }
    }
}
