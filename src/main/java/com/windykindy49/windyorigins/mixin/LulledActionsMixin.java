package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.client.DreamVision;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({MinecraftClient.class})
public abstract class LulledActionsMixin {
    @Inject(
        method = {"doAttack"},
        at = {@At("HEAD")},
        cancellable = true
    )
    private void windyorigins$lateAttack(CallbackInfoReturnable<Boolean> var1) {
        if (DreamVision.delayAttack()) {
            var1.setReturnValue(false);
        }
    }

    @Inject(
        method = {"doItemUse"},
        at = {@At("HEAD")},
        cancellable = true
    )
    private void windyorigins$lateUse(CallbackInfo var1) {
        if (DreamVision.delayUse()) {
            var1.cancel();
        }
    }
}
