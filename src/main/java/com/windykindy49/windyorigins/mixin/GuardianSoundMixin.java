package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.util.MirrorEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.GuardianAttackSoundInstance;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.entity.mob.GuardianEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({GuardianAttackSoundInstance.class})
public abstract class GuardianSoundMixin extends MovingSoundInstance {
    @Shadow
    private GuardianEntity guardian;

    protected GuardianSoundMixin() {
        super(null, null, null);
    }

    @Inject(
        method = {"tick"},
        at = {@At("RETURN")}
    )
    private void onTick(CallbackInfo var1) {
        MinecraftClient var4 = MinecraftClient.getInstance();
        if (var4.cameraEntity != null
            && this.guardian != null
            && ((MirrorEntity)(Object)this.guardian).windyorigins$isInMirrorWorld() != ((MirrorEntity)var4.cameraEntity).windyorigins$isInMirrorWorld()) {
            this.volume = 0.0F;
        }
    }
}
