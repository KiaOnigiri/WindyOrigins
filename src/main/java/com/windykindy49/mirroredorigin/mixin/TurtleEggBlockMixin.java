package com.windykindy49.mirroredorigin.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.TurtleEggBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import com.windykindy49.mirroredorigin.util.SpawnPhaseOverride;
import com.windykindy49.mirroredorigin.util.TurtleEggPhaseState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Baby turtles hatch into the phase their mother laid the eggs in, instead of always
 * appearing in the overworld phase. The clutch's phase (recorded by TurtleLayEggGoalMixin)
 * is published for the duration of randomTick.
 */
@Mixin(value={TurtleEggBlock.class})
public abstract class TurtleEggBlockMixin {
    @Inject(method={"randomTick"}, at={@At(value="HEAD")})
    private void mirroredorigin$beginHatch(BlockState state, ServerWorld world, BlockPos pos, Random random, CallbackInfo ci) {
        // RETURN, not TAIL, and a thread-local scope, not a field. TurtleEggBlock is a
        // singleton shared by every clutch in the world, so the old field was global state;
        // and randomTick returns early whenever the clutch is not ready to progress, which
        // meant TAIL never ran and the override stayed pending. A stranded override is
        // consulted first by ServerWorldSpawnMixin and applies to everything, so a single
        // egg could have pushed every later spawn, drop and projectile into its phase.
        SpawnPhaseOverride.enter(TurtleEggPhaseState.get(world).isMirror(pos));
    }

    @Inject(method={"randomTick"}, at={@At(value="RETURN")})
    private void mirroredorigin$endHatch(BlockState state, ServerWorld world, BlockPos pos, Random random, CallbackInfo ci) {
        SpawnPhaseOverride.exit();
        if (!world.getBlockState(pos).isOf(state.getBlock())) {
            // the clutch finished hatching and the block is gone - forget the position
            TurtleEggPhaseState.get(world).clear(pos);
        }
    }
}
