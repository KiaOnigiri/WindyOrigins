package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.bounty.BountyManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({PlayerEntity.class})
public abstract class BountyComboMixin {
    @Inject(
        method = {"attack"},
        at = {@At("RETURN")}
    )
    private void windyorigins$comboRefund(Entity var1, CallbackInfo var2) {
        PlayerEntity var3 = (PlayerEntity)(Object)this;
        if (var3 instanceof ServerPlayerEntity var4 && BountyManager.isMarkedBy(var4, var1) && BountyManager.hasPower(var4, "duelist")) {
            refund(var3);
            return;
        }
    }

    private static void refund(PlayerEntity var0) {
        LivingEntityAccessor var1 = (LivingEntityAccessor)var0;
        int var2 = Math.round(var0.getAttackCooldownProgressPerTick() * 0.25F);
        var1.windyorigins$setLastAttackedTicks(var1.windyorigins$getLastAttackedTicks() + var2);
    }
}
