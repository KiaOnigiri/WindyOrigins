package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.sanguine.SanguineManager;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.PotionItem;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({PotionItem.class})
public abstract class SanguinePotionMixin {
    @Inject(
        method = {"finishUsing"},
        at = {@At("HEAD")}
    )
    private void windyorigins$notePotion(ItemStack var1, World var2, LivingEntity var3, CallbackInfoReturnable<ItemStack> var4) {
        if (!var2.isClient() && var3 instanceof PlayerEntity var5) {
            SanguineManager.noteMeal(var5, var1);
        }
    }
}
