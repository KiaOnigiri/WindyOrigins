package com.windykindy49.windyorigins.revenant;

import com.windykindy49.windyorigins.common.Flight;
import com.windykindy49.windyorigins.common.MobGroups;
import com.windykindy49.windyorigins.common.PhaseFx;
import com.windykindy49.windyorigins.common.Powers;
import com.windykindy49.windyorigins.common.WindyEffects;
import com.windykindy49.windyorigins.common.WindySounds;
import com.windykindy49.windyorigins.dream.DreamManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;
import org.joml.Vector3f;

public final class RevenantManager {
    public static final String ECHO = "echo";
    public static final int ECHO_MAX = 10;
    public static final int POSSESS_TICKS = 100;
    public static final int POSSESS_MAX = 1100;
    public static final int POSSESS_EXTEND = 200;
    public static final int POSSESS_COOLDOWN = 1200;
    public static final int WISP_COST = 3;
    public static final int WISP_TICKS = 1200;
    public static final int VENGEANCE_TICKS = 200;
    private static final int ECHO_LIFE = 200;
    private static final double ECHO_SPAWN_RANGE = 24.0;
    private static final double POSSESS_RANGE = 6.0;
    private static final DustParticleEffect GHOST = new DustParticleEffect(new Vector3f(0.35F, 0.55F, 1.0F), 1.3F);
    private static int nextEchoId = 1;
    private static final List<RevenantManager.Echo> ECHOES = new ArrayList<>();
        private static final Map<UUID, Integer> POSSESS_COOLDOWNS = new HashMap<>();
    private static final Set<UUID> FINISHING = new HashSet<>();

    private RevenantManager() {
    }

    public static boolean isRevenant(Entity var0) {
        return Powers.has(var0, "echo");
    }

    public static boolean isGhost(Entity var0) {
        if (var0 instanceof LivingEntity var1 && var1.hasStatusEffect(WindyEffects.VENGEANCE)) {
            return true;
        }

        return false;
    }

    /** Only a death that was a threat leaves an Echo: hostile creatures and players, never livestock. */
    private static boolean leavesEcho(LivingEntity var0) {
        return var0 instanceof net.minecraft.entity.player.PlayerEntity || var0 instanceof net.minecraft.entity.mob.Monster;
    }

    public static void onDeath(LivingEntity var0) {
        if (var0.getWorld() instanceof ServerWorld var1 && !isGhost(var0) && leavesEcho(var0)) {
            boolean var7 = PhaseFx.phase(var0);
            boolean var3 = false;

            for (ServerPlayerEntity var5 : var1.getPlayers()) {
                if (var5 != var0 && isRevenant(var5) && PhaseFx.phase(var5) == var7 && var5.squaredDistanceTo(var0) < 576.0) {
                    var3 = true;
                    break;
                }
            }

            if (!var3) {
                return;
            }

            RevenantManager.Echo var8 = new RevenantManager.Echo(
                nextEchoId++, var1, var0.getPos().add(0.0, (double)var0.getHeight() * 0.6, 0.0), var7, var1.getTime() + 200L
            );
            ECHOES.add(var8);

            for (ServerPlayerEntity var6 : var1.getPlayers()) {
                if (eligible(var6, var8)) {
                    RevenantNetworking.echoAdd(var6, var8.id, var8.pos);
                }
            }

            return;
        }
    }

    private static boolean eligible(ServerPlayerEntity var0, RevenantManager.Echo var1) {
        return var0.getWorld() == var1.world && isRevenant(var0) && PhaseFx.phase(var0) == var1.phase && var0.squaredDistanceTo(var1.pos) < 4096.0;
    }

    private static void tickEchoes(MinecraftServer var0) {
        Iterator var1 = ECHOES.iterator();

        while (true) {
            RevenantManager.Echo var2;
            while (true) {
                if (!var1.hasNext()) {
                    return;
                }

                var2 = (RevenantManager.Echo)var1.next();
                ServerPlayerEntity var3 = null;
                if (var2.expires <= var2.world.getTime()) {
                    break;
                }

                for (PlayerEntity var5 : var2.world.getPlayers()) {
                    if (var5 instanceof ServerPlayerEntity var6
                        && eligible(var6, var2)
                        && !var6.isSpectator()
                        && var6.getBoundingBox().expand(1.0).contains(var2.pos)
                        && Powers.resource(var6, "echo", 0) < 10) {
                        var3 = var6;
                        break;
                    }
                }

                if (var3 != null) {
                    Powers.addResource(var3, "echo", 1);
                    PhaseFx.privateSound(var3, WindySounds.REV_ECHO, 0.8F, 1.0F + var3.getRandom().nextFloat() * 0.3F);
                    var3.getServerWorld().spawnParticles(var3, ParticleTypes.SOUL, true, var2.pos.x, var2.pos.y, var2.pos.z, 12, 0.2, 0.2, 0.2, 0.05);
                    break;
                }
            }

            var1.remove();

            for (PlayerEntity var8 : var2.world.getPlayers()) {
                if (var8 instanceof ServerPlayerEntity) {
                    ServerPlayerEntity var9 = (ServerPlayerEntity)var8;
                    if (isRevenant(var9)) {
                        RevenantNetworking.echoRemove(var9, var2.id);
                    }
                }
            }
        }
    }

    public static void tick(MinecraftServer var0) {
        tickEchoes(var0);
        RevenantState var1 = RevenantState.get(var0);
        boolean var2 = false;
        POSSESS_COOLDOWNS.replaceAll((var0x, var1x) -> var1x - 1);
        POSSESS_COOLDOWNS.values().removeIf(var0x -> var0x <= 0);

        for (ServerPlayerEntity var4 : var0.getPlayerManager().getPlayerList()) {
            RevenantState.Possession var5 = var1.possessions.get(var4.getUuid());
            if (var5 != null) {
                var2 |= tickPossession(var4, var5, var1);
            }

            tickEjected(var4);
            RevenantState.Ghost var6 = var1.ghosts.get(var4.getUuid());
            if (var6 != null) {
                var2 |= tickGhost(var4, var6, var1);
            }

            if (isRevenant(var4)) {
                if (var4.age % 10 == 0 || !POSSESS_COOLDOWNS.containsKey(var4.getUuid())) {
                    Powers.setResource(var4, "possession_recharge", POSSESS_COOLDOWNS.getOrDefault(var4.getUuid(), 0));
                }

                WispManager.tick(var4);
            } else if (WispManager.has(var4)) {
                // No longer a Revenant (the origin was changed): the wisps leave, and drop what they carry.
                WispManager.dismiss(var4);
            }
        }

        if (var2) {
            var1.markDirty();
        }
    }

    /**
     * Logging out mid-possession lets go at once: the Revenant is saved in its own game mode rather
     * than as a spectator, and a haunted player is told the weight has lifted.
     */
    public static void onLeave(ServerPlayerEntity var0) {
        RevenantState var1 = RevenantState.get(var0.getServer());
        RevenantState.Possession var2 = var1.possessions.get(var0.getUuid());
        if (var2 != null) {
            Entity var3 = var2.host == null ? null : findHost(var0.getServer(), var2.host);
            endPossession(var0, var2, var1, null);
            if (var3 != null && var3.getWorld() == var0.getWorld()) {
                var0.setPosition(var3.getX(), var3.getY() + 0.1, var3.getZ());
            }

            if (var3 instanceof ServerPlayerEntity var4) {
                RevenantNetworking.haunted(var4, false);
            }
        }

        EJECTED.remove(var0.getUuid());
    }

    /** The server is stopping: nobody is left possessing anything, and nothing leaks into the next world. */
    public static void stopping(MinecraftServer var0) {
        // A server that failed to start is stopped before it has a player list.
        if (var0.getPlayerManager() == null) {
            return;
        }

        for (ServerPlayerEntity var2 : var0.getPlayerManager().getPlayerList()) {
            onLeave(var2);
            WispManager.dismiss(var2);
        }
    }

    public static void reset() {
        ECHOES.clear();
        POSSESS_COOLDOWNS.clear();
        FINISHING.clear();
        EJECTED.clear();
        WispManager.reset();
    }

    public static void onJoin(ServerPlayerEntity var0) {
        RevenantState var1 = RevenantState.get(var0.getServer());
        RevenantState.Possession var2 = var1.possessions.get(var0.getUuid());
        if (var2 != null) {
            endPossession(var0, var2, var1, null);
        }

        RevenantState.Ghost var3 = var1.ghosts.get(var0.getUuid());
        if (var3 != null) {
            var3.ticksLeft = 0;
        }
    }

    public static void possess(ServerPlayerEntity var0) {
        RevenantState var1 = RevenantState.get(var0.getServer());
        RevenantState.Possession var2 = var1.possessions.get(var0.getUuid());
        if (var2 != null) {
            endPossession(var0, var2, var1, null);
        } else if (!isGhost(var0) && !POSSESS_COOLDOWNS.containsKey(var0.getUuid())) {
            LivingEntity var3 = lookTarget(var0);
            label37:
            if (var3 != null && MobGroups.of(var3) != MobGroups.Group.BOSS && !isGhost(var3)) {
                if (var3 instanceof ServerPlayerEntity var4 && (var4.isSpectator() || var1.possessions.containsKey(var4.getUuid()))) {
                    break label37;
                }

                RevenantState.Possession var6 = new RevenantState.Possession();
                var6.host = var3.getUuid();
                var6.previous = var0.interactionManager.getGameMode();
                var6.ticksLeft = POSSESS_TICKS;
                var6.phase = PhaseFx.phase(var3);
                var6.phaseKnown = true;
                var1.possessions.put(var0.getUuid(), var6);
                var1.markDirty();
                swirl(var0, var0.getPos(), var3.getPos());
                var0.changeGameMode(GameMode.SPECTATOR);
                var0.setCameraEntity(var3);
                var0.addStatusEffect(new StatusEffectInstance(WindyEffects.POSSESSED, 2400, 0, false, false, true));
                if (var3 instanceof MobEntity var5) {
                    var5.setTarget(null);
                }

                if (var3 instanceof ServerPlayerEntity var7) {
                    RevenantNetworking.haunted(var7, true);
                }

                PhaseFx.sound(var3, WindySounds.REV_POSSESS, SoundCategory.PLAYERS, 1.0F, 1.0F);
                return;
            }

            PhaseFx.privateSound(var0, WindySounds.DENY, 0.6F, 1.0F);
            var0.sendMessage(Text.translatable("message.windyorigins.revenant.no_host").formatted(Formatting.DARK_AQUA), true);
        } else {
            PhaseFx.privateSound(var0, WindySounds.DENY, 0.6F, 1.0F);
        }
    }

    private static LivingEntity lookTarget(ServerPlayerEntity var0) {
        Vec3d var1 = var0.getEyePos();
        Vec3d var2 = var0.getRotationVec(1.0F);
        Vec3d var3 = var1.add(var2.multiply(6.0));
        BlockHitResult var4 = var0.getWorld().raycast(new RaycastContext(var1, var3, ShapeType.COLLIDER, FluidHandling.NONE, var0));
        if (var4.getType() != Type.MISS) {
            var3 = var4.getPos();
        }

        EntityHitResult var5 = ProjectileUtil.raycast(
            var0,
            var1,
            var3,
            new Box(var1, var3).expand(1.0),
            var1x -> var1x instanceof LivingEntity && var1x != var0 && var1x.isAlive() && !var1x.isSpectator(),
            var1.squaredDistanceTo(var3)
        );
        return var5 == null ? null : (LivingEntity)var5.getEntity();
    }

    /** Sent by the client when the possessing player presses sneak or jump: always lets go. */
    public static void release(ServerPlayerEntity var0) {
        RevenantState var1 = RevenantState.get(var0.getServer());
        RevenantState.Possession var2 = var1.possessions.get(var0.getUuid());
        if (var2 != null) {
            endPossession(var0, var2, var1, var2.host == null ? null : findHost(var0.getServer(), var2.host));
        }
    }

    private static Entity findHost(MinecraftServer var0, UUID var1) {
        for (ServerWorld var3 : var0.getWorlds()) {
            Entity var4 = var3.getEntity(var1);
            if (var4 != null) {
                return var4;
            }
        }

        return null;
    }

    private static boolean tickPossession(ServerPlayerEntity var0, RevenantState.Possession var1, RevenantState var2) {
        Entity var3 = var1.host == null ? null : findHost(var0.getServer(), var1.host);
        if (var3 instanceof LivingEntity var4 && var4.isAlive() && var0.getCameraEntity() == var4 && var0.isSpectator()) {
            if (!var1.phaseKnown) {
                var1.phase = PhaseFx.phase(var4);
                var1.phaseKnown = true;
            } else if (PhaseFx.phase(var4) != var1.phase) {
                // The host has gone to the other side of the mirror: the Revenant does not go with it, and is out, as if it had let go.
                endPossession(var0, var1, var2, var4);
                return true;
            }

            var1.ticksLeft--;
            var1.spent++;
            if (var1.ticksLeft == 60 && var1.spent + var1.ticksLeft + POSSESS_EXTEND <= POSSESS_MAX && Powers.resource(var0, "echo", 0) > 0) {
                Powers.addResource(var0, "echo", -1);
                var1.ticksLeft += POSSESS_EXTEND;
                PhaseFx.privateSound(var0, WindySounds.REV_ECHO, 0.7F, 0.7F);
            }

            if (var1.ticksLeft <= 0) {
                endPossession(var0, var1, var2, var4);
                return true;
            }

            if (var1.ticksLeft % 5 == 0) {
                Powers.setResource(var0, "possession", var1.ticksLeft);
            }

            if (var0.getWorld() != var4.getWorld()) {
                var0.teleport((ServerWorld)var4.getWorld(), var4.getX(), var4.getY(), var4.getZ(), var0.getYaw(), var0.getPitch());
            } else if (var0.age % 10 == 0 || var0.squaredDistanceTo(var4) > 1.0) {
                // The server glues a spectator to its camera silently, so the client never hears
                // where its own body went. Once a fast host (another player) had carried the view
                // out of the chunks around that stale body, the client stopped ticking it: the
                // camera froze and no key could end the possession. Tell it, regularly.
                var0.networkHandler.requestTeleport(var4.getX(), var4.getY(), var4.getZ(), var4.getYaw(), var4.getPitch());
            }

            if (var0.age % 20 == 0) {
                var0.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.SetCameraEntityS2CPacket(var4));
            }

            if (var0.age % 20 == 0) {
                var0.heal(1.0F);
                var4.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 40, 0, false, false, true));
            }

            if (var4 instanceof ServerPlayerEntity var5 && var0.age % 80 == 0) {
                PhaseFx.privateSound(var5, WindySounds.REV_WHISPER, 0.5F, 0.8F + var0.getRandom().nextFloat() * 0.4F);
            }

            if (var0.age % 5 == 0) {
                PhaseFx.particles(var4, ParticleTypes.SOUL, var4.getX(), var4.getBodyY(0.6), var4.getZ(), 1, 0.25, 0.4, 0.25, 0.01);
            }

            return var1.ticksLeft % 20 == 0;
        }

        endPossession(var0, var1, var2, var3);
        return true;
    }

    private static void endPossession(ServerPlayerEntity var0, RevenantState.Possession var1, RevenantState var2, Entity var3) {
        var2.possessions.remove(var0.getUuid());
        var2.markDirty();
        var0.setCameraEntity(var0);
        var0.changeGameMode(var1.previous == null ? GameMode.SURVIVAL : var1.previous);
        var0.removeStatusEffect(WindyEffects.POSSESSED);
        Powers.setResource(var0, "possession", 0);
        if (var3 != null && var3.getWorld() == var0.getWorld()) {
            // The soul bursts out of its host: the client picks the direction from the movement
            // keys (or the camera) and launches itself; the flight that follows cannot hurt.
            var0.networkHandler.requestTeleport(var3.getX(), var3.getY() + 0.1, var3.getZ(), var3.getYaw(), var3.getPitch());
            var0.fallDistance = 0.0F;
            EJECTED.put(var0.getUuid(), 0);
            RevenantNetworking.eject(var0, var3.getYaw(), var3.getPitch());
            PhaseFx.particles(var0, ParticleTypes.SOUL, var3.getX(), var3.getBodyY(0.5), var3.getZ(), 24, 0.3, 0.5, 0.3, 0.12);
        } else if (var0.networkHandler != null && !var0.isDisconnected()) {
            // The host is gone (it died, despawned or logged out): the soul is left where the host
            // last was. The client is told so, and whatever drop is under it does no harm.
            var0.setVelocity(Vec3d.ZERO);
            var0.networkHandler.requestTeleport(var0.getX(), var0.getY(), var0.getZ(), var0.getYaw(), var0.getPitch());
            var0.fallDistance = 0.0F;
            EJECTED.put(var0.getUuid(), 0);
        }

        if (var3 instanceof ServerPlayerEntity var5) {
            RevenantNetworking.haunted(var5, false);
        }

        POSSESS_COOLDOWNS.put(var0.getUuid(), 1200);
        PhaseFx.sound(var0, WindySounds.REV_RELEASE, SoundCategory.PLAYERS, 1.0F, 1.1F);
    }

    private static final Map<UUID, Integer> EJECTED = new HashMap<>();

    /** True while a Revenant is still in the air after being flung out of a host. */
    public static boolean isEjected(Entity var0) {
        return EJECTED.containsKey(var0.getUuid());
    }

    private static void tickEjected(ServerPlayerEntity var0) {
        Integer var1 = EJECTED.get(var0.getUuid());
        if (var1 != null) {
            var0.fallDistance = 0.0F;
            if (var1 > 300 || var1 > 8 && (var0.isOnGround() || var0.isTouchingWater() || var0.getAbilities().flying) || !var0.isAlive()) {
                EJECTED.remove(var0.getUuid());
            } else {
                EJECTED.put(var0.getUuid(), var1 + 1);
                if (var0.age % 2 == 0) {
                    PhaseFx.particles(var0, ParticleTypes.SOUL, var0.getX(), var0.getBodyY(0.4), var0.getZ(), 1, 0.15, 0.2, 0.15, 0.0);
                }
            }
        }
    }

    private static void swirl(ServerPlayerEntity var0, Vec3d var1, Vec3d var2) {
        Vec3d var3 = var2.subtract(var1);

        for (int var4 = 0; var4 <= 24; var4++) {
            double var5 = (double)var4 / 24.0;
            double var7 = var5 * Math.PI * 6.0;
            Vec3d var9 = var1.add(var3.multiply(var5)).add(Math.cos(var7) * 0.4, 1.0 + Math.sin(var5 * Math.PI) * 0.5, Math.sin(var7) * 0.4);
            PhaseFx.particles(var0, ParticleTypes.SOUL, var9.x, var9.y, var9.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    public static void summonWisps(ServerPlayerEntity var0) {
        WispManager.command(var0);
    }

    static RevenantManager.Echo echo(int var0) {
        for (RevenantManager.Echo var2 : ECHOES) {
            if (var2.id == var0) {
                return var2;
            }
        }

        return null;
    }

    static List<RevenantManager.Echo> echoesNear(ServerPlayerEntity var0, double var1) {
        List<RevenantManager.Echo> var3 = new ArrayList<>();

        for (RevenantManager.Echo var5 : ECHOES) {
            if (eligible(var0, var5) && var5.pos.squaredDistanceTo(var0.getPos()) < var1 * var1) {
                var3.add(var5);
            }
        }

        return var3;
    }

    /** A wisp carries the echo off; it is gone for everyone else. */
    static boolean takeEcho(RevenantManager.Echo var0) {
        if (!ECHOES.remove(var0)) {
            return false;
        }

        for (PlayerEntity var2 : var0.world.getPlayers()) {
            if (var2 instanceof ServerPlayerEntity var3 && isRevenant(var3)) {
                RevenantNetworking.echoRemove(var3, var0.id);
            }
        }

        return true;
    }

    public static boolean allowDeath(LivingEntity var0, DamageSource var1) {
        if (var0 instanceof ServerPlayerEntity var2
            && Powers.has(var2, "vengeance")
            && !FINISHING.contains(var2.getUuid())
            && !isGhost(var2)
            && !DreamManager.isDreaming(var2.getUuid())
            && !var1.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)
            && !RevenantState.get(var2.getServer()).possessions.containsKey(var2.getUuid())) {
            RevenantState var3 = RevenantState.get(var2.getServer());
            RevenantState.Ghost var4 = new RevenantState.Ghost();
            var4.world = var2.getWorld().getRegistryKey();
            var4.deathPos = var2.getPos();
            Entity var5 = var1.getAttacker();
            var4.killer = var5 == null ? null : var5.getUuid();
            var4.ticksLeft = VENGEANCE_TICKS;
            var3.ghosts.put(var2.getUuid(), var4);
            var3.markDirty();
            var2.setHealth(var2.getMaxHealth());
            var2.extinguish();
            var2.clearStatusEffects();
            var2.addStatusEffect(new StatusEffectInstance(WindyEffects.VENGEANCE, VENGEANCE_TICKS + 20, 0, false, false, true));
            var2.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, VENGEANCE_TICKS + 40, 0, false, false, false));
            Flight.grant(var2, Flight.GHOST, true);
            WispManager.dismiss(var2);
            if (var5 != null) {
                RevenantNetworking.marks(var2, new int[]{var5.getId()});
            }

            PhaseFx.sound(var2, WindySounds.REV_VENGEANCE, SoundCategory.PLAYERS, 1.2F, 0.8F);
            PhaseFx.particles(var2, ParticleTypes.SCULK_SOUL, var2.getX(), var2.getBodyY(0.5), var2.getZ(), 30, 0.4, 0.7, 0.4, 0.05);
            var2.sendMessage(Text.translatable("message.windyorigins.revenant.vengeance").formatted(Formatting.BLUE), true);
            return false;
        }

        return true;
    }

    private static boolean tickGhost(ServerPlayerEntity var0, RevenantState.Ghost var1, RevenantState var2) {
        var1.ticksLeft--;
        var0.fallDistance = 0.0F;
        if (!var0.getAbilities().flying || !var0.getAbilities().allowFlying) {
            var0.getAbilities().allowFlying = true;
            var0.getAbilities().flying = true;
            var0.sendAbilitiesUpdate();
        }

        if (var0.age % 2 == 0) {
            PhaseFx.particles(var0, GHOST, var0.getX(), var0.getBodyY(0.5), var0.getZ(), 2, 0.25, 0.5, 0.25, 0.0);
            PhaseFx.particles(var0, ParticleTypes.SOUL, var0.getX(), var0.getBodyY(0.3), var0.getZ(), 1, 0.2, 0.3, 0.2, 0.01);
        }

        if (var1.ticksLeft > 0 && var0.isAlive()) {
            return var1.ticksLeft % 20 == 0;
        } else {
            finishGhost(var0, var1, var2);
            return true;
        }
    }

    private static void finishGhost(ServerPlayerEntity var0, RevenantState.Ghost var1, RevenantState var2) {
        var2.ghosts.remove(var0.getUuid());
        var2.markDirty();
        RevenantNetworking.marks(var0, new int[0]);
        ServerWorld var3 = var0.getServer().getWorld(var1.world);
        if (var3 == null) {
            var3 = var0.getServerWorld();
        }

        Flight.revoke(var0, Flight.GHOST);
        var0.removeStatusEffect(WindyEffects.VENGEANCE);
        var0.teleport(var3, var1.deathPos.x, var1.deathPos.y, var1.deathPos.z, var0.getYaw(), var0.getPitch());
        PhaseFx.particles(var0, ParticleTypes.SOUL, var0.getX(), var0.getBodyY(0.5), var0.getZ(), 40, 0.3, 0.8, 0.3, 0.06);
        PhaseFx.sound(var0, WindySounds.REV_FADE, SoundCategory.PLAYERS, 1.0F, 1.0F);
        if (var0.isAlive() && !var0.isCreative() && !var0.isSpectator()) {
            FINISHING.add(var0.getUuid());

            try {
                var0.damage(var3.getDamageSources().magic(), Float.MAX_VALUE);
                if (var0.isAlive()) {
                    var0.setHealth(0.0F);
                    var0.onDeath(var3.getDamageSources().magic());
                }
            } finally {
                FINISHING.remove(var0.getUuid());
            }
        }
    }

    public static boolean ghostStrike(ServerPlayerEntity var0, Entity var1) {
        if (!isGhost(var0)) {
            return false;
        } else {
            if (var1 instanceof LivingEntity var2 && PhaseFx.samePhase(var0, var2)) {
                var2.damage(var0.getWorld().getDamageSources().indirectMagic(var0, var0), 4.0F);
                var2.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 100, 0), var0);
                PhaseFx.particles(var0, ParticleTypes.SOUL, var2.getX(), var2.getBodyY(0.6), var2.getZ(), 6, 0.2, 0.3, 0.2, 0.05);
            }

            return true;
        }
    }

    public static float clamp01(float var0) {
        return MathHelper.clamp(var0, 0.0F, 1.0F);
    }

    static record Echo(int id, World world, Vec3d pos, boolean phase, long expires) {
    }
}
