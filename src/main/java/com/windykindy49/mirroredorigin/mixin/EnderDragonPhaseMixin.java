package com.windykindy49.mirroredorigin.mixin;

import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import com.windykindy49.mirroredorigin.util.MirrorEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The ender dragon sweeps everything near its wings and body into two lists: one gets
 * knocked away, one gets hurt. Neither was filtered by phase.
 *
 * The damage half looked fine only by accident - LivingEntityMixin refuses the hit on
 * arrival, so nothing took damage. The knockback half has no such backstop: it writes
 * velocity straight onto the entity and never goes through damage() or pushAwayFrom(), so a
 * player in the mirror phase standing where the dragon flies was hurled across the island by
 * something they could not see, hear or hit back. Dragon breath was already handled
 * correctly, which made this easy to overlook.
 *
 * Filtering both lists at the source fixes the knockback and stops the pointless damage
 * calls at the same time. The lists come fresh from World#getOtherEntities, so they are safe
 * to modify in place.
 */
@Mixin(value = {EnderDragonEntity.class})
public abstract class EnderDragonPhaseMixin {
    @Inject(method = {"launchLivingEntities", "damageLivingEntities"}, at = @At("HEAD"))
    private void mirroredorigin$onlySweepSamePhase(List<Entity> entities, CallbackInfo ci) {
        boolean phase = ((MirrorEntity) (Object) this).mirroredorigin$isInMirrorWorld();
        entities.removeIf(entity -> ((MirrorEntity) entity).mirroredorigin$isInMirrorWorld() != phase);
    }
}
