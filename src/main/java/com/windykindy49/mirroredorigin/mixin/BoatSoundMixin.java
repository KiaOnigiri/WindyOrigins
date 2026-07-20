package com.windykindy49.mirroredorigin.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.sound.SoundEvent;
import com.windykindy49.mirroredorigin.util.MirrorEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The rowing sound of a boat in the other phase must not be audible.
 * getPaddleSoundEvent may legitimately return null (a boat in mid-air makes no paddle
 * sound), so returning null here silences the oars while leaving the paddle animation
 * itself untouched.
 */
@Mixin(value={BoatEntity.class})
public abstract class BoatSoundMixin {
    @Inject(method={"getPaddleSoundEvent"}, at={@At(value="HEAD")}, cancellable=true)
    private void mirroredorigin$muteCrossPhaseOars(CallbackInfoReturnable<SoundEvent> cir) {
        Entity camera = MinecraftClient.getInstance().getCameraEntity();
        if (camera == null) {
            return;
        }
        if (((MirrorEntity)(Object)this).mirroredorigin$isInMirrorWorld()
                != ((MirrorEntity)camera).mirroredorigin$isInMirrorWorld()) {
            cir.setReturnValue(null);
        }
    }
}
