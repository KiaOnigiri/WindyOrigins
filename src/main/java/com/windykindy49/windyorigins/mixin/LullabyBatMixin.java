package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.common.Powers;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.TargetPredicate;
import net.minecraft.entity.passive.BatEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin({BatEntity.class})
public abstract class LullabyBatMixin {
    @Redirect(
        method = {"mobTick"},
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/World;getClosestPlayer(Lnet/minecraft/entity/ai/TargetPredicate;Lnet/minecraft/entity/LivingEntity;)Lnet/minecraft/entity/player/PlayerEntity;"
        )
    )
    private PlayerEntity windyorigins$lullaby(World var1, TargetPredicate var2, LivingEntity var3) {
        PlayerEntity var4 = var1.getClosestPlayer(var2, var3);
        if (var4 != null && Powers.has(var4, "lullaby")) {
            for (PlayerEntity var6 : var1.getPlayers()) {
                if (var6 != var4 && !Powers.has(var6, "lullaby") && var2.test(var3, var6)) {
                    return var6;
                }
            }

            return null;
        } else {
            return var4;
        }
    }
}
