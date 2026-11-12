package com.windykindy49.windyorigins.dream;

import com.windykindy49.windyorigins.common.MobGroups;
import com.windykindy49.windyorigins.common.PhaseFx;
import com.windykindy49.windyorigins.common.Powers;
import com.windykindy49.windyorigins.common.WindyEffects;
import com.windykindy49.windyorigins.common.WindySounds;
import com.windykindy49.windyorigins.util.SpawnPhaseOverride;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PhantomEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.loot.context.LootContextParameterSet.Builder;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;
import org.joml.Vector3f;

public final class DreamManager {
    public static final RegistryKey<World> DREAMS = RegistryKey.of(RegistryKeys.WORLD, new Identifier("windyorigins", "dreams"));
    public static final Identifier STATE = new Identifier("windyorigins", "dream_state");
    private static final int MOB_DREAM_TICKS = 3600;
    private static final int LUCID_TICKS = 1800;
    private static final int MOB_SLEEP = 200;
    private static final int PLAYER_SLEEP = 160;
    private static final long MOB_DREAM_COOLDOWN = 24000L;
    private static final long NEUTRAL_AFTER = 6000L;
    private static final int INSOMNIA = 72000;
    private static final DustParticleEffect DREAM_DUST = new DustParticleEffect(new Vector3f(0.7F, 0.6F, 1.0F), 1.0F);
    private static final Map<UUID, DreamManager.Session> SESSIONS = new HashMap<>();
    private static final Map<UUID, DreamManager.Session> BY_PLAYER = new HashMap<>();
    private static final Map<UUID, Long> MOB_DREAMT = new HashMap<>();
    private static final Map<UUID, Map<UUID, Long>> NEUTRAL = new HashMap<>();
    private static final Map<UUID, Long> DARK_READY = new HashMap<>();
    private static final int DARK_RADIUS = 7;
    private static final int DARK_TICKS = 160;

    private DreamManager() {
    }

    public static boolean isWalker(Entity var0) {
        return Powers.has(var0, "dream_touch");
    }

    public static boolean isAsleep(LivingEntity var0) {
        return var0.hasStatusEffect(WindyEffects.DREAMING) || var0 instanceof PlayerEntity && var0.hasStatusEffect(WindyEffects.LULLED);
    }

    public static boolean inDreams(Entity var0) {
        return var0.getWorld().getRegistryKey() == DREAMS;
    }

    public static boolean isDreaming(UUID var0) {
        return BY_PLAYER.containsKey(var0);
    }

    public static boolean sessionAlive(UUID var0) {
        DreamManager.Session var1 = SESSIONS.get(var0);
        return var1 != null && !var1.over;
    }

    public static void touch(ServerPlayerEntity var0) {
        if (inDreams(var0)) {
            blink(var0);
        } else if (var0.hasStatusEffect(WindyEffects.DREAM_SICKNESS)) {
            deny(var0, "recoil");
        } else {
            LivingEntity var1 = lookTarget(var0, 4.0);
            if (var1 != null && !isAsleep(var1)) {
                if (MobGroups.of(var1) == MobGroups.Group.BOSS) {
                    deny(var0, "too_strong");
                } else {
                    boolean var2 = var1 instanceof PlayerEntity;
                    int var3 = var2 ? 160 : 200;
                    if (var2) {
                        var1.addStatusEffect(new StatusEffectInstance(WindyEffects.LULLED, var3, 0, false, false, true), var0);
                        var1.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, var3, 0, false, false, false), var0);
                    } else {
                        var1.addStatusEffect(new StatusEffectInstance(WindyEffects.DREAMING, var3, 0, false, false, true), var0);
                    }

                    if (var1 instanceof MobEntity var4) {
                        var4.setTarget(null);
                    }

                    PhaseFx.particles(var0, DREAM_DUST, var1.getX(), var1.getEyeY() + 0.3, var1.getZ(), 20, 0.3, 0.3, 0.3, 0.0);
                    PhaseFx.sound(var0, WindySounds.DREAM_LULL, SoundCategory.PLAYERS, 0.8F, 1.0F);
                }
            } else {
                deny(var0, "no_target");
            }
        }
    }

    private static void deny(ServerPlayerEntity var0, String var1) {
        PhaseFx.privateSound(var0, WindySounds.DENY, 0.6F, 1.1F);
        var0.sendMessage(Text.translatable("message.windyorigins.dream." + var1).formatted(Formatting.LIGHT_PURPLE), true);
    }

    private static LivingEntity lookTarget(ServerPlayerEntity var0, double var1) {
        Vec3d var3 = var0.getEyePos();
        Vec3d var4 = var3.add(var0.getRotationVec(1.0F).multiply(var1));
        BlockHitResult var5 = var0.getWorld().raycast(new RaycastContext(var3, var4, ShapeType.COLLIDER, FluidHandling.NONE, var0));
        if (var5.getType() != Type.MISS) {
            var4 = var5.getPos();
        }

        EntityHitResult var6 = ProjectileUtil.raycast(
            var0,
            var3,
            var4,
            new Box(var3, var4).expand(1.0),
            var1x -> var1x instanceof LivingEntity && var1x != var0 && var1x.isAlive() && !var1x.isSpectator(),
            var3.squaredDistanceTo(var4)
        );
        return var6 == null ? null : (LivingEntity)var6.getEntity();
    }

    public static void enter(ServerPlayerEntity var0) {
        if (inDreams(var0)) {
            sculpt(var0);
        } else if (!isDreaming(var0.getUuid())) {
            LivingEntity var1 = lookTarget(var0, 6.0);
            if (var1 != null && isAsleep(var1)) {
                ServerWorld var2 = var0.getServer().getWorld(DREAMS);
                if (var2 == null) {
                    deny(var0, "no_dimension");
                } else if (var1 instanceof ServerPlayerEntity var6) {
                    if (isDreaming(var6.getUuid())) {
                        deny(var0, "taken");
                    } else {
                        if (var0.isSneaking()) {
                            sweetDream(var0, var6);
                        } else {
                            lucid(var0, var6, var2);
                        }
                    }
                } else {
                    Long var3 = MOB_DREAMT.get(var1.getUuid());

                    for (DreamManager.Session var5 : SESSIONS.values()) {
                        if (var5.kind == DreamManager.Kind.MOB && var1.getUuid().equals(var5.mob) && !var5.over && var5.walkers.size() < 2) {
                            join(var0, var5, var2);
                            return;
                        }
                    }

                    long var9 = var3 == null ? 0L : MOB_DREAM_COOLDOWN - (var0.getServer().getOverworld().getTime() - var3);
                    if (var9 > 0L) {
                        PhaseFx.privateSound(var0, WindySounds.DENY, 0.6F, 1.1F);
                        long var11 = (var9 + 19L) / 20L;
                        var0.sendMessage(
                            Text.translatable("message.windyorigins.dream.dreamt_recently", String.format("%d:%02d", var11 / 60L, var11 % 60L))
                                .formatted(Formatting.LIGHT_PURPLE),
                            true
                        );
                    } else {
                        mobDream(var0, var1, var2);
                    }
                }
            } else {
                if (!var0.getWorld().isDay() && var0.isOnGround() && Powers.has(var0, "sleep_anywhere")) {
                    sleepHere(var0);
                } else {
                    deny(var0, "no_sleeper");
                }
            }
        }
    }

    private static int freeSlot() {
        for (int var0 = 1; var0 < 4096; var0++) {
            int var1 = var0;
            if (SESSIONS.values().stream().noneMatch(var1x -> var1x.slot == var1)) {
                return var1;
            }
        }

        return 4096;
    }

    private static void mobDream(ServerPlayerEntity var0, LivingEntity var1, ServerWorld var2) {
        DreamManager.Session var3 = new DreamManager.Session(DreamManager.Kind.MOB, freeSlot());
        var3.mob = var1.getUuid();
        var3.mobWorld = var1.getWorld().getRegistryKey();
        var3.group = MobGroups.of(var1);
        var3.mobHealth = var1.getMaxHealth();
        int var4 = DreamArena.strengthOf(var3.mobHealth);
        int var5 = 1 + var4;
        DreamArena.Layout var6 = DreamArena.build(var2, var3.slot, var3.group, var4, var5, var1.getUuid().getLeastSignificantBits());
        var3.center = var6.center;

        for (BlockPos var8 : var6.spots) {
            var2.setBlockState(var8, Blocks.AMETHYST_CLUSTER.getDefaultState());
            var3.shards.add(var8);
        }

        var3.ends = var2.getTime() + 3600L;
        SESSIONS.put(var3.id, var3);
        MOB_DREAMT.put(var1.getUuid(), var0.getServer().getOverworld().getTime());
        var3.totalShards = var3.shards.size();
        var1.addStatusEffect(new StatusEffectInstance(WindyEffects.DREAMING, 3600, 0, false, false, true));
        spawnGuards(var2, var3, var4, var0, 1.0F);
        carryIn(var0, var3, var2, var3.center);
    }

    private static void join(ServerPlayerEntity var0, DreamManager.Session var1, ServerWorld var2) {
        BlockPos var3 = DreamArena.ground(var2, var1.center.add(2, 0, 2));
        var2.setBlockState(var3, Blocks.AMETHYST_CLUSTER.getDefaultState());
        var1.shards.add(var3);
        var1.totalShards++;
        spawnGuards(var2, var1, 0, var0, 1.25F);
        carryIn(var0, var1, var2, DreamArena.ground(var2, var1.center.add(1, 0, 0)));
    }

    private static void spawnGuards(ServerWorld var0, DreamManager.Session var1, int var2, ServerPlayerEntity var3, float var4) {
        int[][] var5 = new int[][]{{2, 0, 0}, {3, 1, 0}, {3, 2, 1}};
        int[] var6 = var5[Math.min(var2, 2)];
        int var7 = 0;

        for (int var8 = 0; var8 < 3; var8++) {
            for (int var9 = 0; var9 < var6[var8]; var9++) {
                BlockPos var10 = var1.shards.get(var7++ % var1.shards.size());
                spawnNightmare(var0, var1, var8, DreamArena.ground(var0, var10.add(1 + var9, 0, -1)), null, false, var4, var3);
            }
        }
    }

    static NightmareEntity spawnNightmare(
        ServerWorld var0, DreamManager.Session var1, int var2, BlockPos var3, UUID var4, boolean var5, float var6, ServerPlayerEntity var7
    ) {
        Boolean var8 = SpawnPhaseOverride.push(PhaseFx.phase(var7));

        Object var10;
        try {
            NightmareEntity var9 = DreamEntities.NIGHTMARE.create(var0);
            if (var9 != null) {
                var9.refreshPositionAndAngles(
                    (double)var3.getX() + 0.5, (double)var3.getY(), (double)var3.getZ() + 0.5, var0.getRandom().nextFloat() * 360.0F, 0.0F
                );
                var9.setup(var2, var3, var1.id, var4, var5, var6);
                var0.spawnEntity(var9);
                return var9;
            }

            var10 = null;
        } finally {
            SpawnPhaseOverride.restore(var8);
        }

        return (NightmareEntity)var10;
    }

    private static void sweetDream(ServerPlayerEntity var0, ServerPlayerEntity var1) {
        var1.removeStatusEffect(WindyEffects.DREAMING);
        var1.removeStatusEffect(WindyEffects.LULLED);
        var1.removeStatusEffect(StatusEffects.MINING_FATIGUE);
        var1.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 3600, 0));
        var1.addStatusEffect(new StatusEffectInstance(StatusEffects.LUCK, 3600, 0));
        var0.addExperience(10);
        var1.sendMessage(Text.translatable("message.windyorigins.dream.sweet_woke").formatted(Formatting.LIGHT_PURPLE), true);
        var0.sendMessage(Text.translatable("message.windyorigins.dream.sweet_done").formatted(Formatting.LIGHT_PURPLE), true);
        PhaseFx.sound(var1, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.0F, 1.2F);
        PhaseFx.particles(var1, DREAM_DUST, var1.getX(), var1.getEyeY(), var1.getZ(), 30, 0.4, 0.5, 0.4, 0.0);
    }

    private static void lucid(ServerPlayerEntity var0, ServerPlayerEntity var1, ServerWorld var2) {
        boolean var3 = isWalker(var1);
        DreamManager.Session var4 = new DreamManager.Session(var3 ? DreamManager.Kind.DUEL : DreamManager.Kind.LUCID, freeSlot());
        var4.dreamer = var1.getUuid();
        var4.group = MobGroups.Group.PLAYER;
        long var5 = var1.getUuid().getMostSignificantBits();
        DreamArena.Layout var7 = DreamArena.build(var2, var4.slot, MobGroups.Group.PLAYER, 2, var3 ? 0 : 5, var5);
        var4.center = var7.center;
        if (!var3) {
            ArrayList var8 = new ArrayList<>(var7.spots);
            Collections.shuffle(var8, new Random(var2.getTime()));

            for (int var9 = 0; var9 < 3 && var9 < var8.size(); var9++) {
                BlockPos var10 = (BlockPos)var8.get(var9);
                var2.setBlockState(var10, Blocks.BEACON.getDefaultState());
                var2.setBlockState(var10.down(), Blocks.IRON_BLOCK.getDefaultState());

                for (int var11 = 1; var11 <= 6; var11++) {
                    var2.setBlockState(var10.up(var11), Blocks.LIGHT_BLUE_STAINED_GLASS.getDefaultState());
                }

                var4.anchors.add(var10);
            }
        }

        var4.ends = var2.getTime() + 1800L;
        SESSIONS.put(var4.id, var4);
        var1.removeStatusEffect(WindyEffects.DREAMING);
        var1.removeStatusEffect(WindyEffects.LULLED);
        var1.removeStatusEffect(StatusEffects.MINING_FATIGUE);
        carryIn(var1, var4, var2, var4.center);
        carryIn(var0, var4, var2, DreamArena.ground(var2, var4.center.add(var7.radius - 4, 0, 0)));
        tellRoles(var4, var2.getServer());
    }

    private static void tellRoles(DreamManager.Session var0, MinecraftServer var1) {
        ServerPlayerEntity var2 = var1.getPlayerManager().getPlayer(var0.dreamer);

        for (UUID var4 : var0.walkers) {
            ServerPlayerEntity var5 = var1.getPlayerManager().getPlayer(var4);
            if (var5 != null) {
                var5.sendMessage(
                    Text.translatable(var0.kind == DreamManager.Kind.DUEL ? "message.windyorigins.dream.goal_duel" : "message.windyorigins.dream.goal_walker")
                        .formatted(Formatting.LIGHT_PURPLE),
                    false
                );
            }
        }

        if (var2 != null) {
            var2.sendMessage(
                Text.translatable(var0.kind == DreamManager.Kind.DUEL ? "message.windyorigins.dream.goal_duel" : "message.windyorigins.dream.goal_dreamer")
                    .formatted(Formatting.LIGHT_PURPLE),
                false
            );
        }
    }

    private static void carryIn(ServerPlayerEntity var0, DreamManager.Session var1, ServerWorld var2, BlockPos var3) {
        if (var1.dreamer == null || !var1.dreamer.equals(var0.getUuid())) {
            var1.walkers.add(var0.getUuid());
        }

        BY_PLAYER.put(var0.getUuid(), var1);
        NbtCompound var4 = new NbtCompound();
        var4.put("Inventory", var0.getInventory().writeNbt(new NbtList()));
        var4.putInt("XpLevel", var0.experienceLevel);
        var4.putFloat("XpProgress", var0.experienceProgress);
        var4.putInt("XpTotal", var0.totalExperience);
        var4.putFloat("Health", var0.getHealth());
        var4.putInt("Food", var0.getHungerManager().getFoodLevel());
        var4.putFloat("Saturation", var0.getHungerManager().getSaturationLevel());
        NbtList var5 = new NbtList();

        for (StatusEffectInstance var7 : var0.getStatusEffects()) {
            if (var7.getEffectType() != WindyEffects.DREAMING && var7.getEffectType() != WindyEffects.LULLED) {
                var5.add(var7.writeNbt(new NbtCompound()));
            }
        }

        var4.put("Effects", var5);
        var4.putString("World", var0.getWorld().getRegistryKey().getValue().toString());
        var4.putDouble("X", var0.getX());
        var4.putDouble("Y", var0.getY());
        var4.putDouble("Z", var0.getZ());
        var4.putFloat("Yaw", var0.getYaw());
        ((DreamSnapshotHolder)var0).windyorigins$setDreamSnapshot(var4);
        Boolean var11 = SpawnPhaseOverride.push(PhaseFx.phase(var0));

        try {
            DreamBodyEntity var12 = DreamEntities.BODY.create(var0.getServerWorld());
            if (var12 != null) {
                var12.setOwner(var0.getUuid());
                var12.refreshPositionAndAngles(var0.getX(), var0.getY(), var0.getZ(), var0.getYaw(), 0.0F);
                var0.getServerWorld().spawnEntity(var12);
                DreamBodyEntity.holdChunk(var0.getServerWorld(), var0.getBlockPos());
                var1.bodies.put(var0.getUuid(), var12);
            }
        } finally {
            SpawnPhaseOverride.restore(var11);
        }

        var0.getInventory().clear();
        var0.clearStatusEffects();
        var0.setExperienceLevel(0);
        var0.setExperiencePoints(0);
        var0.setHealth(var0.getMaxHealth());
        var0.getHungerManager().setFoodLevel(17);
        var0.getHungerManager().setSaturationLevel(0.0F);
        var0.extinguish();
        var0.fallDistance = 0.0F;
        var0.teleport(var2, (double)var3.getX() + 0.5, (double)var3.getY(), (double)var3.getZ() + 0.5, var0.getYaw(), var0.getPitch());
        sendState(var0, true);
        PhaseFx.privateSound(var0, WindySounds.DREAM_ENTER, 1.0F, 0.8F);
    }

    public static void wake(ServerPlayerEntity var0, List<ItemStack> var1) {
        BY_PLAYER.remove(var0.getUuid());
        DreamSnapshotHolder var2 = (DreamSnapshotHolder)var0;
        NbtCompound var3 = var2.windyorigins$getDreamSnapshot();
        if (var3 != null) {
            var2.windyorigins$setDreamSnapshot(null);
            ServerWorld var4 = var0.getServer().getWorld(RegistryKey.of(RegistryKeys.WORLD, new Identifier(var3.getString("World"))));
            if (var4 == null) {
                var4 = var0.getServer().getOverworld();
            }

            if (inDreams(var0)) {
                var0.getInventory().dropAll();
                var0.currentScreenHandler.setCursorStack(ItemStack.EMPTY);
            }

            var0.getInventory().clear();
            var0.clearStatusEffects();
            var0.getInventory().readNbt(var3.getList("Inventory", 10));
            var0.setExperienceLevel(var3.getInt("XpLevel"));
            var0.experienceProgress = var3.getFloat("XpProgress");
            var0.totalExperience = var3.getInt("XpTotal");
            var0.setHealth(Math.max(1.0F, var3.getFloat("Health")));
            var0.getHungerManager().setFoodLevel(var3.getInt("Food"));
            var0.getHungerManager().setSaturationLevel(var3.getFloat("Saturation"));
            NbtList var5 = var3.getList("Effects", 10);

            for (int var6 = 0; var6 < var5.size(); var6++) {
                StatusEffectInstance var7 = StatusEffectInstance.fromNbt(var5.getCompound(var6));
                if (var7 != null) {
                    var0.addStatusEffect(var7);
                }
            }

            var0.fallDistance = 0.0F;
            var0.teleport(var4, var3.getDouble("X"), var3.getDouble("Y"), var3.getDouble("Z"), var3.getFloat("Yaw"), var0.getPitch());
            if (var1 != null) {
                for (ItemStack var9 : var1) {
                    if (!var0.getInventory().insertStack(var9.copy())) {
                        var0.dropItem(var9.copy(), false);
                    }
                }
            }

            var0.currentScreenHandler.sendContentUpdates();
            var0.sendAbilitiesUpdate();
            sendState(var0, false);
            PhaseFx.privateSound(var0, WindySounds.DREAM_WAKE, 1.0F, 1.0F);
        }
    }

    private static void sendState(ServerPlayerEntity var0, boolean var1) {
        PacketByteBuf var2 = new PacketByteBuf(Unpooled.buffer());
        var2.writeBoolean(var1);
        ServerPlayNetworking.send(var0, STATE, var2);
    }

    public static void onJoin(ServerPlayerEntity var0) {
        if (((DreamSnapshotHolder)var0).windyorigins$getDreamSnapshot() != null && !BY_PLAYER.containsKey(var0.getUuid())) {
            wake(var0, null);
        }
    }

    public static void onLeave(ServerPlayerEntity var0) {
        DreamManager.Session var1 = BY_PLAYER.get(var0.getUuid());
        if (var1 != null) {
            end(var0.getServer(), var1, null, "disconnect");
        }
    }

    public static void tick(MinecraftServer var0) {
        long var1 = var0.getOverworld().getTime();
        ServerWorld var3 = var0.getWorld(DREAMS);

        for (DreamManager.Session var5 : new ArrayList<>(SESSIONS.values())) {
            if (var5.over) {
                SESSIONS.remove(var5.id);
            } else if (var3 != null && var3.getTime() < var5.ends) {
                tickSession(var0, var3, var5);
            } else {
                end(var0, var5, null, "time");
            }
        }

        for (ServerPlayerEntity var6 : var0.getPlayerManager().getPlayerList()) {
            if (isWalker(var6) && var6.isAlive()) {
                if (inDreams(var6)) {
                    var6.fallDistance = 0.0F;
                    if (var6.age % 20 == 0) {
                        var6.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 40, 0, false, false, false));
                        var6.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, 40, 1, false, false, false));
                    }
                } else if (var6.age % 20 == 0) {
                    tickWaking(var6);
                }
            }
        }
    }

    private static void tickWaking(ServerPlayerEntity var0) {
        int var1 = var0.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.TIME_SINCE_REST));
        boolean var2 = var1 >= 72000;
        if (Powers.has(var0, "phantom_kin") && !var2) {
            for (PhantomEntity var4 : var0.getWorld()
                .getEntitiesByClass(PhantomEntity.class, var0.getBoundingBox().expand(48.0), var1x -> var1x.getTarget() == var0)) {
                var4.setTarget(null);
            }
        }

        if (var2 && var0.age % 100 == 0) {
            var0.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 120, 0, false, false, true));
            var0.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 120, 0, false, false, true));
        }
    }

    private static void tickSession(MinecraftServer var0, ServerWorld var1, DreamManager.Session var2) {
        if (var2.kind == DreamManager.Kind.MOB && var2.mob != null && var1.getTime() % 10L == 0L) {
            ServerWorld var3 = var0.getWorld(var2.mobWorld);
            Entity var4 = var3 == null ? null : var3.getEntity(var2.mob);
            if (var4 == null || !var4.isAlive()) {
                end(var0, var2, null, "faded");
                return;
            }
        }

        if (var2.kind == DreamManager.Kind.MOB) {
            Iterator var9 = var2.shards.iterator();

            while (var9.hasNext()) {
                BlockPos var12 = (BlockPos)var9.next();
                if (var1.getTime() % 4L == 0L) {
                    var1.spawnParticles(
                        ParticleTypes.END_ROD, (double)var12.getX() + 0.5, (double)var12.getY() + 0.6, (double)var12.getZ() + 0.5, 1, 0.2, 0.3, 0.2, 0.01
                    );
                }

                for (UUID var16 : var2.walkers) {
                    ServerPlayerEntity var7 = var0.getPlayerManager().getPlayer(var16);
                    if (var7 != null && var7.getWorld() == var1 && var7.getBlockPos().isWithinDistance(var12, 1.8)) {
                        var9.remove();
                        var1.setBlockState(var12, Blocks.AIR.getDefaultState());
                        // The whole dream is worth one kill: the loot is rolled once and each
                        // shard hands over its share, however many shards the dream holds.
                        if (var2.lootRoll == null) {
                            var2.lootRoll = rollLoot(var0, var2, var7);
                        }

                        var2.collected++;
                        var2.earned.clear();
                        int var17 = Math.max(var2.totalShards, var2.collected);

                        for (ItemStack var19 : var2.lootRoll) {
                            int var20 = var2.collected >= var17 ? var19.getCount() : var19.getCount() * var2.collected / var17;
                            if (var20 > 0) {
                                ItemStack var21 = var19.copy();
                                var21.setCount(var20);
                                var2.earned.add(var21);
                            }
                        }

                        var1.playSound(null, var12, SoundEvents.BLOCK_AMETHYST_CLUSTER_BREAK, SoundCategory.PLAYERS, 1.0F, 1.4F);
                        var1.spawnParticles(
                            ParticleTypes.END_ROD, (double)var12.getX() + 0.5, (double)var12.getY() + 0.5, (double)var12.getZ() + 0.5, 30, 0.3, 0.4, 0.3, 0.1
                        );
                        var7.sendMessage(Text.translatable("message.windyorigins.dream.shard", var2.shards.size()).formatted(Formatting.LIGHT_PURPLE), true);
                        break;
                    }
                }
            }

            if (var2.shards.isEmpty()) {
                end(var0, var2, null, "complete");
            }
        } else {
            ServerPlayerEntity var8 = var0.getPlayerManager().getPlayer(var2.dreamer);
            if (var8 == null) {
                end(var0, var2, null, "disconnect");
            } else {
                Iterator var10 = var2.anchors.iterator();

                while (var10.hasNext()) {
                    BlockPos var5 = (BlockPos)var10.next();
                    if (var8.getWorld() == var1 && var8.getBlockPos().isWithinDistance(var5, 2.0)) {
                        var10.remove();
                        var1.setBlockState(var5, Blocks.AIR.getDefaultState());

                        for (int var6 = 1; var6 <= 6; var6++) {
                            var1.setBlockState(var5.up(var6), Blocks.AIR.getDefaultState());
                        }

                        var2.anchorsTaken++;
                        var1.playSound(null, var5, SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 1.0F, 1.2F);
                        var8.sendMessage(Text.translatable("message.windyorigins.dream.anchor", var2.anchorsTaken).formatted(Formatting.AQUA), true);
                        if (var2.anchorsTaken == 1) {
                            var8.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 1800, 0, false, false, true));
                        }

                        if (var2.anchorsTaken >= 3) {
                            end(var0, var2, var2.dreamer, "anchors");
                            return;
                        }
                    }
                }

                if (var2.anchorsTaken >= 2 && var1.getTime() % 10L == 0L) {
                    for (UUID var13 : var2.walkers) {
                        ServerPlayerEntity var15 = var0.getPlayerManager().getPlayer(var13);
                        if (var15 != null) {
                            var15.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 20, 0, false, false, false));
                        }
                    }
                }
            }
        }
    }

    private static List<ItemStack> rollLoot(MinecraftServer var0, DreamManager.Session var1, ServerPlayerEntity var2) {
        ArrayList var3 = new ArrayList();
        ServerWorld var4 = var0.getWorld(var1.mobWorld);
        if ((var4 == null ? null : var4.getEntity(var1.mob)) instanceof LivingEntity var6) {
            LootTable var7 = var0.getLootManager().getLootTable(var6.getLootTable());
            LootContextParameterSet var8 = new Builder(var4)
                .add(LootContextParameters.THIS_ENTITY, var6)
                .add(LootContextParameters.ORIGIN, var6.getPos())
                .add(LootContextParameters.DAMAGE_SOURCE, var4.getDamageSources().playerAttack(var2))
                .addOptional(LootContextParameters.KILLER_ENTITY, var2)
                .addOptional(LootContextParameters.DIRECT_KILLER_ENTITY, var2)
                .addOptional(LootContextParameters.LAST_DAMAGE_PLAYER, var2)
                .luck(var2.getLuck())
                .build(LootContextTypes.ENTITY);
            var3.addAll(var7.generateLoot(var8));
            return var3;
        } else {
            return var3;
        }
    }

    static void end(MinecraftServer var0, DreamManager.Session var1, UUID var2, String var3) {
        if (!var1.over) {
            var1.over = true;
            SESSIONS.remove(var1.id);

            for (DreamBodyEntity var5 : var1.bodies.values()) {
                var5.discard();
            }

            for (UUID var13 : var1.participants()) {
                ServerPlayerEntity var6 = var0.getPlayerManager().getPlayer(var13);
                if (var6 == null) {
                    BY_PLAYER.remove(var13);
                } else {
                    boolean var7 = var1.walkers.contains(var13);
                    List var8 = var7 && var1.kind == DreamManager.Kind.MOB ? var1.earned : null;
                    if (var7 && var1.kind == DreamManager.Kind.MOB && var1.walkers.size() > 1) {
                        var8 = var13.equals(var1.walkers.get(0)) ? var1.earned : null;
                    }

                    wake(var6, var8);
                    String var9 = var3;
                    if ("killed".equals(var3) && var1.kind != DreamManager.Kind.MOB) {
                        var9 = var2 == null ? "draw" : (var2.equals(var13) ? "won" : "lost");
                    }

                    String var10 = "message.windyorigins.dream.end." + var9;
                    var6.sendMessage(Text.translatable(var10).formatted(Formatting.LIGHT_PURPLE), true);
                    if (var1.kind != DreamManager.Kind.MOB && var2 != null && !var2.equals(var13) && !"disconnect".equals(var3)) {
                        if (var7 && var1.kind == DreamManager.Kind.LUCID) {
                            var6.addStatusEffect(new StatusEffectInstance(WindyEffects.DREAM_SICKNESS, 2400, 0, false, false, true));
                            var6.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 2400, 0));
                        } else {
                            var6.addStatusEffect(new StatusEffectInstance(WindyEffects.NIGHTMARE, 1200, 0, false, false, true));
                            var6.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 1200, 0));
                        }
                    }
                }
            }

            if (var1.kind == DreamManager.Kind.MOB && var1.mob != null) {
                ServerWorld var12 = var0.getWorld(var1.mobWorld);
                if ((var12 == null ? null : var12.getEntity(var1.mob)) instanceof LivingEntity var15) {
                    var15.removeStatusEffect(WindyEffects.DREAMING);
                    Map var16 = NEUTRAL.computeIfAbsent(var1.mob, var0x -> new HashMap<>());

                    for (UUID var18 : var1.walkers) {
                        var16.put(var18, var12.getTime() + 6000L);
                    }
                }
            }
        }
    }

    static void onBodyHit(DreamBodyEntity var0, DamageSource var1) {
        UUID var2 = var0.getOwner();
        DreamManager.Session var3 = var2 == null ? null : BY_PLAYER.get(var2);
        if (var3 == null) {
            var0.discard();
        } else {
            MinecraftServer var4 = var0.getServer();
            if (var3.walkers.contains(var2)) {
                if (var3.kind == DreamManager.Kind.MOB) {
                    var3.earned.clear();
                    var4.execute(() -> end(var4, var3, null, "body"));
                } else {
                    var4.execute(() -> end(var4, var3, var3.dreamer, "body"));
                }
            } else {
                var4.execute(() -> end(var4, var3, null, "body"));
            }
        }
    }

    public static boolean allowDeath(LivingEntity var0, DamageSource var1) {
        if (var0 instanceof ServerPlayerEntity var2) {
            DreamManager.Session var3 = BY_PLAYER.get(var2.getUuid());
            if (var3 == null) {
                return true;
            } else {
                var2.setHealth(var2.getMaxHealth());
                MinecraftServer var4 = var2.getServer();
                if (var3.kind == DreamManager.Kind.MOB) {
                    if (var3.walkers.size() > 1) {
                        var3.walkers.remove(var2.getUuid());
                        var4.execute(() -> wake(var2, null));
                        var2.sendMessage(Text.translatable("message.windyorigins.dream.end.killed").formatted(Formatting.LIGHT_PURPLE), true);
                        DreamBodyEntity var8 = var3.bodies.remove(var2.getUuid());
                        if (var8 != null) {
                            var8.discard();
                        }
                    } else {
                        var3.earned.clear();
                        var4.execute(() -> end(var4, var3, null, "killed"));
                    }

                    return false;
                } else {
                    Entity var5 = var1.getAttacker();
                    UUID var6;
                    if (var3.kind == DreamManager.Kind.DUEL) {
                        var6 = var3.walkers.contains(var2.getUuid()) ? var3.dreamer : var3.walkers.get(0);
                    } else if (var2.getUuid().equals(var3.dreamer)) {
                        var6 = var3.walkers.get(0);
                    } else {
                        var6 = var3.dreamer;
                    }

                    if (var5 != null && var5.getUuid().equals(var2.getUuid())) {
                        var6 = null;
                    }

                    UUID var7 = var6;
                    var4.execute(() -> end(var4, var3, var7, "killed"));
                    return false;
                }
            }
        } else {
            return true;
        }
    }

    private static void blink(ServerPlayerEntity var0) {
        Vec3d var1 = var0.getEyePos();
        Vec3d var2 = var0.getRotationVec(1.0F);
        BlockHitResult var3 = var0.getWorld().raycast(new RaycastContext(var1, var1.add(var2.multiply(8.0)), ShapeType.COLLIDER, FluidHandling.NONE, var0));
        Vec3d var4 = var3.getType() == Type.MISS ? var1.add(var2.multiply(8.0)) : var3.getPos().subtract(var2.multiply(0.6));
        PhaseFx.particles(var0, DREAM_DUST, var0.getX(), var0.getBodyY(0.5), var0.getZ(), 20, 0.3, 0.6, 0.3, 0.0);
        var0.requestTeleport(var4.x, var4.y - 1.0, var4.z);
        PhaseFx.particles(var0, DREAM_DUST, var4.x, var4.y - 0.5, var4.z, 20, 0.3, 0.6, 0.3, 0.0);
        PhaseFx.sound(var0, SoundEvents.ENTITY_ILLUSIONER_MIRROR_MOVE, SoundCategory.PLAYERS, 0.8F, 1.2F);
    }

    private static void sculpt(ServerPlayerEntity var0) {
        Vec3d var1 = var0.getEyePos();
        BlockHitResult var2 = var0.getWorld()
            .raycast(new RaycastContext(var1, var1.add(var0.getRotationVec(1.0F).multiply(12.0)), ShapeType.COLLIDER, FluidHandling.NONE, var0));
        if (var2.getType() != Type.MISS) {
            World var3 = var0.getWorld();
            BlockPos var4 = var2.getBlockPos();
            BlockState var5 = var3.getBlockState(var4);
            if (var0.isSneaking()) {
                for (int var6 = 0; var6 < 3; var6++) {
                    var3.setBlockState(var4.down(var6), Blocks.AIR.getDefaultState());
                }
            } else {
                for (int var7 = 1; var7 <= 3; var7++) {
                    if (var3.getBlockState(var4.up(var7)).isAir()) {
                        var3.setBlockState(var4.up(var7), var5);
                    }
                }
            }

            var3.playSound(null, var4, SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.BLOCKS, 1.0F, 0.6F);
        }
    }

    public static void summon(ServerPlayerEntity var0) {
        DreamManager.Session var1 = BY_PLAYER.get(var0.getUuid());
        if (var1 != null && var0.isSneaking() && var1.walkers.contains(var0.getUuid())) {
            thicken(var0);
        } else if (var1 != null && var1.kind != DreamManager.Kind.MOB && (var1.walkers.contains(var0.getUuid()) || var1.kind == DreamManager.Kind.DUEL)) {
            ServerWorld var2 = var0.getServerWorld();
            long var3 = var2.getTime();
            Long var5 = var1.minionReady.get(var0.getUuid());
            long var6 = (long)var2.getEntitiesByClass(
                    NightmareEntity.class, var0.getBoundingBox().expand(64.0), var1x -> var0.getUuid().equals(var1x.getOwner())
                )
                .size();
            if ((var5 == null || var5 <= var3) && var6 < 2L) {
                var1.minionReady.put(var0.getUuid(), var3 + 300L);
                int var8 = var2.getRandom().nextBoolean() ? 0 : 1;
                BlockPos var9 = var0.getBlockPos().offset(var0.getHorizontalFacing(), 2);
                spawnNightmare(var2, var1, var8, DreamArena.ground(var2, var9), var0.getUuid(), true, 1.0F, var0);
                PhaseFx.sound(var0, SoundEvents.ENTITY_WARDEN_EMERGE, SoundCategory.HOSTILE, 0.6F, 1.5F);
            } else {
                deny(var0, "no_summon");
            }
        } else {
            deny(var0, "no_summon");
        }
    }

    private static void thicken(ServerPlayerEntity var0) {
        ServerWorld var1 = var0.getServerWorld();
        long var2 = var1.getTime();
        Long var4 = DARK_READY.get(var0.getUuid());
        if (var4 != null && var4 > var2) {
            deny(var0, "no_thicken");
        } else {
            DARK_READY.put(var0.getUuid(), var2 + 400L);
            Vec3d var5 = var0.getEyePos();
            Vec3d var6 = var0.getRotationVec(1.0F);
            BlockHitResult var7 = var1.raycast(new RaycastContext(var5, var5.add(var6.multiply(24.0)), ShapeType.COLLIDER, FluidHandling.NONE, var0));
            Vec3d var8 = var7.getType() == Type.MISS ? var5.add(var6.multiply(24.0)) : var7.getPos();
            BlockPos var9 = BlockPos.ofFloored(var8);

            for (BlockPos var11 : BlockPos.iterate(var9.add(-7, -7, -7), var9.add(7, 7, 7))) {
                if (!(var11.getSquaredDistance(var9) > 49.0)) {
                    BlockState var12 = var1.getBlockState(var11);
                    if (var12.getLuminance() > 0
                        && !var12.isOf(Blocks.BEACON)
                        && !var12.isOf(Blocks.AMETHYST_CLUSTER)
                        && !(var12.getHardness(var1, var11) < 0.0F)) {
                        var1.setBlockState(var11, var12.getFluidState().isEmpty() ? Blocks.AIR.getDefaultState() : var12.getFluidState().getBlockState());
                        PhaseFx.particles(
                            var0,
                            ParticleTypes.LARGE_SMOKE,
                            (double)var11.getX() + 0.5,
                            (double)var11.getY() + 0.5,
                            (double)var11.getZ() + 0.5,
                            4,
                            0.2,
                            0.2,
                            0.2,
                            0.01
                        );
                    }
                }
            }

            PhaseFx.particles(var0, ParticleTypes.SQUID_INK, var8.x, var8.y + 0.5, var8.z, 80, 2.8000000000000003, 1.2, 2.8000000000000003, 0.02);
            PhaseFx.particles(var0, ParticleTypes.SCULK_SOUL, var8.x, var8.y + 0.5, var8.z, 20, 1.5, 1.0, 1.5, 0.02);
            PhaseFx.sound(var0, var8.x, var8.y, var8.z, SoundEvents.BLOCK_SCULK_SHRIEKER_SHRIEK, SoundCategory.PLAYERS, 0.7F, 0.6F);

            for (ServerPlayerEntity var14 : var1.getPlayers(
                var2x -> var2x != var0 && var2x.isAlive() && PhaseFx.samePhase(var2x, var0) && var2x.squaredDistanceTo(var8) <= 100.0
            )) {
                var14.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 160, 0, false, false, true), var0);
                var14.addStatusEffect(new StatusEffectInstance(WindyEffects.DREAM_FOG, 160, 0, false, false, true), var0);
            }
        }
    }

    static boolean isPrey(NightmareEntity var0, LivingEntity var1) {
        DreamManager.Session var2 = SESSIONS.get(var0.getSession());
        if (var2 == null || !(var1 instanceof PlayerEntity var3) || var3.isCreative() || var3.isSpectator()) {
            return false;
        }

        if (var0.isMinion()) {
            UUID var4 = var0.getOwner();
            return var4 != null && !var4.equals(var3.getUuid()) ? var2.participants().contains(var3.getUuid()) : false;
        } else {
            return var2.walkers.contains(var3.getUuid());
        }
    }

    static boolean minionMustSpare(NightmareEntity var0, ServerPlayerEntity var1) {
        DreamManager.Session var2 = SESSIONS.get(var0.getSession());
        if (var2 == null) {
            return true;
        } else if (var1.getHealth() <= 4.0F) {
            return true;
        } else {
            for (BlockPos var4 : var2.anchors) {
                if (var1.getBlockPos().isWithinDistance(var4, 3.0)) {
                    return true;
                }
            }

            return false;
        }
    }

    public static boolean neutralTo(Entity var0, Entity var1) {
        Map var2 = NEUTRAL.get(var0.getUuid());
        if (var2 == null) {
            return false;
        } else {
            Long var3 = (Long)var2.get(var1.getUuid());
            return var3 != null && var3 > var0.getWorld().getTime();
        }
    }

    private static void sleepHere(ServerPlayerEntity var0) {
        var0.sleep(var0.getBlockPos());
        var0.getServerWorld().updateSleepingPlayers();
        var0.sendMessage(Text.translatable("message.windyorigins.dream.lay_down").formatted(Formatting.LIGHT_PURPLE), true);
    }

    public static void onHurt(LivingEntity var0, DamageSource var1) {
        boolean var2 = var1.getAttacker() != null || var1.getSource() != null || var1.isIn(DamageTypeTags.IS_EXPLOSION);
        if (var2 && isAsleep(var0) && !var0.getWorld().isClient()) {
            boolean var3 = SESSIONS.values().stream().anyMatch(var1x -> !var1x.over && var0.getUuid().equals(var1x.mob));
            if (var3) {
                SESSIONS.values()
                    .stream()
                    .filter(var1x -> !var1x.over && var0.getUuid().equals(var1x.mob))
                    .findFirst()
                    .ifPresent(var1x -> var0.getServer().execute(() -> end(var0.getServer(), var1x, null, "struck")));
            }

            var0.removeStatusEffect(WindyEffects.DREAMING);
            var0.removeStatusEffect(WindyEffects.LULLED);
            var0.removeStatusEffect(StatusEffects.MINING_FATIGUE);
        }
    }

    public static enum Kind {
        MOB,
        LUCID,
        DUEL;
    }

    public static final class Session {
        final UUID id = UUID.randomUUID();
        final DreamManager.Kind kind;
        final int slot;
        final List<UUID> walkers = new ArrayList<>();
        UUID dreamer;
        UUID mob;
        RegistryKey<World> mobWorld;
        MobGroups.Group group;
        float mobHealth;
        long ends;
        final List<BlockPos> shards = new ArrayList<>();
        final List<BlockPos> anchors = new ArrayList<>();
        int anchorsTaken;
        final List<ItemStack> earned = new ArrayList<>();
        List<ItemStack> lootRoll;
        int totalShards;
        int collected;
        final Map<UUID, DreamBodyEntity> bodies = new HashMap<>();
        final Map<UUID, Long> minionReady = new HashMap<>();
        BlockPos center;
        boolean over;

        Session(DreamManager.Kind var1, int var2) {
            this.kind = var1;
            this.slot = var2;
        }

        List<UUID> participants() {
            ArrayList var1 = new ArrayList<>(this.walkers);
            if (this.dreamer != null) {
                var1.add(this.dreamer);
            }

            return var1;
        }
    }
}
