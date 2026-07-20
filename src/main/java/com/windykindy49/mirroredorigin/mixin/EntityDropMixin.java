package com.windykindy49.mirroredorigin.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import com.windykindy49.mirroredorigin.util.MirrorEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={Entity.class})
public class EntityDropMixin {
    @Inject(method={"dropStack(Lnet/minecraft/item/ItemStack;F)Lnet/minecraft/entity/ItemEntity;"}, at={@At(value="RETURN")})
    private void onDropStack(ItemStack stack, float yOffset, CallbackInfoReturnable<ItemEntity> cir) {
        ItemEntity droppedItem = (ItemEntity)cir.getReturnValue();
        if (droppedItem != null) {
            boolean parentPhase = ((MirrorEntity)(Object)this).mirroredorigin$isInMirrorWorld();
            ((MirrorEntity)droppedItem).mirroredorigin$setInMirrorWorld(parentPhase);
        }
    }
}

