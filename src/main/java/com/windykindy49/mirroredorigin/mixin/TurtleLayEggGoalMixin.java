package com.windykindy49.mirroredorigin.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.entity.passive.TurtleEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import com.windykindy49.mirroredorigin.util.MirrorEntity;
import com.windykindy49.mirroredorigin.util.TurtleEggPhaseState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Records the mother turtle's phase at the position where she lays her eggs, so the
 * hatchlings can later be spawned into the same phase (see TurtleEggBlockMixin).
 */
@Mixin(targets = "net.minecraft.entity.passive.TurtleEntity$LayEggGoal")
public abstract class TurtleLayEggGoalMixin {
    @Shadow @Final
    private TurtleEntity turtle;

    @Redirect(
        method = "tick",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;I)Z")
    )
    private boolean mirroredorigin$recordEggPhase(World world, BlockPos pos, BlockState state, int flags) {
        boolean result = world.setBlockState(pos, state, flags);
        if (result && world instanceof ServerWorld) {
            TurtleEggPhaseState.get((ServerWorld)world)
                .setPhase(pos, ((MirrorEntity)this.turtle).mirroredorigin$isInMirrorWorld());
        }
        return result;
    }
}
