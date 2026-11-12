package com.windykindy49.windyorigins.registry;

import com.windykindy49.windyorigins.bounty.BountyManager;
import com.windykindy49.windyorigins.bounty.client.ClientBountyState;
import io.github.apace100.apoli.power.factory.action.ActionFactory;
import io.github.apace100.apoli.power.factory.condition.ConditionFactory;
import io.github.apace100.apoli.registry.ApoliRegistries;
import io.github.apace100.calio.data.SerializableData;
import io.github.apace100.calio.data.SerializableDataTypes;
import java.util.function.Predicate;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;

public final class BountyPowers {
    private static final String NS = "windyorigins";

    private BountyPowers() {
    }

    public static void register() {
        Registry.register(
            ApoliRegistries.ENTITY_ACTION,
            new Identifier("windyorigins", "mark_of_death"),
            new ActionFactory<>(
                new Identifier("windyorigins", "mark_of_death"), new SerializableData().add("range", SerializableDataTypes.DOUBLE, 100.0), (var0, var1) -> {
                    if (var1 instanceof ServerPlayerEntity var2) {
                        LivingEntity var3 = BountyManager.isReady(var2) ? findTarget(var2, var0.getDouble("range")) : null;
                        BountyManager.tryMark(var2, var3);
                    }
                }
            )
        );
        registerCondition(
            "mark_ready",
            var0 -> var0 instanceof ServerPlayerEntity
                    ? BountyManager.isReady((ServerPlayerEntity)var0)
                    : ClientBountyState.isLocal(var0) && !ClientBountyState.hasContract() && ClientBountyState.getCooldownTicks() <= 0
        );
        registerCondition(
            "has_active_mark",
            var0 -> var0 instanceof ServerPlayerEntity
                    ? BountyManager.hasActiveMark((ServerPlayerEntity)var0)
                    : ClientBountyState.isLocal(var0) && ClientBountyState.hasContract()
        );
        registerCondition(
            "mark_recharging",
            var0 -> var0 instanceof ServerPlayerEntity
                    ? BountyManager.isRecharging((ServerPlayerEntity)var0)
                    : ClientBountyState.isLocal(var0) && !ClientBountyState.hasContract() && ClientBountyState.getCooldownTicks() > 0
        );
        Registry.register(
            ApoliRegistries.BIENTITY_CONDITION,
            new Identifier("windyorigins", "is_my_mark"),
            new ConditionFactory<>(new Identifier("windyorigins", "is_my_mark"), new SerializableData(), (var0, var1) -> {
                Entity var2 = (Entity)var1.getLeft();
                Entity var3 = (Entity)var1.getRight();
                return var2 instanceof ServerPlayerEntity && var3 != null && BountyManager.isMarkedBy((ServerPlayerEntity)var2, var3);
            })
        );
    }

    private static void registerCondition(String var0, Predicate<Entity> var1) {
        Identifier var2 = new Identifier("windyorigins", var0);
        Registry.register(ApoliRegistries.ENTITY_CONDITION, var2, new ConditionFactory<>(var2, new SerializableData(), (var1x, var2x) -> var1.test(var2x)));
    }

    private static LivingEntity findTarget(ServerPlayerEntity var0, double var1) {
        Vec3d var3 = var0.getCameraPosVec(1.0F);
        Vec3d var4 = var0.getRotationVec(1.0F);
        Vec3d var5 = var3.add(var4.multiply(var1));
        BlockHitResult var6 = var0.getWorld().raycast(new RaycastContext(var3, var5, ShapeType.COLLIDER, FluidHandling.NONE, var0));
        double var7 = var6.getType() == Type.MISS ? var1 : var6.getPos().distanceTo(var3);
        Vec3d var9 = var3.add(var4.multiply(var7));
        Box var10 = var0.getBoundingBox().stretch(var4.multiply(var7)).expand(1.0);
        EntityHitResult var11 = ProjectileUtil.raycast(
            var0,
            var3,
            var9,
            var10,
            var0x -> !var0x.isSpectator() && var0x.canHit() && var0x.isAlive() && var0x instanceof LivingEntity && !(var0x instanceof ArmorStandEntity),
            var7 * var7
        );
        return var11 == null ? null : (LivingEntity)var11.getEntity();
    }
}
