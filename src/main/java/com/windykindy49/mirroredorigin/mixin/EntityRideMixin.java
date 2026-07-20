package com.windykindy49.mirroredorigin.mixin;

import net.minecraft.entity.Entity;
import com.windykindy49.mirroredorigin.util.MirrorEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={Entity.class})
public class EntityRideMixin {
    @Inject(method={"startRiding(Lnet/minecraft/entity/Entity;Z)Z"}, at={@At(value="HEAD")}, cancellable=true)
    private void onStartRiding(Entity entity, boolean force, CallbackInfoReturnable<Boolean> cir) {
        if (((MirrorEntity)(Object)this).mirroredorigin$isInMirrorWorld() != ((MirrorEntity)entity).mirroredorigin$isInMirrorWorld()) {
            cir.setReturnValue(false);
        }
    }
}

