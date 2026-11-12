package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.bounty.BountyKillScope;
import com.windykindy49.windyorigins.bounty.BountyManager;
import com.windykindy49.windyorigins.bounty.BountyRewards;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({LivingEntity.class})
public abstract class BountyLootMixin {
    @Inject(
        method = {"drop(Lnet/minecraft/entity/damage/DamageSource;)V"},
        at = {@At("HEAD")}
    )
    private void windyorigins$openKillScope(DamageSource var1, CallbackInfo var2) {
        LivingEntity var3 = (LivingEntity)(Object)this;
        BountyKillScope.Kind var4 = BountyKillScope.Kind.NONE;
        ServerPlayerEntity var5 = null;
        if (var3.getServer() != null) {
            Entity var6 = var1.getAttacker();
            ServerPlayerEntity var7 = BountyManager.firstMarkerOf(var3.getServer(), var3);
            if (var7 == null || !BountyManager.hasPower(var7, "blood_money") || var6 != var7 && var3.getPrimeAdversary() != var7) {
                if (var6 instanceof ServerPlayerEntity var8 && !BountyManager.isMarkedBy(var8, var3) && BountyManager.hasPower(var8, "scarce_loot")) {
                    var4 = BountyKillScope.Kind.UNMARKED;
                    var5 = var8;
                }
            } else {
                var4 = BountyKillScope.Kind.CONTRACT;
                var5 = var7;
            }
        }

        BountyKillScope.open(var4, var5, var3);
    }

    @Inject(
        method = {"drop(Lnet/minecraft/entity/damage/DamageSource;)V"},
        at = {@At("RETURN")}
    )
    private void windyorigins$closeKillScope(DamageSource var1, CallbackInfo var2) {
        BountyKillScope.Context var3 = BountyKillScope.contractFor(this);
        if (var3 != null) {
            BountyRewards.Tier var4 = BountyRewards.pay(var3.hunter, var3.victim);
            BountyManager.notePaid(var3.hunter, var4);
        }

        BountyKillScope.close();
    }

    @Redirect(
        method = {"dropXp"},
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/entity/ExperienceOrbEntity;spawn(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/util/math/Vec3d;I)V"
        )
    )
    private void windyorigins$routeExperience(ServerWorld var1, Vec3d var2, int var3) {
        LivingEntity var4 = (LivingEntity)(Object)this;
        BountyKillScope.Context var5 = BountyKillScope.contractFor(var4);
        if (var5 != null && var5.hunter.isAlive()) {
            int var6 = var4 instanceof PlayerEntity ? var3 : Math.round((float)var3 * BountyRewards.tierOf(var4).experience);
            if (var6 > 0) {
                var5.hunter.addExperience(var6);
                var5.hunter.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.3F, 0.9F + var5.hunter.getRandom().nextFloat() * 0.3F);
            }
        } else {
            if (BountyKillScope.unmarkedFor(var4) != null) {
                var3 /= 2;
            }

            if (var3 > 0) {
                ExperienceOrbEntity.spawn(var1, var2, var3);
            }
        }
    }
}
