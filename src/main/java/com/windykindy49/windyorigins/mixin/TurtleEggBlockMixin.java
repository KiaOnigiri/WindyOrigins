package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.util.SpawnPhaseOverride;
import com.windykindy49.windyorigins.util.TurtleEggPhaseState;
import net.minecraft.block.BlockState;
import net.minecraft.block.TurtleEggBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({TurtleEggBlock.class})
public abstract class TurtleEggBlockMixin {
    @Inject(
        method = {"randomTick"},
        at = {@At("HEAD")}
    )
    private void windyorigins$beginHatch(BlockState var1, ServerWorld var2, BlockPos var3, Random var4, CallbackInfo var5) {
        SpawnPhaseOverride.enter(TurtleEggPhaseState.get(var2).isMirror(var3));
    }

    @Inject(
        method = {"randomTick"},
        at = {@At("RETURN")}
    )
    private void windyorigins$endHatch(BlockState var1, ServerWorld var2, BlockPos var3, Random var4, CallbackInfo var5) {
        SpawnPhaseOverride.exit();
        if (!var2.getBlockState(var3).isOf(var1.getBlock())) {
            TurtleEggPhaseState.get(var2).clear(var3);
        }
    }
}
