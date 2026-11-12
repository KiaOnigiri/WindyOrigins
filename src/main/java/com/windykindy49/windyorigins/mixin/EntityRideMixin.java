package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.util.MirrorEntity;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({Entity.class})
public class EntityRideMixin {
    @Inject(
        method = {"startRiding(Lnet/minecraft/entity/Entity;Z)Z"},
        at = {@At("HEAD")},
        cancellable = true
    )
    private void onStartRiding(Entity var1, boolean var2, CallbackInfoReturnable<Boolean> var3) {
        if (((MirrorEntity)(Object)this).windyorigins$isInMirrorWorld() != ((MirrorEntity)var1).windyorigins$isInMirrorWorld()) {
            var3.setReturnValue(false);
        }
    }
}
