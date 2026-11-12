package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.client.HostClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Shakes the Host's view: interference from an overheating chip, and the kick of Volta's shots. */
@Mixin({Camera.class})
public abstract class VoltaCameraMixin {
    @Shadow
    private float yaw;
    @Shadow
    private float pitch;

    @Shadow
    protected abstract void setRotation(float var1, float var2);

    @Inject(
        method = {"update"},
        at = {@At("TAIL")}
    )
    private void windyorigins$shake(BlockView var1, Entity var2, boolean var3, boolean var4, float var5, CallbackInfo var6) {
        float var7 = HostClient.shakeYaw(var5);
        float var8 = HostClient.shakePitch(var5);
        if (var7 != 0.0F || var8 != 0.0F) {
            this.setRotation(this.yaw + var7, this.pitch + var8);
        }
    }
}
