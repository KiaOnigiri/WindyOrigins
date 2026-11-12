package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.client.HostClient;
import com.windykindy49.windyorigins.folded.FoldRifleItem;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({AbstractClientPlayerEntity.class})
public abstract class RifleZoomMixin {
    @Inject(
        method = {"getFovMultiplier"},
        at = {@At("RETURN")},
        cancellable = true
    )
    private void windyorigins$scope(CallbackInfoReturnable<Float> var1) {
        float var2 = FoldRifleItem.charge((AbstractClientPlayerEntity)(Object)this);
        float var3 = var1.getReturnValueF();
        if (var2 > 0.0F) {
            float var4 = Math.min(1.0F, var2 * 1.8F);
            var3 *= 1.0F - 0.75F * (1.0F - (1.0F - var4) * (1.0F - var4));
        }

        var3 *= HostClient.fovFactor(0.0F);
        var1.setReturnValue(var3);
    }
}
