package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.sanguine.SanguineManager;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({LivingEntity.class})
public abstract class SanguineMealMixin {
    @Inject(
        method = {"eatFood"},
        at = {@At("HEAD")}
    )
    private void windyorigins$noteMeal(World var1, ItemStack var2, CallbackInfoReturnable<ItemStack> var3) {
        if (!var1.isClient() && (Object)this instanceof PlayerEntity var4) {
            SanguineManager.noteMeal(var4, var2);
        }
    }
}
