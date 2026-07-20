package com.windykindy49.mirroredorigin.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileEntity;
import com.windykindy49.mirroredorigin.util.MirrorEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={ProjectileEntity.class})
public class ProjectileEntityMixin {
    @Inject(method={"canHit"}, at={@At(value="HEAD")}, cancellable=true)
    private void onCanHit(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (((MirrorEntity)(Object)this).mirroredorigin$isInMirrorWorld() != ((MirrorEntity)entity).mirroredorigin$isInMirrorWorld()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method={"setOwner"}, at={@At(value="TAIL")})
    private void onSetOwner(Entity owner, CallbackInfo ci) {
        if (owner != null) {
            ((MirrorEntity)(Object)this).mirroredorigin$setInMirrorWorld(((MirrorEntity)owner).mirroredorigin$isInMirrorWorld());
        }
    }
}

