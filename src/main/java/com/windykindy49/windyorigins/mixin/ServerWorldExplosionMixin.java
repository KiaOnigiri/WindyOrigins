package com.windykindy49.windyorigins.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.windykindy49.windyorigins.util.MirrorEntity;
import com.windykindy49.windyorigins.util.PhaseScope;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.explosion.Explosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({ServerWorld.class})
public class ServerWorldExplosionMixin {
    @Inject(
        method = {"createExplosion"},
        at = {@At("HEAD")}
    )
    private void onExplodeHead(CallbackInfoReturnable<Explosion> var13, @Local(argsOnly = true) Entity var1) {
        PhaseScope.enterOrKeep(var1 != null ? ((MirrorEntity)var1).windyorigins$isInMirrorWorld() : null);
    }

    @Inject(
        method = {"createExplosion"},
        at = {@At("RETURN")}
    )
    private void onExplodeReturn(CallbackInfoReturnable<Explosion> var1) {
        PhaseScope.exit();
    }
}
