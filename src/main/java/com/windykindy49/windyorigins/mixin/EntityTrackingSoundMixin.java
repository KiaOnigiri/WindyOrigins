package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.util.MirrorEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.EntityTrackingSoundInstance;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({EntityTrackingSoundInstance.class})
public abstract class EntityTrackingSoundMixin extends MovingSoundInstance {
    @Shadow
    private Entity entity;

    protected EntityTrackingSoundMixin() {
        super(null, null, null);
    }

    @Inject(
        method = {"tick"},
        at = {@At("RETURN")}
    )
    private void onTick(CallbackInfo var1) {
        MinecraftClient var4 = MinecraftClient.getInstance();
        if (var4.cameraEntity != null
            && this.entity != null
            && ((MirrorEntity)(Object)this.entity).windyorigins$isInMirrorWorld() != ((MirrorEntity)var4.cameraEntity).windyorigins$isInMirrorWorld()) {
            this.volume = 0.0F;
        }
    }
}
