package com.windykindy49.windyorigins.folded;

import com.windykindy49.windyorigins.common.PhaseFx;
import com.windykindy49.windyorigins.common.Powers;
import com.windykindy49.windyorigins.common.WindyEffects;
import com.windykindy49.windyorigins.common.WindySounds;
import com.windykindy49.windyorigins.mixin.FloatingTicksAccessor;
import io.netty.buffer.Unpooled;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item.Settings;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;
import org.joml.Vector3f;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;

public final class FoldedManager {
    public static final String FOLD = "fold";
    public static final int SHOT_COST = 30;
    public static final int STEP_COST = 40;
    public static final int KNOT_COST = 25;
    public static final int KNOT_REFUND = 15;
    public static final int HOVER_MIN = 10;
    /** Pierces armour outright; Protection still applies, so full netherite Protection IV falls in three shots. */
    public static final float SHOT_DAMAGE = 19.0F;
    public static final double SHOT_RANGE = 160.0;
    public static final double KNOT_RANGE = 48.0;
    public static final double KNOT_RADIUS = 4.5;
    private static final int KNOT_TICKS = 120;
    private static final int TRACE_TICKS = 80;
    public static final int MODE_OFF = 0;
    public static final int MODE_HOVER = 1;
    public static final int MODE_GLIDE = 2;
    public static final Identifier HOVER_SYNC = new Identifier("windyorigins", "folded_hover");
    public static final Identifier SHOT = new Identifier("windyorigins", "folded_shot");
    public static final Identifier AIM = new Identifier("windyorigins", "folded_aim");
    /** For this long before the rifle is ready, anyone near the line of fire sees the scope glint. */
    public static final int WARN_TICKS = 10;
    /** The glint is seen about six degrees either side of the crosshair, so a last-instant flick still gives it away. */
    private static final double WARN_SPREAD = 0.105;
    public static final RegistryKey<DamageType> SHOT_DAMAGE_TYPE = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, new Identifier("windyorigins", "fold_shot"));
    public static final Item RIFLE = Registry.register(
        Registries.ITEM, new Identifier("windyorigins", "fold_rifle"), new FoldRifleItem(new Settings().maxCount(1))
    );
    /** Set by the client: reports what is under the crosshair at the instant the trigger is released. */
    public static java.util.function.Consumer<LivingEntity> clientShot = var0 -> {
    };
    private static final DustParticleEffect TRACE = new DustParticleEffect(new Vector3f(0.62F, 0.45F, 1.0F), 0.9F);
    private static final DustParticleEffect KNOT_DUST = new DustParticleEffect(new Vector3f(0.45F, 0.3F, 0.95F), 1.3F);
    private static final Set<UUID> HOVERING = new HashSet<>();
    private static final Set<UUID> GLIDING = new HashSet<>();
    private static final Map<UUID, Integer> HOVER_SINCE = new HashMap<>();
    private static final Map<UUID, Integer> PENDING = new HashMap<>();
    private static final Map<UUID, FoldedManager.Knot> KNOTS = new HashMap<>();
    private static final Map<UUID, Long> WARNED = new HashMap<>();

    private FoldedManager() {
    }

    public static void init() {
        ServerPlayNetworking.registerGlobalReceiver(SHOT, (var0, var1, var2, var3, var4) -> {
            int var5 = var3.readInt();
            Vec3d var6 = new Vec3d(var3.readDouble(), var3.readDouble(), var3.readDouble());
            float var7 = var3.readFloat();
            float var8 = var3.readFloat();
            var0.execute(() -> {
                if (PENDING.remove(var1.getUuid()) != null) {
                    shoot(var1, var5, var6, Vec3d.fromPolar(var8, var7));
                }
            });
        });
    }

    public static boolean isRifle(ItemStack var0) {
        return var0.isOf(RIFLE);
    }

    public static boolean isHovering(Entity var0) {
        return HOVERING.contains(var0.getUuid());
    }

    public static void tick(MinecraftServer var0) {
        for (ServerPlayerEntity var2 : var0.getPlayerManager().getPlayerList()) {
            boolean var3 = Powers.has(var2, "fold");
            UUID var6 = var2.getUuid();
            if (var2.age % 20 == 0) {
                keepRifle(var2, var3 && Powers.has(var2, "fold_rifle") && var2.isAlive());
            }

            // The client normally answers within a tick; if it never does, fall back to the server's own aim.
            Integer var7 = PENDING.get(var6);
            if (var7 != null && var2.age - var7 > 5) {
                PENDING.remove(var6);
                shoot(var2, -1, null, null);
            }

            if (var3 && var2.age % 2 == 0 && var2.isUsingItem() && isRifle(var2.getActiveItem())) {
                int var9 = FoldRifleItem.CHARGE_TICKS - var2.getItemUseTime();
                if (var9 <= WARN_TICKS) {
                    warn(var2, Math.max(0, var9));
                }
            }

            if (!var3) {
                if (HOVERING.contains(var6) || GLIDING.contains(var6)) {
                    setMode(var2, MODE_OFF);
                }

                KNOTS.remove(var6);
            } else {
                int var4 = Powers.resource(var2, "fold", 0);
                if (HOVERING.contains(var6)) {
                    var2.fallDistance = 0.0F;
                    ((FloatingTicksAccessor)var2.networkHandler).windyorigins$setFloatingTicks(0);
                    if (var2.age % 6 == 0 && var4 > 0) {
                        Powers.setResource(var2, "fold", var4 - 1);
                    }

                    if (var2.age % 3 == 0 && !var2.isOnGround()) {
                        PhaseFx.particles(var2, ParticleTypes.REVERSE_PORTAL, var2.getX(), var2.getY() - 0.1, var2.getZ(), 2, 0.25, 0.02, 0.25, 0.01);
                    }

                    int var8 = var2.age - HOVER_SINCE.getOrDefault(var6, var2.age);
                    if (var2.isSpectator() || !var2.isAlive() || var2.hasVehicle() || var2.isTouchingWater() || var2.isFallFlying()) {
                        setMode(var2, MODE_OFF);
                    } else if (var2.isOnGround() && var8 > 12) {
                        // Touching down ends the hover.
                        setMode(var2, MODE_OFF);
                    } else if (var4 <= 0) {
                        setMode(var2, MODE_GLIDE);
                    }
                } else {
                    if (GLIDING.contains(var6)) {
                        var2.fallDistance = 0.0F;
                        if (var2.isOnGround() || var2.isTouchingWater() || var2.hasVehicle() || var2.isSpectator() || !var2.isAlive() || var2.isFallFlying() || var2.getAbilities().flying) {
                            setMode(var2, MODE_OFF);
                        } else if (var2.age % 3 == 0) {
                            PhaseFx.particles(var2, ParticleTypes.REVERSE_PORTAL, var2.getX(), var2.getY() - 0.1, var2.getZ(), 1, 0.2, 0.02, 0.2, 0.01);
                        }
                    }

                    if (var2.age % 10 == 0 && var4 < 100 && !var2.isUsingItem()) {
                        Powers.setResource(var2, "fold", var4 + 1);
                    }
                }

                tickKnot(var2);
            }
        }
    }

    private static void keepRifle(ServerPlayerEntity var0, boolean var1) {
        PlayerInventory var2 = var0.getInventory();
        int var3 = 0;

        for (int var4 = 0; var4 < var2.size(); var4++) {
            ItemStack var5 = var2.getStack(var4);
            if (isRifle(var5)) {
                if (!var1 || ++var3 > 1) {
                    var2.setStack(var4, ItemStack.EMPTY);
                }
            }
        }

        if (isRifle(var0.currentScreenHandler.getCursorStack())) {
            var3++;
        }

        if (var1 && var3 == 0 && !var0.isSpectator()) {
            ItemStack var6 = new ItemStack(RIFLE);
            if (var2.insertStack(var6)) {
                PhaseFx.sound(var0, WindySounds.FOLD_RIFLE, SoundCategory.PLAYERS, 0.8F, 1.0F);
                PhaseFx.particles(var0, ParticleTypes.REVERSE_PORTAL, var0.getX(), var0.getBodyY(0.6), var0.getZ(), 30, 0.3, 0.4, 0.3, 0.05);
            }
        }
    }

    /**
     * The rifle cannot be levelled at someone unseen: for the last half second of the charge, and for
     * as long as it is held ready, every player near the line of fire sees a glint where the shooter is.
     */
    private static void warn(ServerPlayerEntity shooter, int ticksToFire) {
        ServerWorld world = shooter.getWorld();
        Vec3d eye = shooter.getEyePos();
        Vec3d look = shooter.getRotationVec(1.0F);
        long now = world.getTime();

        for (ServerPlayerEntity target : world.getPlayers()) {
            if (target == shooter || target.isSpectator() || !target.isAlive() || !PhaseFx.samePhase(shooter, target)) {
                continue;
            }

            Vec3d to = target.getBoundingBox().getCenter().subtract(eye);
            double distance = to.length();
            double along = to.dotProduct(look);
            if (distance > SHOT_RANGE || along <= 0.5) {
                continue;
            }

            double off = Math.sqrt(Math.max(0.0, distance * distance - along * along));
            if (off > 1.5 + distance * WARN_SPREAD) {
                continue;
            }

            if (world.raycast(new RaycastContext(eye, target.getEyePos(), ShapeType.COLLIDER, FluidHandling.NONE, shooter)).getType() != Type.MISS) {
                continue;
            }

            PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
            buf.writeInt(shooter.getId());
            buf.writeDouble(eye.x);
            buf.writeDouble(eye.y);
            buf.writeDouble(eye.z);
            buf.writeByte(ticksToFire);
            ServerPlayNetworking.send(target, AIM, buf);
            Long last = WARNED.put(target.getUuid(), now);
            if (last == null || now - last > 30L) {
                PhaseFx.privateSound(target, WindySounds.FOLD_CHARGE, 1.0F, 1.9F);
                target.sendMessage(Text.translatable("message.windyorigins.folded.aimed").formatted(Formatting.LIGHT_PURPLE), true);
            }
        }
    }

    /** The trigger was released at full charge; the shot resolves as soon as the client reports its aim. */
    public static void requestShot(ServerPlayerEntity var0) {
        if (Powers.resource(var0, "fold", 0) < SHOT_COST) {
            PhaseFx.privateSound(var0, WindySounds.DENY, 0.5F, 1.3F);
        } else {
            PENDING.put(var0.getUuid(), var0.age);
        }
    }

    private static boolean shootable(ServerPlayerEntity var0, Entity var1) {
        return var1 != var0 && var1 instanceof LivingEntity && !var1.isSpectator() && var1.canHit() && var1.isAlive();
    }

    /**
     * A hitscan shot: no travel time and no drop. The client names the creature that was under its
     * crosshair when it fired, so a hit on screen is a hit; the server only checks that the claim is
     * plausible (same aim, nothing solid in between, target near where the client saw it).
     */
    private static void shoot(ServerPlayerEntity var0, int var1, Vec3d var2, Vec3d var3) {
        int var4 = Powers.resource(var0, "fold", 0);
        if (var4 < SHOT_COST || !var0.isAlive() || !isRifle(var0.getMainHandStack()) && !isRifle(var0.getOffHandStack())) {
            return;
        }

        ServerWorld var5 = var0.getWorld();
        Vec3d var6 = var0.getEyePos();
        Vec3d var7 = var0.getRotationVec(1.0F);
        if (var2 != null && var2.squaredDistanceTo(var6) < 16.0) {
            var6 = var2;
        }

        if (var3 != null && var3.dotProduct(var7) > 0.94) {
            var7 = var3.normalize();
        }

        Vec3d var8 = var6.add(var7.multiply(SHOT_RANGE));
        BlockHitResult var9 = var5.raycast(new RaycastContext(var6, var8, ShapeType.COLLIDER, FluidHandling.NONE, var0));
        boolean var10 = var9.getType() != Type.MISS;
        if (var10) {
            var8 = var9.getPos();
        }

        double var11 = var6.distanceTo(var8);
        LivingEntity var13 = null;
        Vec3d var14 = var8;
        Entity var15 = var1 >= 0 ? var5.getEntityById(var1) : null;
        if (var15 != null && shootable(var0, var15) && PhaseFx.samePhase(var0, var15)) {
            // Where the client drew the target can trail the server by a few ticks.
            Box var16 = var15.getBoundingBox().expand(2.5);
            java.util.Optional<Vec3d> var17 = var16.contains(var6) ? java.util.Optional.of(var6) : var16.raycast(var6, var6.add(var7.multiply(SHOT_RANGE)));
            if (var17.isPresent() && var6.distanceTo(var17.get()) <= var11 + 1.5) {
                var13 = (LivingEntity)var15;
                var14 = var15.getBoundingBox().raycast(var6, var6.add(var7.multiply(SHOT_RANGE))).orElse(var15.getBoundingBox().getCenter());
            }
        }

        if (var13 == null) {
            EntityHitResult var18 = ProjectileUtil.raycast(
                var0, var6, var8, new Box(var6, var8).expand(1.0), var1x -> shootable(var0, var1x), var6.squaredDistanceTo(var8)
            );
            if (var18 != null) {
                var13 = (LivingEntity)var18.getEntity();
                var14 = var18.getPos();
            }
        }

        Powers.setResource(var0, "fold", var4 - SHOT_COST);
        if (var13 != null) {
            DamageSource var19 = new DamageSource(var5.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(SHOT_DAMAGE_TYPE), var0, var0);
            var13.timeUntilRegen = 0;
            boolean var20 = var13.damage(var19, SHOT_DAMAGE);
            PhaseFx.particles(var0, ParticleTypes.CRIT, var14.x, var14.y, var14.z, 14, 0.2, 0.2, 0.2, 0.3);
            PhaseFx.particles(var0, ParticleTypes.REVERSE_PORTAL, var14.x, var14.y, var14.z, 10, 0.15, 0.15, 0.15, 0.2);
            if (var20) {
                PhaseFx.privateSound(var0, net.minecraft.sound.SoundEvents.ENTITY_ARROW_HIT_PLAYER, 0.5F, 1.3F);
                if (inOwnKnot(var0, var13)) {
                    Powers.addResource(var0, "fold", KNOT_REFUND);
                    PhaseFx.particles(var0, ParticleTypes.END_ROD, var14.x, var14.y, var14.z, 12, 0.2, 0.3, 0.2, 0.08);
                }
            }
        } else if (var10) {
            PhaseFx.particles(var0, ParticleTypes.SMOKE, var14.x, var14.y, var14.z, 8, 0.1, 0.1, 0.1, 0.02);
        }

        double var21 = var6.distanceTo(var14);

        for (double var23 = 1.0; var23 < var21; var23 += 0.6) {
            Vec3d var25 = var6.add(var7.multiply(var23));
            PhaseFx.particles(var0, TRACE, var25.x, var25.y, var25.z, 1, 0.0, 0.0, 0.0, 0.0);
        }

        PhaseFx.sound(var0, WindySounds.FOLD_SHOT, SoundCategory.PLAYERS, 2.0F, 0.8F);
        if (Powers.has(var0, "shot_trace")) {
            var0.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, TRACE_TICKS, 0, false, false, true));
        }

        if (isHovering(var0)) {
            var0.addVelocity(-var7.x * 0.9, -var7.y * 0.4, -var7.z * 0.9);
            var0.velocityModified = true;
        }
    }

    public static void toggleHover(ServerPlayerEntity var0) {
        if (isHovering(var0)) {
            setMode(var0, MODE_GLIDE);
        } else if (Powers.resource(var0, "fold", 0) >= 10 && !var0.isTouchingWater() && !var0.hasVehicle()) {
            setMode(var0, MODE_HOVER);
            if (var0.isOnGround()) {
                var0.addVelocity(0.0, 0.42, 0.0);
                var0.velocityModified = true;
            }
        } else {
            PhaseFx.privateSound(var0, WindySounds.DENY, 0.5F, 1.3F);
            var0.sendMessage(Text.translatable("message.windyorigins.folded.no_fold").formatted(Formatting.LIGHT_PURPLE), true);
        }
    }

    private static void setMode(ServerPlayerEntity var0, int var1) {
        UUID var2 = var0.getUuid();
        boolean var3 = HOVERING.contains(var2);
        int var4 = var3 ? MODE_HOVER : (GLIDING.contains(var2) ? MODE_GLIDE : MODE_OFF);
        if (var1 == MODE_GLIDE && (var0.isOnGround() || var0.isTouchingWater() || var0.hasVehicle())) {
            var1 = MODE_OFF;
        }

        if (var4 != var1) {
            HOVERING.remove(var2);
            GLIDING.remove(var2);
            HOVER_SINCE.remove(var2);
            if (var1 == MODE_HOVER) {
                HOVERING.add(var2);
                HOVER_SINCE.put(var2, var0.age);
            } else if (var1 == MODE_GLIDE) {
                GLIDING.add(var2);
            }

            var0.fallDistance = 0.0F;
            PacketByteBuf var5 = new PacketByteBuf(Unpooled.buffer());
            var5.writeByte(var1);
            ServerPlayNetworking.send(var0, HOVER_SYNC, var5);
            if (var3 != (var1 == MODE_HOVER)) {
                PhaseFx.sound(var0, WindySounds.FOLD_HOVER, SoundCategory.PLAYERS, 0.6F, var1 == MODE_HOVER ? 1.3F : 0.8F);
            }
        }
    }

    public static void reset() {
        HOVERING.clear();
        GLIDING.clear();
        HOVER_SINCE.clear();
        PENDING.clear();
        KNOTS.clear();
        WARNED.clear();
    }

    public static void forget(ServerPlayerEntity var0) {
        HOVERING.remove(var0.getUuid());
        GLIDING.remove(var0.getUuid());
        HOVER_SINCE.remove(var0.getUuid());
        PENDING.remove(var0.getUuid());
        KNOTS.remove(var0.getUuid());
        WARNED.remove(var0.getUuid());
    }

    public static void step(ServerPlayerEntity var0) {
        int var1 = Powers.resource(var0, "fold", 0);
        if (var1 < 40) {
            PhaseFx.privateSound(var0, WindySounds.DENY, 0.5F, 1.3F);
        } else {
            Powers.setResource(var0, "fold", var1 - 40);
            var0.addStatusEffect(new StatusEffectInstance(WindyEffects.FOLDED, 30, 0, false, false, true));
            var0.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, 30, 0, false, false, false));

            for (MobEntity var3 : var0.getWorld().getEntitiesByClass(MobEntity.class, var0.getBoundingBox().expand(24.0), var1x -> var1x.getTarget() == var0)) {
                var3.setTarget(null);
            }

            PhaseFx.sound(var0, WindySounds.FOLD_STEP, SoundCategory.PLAYERS, 1.0F, 1.0F);
            PhaseFx.particles(var0, ParticleTypes.REVERSE_PORTAL, var0.getX(), var0.getBodyY(0.5), var0.getZ(), 60, 0.4, 0.8, 0.4, 0.4);
        }
    }

    /** Fold Knot: space is crumpled around a point, and everything caught in it is held for the rifle. */
    public static void knot(ServerPlayerEntity var0) {
        int var1 = Powers.resource(var0, "fold", 0);
        if (var1 < KNOT_COST) {
            PhaseFx.privateSound(var0, WindySounds.DENY, 0.5F, 1.3F);
            var0.sendMessage(Text.translatable("message.windyorigins.folded.no_fold").formatted(Formatting.LIGHT_PURPLE), true);
        } else {
            Vec3d var2 = var0.getEyePos();
            Vec3d var3 = var0.getRotationVec(1.0F);
            Vec3d var4 = var2.add(var3.multiply(KNOT_RANGE));
            BlockHitResult var5 = var0.getWorld().raycast(new RaycastContext(var2, var4, ShapeType.COLLIDER, FluidHandling.NONE, var0));
            if (var5.getType() != Type.MISS) {
                var4 = var5.getPos().subtract(var3.multiply(0.4));
            }

            EntityHitResult var6 = ProjectileUtil.raycast(
                var0, var2, var4, new Box(var2, var4).expand(1.0), var1x -> shootable(var0, var1x), var2.squaredDistanceTo(var4)
            );
            if (var6 != null) {
                var4 = var6.getEntity().getPos().add(0.0, (double)var6.getEntity().getHeight() * 0.5, 0.0);
            }

            Powers.setResource(var0, "fold", var1 - KNOT_COST);
            KNOTS.put(var0.getUuid(), new FoldedManager.Knot(var0.getWorld(), var4, var0.getWorld().getTime() + (long)KNOT_TICKS));
            PhaseFx.sound(var0, var4.x, var4.y, var4.z, WindySounds.FOLD_POINT, SoundCategory.PLAYERS, 1.6F, 0.7F);
            PhaseFx.particles(var0, ParticleTypes.REVERSE_PORTAL, var4.x, var4.y, var4.z, 80, 0.3, 0.3, 0.3, 0.9);
        }
    }

    private static void tickKnot(ServerPlayerEntity var0) {
        FoldedManager.Knot var1 = KNOTS.get(var0.getUuid());
        if (var1 == null) {
            return;
        }

        if (var1.world != var0.getWorld() || var1.expires < var0.getWorld().getTime()) {
            KNOTS.remove(var0.getUuid());
            return;
        }

        Vec3d var2 = var1.pos;
        if (var0.age % 2 == 0) {
            double var3 = (double)var0.age * 0.35;

            for (int var5 = 0; var5 < 6; var5++) {
                double var6 = var3 + (double)var5 * Math.PI / 3.0;
                PhaseFx.particles(
                    var0, KNOT_DUST, var2.x + Math.cos(var6) * KNOT_RADIUS, var2.y + Math.sin(var3 * 0.7 + (double)var5) * 0.6, var2.z + Math.sin(var6) * KNOT_RADIUS, 1, 0.0, 0.0, 0.0, 0.0
                );
            }

            PhaseFx.particles(var0, ParticleTypes.REVERSE_PORTAL, var2.x, var2.y, var2.z, 3, 0.1, 0.1, 0.1, 0.25);
        }

        if (var0.age % 5 == 0) {
            java.util.List<LivingEntity> var8 = var1.world
                .getEntitiesByClass(
                    LivingEntity.class,
                    new Box(var2, var2).expand(KNOT_RADIUS),
                    var2x -> shootable(var0, var2x) && PhaseFx.samePhase(var0, var2x) && var2x.getBoundingBox().getCenter().squaredDistanceTo(var2) <= KNOT_RADIUS * KNOT_RADIUS
                );
            if (!var8.isEmpty()) {
                int[] var9 = new int[var8.size()];

                for (int var10 = 0; var10 < var9.length; var10++) {
                    LivingEntity var11 = var8.get(var10);
                    var9[var10] = var11.getId();
                    var11.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 15, 3, false, false, true), var0);
                    // A faint tug towards the middle keeps the catch from simply walking out.
                    Vec3d var12 = var2.subtract(var11.getBoundingBox().getCenter());
                    if (var12.lengthSquared() > 1.0) {
                        Vec3d var13 = var12.normalize().multiply(0.06);
                        var11.addVelocity(var13.x, 0.0, var13.z);
                        var11.velocityModified = true;
                    }
                }

                com.windykindy49.windyorigins.host.HostNetworking.marks(var0, var9, 12);
            }
        }
    }

    private static boolean inOwnKnot(ServerPlayerEntity var0, Entity var1) {
        FoldedManager.Knot var2 = KNOTS.get(var0.getUuid());
        return var2 != null
            && var2.world == var1.getWorld()
            && var1.getBoundingBox().getCenter().squaredDistanceTo(var2.pos) <= (KNOT_RADIUS + 1.0) * (KNOT_RADIUS + 1.0);
    }

    static float clampCharge(float var0) {
        return MathHelper.clamp(var0, 0.0F, 1.0F);
    }

    private static record Knot(World world, Vec3d pos, long expires) {
    }
}
