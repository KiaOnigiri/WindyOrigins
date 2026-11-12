package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.common.WindyEffects;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ClientPlayerEntity.class})
public abstract class GhostPushMixin {
    @Inject(
        method = {"pushOutOfBlocks"},
        at = {@At("HEAD")},
        cancellable = true
    )
    private void windyorigins$ghostStays(double var1, double var3, CallbackInfo var5) {
        if (((ClientPlayerEntity)(Object)this).hasStatusEffect(WindyEffects.VENGEANCE)) {
            var5.cancel();
        }
    }
}
