package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.folded.FoldedManager;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ItemEntity.class})
public abstract class RifleItemEntityMixin {
    @Shadow
    public abstract ItemStack getStack();

    @Inject(
        method = {"tick"},
        at = {@At("HEAD")},
        cancellable = true
    )
    private void windyorigins$foldAway(CallbackInfo var1) {
        if (FoldedManager.isRifle(this.getStack())) {
            ((ItemEntity)(Object)this).discard();
            var1.cancel();
        }
    }
}
