package com.windykindy49.mirroredorigin.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.AbstractBeeSoundInstance;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.entity.passive.BeeEntity;
import com.windykindy49.mirroredorigin.util.MirrorEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={AbstractBeeSoundInstance.class})
public abstract class BeeSoundMixin
extends MovingSoundInstance {
    @Shadow
    protected BeeEntity bee;

    protected BeeSoundMixin() {
        super(null, null, null);
    }

    @Inject(method={"tick"}, at={@At(value="RETURN")})
    private void onTick(CallbackInfo ci) {
        boolean playerPhase;
        boolean beePhase;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.cameraEntity != null && this.bee != null && (beePhase = ((MirrorEntity)(Object)this.bee).mirroredorigin$isInMirrorWorld()) != (playerPhase = ((MirrorEntity)client.cameraEntity).mirroredorigin$isInMirrorWorld())) {
            this.volume = 0.0f;
        }
    }
}

