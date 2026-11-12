package com.windykindy49.windyorigins.registry;

import com.windykindy49.windyorigins.common.ApoliReg;
import com.windykindy49.windyorigins.common.StillTracker;
import com.windykindy49.windyorigins.common.WindyEffects;
import com.windykindy49.windyorigins.dream.DreamManager;
import com.windykindy49.windyorigins.folded.FoldedManager;
import com.windykindy49.windyorigins.frost.FrostManager;
import com.windykindy49.windyorigins.luminian.LuminianManager;
import com.windykindy49.windyorigins.mixin.EntityFlagAccessor;
import com.windykindy49.windyorigins.revenant.RevenantManager;
import com.windykindy49.windyorigins.sanguine.SanguineManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;

public final class RacePowers {
    private RacePowers() {
    }

    public static void register() {
        ApoliReg.action("ice_coffin", var0 -> {
            if (var0 instanceof ServerPlayerEntity var1) {
                FrostManager.coffin(var1);
            }
        });
        ApoliReg.action("ice_wall", var0 -> {
            if (var0 instanceof ServerPlayerEntity var1) {
                FrostManager.wall(var1);
            }
        });
        ApoliReg.action("luminian_revive", var0 -> {
            if (var0 instanceof ServerPlayerEntity var1) {
                LuminianManager.revive(var1);
            }
        });
        ApoliReg.action("fold_hover", var0 -> {
            if (var0 instanceof ServerPlayerEntity var1) {
                FoldedManager.toggleHover(var1);
            }
        });
        ApoliReg.action("fold_step", var0 -> {
            if (var0 instanceof ServerPlayerEntity var1) {
                FoldedManager.step(var1);
            }
        });
        ApoliReg.action("fold_knot", var0 -> {
            if (var0 instanceof ServerPlayerEntity var1) {
                FoldedManager.knot(var1);
            }
        });
        ApoliReg.action("bat_form", var0 -> {
            if (var0 instanceof ServerPlayerEntity var1) {
                SanguineManager.batForm(var1);
            }
        });
        ApoliReg.condition(
            "bat_hidden",
            var0 -> vanillaInvisible(var0) && (var0.getWorld().isClient() || var0 instanceof LivingEntity var1 && var1.hasStatusEffect(WindyEffects.BAT_FORM))
        );
        ApoliReg.action("possess", var0 -> {
            if (var0 instanceof ServerPlayerEntity var1) {
                RevenantManager.possess(var1);
            }
        });
        ApoliReg.action("wisps", var0 -> {
            if (var0 instanceof ServerPlayerEntity var1) {
                RevenantManager.summonWisps(var1);
            }
        });
        ApoliReg.condition("still", var0 -> {
            if (var0 instanceof StillTracker var1 && var1.windyorigins$getStillTicks() >= 40) {
                return true;
            }

            return false;
        });
        ApoliReg.action("dream_touch", var0 -> {
            if (var0 instanceof ServerPlayerEntity var1) {
                DreamManager.touch(var1);
            }
        });
        ApoliReg.action("dream_enter", var0 -> {
            if (var0 instanceof ServerPlayerEntity var1) {
                DreamManager.enter(var1);
            }
        });
        ApoliReg.action("dream_summon", var0 -> {
            if (var0 instanceof ServerPlayerEntity var1) {
                DreamManager.summon(var1);
            }
        });
        ApoliReg.condition("in_dreams", var0 -> var0.getWorld().getRegistryKey() == DreamManager.DREAMS);
        ApoliReg.condition(
            "folded_away",
            var0 -> vanillaInvisible(var0) && (var0.getWorld().isClient() || var0 instanceof LivingEntity var1 && var1.hasStatusEffect(WindyEffects.FOLDED))
        );
    }

    private static boolean vanillaInvisible(Entity var0) {
        return ((EntityFlagAccessor)var0).windyorigins$getFlag(5);
    }
}
