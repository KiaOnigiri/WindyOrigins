package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.util.MirrorEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({Entity.class})
public class EntityDropMixin {
    @Inject(
        method = {"dropStack(Lnet/minecraft/item/ItemStack;F)Lnet/minecraft/entity/ItemEntity;"},
        at = {@At("RETURN")}
    )
    private void onDropStack(ItemStack var1, float var2, CallbackInfoReturnable<ItemEntity> var3) {
        ItemEntity var4 = (ItemEntity)var3.getReturnValue();
        if (var4 != null) {
            boolean var5 = ((MirrorEntity)(Object)this).windyorigins$isInMirrorWorld();
            ((MirrorEntity)var4).windyorigins$setInMirrorWorld(var5);
        }
    }
}
