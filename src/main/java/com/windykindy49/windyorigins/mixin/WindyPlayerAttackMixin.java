package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.common.AttackHooks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({PlayerEntity.class})
public abstract class WindyPlayerAttackMixin {
    @Inject(
        method = {"attack"},
        at = {@At("HEAD")},
        cancellable = true
    )
    private void windyorigins$beforeAttack(Entity var1, CallbackInfo var2) {
        if (AttackHooks.beforeAttack((PlayerEntity)(Object)this, var1)) {
            var2.cancel();
        }
    }
}
