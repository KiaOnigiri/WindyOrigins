package com.windykindy49.mirroredorigin.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.WardenEntity;
import com.windykindy49.mirroredorigin.util.MirrorEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={WardenEntity.class})
public class WardenEntityMixin {
    @Inject(method={"isValidTarget"}, at={@At(value="HEAD")}, cancellable=true)
    private void onIsValidTarget(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        boolean targetPhase;
        boolean wardenPhase;
        if (entity != null && (wardenPhase = ((MirrorEntity)(Object)this).mirroredorigin$isInMirrorWorld()) != (targetPhase = ((MirrorEntity)entity).mirroredorigin$isInMirrorWorld())) {
            cir.setReturnValue(false);
        }
    }
}

