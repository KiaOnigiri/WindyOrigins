package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.util.MirrorEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MovingMinecartSoundInstance;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({MovingMinecartSoundInstance.class})
public abstract class MinecartSoundMixin extends MovingSoundInstance {
    @Shadow
    private AbstractMinecartEntity minecart;

    protected MinecartSoundMixin() {
        super(null, null, null);
    }

    @Inject(
        method = {"tick"},
        at = {@At("RETURN")}
    )
    private void onTick(CallbackInfo var1) {
        MinecraftClient var4 = MinecraftClient.getInstance();
        if (var4.cameraEntity != null
            && this.minecart != null
            && ((MirrorEntity)(Object)this.minecart).windyorigins$isInMirrorWorld() != ((MirrorEntity)var4.cameraEntity).windyorigins$isInMirrorWorld()) {
            this.volume = 0.0F;
        }
    }
}
