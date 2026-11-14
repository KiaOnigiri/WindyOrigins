package com.windykindy49.windyorigins.bounty;

import com.windykindy49.windyorigins.dream.DreamRules;
import com.windykindy49.windyorigins.util.MirrorEntity;
import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.PowerTypeRegistry;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.joml.Vector3f;

public final class BountyManager {
    public static final int MARK_DURATION = 1200;
    public static final int MARK_EXTEND_ON_HIT = 60;
    public static final int COOLDOWN = 2400;
    public static final double MARK_RANGE = 100.0;
    public static final double LEASH_RANGE = 150.0;
    /** A marked player stops seeing creatures and players (not terrain) past this many blocks. */
    public static final double MARKED_VISION = 12.0;
    private static final int LOST_GRACE = 200;
    public static final int PHASE_GRACE = 100;
    private static final int DETAIL_INTERVAL = 5;
    private static final int PURSUIT_INTERVAL = 5;
    private static final double PURSUIT_MIN_GAIN = 0.25;
    private static final DustParticleEffect BLOOD = new DustParticleEffect(new Vector3f(0.72F, 0.04F, 0.06F), 1.2F);
    private static final Map<UUID, BountyRewards.Tier> JUST_PAID = new HashMap<>();

    private BountyManager() {
    }

    public static boolean hasPower(Entity var0, String var1) {
        if (!(var0 instanceof LivingEntity)) {
            return false;
        } else {
            Identifier var2 = new Identifier("windyorigins", var1);
            if (!PowerTypeRegistry.contains(var2)) {
                return false;
            } else if (DreamRules.silenced(var0, var2)) {
                return false;
            } else {
                PowerType var3 = PowerTypeRegistry.get(var2);
                return PowerHolderComponent.KEY.maybeGet(var0).map(var1x -> var1x.hasPower(var3)).orElse(false);
            }
        }
    }

    public static boolean isMirrored(Entity var0) {
        return ((MirrorEntity)var0).windyorigins$isInMirrorWorld();
    }

    public static boolean isReady(ServerPlayerEntity var0) {
        BountyMarkState.Entry var1 = BountyMarkState.get(var0.getServerWorld()).peek(var0.getUuid());
        return var1 == null || var1.target == null && var1.cooldownTicks <= 0;
    }

    public static void tryMark(ServerPlayerEntity var0, LivingEntity var1) {
        BountyMarkState.Entry var2 = BountyMarkState.get(var0.getServerWorld()).peek(var0.getUuid());
        if (var2 != null && var2.target != null) {
            deny(var0, Text.translatable("message.windyorigins.bounty.busy"));
        } else if (var2 != null && var2.cooldownTicks > 0) {
            deny(var0, Text.translatable("message.windyorigins.bounty.cooldown", (var2.cooldownTicks + 19) / 20));
        } else if (var1 != null && var1 != var0) {
            mark(var0, var1);
        } else {
            deny(var0, Text.translatable("message.windyorigins.bounty.no_target"));
        }
    }

    private static void deny(ServerPlayerEntity var0, Text var1) {
        BountySounds.playPrivate(var0, BountySounds.DENY, 0.6F, 1.3F);
        var0.sendMessage(var1.copy().formatted(Formatting.GRAY), true);
    }

    public static void mark(ServerPlayerEntity var0, LivingEntity var1) {
        BountyMarkState var2 = BountyMarkState.get(var0.getServerWorld());
        BountyMarkState.Entry var3 = var2.getOrCreate(var0.getUuid());
        var3.target = var1.getUuid();
        var3.markTicks = 1200;
        var3.cooldownTicks = 0;
        var3.lostTicks = 0;
        var3.phaseTicks = 0;
        var3.lastDistance = -1.0;
        var2.touch();
        BountyNetworking.syncHunter(var0, var1.getId(), var3.markTicks, 0);
        BountySounds.playPrivate(var0, BountySounds.MARK, 0.7F, 0.5F);
        var0.sendMessage(Text.translatable("message.windyorigins.bounty.marked", var1.getDisplayName()).formatted(Formatting.DARK_RED), true);
        var0.getServerWorld().spawnParticles(var0, BLOOD, true, var1.getX(), var1.getBodyY(1.0) + 0.4, var1.getZ(), 14, 0.25, 0.15, 0.25, 0.0);
        if (var1 instanceof ServerPlayerEntity var4) {
            BountyNetworking.syncMarked(var4, true);
            BountySounds.playPrivate(var4, BountySounds.HUNTED, 1.0F, 0.8F);
            var4.sendMessage(Text.translatable("message.windyorigins.bounty.hunted").formatted(Formatting.DARK_RED), true);
        }
    }

    public static void extend(ServerPlayerEntity var0) {
        BountyMarkState var1 = BountyMarkState.get(var0.getServerWorld());
        BountyMarkState.Entry var2 = var1.peek(var0.getUuid());
        if (var2 != null && var2.target != null) {
            var2.markTicks = Math.min(1200, var2.markTicks + 60);
            var1.touch();
        }
    }

    public static boolean isMarkedBy(ServerPlayerEntity var0, Entity var1) {
        if (var1 == null) {
            return false;
        } else {
            BountyMarkState.Entry var2 = BountyMarkState.get(var0.getServerWorld()).peek(var0.getUuid());
            return var2 != null && var2.target != null && var2.target.equals(var1.getUuid());
        }
    }

    public static boolean hasActiveMark(ServerPlayerEntity var0) {
        BountyMarkState.Entry var1 = BountyMarkState.get(var0.getServerWorld()).peek(var0.getUuid());
        return var1 != null && var1.target != null;
    }

    public static boolean isRecharging(ServerPlayerEntity var0) {
        BountyMarkState.Entry var1 = BountyMarkState.get(var0.getServerWorld()).peek(var0.getUuid());
        return var1 != null && var1.target == null && var1.cooldownTicks > 0;
    }

    public static ServerPlayerEntity firstMarkerOf(MinecraftServer var0, Entity var1) {
        UUID var2 = var1.getUuid();

        for (Entry var4 : BountyMarkState.get(var0.getOverworld()).all().entrySet()) {
            BountyMarkState.Entry var5 = (BountyMarkState.Entry)var4.getValue();
            if (var5.target != null && var5.target.equals(var2)) {
                ServerPlayerEntity var6 = var0.getPlayerManager().getPlayer((UUID)var4.getKey());
                if (var6 != null) {
                    return var6;
                }
            }
        }

        return null;
    }

    public static boolean isMarkedByAnyone(MinecraftServer var0, Entity var1) {
        return isTargeted(var0, var1.getUuid());
    }

    public static void reset() {
        JUST_PAID.clear();
    }

    public static void notePaid(ServerPlayerEntity var0, BountyRewards.Tier var1) {
        JUST_PAID.put(var0.getUuid(), var1);
    }

    public static void onTargetDied(MinecraftServer var0, Entity var1) {
        UUID var2 = var1.getUuid();
        BountyMarkState var3 = BountyMarkState.get(var0.getOverworld());
        boolean var4 = false;

        for (Entry var6 : var3.all().entrySet()) {
            BountyMarkState.Entry var7 = (BountyMarkState.Entry)var6.getValue();
            if (var7.target != null && var7.target.equals(var2)) {
                var4 = true;
                var7.target = null;
                var7.markTicks = 0;
                var7.cooldownTicks = cooldownAfterKill(var1);
                var7.phaseTicks = 0;
                var7.lastDistance = -1.0;
                ServerPlayerEntity var8 = var0.getPlayerManager().getPlayer((UUID)var6.getKey());
                if (var8 != null) {
                    BountyNetworking.syncHunter(var8, -1, 0, var7.cooldownTicks);
                    celebrate(var8, var1);
                }
            }
        }

        JUST_PAID.clear();
        if (var4) {
            var3.touch();
        }

        if (var1 instanceof ServerPlayerEntity) {
            BountyNetworking.syncMarked((ServerPlayerEntity)var1, false);
        }
    }

    /**
     * Only a hunted player clears the cooldown outright. A creature refunds a share of it, larger
     * for more dangerous prey, but most of the wait always remains.
     */
    public static int cooldownAfterKill(Entity var0) {
        if (var0 instanceof ServerPlayerEntity || !(var0 instanceof LivingEntity)) {
            return var0 instanceof ServerPlayerEntity ? 0 : COOLDOWN;
        }

        float var1 = switch (BountyRewards.tierOf((LivingEntity)var0)) {
            case SMALL_FRY -> 0.15F;
            case STANDARD -> 0.25F;
            case ELITE -> 0.4F;
            case LEGENDARY -> 0.55F;
            default -> 1.0F;
        };
        return Math.round(COOLDOWN * (1.0F - var1));
    }

    private static void celebrate(ServerPlayerEntity var0, Entity var1) {
        BountySounds.playPrivate(var0, BountySounds.COMPLETE, 0.6F, 1.2F);
        ServerWorld var2 = var0.getServerWorld();
        if (var1.getWorld() == var2) {
            double var3 = var1.getBodyY(0.5);
            var2.spawnParticles(var0, BLOOD, true, var1.getX(), var3, var1.getZ(), 40, 0.4, 0.5, 0.4, 0.0);
            var2.spawnParticles(var0, ParticleTypes.SOUL, true, var1.getX(), var3, var1.getZ(), 10, 0.3, 0.4, 0.3, 0.03);
        }

        BountyRewards.Tier var5 = JUST_PAID.get(var0.getUuid());
        MutableText var4 = var5 == null
            ? Text.translatable("message.windyorigins.bounty.complete")
            : Text.translatable("message.windyorigins.bounty.complete.paid", Text.translatable("message.windyorigins.bounty.tier." + var5.key()));
        var0.sendMessage(var4.copy().formatted(Formatting.GOLD), true);
    }

    private static void endMark(MinecraftServer var0, UUID var1, BountyMarkState.Entry var2, BountyManager.EndReason var3) {
        UUID var4 = var2.target;
        var2.target = null;
        var2.markTicks = 0;
        var2.lostTicks = 0;
        var2.phaseTicks = 0;
        var2.lastDistance = -1.0;
        var2.cooldownTicks = 2400;
        ServerPlayerEntity var5 = var0.getPlayerManager().getPlayer(var1);
        if (var5 != null) {
            BountyNetworking.syncHunter(var5, -1, 0, var2.cooldownTicks);
            BountySounds.playPrivate(var5, BountySounds.BROKEN, 0.6F, 0.6F);
            var5.sendMessage(Text.translatable(var3.key()).formatted(Formatting.GRAY), true);
        }

        if (var4 != null && !isTargeted(var0, var4)) {
            ServerPlayerEntity var6 = var0.getPlayerManager().getPlayer(var4);
            if (var6 != null) {
                BountyNetworking.syncMarked(var6, false);
                var6.sendMessage(Text.translatable("message.windyorigins.bounty.released").formatted(Formatting.GRAY), true);
            }
        }
    }

    private static boolean isTargeted(MinecraftServer var0, UUID var1) {
        for (BountyMarkState.Entry var3 : BountyMarkState.get(var0.getOverworld()).all().values()) {
            if (var3.target != null && var3.target.equals(var1)) {
                return true;
            }
        }

        return false;
    }

    public static void tick(MinecraftServer var0) {
        BountyMarkState var1 = BountyMarkState.get(var0.getOverworld());
        boolean var2 = false;

        for (Entry var4 : var1.all().entrySet()) {
            BountyMarkState.Entry var5 = (BountyMarkState.Entry)var4.getValue();
            ServerPlayerEntity var6 = var0.getPlayerManager().getPlayer((UUID)var4.getKey());
            if (var5.target == null) {
                if (var5.cooldownTicks > 0) {
                    var5.cooldownTicks--;
                    var2 = true;
                    if (var5.cooldownTicks == 0 && var6 != null) {
                        BountyNetworking.syncHunter(var6, -1, 0, 0);
                        BountySounds.playPrivate(var6, BountySounds.READY, 0.8F, 0.8F);
                        var6.sendMessage(Text.translatable("message.windyorigins.bounty.ready").formatted(Formatting.DARK_RED), true);
                    }
                }
            } else {
                Entity var7 = findEntity(var0, var5.target);
                if (var7 != null && var6 != null) {
                    var5.lostTicks = 0;
                    boolean var8 = var7.getWorld() == var6.getWorld();
                    if (!var8 || (double)var6.distanceTo(var7) > 150.0) {
                        endMark(var0, (UUID)var4.getKey(), var5, BountyManager.EndReason.TOO_FAR);
                        var2 = true;
                        continue;
                    }

                    if (isMirrored(var7) != isMirrored(var6)) {
                        if (++var5.phaseTicks > 100) {
                            endMark(var0, (UUID)var4.getKey(), var5, BountyManager.EndReason.OTHER_PHASE);
                            var2 = true;
                            continue;
                        }
                    } else {
                        var5.phaseTicks = 0;
                    }
                } else {
                    var5.lostTicks++;
                    if (var5.lostTicks > 200) {
                        endMark(var0, (UUID)var4.getKey(), var5, BountyManager.EndReason.LOST);
                        var2 = true;
                        continue;
                    }
                }

                var5.markTicks--;
                var2 = true;
                if (var5.markTicks <= 0) {
                    endMark(var0, (UUID)var4.getKey(), var5, BountyManager.EndReason.EXPIRED);
                } else if (var6 != null && var7 != null) {
                    BountyNetworking.syncHunter(var6, var7.getId(), var5.markTicks, 0);
                    if (var6.age % 5 == 0) {
                        applyPursuit(var6, var7, var5);
                    }

                    if (var7 instanceof ServerPlayerEntity var9 && var6.age % 5 == 0 && hasPower(var6, "dossier")) {
                        BountyNetworking.sendDetail(var6, var9);
                    }
                }
            }
        }

        if (var2) {
            var1.touch();
        }

        var1.all().entrySet().removeIf(var0x -> var0x.getValue().isIdle());
    }

    private static void applyPursuit(ServerPlayerEntity var0, Entity var1, BountyMarkState.Entry var2) {
        double var3 = (double)var0.distanceTo(var1);
        double var5 = var2.lastDistance;
        var2.lastDistance = var3;
        if (!(var5 < 0.0) && hasPower(var0, "pursuit") && var2.phaseTicks <= 0) {
            if (var5 - var3 >= 0.25) {
                var0.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 30, 0, true, false, true));
            }
        }
    }

    private static Entity findEntity(MinecraftServer var0, UUID var1) {
        for (ServerWorld var3 : var0.getWorlds()) {
            Entity var4 = var3.getEntity(var1);
            if (var4 != null) {
                return var4;
            }
        }

        return null;
    }

    public static enum EndReason {
        EXPIRED,
        TOO_FAR,
        LOST,
        OTHER_PHASE;

        public String key() {
            return "message.windyorigins.bounty.broken." + this.name().toLowerCase(Locale.ROOT);
        }
    }
}
