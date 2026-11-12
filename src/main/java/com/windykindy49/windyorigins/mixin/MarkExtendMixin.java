package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.bounty.BountyManager;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({LivingEntity.class})
public class MarkExtendMixin {
    @Inject(
        method = {"damage"},
        at = {@At("RETURN")}
    )
    private void windyorigins$extendMarkOnHit(DamageSource var1, float var2, CallbackInfoReturnable<Boolean> var3) {
        if (var3.getReturnValueZ()) {
            if (var1.getAttacker() instanceof ServerPlayerEntity var5) {
                if (BountyManager.isMarkedBy(var5, (LivingEntity)(Object)this)) {
                    BountyManager.extend(var5);
                }
            }
        }
    }
}
