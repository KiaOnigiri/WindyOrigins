package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.folded.FoldedManager;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({Slot.class})
public abstract class RifleSlotMixin {
    @Shadow
    @Final
    public Inventory inventory;

    @Inject(
        method = {"canInsert"},
        at = {@At("HEAD")},
        cancellable = true
    )
    private void windyorigins$keepRifle(ItemStack var1, CallbackInfoReturnable<Boolean> var2) {
        if (FoldedManager.isRifle(var1) && !(this.inventory instanceof PlayerInventory)) {
            var2.setReturnValue(false);
        }
    }
}
