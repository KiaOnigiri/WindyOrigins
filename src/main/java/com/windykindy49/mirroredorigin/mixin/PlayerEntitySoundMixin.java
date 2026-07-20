package com.windykindy49.mirroredorigin.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundEvent;
import com.windykindy49.mirroredorigin.util.SoundState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={PlayerEntity.class})
public abstract class PlayerEntitySoundMixin {
    @Inject(method={"playSound(Lnet/minecraft/sound/SoundEvent;FF)V"}, at={@At(value="HEAD")})
    private void onPlaySoundStart(SoundEvent sound, float volume, float pitch, CallbackInfo ci) {
        SoundState.CURRENT_SOUND_SOURCE.set((Entity)(Object)this);
    }

    @Inject(method={"playSound(Lnet/minecraft/sound/SoundEvent;FF)V"}, at={@At(value="RETURN")})
    private void onPlaySoundEnd(SoundEvent sound, float volume, float pitch, CallbackInfo ci) {
        SoundState.CURRENT_SOUND_SOURCE.remove();
    }
}

