package com.windykindy49.mirroredorigin.mixin;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.TurtleEggBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import com.windykindy49.mirroredorigin.util.TurtleEggPhaseState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * TurtleEggPhaseState only forgot a position when the clutch hatched. An egg mined with a
 * tool, trampled by a mob, blown up or pushed by a piston left its entry behind for good, so
 * the saved set grew without limit for the lifetime of the world.
 *
 * onStateReplaced is the common exit point for a block ceasing to exist, whatever destroyed
 * it. The "still a turtle egg" check matters: the same callback fires while the clutch is
 * alive, when the egg count goes from one to four or the hatch stage advances, and clearing
 * there would throw away the phase of a perfectly healthy clutch.
 */
@Mixin(value = {AbstractBlock.class})
public class TurtleEggCleanupMixin {
    @Inject(method = "onStateReplaced", at = @At("HEAD"))
    private void mirroredorigin$forgetDestroyedEggs(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved, CallbackInfo ci) {
        if (!(state.getBlock() instanceof TurtleEggBlock) || newState.getBlock() instanceof TurtleEggBlock) {
            return;
        }
        if (world instanceof ServerWorld) {
            TurtleEggPhaseState.get((ServerWorld) world).clear(pos);
        }
    }
}
