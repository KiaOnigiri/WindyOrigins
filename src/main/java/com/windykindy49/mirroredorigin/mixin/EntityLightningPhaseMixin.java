package com.windykindy49.mirroredorigin.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LightningEntity;
import net.minecraft.server.world.ServerWorld;
import com.windykindy49.mirroredorigin.util.CrossPhaseEntity;
import com.windykindy49.mirroredorigin.util.MirrorEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lightning strikes everything standing near where it lands, and that list was never
 * filtered by phase.
 *
 * The damage half looked handled - LivingEntityMixin refuses it - but Entity#onStruckByLightning
 * also sets the victim on fire, and setOnFireFor() has nothing to do with damage(). A player
 * or animal in the other phase burst into flames from a bolt that, for them, never happened.
 * The same blind spot as the ender dragon's knockback: harm that does not travel through
 * damage() slips past every guard built around it.
 *
 * LightningConvertMixin handles the mobs that override this method to transform themselves;
 * this covers everything else, which is to say everything that burns.
 */
@Mixin(value = {Entity.class})
public abstract class EntityLightningPhaseMixin {
    @Inject(method = "onStruckByLightning", at = @At("HEAD"), cancellable = true)
    private void mirroredorigin$ignoreCrossPhaseBolt(ServerWorld world, LightningEntity lightning, CallbackInfo ci) {
        if (((CrossPhaseEntity) lightning).mirroredorigin$isCrossPhase()) {
            return; // a storm strikes both worlds
        }
        if (((MirrorEntity) (Object) this).mirroredorigin$isInMirrorWorld()
            != ((MirrorEntity) lightning).mirroredorigin$isInMirrorWorld()) {
            ci.cancel();
        }
    }
}
