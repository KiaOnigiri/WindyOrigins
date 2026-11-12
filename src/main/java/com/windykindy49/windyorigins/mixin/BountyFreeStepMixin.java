package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.bounty.BountyManager;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({Entity.class})
public abstract class BountyFreeStepMixin {
    @Inject(
        method = {"slowMovement"},
        at = {@At("HEAD")},
        cancellable = true
    )
    private void windyorigins$ignoreCobwebs(BlockState var1, Vec3d var2, CallbackInfo var3) {
        if (var1.isOf(Blocks.COBWEB) && BountyManager.hasPower((Entity)(Object)this, "free_step")) {
            var3.cancel();
        }
    }
}
