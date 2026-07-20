package com.windykindy49.mirroredorigin.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MovingMinecartSoundInstance;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import com.windykindy49.mirroredorigin.util.MirrorEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={MovingMinecartSoundInstance.class})
public abstract class MinecartSoundMixin
extends MovingSoundInstance {
    @Shadow
    private AbstractMinecartEntity minecart;

    protected MinecartSoundMixin() {
        super(null, null, null);
    }

    @Inject(method={"tick"}, at={@At(value="RETURN")})
    private void onTick(CallbackInfo ci) {
        boolean playerPhase;
        boolean phase;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.cameraEntity != null && this.minecart != null && (phase = ((MirrorEntity)(Object)this.minecart).mirroredorigin$isInMirrorWorld()) != (playerPhase = ((MirrorEntity)client.cameraEntity).mirroredorigin$isInMirrorWorld())) {
            this.volume = 0.0f;
        }
    }
}

