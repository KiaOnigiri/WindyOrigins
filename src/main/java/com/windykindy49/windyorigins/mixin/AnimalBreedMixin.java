package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.util.MirrorEntity;
import com.windykindy49.windyorigins.util.SpawnPhaseOverride;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({AnimalEntity.class})
public abstract class AnimalBreedMixin {
    @Inject(
        method = {"breed(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/passive/AnimalEntity;)V"},
        at = {@At("HEAD")}
    )
    private void windyorigins$beginBreed(ServerWorld var1, AnimalEntity var2, CallbackInfo var3) {
        SpawnPhaseOverride.enter(((MirrorEntity)(Object)this).windyorigins$isInMirrorWorld());
    }

    @Inject(
        method = {"breed(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/passive/AnimalEntity;)V"},
        at = {@At("RETURN")}
    )
    private void windyorigins$endBreed(ServerWorld var1, AnimalEntity var2, CallbackInfo var3) {
        SpawnPhaseOverride.exit();
    }
}
