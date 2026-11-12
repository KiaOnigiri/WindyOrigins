package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.util.MirrorEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.sound.SoundEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({BoatEntity.class})
public abstract class BoatSoundMixin {
    @Inject(
        method = {"getPaddleSoundEvent"},
        at = {@At("HEAD")},
        cancellable = true
    )
    private void windyorigins$muteCrossPhaseOars(CallbackInfoReturnable<SoundEvent> var1) {
        Entity var2 = MinecraftClient.getInstance().getCameraEntity();
        if (var2 != null) {
            if (((MirrorEntity)(Object)this).windyorigins$isInMirrorWorld() != ((MirrorEntity)var2).windyorigins$isInMirrorWorld()) {
                var1.setReturnValue(null);
            }
        }
    }
}
