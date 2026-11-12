package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.util.MirrorEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({ProjectileEntity.class})
public class ProjectileEntityMixin {
    @Inject(
        method = {"canHit"},
        at = {@At("HEAD")},
        cancellable = true
    )
    private void onCanHit(Entity var1, CallbackInfoReturnable<Boolean> var2) {
        if (((MirrorEntity)(Object)this).windyorigins$isInMirrorWorld() != ((MirrorEntity)var1).windyorigins$isInMirrorWorld()) {
            var2.setReturnValue(false);
        }
    }

    @Inject(
        method = {"setOwner"},
        at = {@At("TAIL")}
    )
    private void onSetOwner(Entity var1, CallbackInfo var2) {
        if (var1 != null) {
            ((MirrorEntity)(Object)this).windyorigins$setInMirrorWorld(((MirrorEntity)var1).windyorigins$isInMirrorWorld());
        }
    }
}
