package com.windykindy49.mirroredorigin.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.sound.SoundEvent;
import com.windykindy49.mirroredorigin.util.SoundState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={Entity.class})
/**
 * Entity#playSound starts with "if (this.isSilent()) return;", so for a silent entity the
 * TAIL callback that used to clear the source never ran: CURRENT_SOUND_SOURCE stayed
 * pointing at it, and the next sound with no source of its own - any positional world sound -
 * was filtered as if it belonged to that entity's phase. RETURN covers the early exit too.
 */
public abstract class EntitySoundMixin {
    @Inject(method={"playSound(Lnet/minecraft/sound/SoundEvent;FF)V"}, at={@At(value="HEAD")})
    private void onPlaySoundStart(SoundEvent sound, float volume, float pitch, CallbackInfo ci) {
        SoundState.CURRENT_SOUND_SOURCE.set((Entity)(Object)this);
    }

    @Inject(method={"playSound(Lnet/minecraft/sound/SoundEvent;FF)V"}, at={@At(value="RETURN")})
    private void onPlaySoundEnd(SoundEvent sound, float volume, float pitch, CallbackInfo ci) {
        SoundState.CURRENT_SOUND_SOURCE.remove();
    }
}

