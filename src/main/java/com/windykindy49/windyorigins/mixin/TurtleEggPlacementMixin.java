package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.util.MirrorEntity;
import com.windykindy49.windyorigins.util.TurtleEggPhaseState;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.TurtleEggBlock;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({Block.class})
public class TurtleEggPlacementMixin {
    @Inject(
        method = {"onPlaced"},
        at = {@At("TAIL")}
    )
    private void windyorigins$recordEggPhase(World var1, BlockPos var2, BlockState var3, LivingEntity var4, ItemStack var5, CallbackInfo var6) {
        if (var4 != null && var1 instanceof ServerWorld && var3.getBlock() instanceof TurtleEggBlock) {
            TurtleEggPhaseState.get((ServerWorld)var1).setPhase(var2, ((MirrorEntity)var4).windyorigins$isInMirrorWorld());
        }
    }
}
