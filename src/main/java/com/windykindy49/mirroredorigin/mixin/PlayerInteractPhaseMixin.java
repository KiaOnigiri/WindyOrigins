package com.windykindy49.mirroredorigin.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import com.windykindy49.mirroredorigin.util.MirrorEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Right-clicking an entity from the other phase.
 *
 * Attacking was already refused by the server (LivingEntityMixin, and now EntityMixin for
 * the non-living ones) and so was mounting (EntityRideMixin), but interaction had no
 * server-side check at all. It only appeared safe because ProjectileUtilMixin filters the
 * raycast, so a vanilla client never aims at an entity in the other phase and never sends
 * the packet - the rule was enforced entirely by the attacker's own client.
 *
 * That is fine against accidents and useless against anyone running a modified client, who
 * could shear another phase's sheep, empty its chest boat, trade with its villagers, leash
 * its animals or breed them. On a server the decision belongs here.
 *
 * PlayerEntity#interact is the shared entry point for the interact and interact-at packets,
 * and returning FAIL there is exactly what vanilla does for an interaction that is not
 * allowed. Cancelling the packet handler itself was the obvious alternative and is the wrong
 * one: ServerPlayNetworkHandlerContextMixin opens a phase scope at its HEAD and closes it at
 * RETURN, and a cancel would return past that closing callback and strand the scope.
 */
@Mixin(value = {PlayerEntity.class})
public abstract class PlayerInteractPhaseMixin {
    @Inject(method = "interact", at = @At("HEAD"), cancellable = true)
    private void mirroredorigin$refuseCrossPhaseInteraction(Entity entity, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        if (((MirrorEntity) (Object) this).mirroredorigin$isInMirrorWorld()
            != ((MirrorEntity) entity).mirroredorigin$isInMirrorWorld()) {
            cir.setReturnValue(ActionResult.FAIL);
        }
    }
}
