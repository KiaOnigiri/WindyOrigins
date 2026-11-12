package com.windykindy49.windyorigins.mixin;

import com.mojang.datafixers.util.Either;
import com.windykindy49.windyorigins.sanguine.SanguineManager;
import net.minecraft.entity.player.PlayerEntity.SleepFailureReason;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Unit;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({ServerPlayerEntity.class})
public abstract class SanguineDayRestMixin {
    @Inject(
        method = {"trySleep"},
        at = {@At("HEAD")},
        cancellable = true
    )
    private void windyorigins$dayRest(BlockPos var1, CallbackInfoReturnable<Either<SleepFailureReason, Unit>> var2) {
        ServerPlayerEntity var3 = (ServerPlayerEntity)(Object)this;
        if (var3.getPos().squaredDistanceTo(var1.toCenterPos()) < 16.0 && SanguineManager.dayRest(var3, var1)) {
            var2.setReturnValue(Either.left(SleepFailureReason.OTHER_PROBLEM));
        }
    }
}
