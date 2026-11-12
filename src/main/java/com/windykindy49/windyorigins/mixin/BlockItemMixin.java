package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.util.MirrorEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({BlockItem.class})
public class BlockItemMixin {
    @Inject(
        method = {"canPlace"},
        at = {@At("RETURN")},
        cancellable = true
    )
    private void onCanPlaceThroughGhosts(ItemPlacementContext var1, BlockState var2, CallbackInfoReturnable<Boolean> var3) {
        if (!(Boolean)var3.getReturnValue()) {
            PlayerEntity var4 = var1.getPlayer();
            if (var4 != null) {
                World var5 = var1.getWorld();
                BlockPos var6 = var1.getBlockPos();
                if (var2.canPlaceAt(var5, var6)) {
                    VoxelShape var7 = var2.getCollisionShape(var5, var6, ShapeContext.of(var4));
                    if (!var7.isEmpty()) {
                        Box var8 = var7.getBoundingBox().offset(var6);
                        boolean var9 = ((MirrorEntity)var4).windyorigins$isInMirrorWorld();

                        for (Entity var11 : var5.getOtherEntities(null, var8)) {
                            if (((MirrorEntity)var11).windyorigins$isInMirrorWorld() == var9) {
                                return;
                            }
                        }

                        var3.setReturnValue(true);
                    }
                }
            }
        }
    }
}
