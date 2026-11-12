package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.util.MirrorEntity;
import com.windykindy49.windyorigins.util.TurtleEggPhaseState;
import net.minecraft.block.BlockState;
import net.minecraft.entity.passive.TurtleEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(
    targets = {"net.minecraft.entity.passive.TurtleEntity$LayEggGoal"}
)
public abstract class TurtleLayEggGoalMixin {
    @Shadow
    @Final
    private TurtleEntity turtle;

    @Redirect(
        method = {"tick"},
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/World;setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;I)Z"
        )
    )
    private boolean windyorigins$recordEggPhase(World var1, BlockPos var2, BlockState var3, int var4) {
        boolean var5 = var1.setBlockState(var2, var3, var4);
        if (var5 && var1 instanceof ServerWorld) {
            TurtleEggPhaseState.get((ServerWorld)var1).setPhase(var2, ((MirrorEntity)(Object)this.turtle).windyorigins$isInMirrorWorld());
        }

        return var5;
    }
}
