package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.util.SoundState;
import net.minecraft.entity.Entity;
import net.minecraft.sound.SoundEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({Entity.class})
public abstract class EntitySoundMixin {
    @Inject(
        method = {"playSound(Lnet/minecraft/sound/SoundEvent;FF)V"},
        at = {@At("HEAD")}
    )
    private void onPlaySoundStart(SoundEvent var1, float var2, float var3, CallbackInfo var4) {
        SoundState.CURRENT_SOUND_SOURCE.set((Entity)(Object)this);
    }

    @Inject(
        method = {"playSound(Lnet/minecraft/sound/SoundEvent;FF)V"},
        at = {@At("RETURN")}
    )
    private void onPlaySoundEnd(SoundEvent var1, float var2, float var3, CallbackInfo var4) {
        SoundState.CURRENT_SOUND_SOURCE.remove();
    }
}
