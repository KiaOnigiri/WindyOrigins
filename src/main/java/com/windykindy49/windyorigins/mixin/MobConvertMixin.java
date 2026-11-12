package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.util.MirrorEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({MobEntity.class})
public abstract class MobConvertMixin {
    @Inject(
        method = {"convertTo(Lnet/minecraft/entity/EntityType;Z)Lnet/minecraft/entity/mob/MobEntity;"},
        at = {@At("RETURN")}
    )
    private <T extends MobEntity> void windyorigins$keepPhase(EntityType<T> var1, boolean var2, CallbackInfoReturnable<T> var3) {
        MobEntity var4 = (MobEntity)var3.getReturnValue();
        if (var4 != null) {
            ((MirrorEntity)var4).windyorigins$setInMirrorWorld(((MirrorEntity)(Object)this).windyorigins$isInMirrorWorld());
        }
    }
}
