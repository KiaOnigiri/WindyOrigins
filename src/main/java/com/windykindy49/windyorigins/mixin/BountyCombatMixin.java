package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.bounty.BountyManager;
import com.windykindy49.windyorigins.bounty.BountySounds;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({LivingEntity.class})
public abstract class BountyCombatMixin {
    @Inject(
        method = {"damage"},
        at = {@At("HEAD")},
        cancellable = true
    )
    private void windyorigins$duelistDodge(DamageSource var1, float var2, CallbackInfoReturnable<Boolean> var3) {
        LivingEntity var4 = (LivingEntity)(Object)this;
        if (!(var4 instanceof ServerPlayerEntity var5) || var1.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }

        if (var5.timeUntilRegen <= 10) {
            Entity var6 = var1.getAttacker();
            if (var6 != null && BountyManager.isMarkedBy(var5, var6) && BountyManager.hasPower(var5, "duelist")) {
                if (!(var5.getRandom().nextFloat() >= 0.25F)) {
                    var3.setReturnValue(false);
                    BountySounds.playPrivate(var5, BountySounds.DODGE, 0.8F, 1.5F);
                    var5.getWorld()
                        .spawnParticles(var5, ParticleTypes.SWEEP_ATTACK, false, var5.getX(), var5.getBodyY(0.6), var5.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
                    if (var6 instanceof ServerPlayerEntity var7) {
                        BountySounds.playPrivate(var7, BountySounds.DODGE, 0.8F, 1.5F);
                        var7.getWorld()
                            .spawnParticles(var7, ParticleTypes.SWEEP_ATTACK, false, var5.getX(), var5.getBodyY(0.6), var5.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
                    }
                }
            }
        }
    }

    @ModifyVariable(
        method = {"damage"},
        at = @At("HEAD"),
        argsOnly = true
    )
    private float windyorigins$exposed(float var1, DamageSource var2) {
        LivingEntity var3 = (LivingEntity)(Object)this;
        if (var3 instanceof ServerPlayerEntity var4) {
            Entity var5 = var2.getAttacker();
            return var5 instanceof LivingEntity && var5 != var4 && !BountyManager.isMarkedBy(var4, var5) && BountyManager.hasPower(var4, "exposed")
                ? var1 * 1.25F
                : var1;
        } else {
            return var1;
        }
    }

    @Inject(
        method = {"damage"},
        at = {@At("RETURN")}
    )
    private void windyorigins$cripplingShot(DamageSource var1, float var2, CallbackInfoReturnable<Boolean> var3) {
        if (var3.getReturnValueZ()) {
            Entity var4 = var1.getSource();
            if (var4 instanceof ProjectileEntity && !(var4 instanceof ThrownItemEntity)) {
                if (var1.getAttacker() instanceof ServerPlayerEntity var5) {
                    LivingEntity var7 = (LivingEntity)(Object)this;
                    if (BountyManager.isMarkedBy(var5, var7) && BountyManager.hasPower(var5, "crippling_shot")) {
                        var7.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 30, 2), var5);
                    }
                }
            }
        }
    }
}
