package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.util.MirrorEntity;
import net.minecraft.entity.passive.AnimalEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({AnimalEntity.class})
public class AnimalEntityMixin {
    @Inject(
        method = {"canBreedWith"},
        at = {@At("HEAD")},
        cancellable = true
    )
    private void onCanBreedWith(AnimalEntity var1, CallbackInfoReturnable<Boolean> var2) {
        if (var1 != null && ((MirrorEntity)(Object)this).windyorigins$isInMirrorWorld() != ((MirrorEntity)var1).windyorigins$isInMirrorWorld()) {
            var2.setReturnValue(false);
        }
    }
}
