package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.util.TurtleEggPhaseState;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.TurtleEggBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({AbstractBlock.class})
public class TurtleEggCleanupMixin {
    @Inject(
        method = {"onStateReplaced"},
        at = {@At("HEAD")}
    )
    private void windyorigins$forgetDestroyedEggs(BlockState var1, World var2, BlockPos var3, BlockState var4, boolean var5, CallbackInfo var6) {
        if (var1.getBlock() instanceof TurtleEggBlock && !(var4.getBlock() instanceof TurtleEggBlock)) {
            if (var2 instanceof ServerWorld) {
                TurtleEggPhaseState.get((ServerWorld)var2).clear(var3);
            }
        }
    }
}
