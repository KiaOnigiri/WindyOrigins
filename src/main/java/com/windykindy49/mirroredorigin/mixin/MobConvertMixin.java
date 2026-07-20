package com.windykindy49.mirroredorigin.mixin;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import com.windykindy49.mirroredorigin.util.MirrorEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * When a mob is replaced by another mob (lightning: pig -> zombified piglin,
 * villager -> witch; mooshroom shearing; etc.) the replacement inherits the original's
 * phase instead of falling back to the overworld.
 */
@Mixin(value={MobEntity.class})
public abstract class MobConvertMixin {
    @Inject(method={"convertTo(Lnet/minecraft/entity/EntityType;Z)Lnet/minecraft/entity/mob/MobEntity;"}, at={@At(value="RETURN")})
    private <T extends MobEntity> void mirroredorigin$keepPhase(EntityType<T> entityType, boolean keepEquipment, CallbackInfoReturnable<T> cir) {
        T converted = cir.getReturnValue();
        if (converted != null) {
            ((MirrorEntity)converted).mirroredorigin$setInMirrorWorld(
                ((MirrorEntity)(Object)this).mirroredorigin$isInMirrorWorld());
        }
    }
}
