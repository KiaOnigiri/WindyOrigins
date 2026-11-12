package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.bounty.BountyKillScope;
import com.windykindy49.windyorigins.bounty.BountyRewards;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({Entity.class})
public abstract class BountyDropMixin {
    @Inject(
        method = {"dropStack(Lnet/minecraft/item/ItemStack;F)Lnet/minecraft/entity/ItemEntity;"},
        at = {@At("HEAD")},
        cancellable = true
    )
    private void windyorigins$routeLoot(ItemStack var1, float var2, CallbackInfoReturnable<ItemEntity> var3) {
        if (!var1.isEmpty()) {
            BountyKillScope.Context var4 = BountyKillScope.contractFor(this);
            if (var4 != null && var4.hunter.isAlive()) {
                BountyRewards.give(var4.hunter, var1.copy());
                var3.setReturnValue(null);
            } else {
                if (BountyKillScope.unmarkedFor(this) != null && var1.getCount() > 1) {
                    var1.setCount(var1.getCount() / 2);
                }
            }
        }
    }
}
