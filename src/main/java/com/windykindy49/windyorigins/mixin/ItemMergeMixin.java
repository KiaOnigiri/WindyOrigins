package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.util.MirrorEntity;
import net.minecraft.entity.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ItemEntity.class})
public abstract class ItemMergeMixin {
    @Inject(
        method = {"tryMerge(Lnet/minecraft/entity/ItemEntity;)V"},
        at = {@At("HEAD")},
        cancellable = true
    )
    private void windyorigins$onlyMergeSamePhase(ItemEntity var1, CallbackInfo var2) {
        if (((MirrorEntity)(Object)this).windyorigins$isInMirrorWorld() != ((MirrorEntity)var1).windyorigins$isInMirrorWorld()) {
            var2.cancel();
        }
    }
}
