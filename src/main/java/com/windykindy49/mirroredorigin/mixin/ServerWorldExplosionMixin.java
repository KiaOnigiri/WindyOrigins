package com.windykindy49.mirroredorigin.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;
import net.minecraft.world.explosion.ExplosionBehavior;
import com.windykindy49.mirroredorigin.util.MirrorEntity;
import com.windykindy49.mirroredorigin.util.PhaseScope;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={ServerWorld.class})
public class ServerWorldExplosionMixin {
    @Inject(method={"createExplosion"}, at={@At(value="HEAD")})
    private void onExplodeHead(Entity entity, DamageSource damageSource, ExplosionBehavior behavior, double x, double y, double z, float power, boolean createFire, World.ExplosionSourceType explosionSourceType, CallbackInfoReturnable<Explosion> cir) {
        // An explosion without a source entity (command, bed, respawn anchor) used to fall
        // through to the unconditional clear below and wipe the phase of whatever tick it
        // happened inside. Opening a no-op scope keeps enter/exit paired.
        PhaseScope.enterOrKeep(entity != null
            ? Boolean.valueOf(((MirrorEntity)entity).mirroredorigin$isInMirrorWorld())
            : null);
    }

    @Inject(method={"createExplosion"}, at={@At(value="RETURN")})
    private void onExplodeReturn(CallbackInfoReturnable<Explosion> cir) {
        PhaseScope.exit();
    }
}

