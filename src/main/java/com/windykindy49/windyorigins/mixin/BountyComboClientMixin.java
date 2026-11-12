package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.bounty.BountyManager;
import com.windykindy49.windyorigins.bounty.client.ClientBountyState;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ClientPlayerInteractionManager.class})
public abstract class BountyComboClientMixin {
    @Inject(
        method = {"attackEntity"},
        at = {@At("RETURN")}
    )
    private void windyorigins$comboRefund(PlayerEntity var1, Entity var2, CallbackInfo var3) {
        if (ClientBountyState.hasContract() && var2.getId() == ClientBountyState.getTargetId() && BountyManager.hasPower(var1, "duelist")) {
            LivingEntityAccessor var4 = (LivingEntityAccessor)var1;
            int var5 = Math.round(var1.getAttackCooldownProgressPerTick() * 0.25F);
            var4.windyorigins$setLastAttackedTicks(var4.windyorigins$getLastAttackedTicks() + var5);
        }
    }
}
