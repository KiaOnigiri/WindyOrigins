package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.util.MirrorEntity;
import java.util.List;
import net.minecraft.block.entity.Hopper;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({HopperBlockEntity.class})
public class HopperBlockEntityMixin {
    @Inject(
        method = {"getInputItemEntities"},
        at = {@At("RETURN")}
    )
    private static void filterItemsForEntityHoppers(World var0, Hopper var1, CallbackInfoReturnable<List<ItemEntity>> var2) {
        if (var1 instanceof Entity var3) {
            List var4 = (List)var2.getReturnValue();
            var4.removeIf(var1x -> ((MirrorEntity)var1x).windyorigins$isInMirrorWorld() != ((MirrorEntity)var3).windyorigins$isInMirrorWorld());
        }
    }
}
