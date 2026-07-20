package com.windykindy49.mirroredorigin.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import com.windykindy49.mirroredorigin.util.MirrorEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={LivingEntity.class})
public class LivingEntityEffectMixin {
    @Inject(method={"addStatusEffect(Lnet/minecraft/entity/effect/StatusEffectInstance;Lnet/minecraft/entity/Entity;)Z"}, at={@At(value="HEAD")}, cancellable=true)
    private void onAddStatusEffect(StatusEffectInstance effect, @Nullable Entity source, CallbackInfoReturnable<Boolean> cir) {
        if (source != null && ((MirrorEntity)(Object)this).mirroredorigin$isInMirrorWorld() != ((MirrorEntity)source).mirroredorigin$isInMirrorWorld()) {
            cir.setReturnValue(false);
        }
    }
}

