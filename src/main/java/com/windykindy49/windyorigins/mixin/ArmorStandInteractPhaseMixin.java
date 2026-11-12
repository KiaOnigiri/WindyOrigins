package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.util.MirrorEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({ArmorStandEntity.class})
public abstract class ArmorStandInteractPhaseMixin {
    @Inject(
        method = {"interactAt"},
        at = {@At("HEAD")},
        cancellable = true
    )
    private void windyorigins$refuseCrossPhase(PlayerEntity var1, Vec3d var2, Hand var3, CallbackInfoReturnable<ActionResult> var4) {
        if (((MirrorEntity)(Object)this).windyorigins$isInMirrorWorld() != ((MirrorEntity)var1).windyorigins$isInMirrorWorld()) {
            var4.setReturnValue(ActionResult.FAIL);
        }
    }
}
