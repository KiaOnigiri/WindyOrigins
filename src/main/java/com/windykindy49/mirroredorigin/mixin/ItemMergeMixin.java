package com.windykindy49.mirroredorigin.mixin;

import net.minecraft.entity.ItemEntity;
import com.windykindy49.mirroredorigin.util.MirrorEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Dropped stacks used to merge regardless of phase: drop sixteen planks in the overworld
 * phase and sixteen more on the same block in the mirror phase, and they collapsed into one
 * stack of thirty-two that only one of the two players could ever pick up. Items were
 * literally crossing between the worlds.
 *
 * tryMerge(ItemEntity) is the single pairwise entry point - the no-argument tryMerge() scans
 * the neighbourhood and funnels every candidate through it - so one guard here covers all
 * merging.
 */
@Mixin(value = {ItemEntity.class})
public abstract class ItemMergeMixin {
    @Inject(method = "tryMerge(Lnet/minecraft/entity/ItemEntity;)V", at = @At("HEAD"), cancellable = true)
    private void mirroredorigin$onlyMergeSamePhase(ItemEntity other, CallbackInfo ci) {
        if (((MirrorEntity) (Object) this).mirroredorigin$isInMirrorWorld()
            != ((MirrorEntity) other).mirroredorigin$isInMirrorWorld()) {
            ci.cancel();
        }
    }
}
