package com.windykindy49.mirroredorigin.mixin;

import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import com.windykindy49.mirroredorigin.util.MirrorEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The one hole left after PlayerInteractPhaseMixin: armor stands are driven by the
 * interact-at packet, which calls Entity#interactAt directly and never passes through
 * PlayerEntity#interact. Swapping armour on a stand belonging to the other phase was
 * therefore still reachable with a modified client.
 *
 * Vanilla clients cannot aim at it - the raycast is phase-filtered - so this changes nothing
 * in normal play; it just moves the last interaction rule from the client to the server.
 */
@Mixin(value = {ArmorStandEntity.class})
public abstract class ArmorStandInteractPhaseMixin {
    @Inject(method = "interactAt", at = @At("HEAD"), cancellable = true)
    private void mirroredorigin$refuseCrossPhase(PlayerEntity player, Vec3d hitPos, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        if (((MirrorEntity) (Object) this).mirroredorigin$isInMirrorWorld()
            != ((MirrorEntity) player).mirroredorigin$isInMirrorWorld()) {
            cir.setReturnValue(ActionResult.FAIL);
        }
    }
}
