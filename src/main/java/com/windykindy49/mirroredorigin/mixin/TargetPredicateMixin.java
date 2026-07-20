package com.windykindy49.mirroredorigin.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.TargetPredicate;
import com.windykindy49.mirroredorigin.util.MirrorEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={TargetPredicate.class})
public class TargetPredicateMixin {
    @Inject(method={"test"}, at={@At(value="HEAD")}, cancellable=true)
    private void onTest(@Nullable LivingEntity baseEntity, LivingEntity targetEntity, CallbackInfoReturnable<Boolean> cir) {
        if (baseEntity != null && targetEntity != null && ((MirrorEntity)baseEntity).mirroredorigin$isInMirrorWorld() != ((MirrorEntity)targetEntity).mirroredorigin$isInMirrorWorld()) {
            cir.setReturnValue(false);
        }
    }
}

