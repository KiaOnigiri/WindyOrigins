package com.windykindy49.mirroredorigin.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import com.windykindy49.mirroredorigin.util.MirrorEntity;
import com.windykindy49.mirroredorigin.util.PhaseScope;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Two jobs, and the second one is the fix for "pickup sounds are audible in both phases".
 *
 * 1. A player must not pick up arrows and tridents lying in the other phase -> cancel outright.
 * 2. A LEGITIMATE, same-phase pickup emits its sound through World#playSound, which carries
 *    no source entity, so the packet filter can only fall back to the ambient phase context.
 *    Relying on the surrounding tick to have published that context turned out to be too
 *    fragile, so the phase is published explicitly for the duration of the collision
 *    handler. Now the sound is unambiguously attributed to the picking player's phase and
 *    the filter drops it for everyone in the other one.
 */
@Mixin(value = {PersistentProjectileEntity.class})
public abstract class PersistentProjectileEntityMixin {
    @Unique
    private boolean mirroredorigin$scoped;

    @Inject(method = {"onPlayerCollision"}, at = {@At(value = "HEAD")}, cancellable = true)
    private void mirroredorigin$beginCollision(PlayerEntity player, CallbackInfo ci) {
        boolean own = ((MirrorEntity) (Object) this).mirroredorigin$isInMirrorWorld();
        boolean playerPhase = ((MirrorEntity) player).mirroredorigin$isInMirrorWorld();
        if (own != playerPhase) {
            ci.cancel();
            return;
        }
        PhaseScope.enter(playerPhase);
        this.mirroredorigin$scoped = true;
    }

    @Inject(method = {"onPlayerCollision"}, at = {@At(value = "RETURN")})
    private void mirroredorigin$endCollision(PlayerEntity player, CallbackInfo ci) {
        if (this.mirroredorigin$scoped) {
            this.mirroredorigin$scoped = false;
            PhaseScope.exit();
        }
    }
}
