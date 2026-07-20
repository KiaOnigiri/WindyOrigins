package com.windykindy49.mirroredorigin.mixin;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.TurtleEggBlock;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import com.windykindy49.mirroredorigin.util.MirrorEntity;
import com.windykindy49.mirroredorigin.util.TurtleEggPhaseState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Only TurtleLayEggGoalMixin recorded a clutch's phase, i.e. only eggs laid by a mother
 * turtle. An egg placed from the inventory - which is how the bug was found, a mirror-phase
 * player placing one in creative - was never recorded, so TurtleEggPhaseState reported the
 * default (overworld) and the hatchling appeared in the wrong phase.
 *
 * Block#onPlaced is the common exit point for placing any block from an item, so recording
 * here covers survival and creative alike.
 */
@Mixin(value = {Block.class})
public class TurtleEggPlacementMixin {
    @Inject(method = "onPlaced", at = @At("TAIL"))
    private void mirroredorigin$recordEggPhase(World world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack, CallbackInfo ci) {
        if (placer == null || !(world instanceof ServerWorld) || !(state.getBlock() instanceof TurtleEggBlock)) {
            return;
        }
        TurtleEggPhaseState.get((ServerWorld) world)
            .setPhase(pos, ((MirrorEntity) placer).mirroredorigin$isInMirrorWorld());
    }
}
