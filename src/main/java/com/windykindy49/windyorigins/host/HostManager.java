package com.windykindy49.windyorigins.host;

import com.windykindy49.windyorigins.common.MobGroups;
import com.windykindy49.windyorigins.common.PhaseFx;
import com.windykindy49.windyorigins.common.Powers;
import com.windykindy49.windyorigins.common.WindyEffects;
import com.windykindy49.windyorigins.common.WindySounds;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FallingBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.LockableContainerBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.damage.DamageSources;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.UpdateSelectedSlotS2CPacket;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Properties;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;
import org.joml.Vector3f;
import net.minecraft.entity.decoration.ArmorStandEntity;

public final class HostManager {
    public static final String HEAT = "heat";
    public static final String VOLTA = "volta";
    /** Cooling starts only after this long without a request or a hit, and then crawls. */
    private static final int COOL_DELAY = 200;
    private static final int COOL_INTERVAL = 30;
    private static final int HEAT_AFTER_PSYCHOSIS = 70;
    private static final int PSYCHOSIS_TICKS = 300;
    private static final int WARP_TICKS = 100;
    private static final int SIPHON_TICKS = 80;
    private static final int TURRET_TICKS = 240;
    private static final int SHIELD_TICKS = 120;
    /** Volta needs a breath between any two requests, whatever they are. */
    private static final int GLOBAL_COOLDOWN = 30;
    private static final int MAGNET_TICKS = 70;
    /** How far away a dropped item may lie and still be picked up by name, and how long an arm takes to bring it. */
    private static final double PICK_RANGE = 24.0;
    private static final int HAUL_TICKS = 60;
    private static final int DECOY_TICKS = 160;
    /** From the request to the hit: her face strobes for a few ticks, then the shot is there. */
    private static final int BOLT_TICKS = 6;
    private static final int QUIET_TICKS = 3600;
    private static final int QUIET_COOLDOWN = 24000;
    private static final int LISTEN_TICKS = 300;
    private static final int PRANK_MIN = 1200;
    private static final int PRANK_SPREAD = 1800;
    private static final double HACK_RANGE = 28.0;
    private static final double SCAN_RANGE = 32.0;
    private static final DustParticleEffect ARC_CYAN = new DustParticleEffect(new Vector3f(0.27F, 0.94F, 0.9F), 0.8F);
    private static final DustParticleEffect ARC_PINK = new DustParticleEffect(new Vector3f(0.94F, 0.24F, 0.78F), 0.7F);
    private static final Map<UUID, HostManager.State> STATES = new HashMap<>();
    /** One open "what is that called?" per Host; see {@link #askNames}. */
    private static final Map<UUID, HostManager.Ask> ASKS = new HashMap<>();
    private static final int ASK_TICKS = 100;
    private static int askCounter;
    private static final Map<UUID, Long> TURNED = new HashMap<>();
    private static final Map<UUID, UUID> TURNED_OWNER = new HashMap<>();
    private static final Map<UUID, Long> BLINDED = new HashMap<>();
    private static final Map<UUID, Long> DISTRACTED = new HashMap<>();
    private static final Map<UUID, UUID> DISTRACTED_OWNER = new HashMap<>();
    private static final Map<UUID, HostManager.Infection> VIRUS = new HashMap<>();

    private HostManager() {
    }


    public static boolean isHost(Entity var0) {
        return Powers.has(var0, "volta");
    }

    static HostManager.State state(PlayerEntity var0) {
        return STATES.computeIfAbsent(var0.getUuid(), var0x -> new HostManager.State());
    }

    /**
     * Logging out stops whatever Volta was in the middle of, but not her cooldowns: those are kept,
     * or a quick relog would reset every command and the quiet-mode wait.
     */
    public static void forget(ServerPlayerEntity var0) {
        Pilot.forget(var0);
        HostManager.State var1 = STATES.get(var0.getUuid());
        if (var1 != null) {
            var1.work.clear();
            var1.later.clear();
            var1.trail.clear();
            var1.healthTrail.clear();
            var1.trailWorld = null;
            var1.magnetEnds = 0L;
            var1.decoyEnds = 0L;
            var1.siphonEnds = 0L;
            var1.turretEnds = 0L;
            var1.warpEnds = 0L;
            var1.shieldEnds = 0L;
            var1.healthBefore = 0.0F;
            var1.listenUntil = 0L;
        }

        ASKS.remove(var0.getUuid());
    }

    // ------------------------------------------------------------------ other players

    /** Has the Host told Volta to leave other players alone? Kept as a resource, so it survives death and relog and the wheel can show it. */
    public static boolean sparesPlayers(PlayerEntity host) {
        return Powers.resource(host, "volta_spares", 0) > 0;
    }

    /** Another player, while the Host has players switched off: nothing of Volta's may touch them. */
    static boolean spared(ServerPlayerEntity host, Entity other) {
        return other instanceof PlayerEntity && other != host && sparesPlayers(host);
    }

    /**
     * Another player Volta goes for of her own accord, with players switched on: the turret, the chain of an
     * overload, a spreading virus, lifted gravity, a turned mob. Never a spectator, somebody building in
     * creative, or one of the Host's own team; those can still be aimed at by hand.
     */
    static boolean fairGame(ServerPlayerEntity host, Entity other) {
        return other instanceof PlayerEntity player && other != host && !sparesPlayers(host) && !player.isSpectator() && !player.isCreative() && !host.isTeammate(other);
    }

    /** What her area abilities take for an enemy: monsters, whatever is after the Host, and players when they are switched on. */
    static boolean enemy(ServerPlayerEntity host, LivingEntity other) {
        return other instanceof Monster || other instanceof MobEntity mob && mob.getTarget() == host || fairGame(host, other);
    }

    // ------------------------------------------------------------------ names

    private record Ask(int id, long deadline, java.util.function.BiConsumer<List<net.minecraft.util.Identifier>, List<net.minecraft.util.Identifier>> then) {
    }

    /**
     * Asks the Host's own game what the words name. The server has no translations, the client has all of
     * them. The answer comes back in a tick or two; a client that does not answer in five seconds counts as
     * having answered "nothing", so the request still ends.
     */
    static void askNames(
        ServerPlayerEntity host, boolean block, String said, java.util.function.BiConsumer<List<net.minecraft.util.Identifier>, List<net.minecraft.util.Identifier>> then
    ) {
        askNames(host, block ? HostNetworking.NAME_BLOCK : HostNetworking.NAME_ITEM, said, then);
    }

    static void askNames(
        ServerPlayerEntity host, int kind, String said, java.util.function.BiConsumer<List<net.minecraft.util.Identifier>, List<net.minecraft.util.Identifier>> then
    ) {
        int id = ++askCounter;
        ASKS.put(host.getUuid(), new HostManager.Ask(id, (long)host.getServer().getTicks() + (long)ASK_TICKS, then));
        HostNetworking.ask(host, id, kind, said);
    }

    public static void answer(ServerPlayerEntity host, int id, List<net.minecraft.util.Identifier> exact, List<net.minecraft.util.Identifier> partial) {
        HostManager.Ask ask = ASKS.get(host.getUuid());
        if (ask == null || ask.id() != id) {
            return;
        }

        ASKS.remove(host.getUuid());
        if (isHost(host) && host.isAlive() && !isBroken(host)) {
            ask.then().accept(exact, partial);
        }
    }

    /** The open question of this Host, or -1. */
    public static int openQuestion(ServerPlayerEntity host) {
        HostManager.Ask ask = ASKS.get(host.getUuid());
        return ask == null ? -1 : ask.id();
    }

    private static void tickAsks(MinecraftServer server) {
        if (ASKS.isEmpty()) {
            return;
        }

        List<Runnable> late = new ArrayList<>();
        ASKS.entrySet().removeIf(entry -> {
            if ((long)server.getTicks() < entry.getValue().deadline()) {
                return false;
            }

            ServerPlayerEntity host = server.getPlayerManager().getPlayer(entry.getKey());
            if (host != null && isHost(host) && host.isAlive() && !isBroken(host)) {
                late.add(() -> entry.getValue().then().accept(List.of(), List.of()));
            }

            return true;
        });
        late.forEach(Runnable::run);
    }

    /** Before the worlds close: no lid is left standing open. */
    public static void stopping() {
        tickLids(true);
    }

    public static void reset() {
        LIDS.clear();
        Pilot.reset();
        ASKS.clear();
        STATES.clear();
        TURNED.clear();
        TURNED_OWNER.clear();
        BLINDED.clear();
        DISTRACTED.clear();
        DISTRACTED_OWNER.clear();
        VIRUS.clear();
    }

    public static void say(ServerPlayerEntity var0, String var1, int var2, Object... var3) {
        if (isBroken(var0)) {
            return;
        }

        if (!isQuiet(var0) || var1.startsWith("quiet")) {
            if (!var0.hasStatusEffect(WindyEffects.CYBERPSYCHOSIS) || var1.equals("psychosis")) {
                HostNetworking.say(var0, Text.translatable("message.windyorigins.volta." + var1, var3), var2);
            }
        }
    }

    public static int heat(PlayerEntity var0) {
        return Powers.resource(var0, "heat", 0);
    }

    public static void addHeat(ServerPlayerEntity var0, int var1) {
        int var2 = MathHelper.clamp(heat(var0) + var1, 0, 100);
        Powers.setResource(var0, "heat", var2);
        if (var2 >= 100 && !var0.hasStatusEffect(WindyEffects.CYBERPSYCHOSIS)) {
            psychosis(var0);
        } else if (var2 >= 85 && var1 > 0) {
            say(var0, "overheat", 3);
        }
    }

    private static void psychosis(ServerPlayerEntity var0) {
        var0.addStatusEffect(new StatusEffectInstance(WindyEffects.CYBERPSYCHOSIS, 300, 0, false, false, true));
        var0.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 300, 0, false, false, false));
        say(var0, "psychosis", 7);
        Pilot.release(var0, null);
        PhaseFx.sound(var0, SoundEvents.ENTITY_GUARDIAN_ATTACK, SoundCategory.PLAYERS, 1.0F, 1.6F);
        state(var0).work.clear();
        state(var0).later.clear();
        state(var0).magnetEnds = 0L;
        HostNetworking.fx(var0, HostNetworking.FX_STOP, 0, -1, var0.getPos(), 0, 0);
    }

    /** Each point of damage actually taken is this much chance of cracking the chip... */
    public static final float CHIP_BREAK_PER_DAMAGE = 0.025F;
    /** ...up to this much for the heaviest blows. */
    public static final float CHIP_BREAK_MAX = 0.2F;
    /** The repair kit has to be held in use this long. */
    public static final int REPAIR_TICKS = 40;

    public static float chipBreakChance(float taken) {
        return Math.min(CHIP_BREAK_MAX, Math.max(0.0F, taken) * CHIP_BREAK_PER_DAMAGE);
    }

    /** Called before a blow lands, so that afterwards it is known how much of it got through the armour. */
    public static void beforeHurt(LivingEntity victim) {
        if (victim instanceof ServerPlayerEntity host && !victim.getWorld().isClient()) {
            HostManager.State st = STATES.get(host.getUuid());
            if (st != null) {
                st.healthBefore = host.getHealth() + host.getAbsorptionAmount();
            }
        }
    }
    public static final Item REPAIR_KIT = net.minecraft.registry.Registry.register(
        Registries.ITEM, Powers.id("chip_repair_kit"), new ChipRepairItem(new Item.Settings().maxCount(16))
    );
    private static boolean selfHarm;

    public static void init() {
        net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents.modifyEntriesEvent(net.minecraft.item.ItemGroups.TOOLS).register(entries -> entries.add(REPAIR_KIT));
    }

    public static boolean isBroken(Entity var0) {
        return Powers.resource(var0, "chip_broken", 0) > 0;
    }

    public static void onHurt(LivingEntity var0, net.minecraft.entity.damage.DamageSource var1, float var2) {
        if (var0 instanceof ServerPlayerEntity var3 && isHost(var3)) {
            HostManager.State var4 = state(var3);
            var4.lastHurt = var3.getWorld().getTime();
            // What counts is what got through: armour and Protection protect the chip as they protect the head.
            float var5 = var4.healthBefore > 0.0F ? var4.healthBefore - (var3.getHealth() + var3.getAbsorptionAmount()) : var2;
            var4.healthBefore = 0.0F;
            // Any real blow can crack the chip. The chip's own overheating and hunger cannot.
            if (!selfHarm
                && var5 > 0.0F
                && !var1.isOf(net.minecraft.entity.damage.DamageTypes.STARVE)
                && !isBroken(var3)
                && Powers.has(var3, "chip_broken")
                && var3.getRandom().nextFloat() < chipBreakChance(var5)) {
                breakChip(var3);
            }
        }
    }

    private static void breakChip(ServerPlayerEntity var0) {
        HostManager.State var1 = state(var0);
        HostNetworking.say(var0, Text.translatable("message.windyorigins.volta.chip_broken"), HostNetworking.GLITCH);
        Powers.setResource(var0, "chip_broken", 1);
        var1.work.clear();
        var1.later.clear();
        var1.magnetEnds = 0L;
        var1.decoyEnds = 0L;
        var1.siphonEnds = 0L;
        var1.turretEnds = 0L;
        var1.warpEnds = 0L;
        var1.shieldEnds = 0L;
        HostNetworking.fx(var0, HostNetworking.FX_STOP, 2, -1, var0.getPos(), 0, 0);
        HostNetworking.fx(var0, HostNetworking.FX_STAMP, HostNetworking.STAMP_CRACK, -1, var0.getPos(), HostNetworking.CYAN, 16);
        PhaseFx.sound(var0, SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 1.0F, 0.6F);
        PhaseFx.privateSound(var0, SoundEvents.BLOCK_BEACON_DEACTIVATE, 1.0F, 0.5F);
        hostFx(var0, ParticleTypes.ELECTRIC_SPARK, var0.getX(), var0.getEyeY(), var0.getZ(), 30, 0.3, 0.3, 0.3, 0.4);
        var0.sendMessage(Text.translatable("message.windyorigins.volta.chip_broken_hint", REPAIR_KIT.getName()).formatted(Formatting.RED), true);
        // The recipe goes to chat, where it stays and can be scrolled back to.
        var0.sendMessage(Text.translatable("message.windyorigins.volta.chip_recipe", REPAIR_KIT.getName()).formatted(Formatting.GRAY), false);
    }

    /** The repair kit, held and used: the chip comes back, warm, and Volta with it. */
    public static boolean repairChip(ServerPlayerEntity var0) {
        if (!isHost(var0) || !isBroken(var0)) {
            return false;
        }

        Powers.setResource(var0, "chip_broken", 0);
        Powers.setResource(var0, "heat", 50);
        state(var0).lastCommand = var0.getWorld().getTime();
        PhaseFx.sound(var0, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1.0F, 1.6F);
        PhaseFx.privateSound(var0, SoundEvents.BLOCK_ANVIL_USE, 0.5F, 1.8F);
        HostNetworking.fx(var0, HostNetworking.FX_SELF, 0, -1, var0.getPos(), HostNetworking.GREEN, 30);
        HostNetworking.word(var0, HostNetworking.WORD_PATCHED, null, HostNetworking.GREEN);
        HostNetworking.say(var0, Text.translatable("message.windyorigins.volta.chip_fixed"), HostNetworking.SMILE);
        return true;
    }

    static void deadChip(ServerPlayerEntity var0) {
        PhaseFx.privateSound(var0, WindySounds.DENY, 0.6F, 0.5F);
        var0.sendMessage(Text.translatable("message.windyorigins.volta.chip_broken_hint", REPAIR_KIT.getName()).formatted(Formatting.RED), true);
    }

    public static void tick(MinecraftServer var0) {
        tickAsks(var0);

        for (ServerPlayerEntity var2 : var0.getPlayerManager().getPlayerList()) {
            Pilot.tick(var2);
            if (isHost(var2) && var2.isAlive()) {
                if (var2.age % 200 == 20) {
                    // Every Host knows how to mend the chip: the recipe is in the book without being found first.
                    var2.unlockRecipes(new net.minecraft.util.Identifier[]{Powers.id("chip_repair_kit")});
                }

                HostManager.State var3 = state(var2);
                long var4 = var2.getWorld().getTime();
                boolean var6 = var2.hasStatusEffect(WindyEffects.CYBERPSYCHOSIS);
                if (var6) {
                    if (var2.age % 50 == 0) {
                        selfHarm = true;

                        try {
                            var2.damage(var2.getWorld().getDamageSources().magic(), 1.0F);
                        } finally {
                            selfHarm = false;
                        }

                        hostFx(var2, ParticleTypes.ELECTRIC_SPARK, var2.getX(), var2.getEyeY(), var2.getZ(), 12, 0.3, 0.3, 0.3, 0.2);
                    }

                    StatusEffectInstance var7 = var2.getStatusEffect(WindyEffects.CYBERPSYCHOSIS);
                    if (var7 != null && var7.getDuration() <= 1) {
                        Powers.setResource(var2, "heat", HEAT_AFTER_PSYCHOSIS);
                        var3.lastCommand = var4;
                        say(var2, "psychosis_end", 2);
                    }
                } else if (var2.age % COOL_INTERVAL == 0
                    && var4 - var3.lastHurt > (long)COOL_DELAY
                    && var4 - var3.lastCommand > (long)COOL_DELAY
                    && var4 >= var3.warpEnds
                    && !Pilot.active(var2)
                    && !var2.isTouchingWaterOrRain()
                    && heat(var2) > 0) {
                    // The chip sheds heat slowly, and only once it has been left alone for a while.
                    Powers.setResource(var2, "heat", heat(var2) - 1);
                }

                if (!var6) {
                    if (var2.isTouchingWater()) {
                        if (var2.age % 10 == 0) {
                            addHeat(var2, 2);
                            hostFx(var2, ParticleTypes.ELECTRIC_SPARK, var2.getX(), var2.getEyeY(), var2.getZ(), 3, 0.2, 0.2, 0.2, 0.1);
                        }
                    } else if (var2.age % 20 == 0 && var2.isTouchingWaterOrRain()) {
                        addHeat(var2, 2);
                    }
                }

                if (var2.age % 5 == 0
                    && !var6
                    && !var2.getWorld().getEntitiesByClass(LightningEntity.class, var2.getBoundingBox().expand(16.0), var0x -> true).isEmpty()) {
                    addHeat(var2, 100);
                }


                // A block every three ticks: the arm whips from one to the next.
                if (!var3.work.isEmpty() && var2.age % 3 == 0) {
                    var3.work.poll().run();
                    if (var3.work.isEmpty()) {
                        HostNetworking.fx(var2, HostNetworking.FX_STOP, 0, -1, var2.getPos(), 0, 0);
                    }
                }

                if (!var3.later.isEmpty()) {
                    List<HostManager.Later> var8 = new ArrayList<>();
                    var3.later.removeIf(var3x -> {
                        if (var3x.at <= var4) {
                            var8.add(var3x);
                            return true;
                        } else {
                            return false;
                        }
                    });

                    for (HostManager.Later var10 : var8) {
                        var10.task.run();
                    }
                }

                tickMagnet(var2, var3, var4);
                tickHaul(var2, var3, var4);
                tickDecoy(var2, var3, var4);
                tickWarp(var2, var3, var4);
                tickSiphon(var2, var3, var4);
                tickTurret(var2, var3, var4);
                tickShield(var2, var3, var4);
                if (var2.age % 20 == 0) {
                    if (var3.trailWorld != var2.getWorld()) {
                        var3.trail.clear();
                        var3.healthTrail.clear();
                        var3.trailWorld = var2.getWorld();
                    }

                    var3.healthTrail.addLast(var2.getHealth());
                    if (var3.healthTrail.size() > 5) {
                        var3.healthTrail.removeFirst();
                    }

                    var3.trail.addLast(var2.getPos());
                    if (var3.trail.size() > 5) {
                        var3.trail.removeFirst();
                    }
                }

                if (var2.getHealth() < var2.getMaxHealth() * 0.3F && var4 - var3.lastLowHealthLine > 1200L) {
                    var3.lastLowHealthLine = var4;
                    say(var2, "low_health", 3);
                }

                tickPranks(var2, var3, var4);
            }
        }

        tickTurned(var0);
        tickVirus(var0);
        tickLids(false);
    }

    /** Volta's energy is drawn for the Host alone; nobody else sees her sparks. */
    private static <T extends net.minecraft.particle.ParticleEffect> void hostFx(
        Entity viewer, T effect, double x, double y, double z, int count, double dx, double dy, double dz, double speed
    ) {
        if (viewer instanceof ServerPlayerEntity player) {
            PhaseFx.only(player, effect, x, y, z, count, dx, dy, dz, speed);
        }
    }

    private static void later(ServerPlayerEntity var0, int var1, Runnable var2) {
        state(var0).later.add(new HostManager.Later(var0.getWorld().getTime() + (long)var1, var2));
    }

    public static boolean isQuiet(ServerPlayerEntity var0) {
        return state(var0).quietUntil > var0.getWorld().getTime();
    }

    /** From the command wheel: no words, only what is under the crosshair. */
    public static void command(ServerPlayerEntity var0, HostCommand var1, String var2, int var3) {
        int var4 = var0.isSneaking() ? (VoltaChat.FLAG_DOWN | VoltaChat.FLAG_WALL | VoltaChat.FLAG_PULL) : 0;
        command(var0, var1, new VoltaChat.Request(var1, var2 == null ? "" : var2, var3, null, var4, null), false);
    }

    public static void command(ServerPlayerEntity var0, HostCommand var1, VoltaChat.Request var2, boolean var3) {
        if (var1 == HostCommand.PILOT) {
            if (isHost(var0) && var0.isAlive() && !var0.isSpectator()) {
                if (isBroken(var0)) {
                    deadChip(var0);
                } else {
                    Pilot.toggle(var0);
                }
            }

            return;
        }

        if (isHost(var0) && var0.isAlive() && !var0.isSpectator()) {
            HostManager.State var4 = state(var0);
            long var5 = var0.getWorld().getTime();
            if (isBroken(var0)) {
                deadChip(var0);
            } else if (var1.free()) {
                free(var0, var1, var2, var4, var5);
            } else if (var0.hasStatusEffect(WindyEffects.CYBERPSYCHOSIS)) {
                PhaseFx.privateSound(var0, WindySounds.DENY, 0.6F, 0.6F);
            } else if (var0.isTouchingWater()) {
                say(var0, "water", 3);
                addHeat(var0, 8);
            } else {
                Long var7 = var4.ready.get(var1);
                if (var5 - var4.lastCommand < (long)GLOBAL_COOLDOWN && var4.lastCommand > 0L) {
                    PhaseFx.privateSound(var0, WindySounds.DENY, 0.5F, 1.4F);
                } else if (var7 != null && var7 > var5) {
                    say(var0, "cooldown", 2, (var7 - var5 + 19L) / 20L);
                } else if (var1.chatOnly() && !var3) {
                    say(var0, var1 == HostCommand.FIND ? "how_find" : "how_fetch", 6);
                } else {
                    int var8 = run(var0, var1, var2, var3);
                    if (var8 >= 0) {
                        var4.ready.put(var1, var5 + (long)var1.cooldown);
                        var4.lastCommand = var5;
                        addHeat(var0, var1.heat + var8);
                    }
                }
            }
        }
    }

    private static void free(ServerPlayerEntity var0, HostCommand var1, VoltaChat.Request var2, HostManager.State var3, long var4) {
        switch (var1) {
            case QUIET:
                boolean var6 = var3.quietUntil > var4;
                if (var2.has(VoltaChat.FLAG_TALK)) {
                    HostNetworking.say(var0, Text.translatable(var6 ? "message.windyorigins.volta.quiet_off" : "message.windyorigins.volta.quiet_not"), var6 ? 0 : 6);
                    var3.quietUntil = 0L;
                } else if (var6) {
                    HostNetworking.say(var0, Text.translatable("message.windyorigins.volta.quiet_already", (var3.quietUntil - var4 + 19L) / 20L), 2);
                } else if (var3.quietReady > var4) {
                    HostNetworking.say(var0, Text.translatable("message.windyorigins.volta.quiet_refuse", (var3.quietReady - var4 + 1199L) / 1200L), 5);
                } else {
                    var3.quietUntil = var4 + (long)QUIET_TICKS;
                    var3.quietReady = var4 + (long)QUIET_COOLDOWN;
                    HostNetworking.say(var0, Text.translatable("message.windyorigins.volta.quiet_on", QUIET_TICKS / 1200, QUIET_COOLDOWN / 1200), 2);
                }
                break;
            case STOP:
                Pilot.release(var0, null);
                ASKS.remove(var0.getUuid());
                var3.work.clear();
                var3.later.clear();
                var3.magnetEnds = 0L;
                var3.decoyEnds = 0L;
                var3.siphonEnds = 0L;
                var3.turretEnds = 0L;
                var3.warpEnds = 0L;
                HostNetworking.fx(var0, HostNetworking.FX_STOP, 0, -1, var0.getPos(), 0, 0);
                say(var0, "stopped", 0);
                break;
            case STATUS:
                int var7 = heat(var0);
                say(var0, var7 >= 80 ? "status_hot" : (var7 >= 45 ? "status_warm" : "status_cool"), var7 >= 80 ? 3 : (var7 >= 45 ? 2 : 0), var7);
                break;
            case HELP:
                say(var0, "help", 6);
                break;
            case PVP:
                // 1: players are fair game, 0: hands off, anything else (the wheel): the other way round.
                boolean spare = var2.number() == 0 || var2.number() != 1 && !sparesPlayers(var0);
                Powers.setResource(var0, "volta_spares", spare ? 1 : 0);
                if (spare) {
                    // Whatever of hers is on a player right now lets go.
                    VIRUS.entrySet().removeIf(entry -> entry.getValue().owner.equals(var0.getUuid()) && var0.getServer().getPlayerManager().getPlayer(entry.getKey()) != null);
                }

                PhaseFx.privateSound(var0, SoundEvents.BLOCK_LEVER_CLICK, 0.6F, spare ? 0.8F : 1.3F);
                say(var0, spare ? "pvp_off" : "pvp_on", spare ? 0 : 1);
                break;
            default:
                String var8 = var2.topic() == null ? "hello" : var2.topic();
                int var9 = switch (var8) {
                    case "insult" -> 4;
                    case "love" -> 6;
                    case "joke" -> 5;
                    case "thanks" -> 6;
                    case "how" -> heat(var0) >= 70 ? 3 : 0;
                    default -> 0;
                };
                say(var0, "talk." + var8 + "." + var0.getRandom().nextInt(3), var9);
        }
    }

    /** Runs a request. Returns extra heat on success, -1 when nothing was done. */
    private static int run(ServerPlayerEntity var0, HostCommand var1, VoltaChat.Request var2, boolean var3) {
        switch (var1) {
            case OVERLOAD:
            case VIRUS:
            case BLIND:
            case IMPULSE:
            case PUPPET:
            case SIPHON:
            case SWAP:
                LivingEntity var4 = resolveTarget(var0, var2, var3);
                if (var4 != null
                    && (var1 != HostCommand.PUPPET || var4 instanceof HostileEntity && MobGroups.of(var4) != MobGroups.Group.BOSS)
                    && (var1 != HostCommand.SWAP || MobGroups.of(var4) != MobGroups.Group.BOSS)) {
                    return strike(var0, var1, var4, var2) ? 0 : -1;
                }

                if (var4 == null && sparesPlayers(var0) && (var2.player() != null || lookTarget(var0, HACK_RANGE) instanceof PlayerEntity)) {
                    say(var0, "spared", 2);
                    return -1;
                }

                say(var0, var4 == null ? "no_target" : "cant_turn", 2);
                return -1;
            case DECOY:
                return decoy(var0) ? 0 : -1;
            case TIMEWARP:
                return timewarp(var0);
            case TURRET:
                return turret(var0);
            case SHIELD:
                state(var0).shieldEnds = var0.getWorld().getTime() + (long)SHIELD_TICKS;
                HostNetworking.fx(var0, HostNetworking.FX_SELF, 0, -1, var0.getPos(), HostNetworking.BLUE, SHIELD_TICKS);
                PhaseFx.privateSound(var0, SoundEvents.BLOCK_BEACON_ACTIVATE, 0.6F, 1.5F);
                say(var0, "shield", 1);
                cast(var0, 2);
                return 0;
            case GRAVITY:
                return gravity(var0);
            case REWIND:
                return rewind(var0);
            case REPAIR:
                var0.heal(4.0F);
                var0.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 120, 1));
                var0.removeStatusEffect(StatusEffects.POISON);
                var0.removeStatusEffect(StatusEffects.WITHER);
                var0.extinguish();
                hostFx(var0, ParticleTypes.HAPPY_VILLAGER, var0.getX(), var0.getBodyY(0.5), var0.getZ(), 15, 0.4, 0.6, 0.4, 0.0);
                HostNetworking.fx(var0, HostNetworking.FX_SELF, 0, -1, var0.getPos(), HostNetworking.GREEN, 40);
                PhaseFx.sound(var0, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.9F, 1.3F);
                HostNetworking.word(var0, HostNetworking.WORD_PATCHED, null, HostNetworking.GREEN);
                HostNetworking.fx(var0, HostNetworking.FX_STAMP, HostNetworking.STAMP_MEND, -1, var0.getPos(), HostNetworking.GREEN, 34);
                say(var0, "repair", 0);
                cast(var0, 2);
                return 0;
            case BLINK:
                Vec3d var5 = var0.getPos();
                if (!blink(var0)) {
                    return -1;
                }

                HostNetworking.fx(var0, HostNetworking.FX_PULSE, 1, -1, var5, HostNetworking.CYAN, 10);
                HostNetworking.fx(var0, HostNetworking.FX_PULSE, 0, -1, var0.getPos(), HostNetworking.PINK, 10);
                HostNetworking.fx(var0, HostNetworking.FX_SELF, 0, -1, var0.getPos(), HostNetworking.CYAN, 8);
                HostNetworking.fx(var0, HostNetworking.FX_RUSH, 0, -1, var0.getPos(), HostNetworking.CYAN, 6);
                return 0;
            case SCAN:
                List<LivingEntity> var6 = var0.getWorld()
                    .getEntitiesByClass(
                        LivingEntity.class,
                        var0.getBoundingBox().expand(SCAN_RANGE),
                        var1x -> var1x != var0
                                && (var1x instanceof Monster || var1x instanceof PlayerEntity)
                                && var1x.isAlive()
                                && !var1x.isSpectator()
                                && PhaseFx.samePhase(var1x, var0)
                    );
                HostNetworking.marks(var0, var6.stream().mapToInt(Entity::getId).toArray(), 240);
                HostNetworking.fx(var0, HostNetworking.FX_PULSE, 0, -1, var0.getPos(), HostNetworking.CYAN, 30);
                HostNetworking.word(var0, HostNetworking.WORD_PING, null, HostNetworking.CYAN);
                var6.sort(java.util.Comparator.comparingDouble(var1x -> var1x.squaredDistanceTo(var0)));

                for (int var20 = 0; var20 < Math.min(8, var6.size()); var20++) {
                    LivingEntity var21 = var6.get(var20);
                    HostNetworking.fx(var0, HostNetworking.FX_TENDRIL, HostNetworking.SLOT_TRACE + var20, var21.getId(), chest(var21), var21 instanceof PlayerEntity ? HostNetworking.PINK : HostNetworking.CYAN, 22);
                }

                PhaseFx.privateSound(var0, SoundEvents.BLOCK_BEACON_ACTIVATE, 0.5F, 1.8F);
                if (var6.isEmpty()) {
                    say(var0, "detect_none", 0);
                } else {
                    say(var0, "detect", 1, var6.size());
                }

                cast(var0, 3);
                return 0;
            case MAGNET:
                return pick(var0, var2);
            case TOGGLE:
                return toggle(var0) ? 0 : -1;
            case LIGHT:
                return light(var0) ? 0 : -1;
            case DIG:
                return VoltaChat.thing(var2.subject()).isBlank() ? dig(var0, var2.has(VoltaChat.FLAG_DOWN), var2.number()) : mine(var0, var1, var2);
            case BUILD:
                return build(var0, !var2.has(VoltaChat.FLAG_WALL), var2.number());
            case HARVEST:
                return VoltaChat.thing(var2.subject()).isBlank() ? harvest(var0) : mine(var0, var1, var2);
            case FIND:
                if (var2.player() != null) {
                    ServerPlayerEntity var7 = var0.getServer().getPlayerManager().getPlayer(var2.player());
                    if (var7 != null) {
                        findPlayer(var0, var7);
                        return SEARCH_PLAYER - HostCommand.FIND.heat;
                    }
                }

                return find(var0, var2.subject());
            case FETCH:
                return fetch(var0, var2.subject()) ? 0 : -1;
            default:
                return -1;
        }
    }

    // ------------------------------------------------------------------ targeting

    private static boolean visible(ServerPlayerEntity var0, Entity var1) {
        return var1 != var0 && var1.isAlive() && !var1.isSpectator() && !spared(var0, var1) && PhaseFx.samePhase(var1, var0) && var0.canSee(var1);
    }

    /**
     * Who a request is about: a named player, a named kind of creature ("ударь зомби"), whatever is
     * under the crosshair, or — when spoken — the nearest threat in front of the Host.
     */
    private static LivingEntity resolveTarget(ServerPlayerEntity var0, VoltaChat.Request var1, boolean var2) {
        if (var1 != null && var1.player() != null) {
            ServerPlayerEntity var3 = var0.getServer().getPlayerManager().getPlayer(var1.player());
            return var3 != null && var3.getWorld() == var0.getWorld() && var3.squaredDistanceTo(var0) <= HACK_RANGE * HACK_RANGE && visible(var0, var3) ? var3 : null;
        }

        if (var1 != null && !var1.subject().isBlank()) {
            String var4 = VoltaChat.canonical(var1.subject());
            EntityType<?> var5 = entityFor(var4 != null ? var4 : var1.subject().trim().replace(' ', '_'));
            if (var5 != null) {
                LivingEntity var6 = nearest(var0, var2x -> var2x.getType() == var5, false);
                if (var6 != null) {
                    return var6;
                }
            }
        }

        LivingEntity var7 = lookTarget(var0, HACK_RANGE);
        if (var7 != null && !spared(var0, var7)) {
            return var7;
        }

        // A little aim assist: the closest thing to the crosshair within a few degrees.
        Vec3d var8 = var0.getEyePos();
        Vec3d var9 = var0.getRotationVec(1.0F);
        LivingEntity var10 = null;
        double var11 = 0.985;

        for (LivingEntity var14 : var0.getWorld()
            .getEntitiesByClass(LivingEntity.class, var0.getBoundingBox().expand(HACK_RANGE), var1x -> visible(var0, var1x) && !(var1x instanceof ArmorStandEntity))) {
            double var15 = var14.getBoundingBox().getCenter().subtract(var8).normalize().dotProduct(var9);
            if (var15 > var11) {
                var11 = var15;
                var10 = var14;
            }
        }

        if (var10 != null || !var2) {
            return var10;
        }

        return nearest(var0, var2x -> (var2x instanceof Monster || fairGame(var0, var2x)) && var2x.getBoundingBox().getCenter().subtract(var8).normalize().dotProduct(var9) > 0.2, true);
    }

    private static LivingEntity nearest(ServerPlayerEntity var0, java.util.function.Predicate<LivingEntity> var1, boolean var2) {
        LivingEntity var3 = null;
        double var4 = (var2 ? 16.0 : HACK_RANGE) * (var2 ? 16.0 : HACK_RANGE);

        for (LivingEntity var7 : var0.getWorld()
            .getEntitiesByClass(LivingEntity.class, var0.getBoundingBox().expand(HACK_RANGE), var2x -> visible(var0, var2x) && var1.test(var2x))) {
            double var8 = var7.squaredDistanceTo(var0);
            if (var8 < var4) {
                var4 = var8;
                var3 = var7;
            }
        }

        return var3;
    }

    // ------------------------------------------------------------------ combat requests

    private static Vec3d chest(Entity var0) {
        return var0.getPos().add(0.0, (double)var0.getHeight() * 0.6, 0.0);
    }

    /** Volta's face flashes, a bolt leaves the screen, and the effect lands as the bolt does. */
    private static boolean strike(ServerPlayerEntity var0, HostCommand var1, LivingEntity var2, VoltaChat.Request var3) {
        int var4 = var1 == HostCommand.VIRUS || var1 == HostCommand.PUPPET ? HostNetworking.PINK : HostNetworking.CYAN;
        boolean yank = var3 != null && var3.has(VoltaChat.FLAG_PULL);
        if (var1 == HostCommand.SWAP) {
            // Two arms, one from each side of the screen, and the trade on the tick the hit would land.
            HostNetworking.fx(var0, HostNetworking.FX_SWAP, BOLT_TICKS, var2.getId(), chest(var2), HostNetworking.PINK, BOLT_TICKS + 8);
        } else if (var1 == HostCommand.IMPULSE) {
            // A fist from under the picture: it takes hold first and throws after.
            HostNetworking.fx(var0, HostNetworking.FX_TENDRIL, yank ? HostNetworking.SLOT_YANK : HostNetworking.SLOT_SHOVE, var2.getId(), chest(var2), HostNetworking.CYAN, yank ? 14 : 10);
        } else if (var1 == HostCommand.VIRUS) {
            // A needle on a thin arm: in on the second tick, the plunger on the sixth.
            HostNetworking.fx(var0, HostNetworking.FX_TENDRIL, HostNetworking.SLOT_INJECT, var2.getId(), chest(var2), HostNetworking.PINK, 12);
        } else if (var1 == HostCommand.BLIND) {
            // An arm from the left claps a blindfold over its eyes.
            HostNetworking.fx(var0, HostNetworking.FX_TENDRIL, HostNetworking.SLOT_SLAP, var2.getId(), var2.getEyePos(), HostNetworking.CYAN, 11);
        } else if (var1 == HostCommand.PUPPET) {
            // The strings come down first; by the time they are taut it is hers.
            HostNetworking.fx(var0, HostNetworking.FX_STRING, 3, var2.getId(), chest(var2), HostNetworking.PINK, 300 + BOLT_TICKS);
        } else if (var1 == HostCommand.SIPHON) {
            // The drinking arm latches on straight away and starts to pull when the hold is good.
            HostNetworking.fx(var0, HostNetworking.FX_TENDRIL, HostNetworking.SLOT_DRAIN, var2.getId(), chest(var2), HostNetworking.GREEN, SIPHON_TICKS + BOLT_TICKS);
        } else {
            HostNetworking.fx(var0, HostNetworking.FX_BOLT, 0, var2.getId(), chest(var2), var4, BOLT_TICKS);
        }

        PhaseFx.sound(var0, SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.PLAYERS, 0.6F, 1.9F);
        cast(var0, 3);
        boolean var5 = var3 != null && var3.has(VoltaChat.FLAG_PULL);
        later(var0, BOLT_TICKS, () -> {
            if (!var2.isAlive() || var2.getWorld() != var0.getWorld() || !var0.isAlive()) {
                return;
            }

            DamageSources var3x = var0.getWorld().getDamageSources();
            Vec3d var4x = chest(var2);
            PhaseFx.sound(var0, var4x.x, var4x.y, var4x.z, SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.PLAYERS, 0.7F, 1.7F);
            hostFx(var0, ParticleTypes.ELECTRIC_SPARK, var4x.x, var4x.y, var4x.z, 24, 0.3, 0.4, 0.3, 0.6);
            hostFx(var0, var1 == HostCommand.VIRUS || var1 == HostCommand.PUPPET ? ARC_PINK : ARC_CYAN, var4x.x, var4x.y, var4x.z, 16, 0.4, 0.5, 0.4, 0.0);
            switch (var1) {
                case OVERLOAD:
                    var2.damage(var3x.indirectMagic(var0, var0), 8.0F);
                    var2.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 1), var0);
                    int var5x = 0;

                    for (LivingEntity var7 : var0.getWorld()
                        .getEntitiesByClass(
                            LivingEntity.class,
                            var2.getBoundingBox().expand(5.0),
                            var2x -> var2x != var2
                                    && var2x != var0
                                    && var2x.isAlive()
                                    && PhaseFx.samePhase(var2x, var0)
                                    && enemy(var0, var2x)
                        )) {
                        if (var5x++ >= 2) {
                            break;
                        }

                        arcBetween(var0, var4x, chest(var7));
                        var7.damage(var3x.indirectMagic(var0, var0), 4.0F);
                    }
                    break;
                case VIRUS:
                    infect(var0, var2, 3);
                    HostNetworking.word(var0, HostNetworking.WORD_INFECTED, var4x, HostNetworking.PINK);
                    break;
                case BLIND:
                    HostNetworking.word(var0, HostNetworking.WORD_LIGHTS_OUT, var4x, 0xFFFFFF);
                    HostNetworking.fx(var0, HostNetworking.FX_TAG, HostNetworking.TAG_BLINDFOLD, var2.getId(), var4x, HostNetworking.CYAN, 120);
                    var2.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 120, 0), var0);
                    if (var2 instanceof MobEntity var8) {
                        var8.setTarget(null);
                        var8.getNavigation().stop();
                        BLINDED.put(var8.getUuid(), var0.getWorld().getTime() + 120L);
                    } else if (var2 instanceof ServerPlayerEntity var9) {
                        var9.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 100, 0), var0);
                        HostNetworking.scanned(var9);
                    }
                    break;
                case IMPULSE:
                    Vec3d var10 = var2.getPos().subtract(var0.getPos());
                    Vec3d var11 = new Vec3d(var10.x, 0.0, var10.z).normalize();
                    if (var5) {
                        double var12 = Math.min(1.8, 0.4 + var10.horizontalLength() * 0.12);
                        var2.setVelocity(-var11.x * var12, 0.42, -var11.z * var12);
                    } else {
                        var2.setVelocity(var11.x * 1.7, 0.75, var11.z * 1.7);
                    }

                    var2.velocityModified = true;
                    break;
                case SIPHON:
                    HostManager.State drain = state(var0);
                    drain.siphon = var2.getUuid();
                    drain.siphonEnds = var0.getWorld().getTime() + (long)SIPHON_TICKS;
                    HostNetworking.word(var0, HostNetworking.WORD_SLURP, var4x, HostNetworking.GREEN);
                    say(var0, "siphon", 1);
                    break;
                case SWAP:
                    Vec3d mine = var0.getPos();
                    Vec3d theirs = var2.getPos();
                    HostNetworking.fx(var0, HostNetworking.FX_PULSE, 1, -1, mine, HostNetworking.PINK, 10);
                    var2.requestTeleport(mine.x, mine.y, mine.z);
                    var0.requestTeleport(theirs.x, theirs.y, theirs.z);
                    var2.fallDistance = 0.0F;
                    var0.fallDistance = 0.0F;
                    if (var2 instanceof MobEntity swapped) {
                        swapped.getNavigation().stop();
                    }

                    HostNetworking.fx(var0, HostNetworking.FX_PULSE, 0, -1, theirs, HostNetworking.CYAN, 10);
                    PhaseFx.sound(var0, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.7F, 1.5F);
                    say(var0, "swap", 5);
                    break;
                case PUPPET:
                    TURNED.put(var2.getUuid(), var0.getWorld().getTime() + 300L);
                    TURNED_OWNER.put(var2.getUuid(), var0.getUuid());
                    ((MobEntity)var2).setTarget(null);
                    HostNetworking.word(var0, HostNetworking.WORD_MINE, var4x, HostNetworking.PINK);
                    say(var0, "puppet", 1);
                    break;
                default:
            }

            if (var2 instanceof ServerPlayerEntity var14 && var1 != HostCommand.BLIND) {
                HostNetworking.scanned(var14);
            }
        });
        return true;
    }

    private static void arcBetween(ServerPlayerEntity var0, Vec3d var1, Vec3d var2) {
        HostNetworking.arc(var0, var1, var2, HostNetworking.CYAN, 7);
    }

    private static void infect(ServerPlayerEntity var0, LivingEntity var1, int var2) {
        long var3 = var1.getWorld().getTime();
        VIRUS.put(var1.getUuid(), new HostManager.Infection(var0.getUuid(), var1.getWorld().getRegistryKey(), var3 + 160L, var3 + 60L, var2));
        HostNetworking.fx(var0, HostNetworking.FX_TAG, HostNetworking.TAG_VIRUS, var1.getId(), chest(var1), HostNetworking.PINK, 160);
    }

    /** The virus gnaws at its carrier and, once ripe or orphaned, leaps to the next creature. */
    private static void tickVirus(MinecraftServer var0) {
        if (VIRUS.isEmpty() || var0.getTicks() % 10 != 0) {
            return;
        }

        List<Runnable> var1 = new ArrayList<>();
        java.util.Iterator<Entry<UUID, HostManager.Infection>> var2 = VIRUS.entrySet().iterator();

        while (var2.hasNext()) {
            Entry<UUID, HostManager.Infection> var3 = var2.next();
            HostManager.Infection var4 = var3.getValue();
            ServerWorld var5 = var0.getWorld(var4.world);
            ServerPlayerEntity var6 = var0.getPlayerManager().getPlayer(var4.owner);
            Entity var7 = var5 == null ? null : var5.getEntity(var3.getKey());
            if (var5 == null || var6 == null || var6.getWorld() != var5) {
                var2.remove();
                continue;
            }

            long var8 = var5.getTime();
            boolean var10 = var7 instanceof LivingEntity var11 && var11.isAlive();
            if (var10) {
                LivingEntity var12 = (LivingEntity)var7;
                hostFx(var6, ARC_PINK, var12.getX(), var12.getBodyY(0.6), var12.getZ(), 3, 0.3, 0.4, 0.3, 0.0);
                if (var0.getTicks() % 20 == 0) {
                    var12.damage(var5.getDamageSources().indirectMagic(var6, var6), 1.5F);
                    var12.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 30, 0), var6);
                }
            }

            boolean var13 = !var10 || var8 >= var4.ends;
            if ((var13 || var8 >= var4.jumpAt) && var4.jumps > 0 && var7 != null) {
                LivingEntity var14 = null;
                double var15 = 36.0;

                for (LivingEntity var18 : var5.getEntitiesByClass(
                    LivingEntity.class,
                    var7.getBoundingBox().expand(6.0),
                    var3x -> var3x != var7
                            && var3x != var6
                            && var3x.isAlive()
                            && (var3x instanceof Monster || fairGame(var6, var3x))
                            && !VIRUS.containsKey(var3x.getUuid())
                            && PhaseFx.samePhase(var3x, var6)
                )) {
                    double var19 = var18.squaredDistanceTo(var7);
                    if (var19 < var15) {
                        var15 = var19;
                        var14 = var18;
                    }
                }

                if (var14 != null) {
                    LivingEntity var21 = var14;
                    int var22 = var4.jumps - 1;
                    var4.jumps = 0;
                    var1.add(() -> {
                        arcBetween(var6, chest(var7), chest(var21));
                        infect(var6, var21, var22);
                    });
                } else if (!var13) {
                    var4.jumpAt = var8 + 20L;
                }
            }

            if (var13) {
                var2.remove();
            }
        }

        var1.forEach(Runnable::run);
    }

    // ------------------------------------------------------------------ time, turret, shield, gravity, rewind

    /** Everything around the Host crawls for a few seconds: creatures, and whatever is flying at her. */
    private static int timewarp(ServerPlayerEntity host) {
        HostManager.State st = state(host);
        st.warpEnds = host.getWorld().getTime() + (long)WARP_TICKS;
        HostNetworking.fx(host, HostNetworking.FX_PULSE, 0, -1, host.getPos(), HostNetworking.GOLD, 16);
        HostNetworking.fx(host, HostNetworking.FX_SELF, 0, -1, host.getPos(), HostNetworking.GOLD, WARP_TICKS);
        HostNetworking.word(host, HostNetworking.WORD_SLOW, null, HostNetworking.GOLD);
        HostNetworking.fx(host, HostNetworking.FX_STAMP, HostNetworking.STAMP_CLOCK, -1, host.getPos(), HostNetworking.GOLD, WARP_TICKS);
        PhaseFx.sound(host, SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 0.9F, 0.6F);
        say(host, "timewarp", 6);
        cast(host, 6);
        return 0;
    }

    private static void tickWarp(ServerPlayerEntity host, HostManager.State st, long now) {
        if (st.warpEnds <= 0L) {
            return;
        }

        if (now >= st.warpEnds) {
            st.warpEnds = 0L;
            say(host, "timewarp_end", 2);
            return;
        }

        Box box = host.getBoundingBox().expand(10.0);
        if (host.age % 5 == 0) {
            for (LivingEntity e : host.getWorld()
                .getEntitiesByClass(LivingEntity.class, box, x -> x != host && x.isAlive() && !spared(host, x) && PhaseFx.samePhase(x, host) && MobGroups.of(x) != MobGroups.Group.BOSS)) {
                e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 12, 4, false, false, true), host);
                e.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 12, 2, false, false, false), host);
            }
        }

        for (net.minecraft.entity.projectile.ProjectileEntity shot : host.getWorld()
            .getEntitiesByClass(net.minecraft.entity.projectile.ProjectileEntity.class, box, x -> x.getOwner() != host && PhaseFx.samePhase(x, host))) {
            Vec3d v = shot.getVelocity();
            if (v.lengthSquared() > 0.02) {
                shot.setVelocity(v.multiply(0.8));
                shot.velocityModified = true;
            }
        }
    }

    /** While the tether holds, the target's life runs down it into the Host. */
    private static void tickSiphon(ServerPlayerEntity host, HostManager.State st, long now) {
        if (st.siphonEnds <= 0L) {
            return;
        }

        Entity found = st.siphon == null ? null : host.getWorld().getEntity(st.siphon);
        if (now >= st.siphonEnds || !(found instanceof LivingEntity target) || !target.isAlive() || spared(host, target) || target.squaredDistanceTo(host) > 24.0 * 24.0 || !host.canSee(target)) {
            st.siphonEnds = 0L;
            st.siphon = null;
            HostNetworking.fx(host, HostNetworking.FX_STOP, 0, -1, host.getPos(), 0, 0);
            return;
        }

        if ((st.siphonEnds - now) % 10L == 0L) {
            if (target.damage(host.getWorld().getDamageSources().indirectMagic(host, host), 1.5F)) {
                host.heal(1.0F);
            }
        }
    }

    private static int turret(ServerPlayerEntity host) {
        Vec3d eye = host.getEyePos();
        Vec3d look = host.getRotationVec(1.0F);
        BlockHitResult hit = host.getWorld().raycast(new RaycastContext(eye, eye.add(look.multiply(16.0)), ShapeType.COLLIDER, FluidHandling.NONE, host));
        Vec3d pos = hit.getType() == Type.MISS ? eye.add(look.multiply(8.0)) : hit.getPos().subtract(look.multiply(0.4)).add(0.0, 1.2, 0.0);
        HostManager.State st = state(host);
        st.turret = pos;
        st.turretEnds = host.getWorld().getTime() + (long)TURRET_TICKS;
        HostNetworking.fx(host, HostNetworking.FX_TENDRIL, HostNetworking.SLOT_PLANT, -1, pos, HostNetworking.CYAN, 9);
        HostNetworking.fx(host, HostNetworking.FX_TENDRIL, HostNetworking.SLOT_CABLE, -1, pos, HostNetworking.CYAN, TURRET_TICKS);
        HostNetworking.fx(host, HostNetworking.FX_NODE, 0, -1, pos, HostNetworking.CYAN, TURRET_TICKS);
        HostNetworking.word(host, HostNetworking.WORD_ONLINE, pos, HostNetworking.CYAN);
        PhaseFx.privateSound(host, SoundEvents.BLOCK_BEACON_POWER_SELECT, 0.7F, 1.9F);
        say(host, "turret", 1);
        cast(host, 3);
        return 0;
    }

    /** The node she leaves behind picks off whatever hostile thing comes nearest. */
    private static void tickTurret(ServerPlayerEntity host, HostManager.State st, long now) {
        if (st.turretEnds <= 0L) {
            return;
        }

        if (now >= st.turretEnds || st.turret == null) {
            st.turretEnds = 0L;
            return;
        }

        if ((st.turretEnds - now) % 30L != 0L) {
            return;
        }

        Vec3d at = st.turret;
        LivingEntity best = null;
        double bestDistance = 64.0;

        for (LivingEntity e : host.getWorld()
            .getEntitiesByClass(
                LivingEntity.class,
                new Box(at, at).expand(8.0),
                x -> x != host
                        && x.isAlive()
                        && PhaseFx.samePhase(x, host)
                        && enemy(host, x)
                        && !TURNED.containsKey(x.getUuid())
            )) {
            double d = chest(e).squaredDistanceTo(at);
            if (d < bestDistance
                && host.getWorld().raycast(new RaycastContext(at, chest(e), ShapeType.COLLIDER, FluidHandling.NONE, host)).getType() == Type.MISS) {
                bestDistance = d;
                best = e;
            }
        }

        if (best != null) {
            HostNetworking.arc(host, at, chest(best), HostNetworking.CYAN, 6);
            // Each shot is fed down the cable from the screen.
            HostNetworking.fx(host, HostNetworking.FX_TENDRIL, HostNetworking.SLOT_CABLE, -1, at, HostNetworking.CYAN, (int)Math.max(2L, st.turretEnds - now));
            best.damage(host.getWorld().getDamageSources().indirectMagic(host, host), 3.0F);
            PhaseFx.sound(host, at.x, at.y, at.z, SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.PLAYERS, 0.3F, 2.0F);
        }
    }

    /**
     * While the barrier stands, whatever is shot or thrown at the Host is plucked out of the air and crushed:
     * arrows, tridents, fireballs, potions. That does not use the barrier up; it still waits for its one blow.
     */
    private static void tickShield(ServerPlayerEntity host, HostManager.State st, long now) {
        if (st.shieldEnds <= now) {
            return;
        }

        Box body = host.getBoundingBox().expand(0.75);
        boolean caught = false;

        for (net.minecraft.entity.projectile.ProjectileEntity shot : host.getWorld()
            .getEntitiesByClass(net.minecraft.entity.projectile.ProjectileEntity.class, host.getBoundingBox().expand(10.0), x -> x.isAlive() && comingAt(host, x, body))) {
            Vec3d at = shot.getPos();
            HostNetworking.fx(host, HostNetworking.FX_TENDRIL, HostNetworking.SLOT_POKE, -1, at, HostNetworking.BLUE, 6);
            hostFx(host, ARC_CYAN, at.x, at.y, at.z, 5, 0.12, 0.12, 0.12, 0.0);
            PhaseFx.sound(host, at.x, at.y, at.z, SoundEvents.ENTITY_ITEM_BREAK, SoundCategory.PLAYERS, 0.5F, 1.8F);
            shot.discard();
            caught = true;
        }

        if (caught) {
            st.shieldCaught++;
            if (now - st.catchNotice > 60L || st.catchNotice == 0L) {
                // Once per volley, not once per arrow.
                HostNetworking.word(host, HostNetworking.WORD_BLOCK, null, HostNetworking.BLUE);
                say(host, "shield_catch", 3);
            }

            st.catchNotice = now;
        }
    }

    /** Is this thing in the air and on its way into the Host within the next few ticks? */
    private static boolean comingAt(ServerPlayerEntity host, net.minecraft.entity.projectile.ProjectileEntity shot, Box body) {
        if (shot instanceof net.minecraft.entity.projectile.FishingBobberEntity || !PhaseFx.samePhase(shot, host)) {
            return false;
        }

        Entity owner = shot.getOwner();
        if (owner == host || owner != null && host.isTeammate(owner)) {
            return false;
        }

        Vec3d speed = shot.getVelocity();
        double fast = speed.length();
        if (fast < 0.05) {
            return false;
        }

        if (shot instanceof net.minecraft.entity.projectile.PersistentProjectileEntity) {
            // An arrow stuck in a wall keeps the speed it arrived with; it is going nowhere.
            net.minecraft.nbt.NbtCompound saved = shot.writeNbt(new net.minecraft.nbt.NbtCompound());
            if (saved.getBoolean("inGround")) {
                return false;
            }
        }

        Vec3d from = shot.getPos();
        return body.contains(from) || body.raycast(from, from.add(speed.multiply(Math.max(3.0, 2.5 / fast)))).isPresent();
    }

    /** The barrier swallows the next blow whole, then bursts in the attacker's face. */
    public static boolean absorb(LivingEntity victim, net.minecraft.entity.damage.DamageSource source) {
        if (victim.getWorld().isClient() || !(victim instanceof ServerPlayerEntity host)) {
            return false;
        }

        HostManager.State st = STATES.get(host.getUuid());
        if (st == null || st.shieldEnds <= host.getWorld().getTime()) {
            return false;
        }

        st.shieldEnds = 0L;
        HostNetworking.fx(host, HostNetworking.FX_STOP, 1, -1, host.getPos(), 0, 0);
        HostNetworking.fx(host, HostNetworking.FX_PULSE, 0, -1, host.getPos(), HostNetworking.BLUE, 12);
        HostNetworking.word(host, HostNetworking.WORD_BLOCK, null, HostNetworking.BLUE);
        HostNetworking.fx(host, HostNetworking.FX_STAMP, HostNetworking.STAMP_PALM, -1, host.getPos(), HostNetworking.BLUE, 8);
        PhaseFx.sound(host, SoundEvents.ITEM_SHIELD_BLOCK, SoundCategory.PLAYERS, 1.0F, 1.4F);
        if (source.getAttacker() instanceof LivingEntity attacker && attacker != host) {
            attacker.takeKnockback(1.2, host.getX() - attacker.getX(), host.getZ() - attacker.getZ());
            attacker.velocityModified = true;
        }

        say(host, "shield_break", 5);
        return true;
    }

    /** Every enemy close by is lifted off the ground, hangs helpless, and is thrown back down. */
    private static int gravity(ServerPlayerEntity host) {
        List<LivingEntity> caught = host.getWorld()
            .getEntitiesByClass(
                LivingEntity.class,
                host.getBoundingBox().expand(7.0),
                x -> x != host
                        && x.isAlive()
                        && PhaseFx.samePhase(x, host)
                        && MobGroups.of(x) != MobGroups.Group.BOSS
                        && enemy(host, x)
            );
        if (caught.isEmpty()) {
            say(host, "no_target", 2);
            return -1;
        }

        HostNetworking.fx(host, HostNetworking.FX_PULSE, 0, -1, host.getPos(), HostNetworking.PINK, 14);

        for (LivingEntity e : caught) {
            HostNetworking.fx(host, HostNetworking.FX_STRING, 1, e.getId(), chest(e), HostNetworking.PINK, 34);
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, 30, 3, false, false, true), host);
            if (e instanceof MobEntity mob) {
                mob.setTarget(null);
            }
        }

        later(host, 32, () -> {
            for (LivingEntity e : caught) {
                if (e.isAlive()) {
                    e.removeStatusEffect(StatusEffects.LEVITATION);
                    e.setVelocity(e.getVelocity().x, -1.5, e.getVelocity().z);
                    e.velocityModified = true;
                }
            }
        });
        later(host, 44, () -> {
            for (LivingEntity e : caught) {
                if (e.isAlive() && e.getWorld() == host.getWorld()) {
                    e.damage(host.getWorld().getDamageSources().indirectMagic(host, host), 4.0F);
                    HostNetworking.fx(host, HostNetworking.FX_PULSE, 1, -1, e.getPos(), HostNetworking.PINK, 8);
                    HostNetworking.word(host, HostNetworking.WORD_SLAM, chest(e), 0xFFFFFF);
                }
            }
        });
        PhaseFx.sound(host, SoundEvents.ENTITY_SHULKER_SHOOT, SoundCategory.PLAYERS, 0.9F, 0.7F);
        say(host, "gravity", 4);
        cast(host, 6);
        return 0;
    }

    /** Back to where the Host stood a few seconds ago, with the health she had there if it was better. */
    private static int rewind(ServerPlayerEntity host) {
        HostManager.State st = state(host);
        Vec3d then = st.trail.peekFirst();
        Float health = st.healthTrail.peekFirst();
        if (then == null || st.trail.size() < 3 || st.trailWorld != host.getWorld() || then.squaredDistanceTo(host.getPos()) > 64.0 * 64.0) {
            say(host, "no_rewind", 2);
            return -1;
        }

        HostNetworking.fx(host, HostNetworking.FX_PULSE, 1, -1, host.getPos(), HostNetworking.CYAN, 10);
        host.requestTeleport(then.x, then.y, then.z);
        host.setVelocity(Vec3d.ZERO);
        host.velocityModified = true;
        host.fallDistance = 0.0F;
        host.extinguish();
        if (health != null && health > host.getHealth()) {
            host.setHealth(Math.min(host.getMaxHealth(), health));
        }

        st.trail.clear();
        st.healthTrail.clear();
        HostNetworking.fx(host, HostNetworking.FX_PULSE, 0, -1, then, HostNetworking.PINK, 12);
        HostNetworking.fx(host, HostNetworking.FX_SELF, 0, -1, then, HostNetworking.CYAN, 12);
        HostNetworking.fx(host, HostNetworking.FX_RUSH, 1, -1, then, HostNetworking.CYAN, 7);
        PhaseFx.sound(host, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.7F, 0.6F);
        say(host, "rewind", 6);
        cast(host, 2);
        return 0;
    }

    private static boolean decoy(ServerPlayerEntity var0) {
        Vec3d var1 = var0.getEyePos();
        Vec3d var2 = var0.getRotationVec(1.0F);
        BlockHitResult var3 = var0.getWorld().raycast(new RaycastContext(var1, var1.add(var2.multiply(20.0)), ShapeType.COLLIDER, FluidHandling.NONE, var0));
        Vec3d var4 = var3.getType() == Type.MISS ? var1.add(var2.multiply(10.0)) : var3.getPos().subtract(var2.multiply(0.5));
        BlockHitResult var5 = var0.getWorld().raycast(new RaycastContext(var4, var4.subtract(0.0, 12.0, 0.0), ShapeType.COLLIDER, FluidHandling.NONE, var0));
        if (var5.getType() != Type.MISS) {
            var4 = var5.getPos();
        }

        HostManager.State var6 = state(var0);
        var6.decoy = var4;
        var6.decoyEnds = var0.getWorld().getTime() + (long)DECOY_TICKS;
        HostNetworking.fx(var0, HostNetworking.FX_DECOY, 0, -1, var4, HostNetworking.CYAN, DECOY_TICKS);
        HostNetworking.fx(var0, HostNetworking.FX_TENDRIL, HostNetworking.SLOT_PLANT, -1, var4.add(0.0, 1.0, 0.0), HostNetworking.CYAN, 9);
        HostNetworking.word(var0, HostNetworking.WORD_PSST, var4.add(0.0, 2.0, 0.0), HostNetworking.CYAN);
        PhaseFx.sound(var0, var4.x, var4.y, var4.z, SoundEvents.ENTITY_ILLUSIONER_MIRROR_MOVE, SoundCategory.PLAYERS, 1.0F, 1.3F);
        say(var0, "decoy", 5);
        cast(var0, 3);
        return true;
    }

    /** Hostile creatures near the hologram forget the Host and go for it instead. */
    private static void tickDecoy(ServerPlayerEntity var0, HostManager.State var1, long var2) {
        if (var1.decoyEnds <= 0L) {
            return;
        }

        if (var2 >= var1.decoyEnds || var1.decoy == null) {
            var1.decoyEnds = 0L;
            return;
        }

        if (var0.age % 10 == 0) {
            Vec3d var4 = var1.decoy;

            for (MobEntity var6 : var0.getWorld()
                .getEntitiesByClass(
                    MobEntity.class,
                    new Box(var4, var4).expand(14.0),
                    var1x -> var1x instanceof Monster
                            && var1x.isAlive()
                            && PhaseFx.samePhase(var1x, var0)
                            && MobGroups.of(var1x) != MobGroups.Group.BOSS
                            && (var1x.getTarget() == null || var1x.getTarget() == var0)
                )) {
                DISTRACTED.put(var6.getUuid(), var2 + 25L);
                DISTRACTED_OWNER.put(var6.getUuid(), var0.getUuid());
                var6.setTarget(null);
                var6.getNavigation().startMovingTo(var4.x, var4.y, var4.z, 1.15);
                var6.getLookControl().lookAt(var4.x, var4.y + 1.2, var4.z);
            }

            PhaseFx.sound(var0, var4.x, var4.y, var4.z, SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(), SoundCategory.PLAYERS, 0.6F, 1.2F + var0.getRandom().nextFloat() * 0.6F);
        }
    }

    /** May this mob go after that entity? Blinded mobs see nobody; turned and distracted ones spare the Host. */
    public static boolean isTurnedAgainst(Entity var0, Entity var1) {
        UUID var2 = TURNED_OWNER.get(var0.getUuid());
        if (var2 != null && var2.equals(var1.getUuid())) {
            return true;
        }

        long var3 = var0.getWorld().getTime();
        Long var5 = BLINDED.get(var0.getUuid());
        if (var5 != null) {
            if (var5 > var3) {
                return true;
            }

            BLINDED.remove(var0.getUuid());
        }

        Long var6 = DISTRACTED.get(var0.getUuid());
        if (var6 != null) {
            if (var6 > var3) {
                return var1.getUuid().equals(DISTRACTED_OWNER.get(var0.getUuid()));
            }

            DISTRACTED.remove(var0.getUuid());
            DISTRACTED_OWNER.remove(var0.getUuid());
        }

        return false;
    }

    // ------------------------------------------------------------------ utility requests

    /** Loose items and experience stream towards the Host for a few seconds. */
    private static void tickMagnet(ServerPlayerEntity var0, HostManager.State var1, long var2) {
        if (var1.magnetEnds <= 0L) {
            return;
        }

        if (var2 >= var1.magnetEnds) {
            var1.magnetEnds = 0L;
            HostNetworking.fx(var0, HostNetworking.FX_STOP, 0, -1, var0.getPos(), 0, 0);
            return;
        }

        if (var0.age % 2 != 0) {
            return;
        }

        Vec3d var4 = var0.getPos().add(0.0, 0.6, 0.0);
        int var5 = 0;

        for (Entity var7 : var0.getWorld()
            .getOtherEntities(
                var0,
                var0.getBoundingBox().expand(16.0),
                var1x -> (var1x instanceof ItemEntity || var1x instanceof ExperienceOrbEntity) && var1x.isAlive() && PhaseFx.samePhase(var1x, var0)
            )) {
            Vec3d var8 = var4.subtract(var7.getPos());
            double var9 = var8.length();
            if (var9 > 1.2) {
                var7.setVelocity(var8.multiply(Math.min(0.9, 0.25 + var9 * 0.06) / var9).add(0.0, 0.04, 0.0));
                var7.velocityModified = true;
            }

            if (var7 instanceof ItemEntity var11) {
                var11.setPickupDelay(0);
            }

            if (var5 < 3 && var0.age % 6 == 0) {
                HostNetworking.fx(var0, HostNetworking.FX_TENDRIL, var5++, var7.getId(), var7.getPos().add(0.0, 0.2, 0.0), HostNetworking.GOLD, 8);
            }
        }
    }

    private static boolean toggle(ServerPlayerEntity var0) {
        Vec3d var1 = var0.getEyePos();
        BlockHitResult var2 = var0.getWorld()
            .raycast(new RaycastContext(var1, var1.add(var0.getRotationVec(1.0F).multiply(16.0)), ShapeType.OUTLINE, FluidHandling.NONE, var0));
        if (var2.getType() == Type.MISS) {
            say(var0, "no_target", 2);
            return false;
        }

        BlockPos var3 = var2.getBlockPos();
        World var4 = var0.getWorld();
        BlockState var5 = var4.getBlockState(var3);
        if (!var5.contains(Properties.OPEN) && !var5.contains(Properties.POWERED) || !var4.canPlayerModifyAt(var0, var3)) {
            say(var0, "no_target", 2);
            return false;
        }

        HostNetworking.fx(var0, HostNetworking.FX_TENDRIL, HostNetworking.SLOT_POKE, -1, var2.getPos(), HostNetworking.CYAN, 8);
        cast(var0, 3);
        later(var0, 3, () -> {
            BlockState var4x = var4.getBlockState(var3);
            if (var4x.getBlock() != var5.getBlock()) {
                return;
            }

            // Locks do not matter to her: iron doors and trapdoors open like any other.
            if (var4x.getBlock() instanceof net.minecraft.block.DoorBlock var5x) {
                var5x.setOpen(var0, var4, var4x, var3, !var4x.get(Properties.OPEN));
            } else if (var4x.getBlock() instanceof net.minecraft.block.TrapdoorBlock && var4x.contains(Properties.OPEN)) {
                var4.setBlockState(var3, var4x.cycle(Properties.OPEN), 2);
                var4.playSound(null, var3, SoundEvents.BLOCK_IRON_TRAPDOOR_OPEN, SoundCategory.BLOCKS, 1.0F, 1.0F);
            } else {
                var4x.onUse(var4, var0, Hand.MAIN_HAND, var2);
            }

            hostFx(var0, ParticleTypes.ELECTRIC_SPARK, var2.getPos().x, var2.getPos().y, var2.getPos().z, 10, 0.2, 0.2, 0.2, 0.3);
        });
        return true;
    }

    private static void tendril(ServerPlayerEntity var0, BlockPos var1) {
        HostNetworking.fx(var0, HostNetworking.FX_TENDRIL, 0, -1, Vec3d.ofCenter(var1), HostNetworking.CYAN, 12);
    }

    /** Fells a whole tree or strips an ore vein, block by block, with the Host's own tool. */
    private static int harvest(ServerPlayerEntity var0) {
        Vec3d var1 = var0.getEyePos();
        ServerWorld var2 = var0.getWorld();
        BlockHitResult var3 = var2.raycast(new RaycastContext(var1, var1.add(var0.getRotationVec(1.0F).multiply(16.0)), ShapeType.OUTLINE, FluidHandling.NONE, var0));
        if (var3.getType() == Type.MISS) {
            say(var0, "no_target", 2);
            return -1;
        }

        BlockPos var4 = var3.getBlockPos();
        BlockState var5 = var2.getBlockState(var4);
        float var6 = var5.getHardness(var2, var4);
        if (var5.isAir() || var6 < 0.0F || var6 >= 50.0F || var2.getBlockEntity(var4) != null) {
            say(var0, "no_target", 2);
            return -1;
        }

        boolean var7 = var5.isIn(net.minecraft.registry.tag.BlockTags.LOGS);
        Block var8 = var5.getBlock();
        int var9 = var7 ? 48 : 24;
        List<BlockPos> var10 = new ArrayList<>();
        HashSet<BlockPos> var11 = new HashSet<>();
        ArrayDeque<BlockPos> var12 = new ArrayDeque<>();
        var12.add(var4);
        var11.add(var4);

        while (!var12.isEmpty() && var10.size() < var9) {
            BlockPos var13 = var12.poll();
            var10.add(var13);

            for (BlockPos var15 : BlockPos.iterate(var13.add(-1, -1, -1), var13.add(1, 1, 1))) {
                if (!var11.contains(var15)) {
                    BlockState var16 = var2.getBlockState(var15);
                    if (var7 ? var16.isIn(net.minecraft.registry.tag.BlockTags.LOGS) : var16.getBlock() == var8) {
                        BlockPos var17 = var15.toImmutable();
                        var11.add(var17);
                        var12.add(var17);
                    }
                }
            }
        }

        HostManager.State var18 = state(var0);

        for (BlockPos var20 : var10) {
            var18.work.add(() -> {
                tendril(var0, var20);
                breakFor(var0, var2, var20);
            });
        }

        tendril(var0, var4);
        PhaseFx.sound(var0, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 0.5F, 1.9F);
        say(var0, var7 ? "harvest_tree" : "harvest_vein", 1, var10.size());
        cast(var0, 3);
        return var10.size() / 4;
    }

    private static void cast(ServerPlayerEntity var0, int var1) {
        HostNetworking.cast(var0, var1, 20);
    }

    static LivingEntity lookTarget(ServerPlayerEntity var0, double var1) {
        Vec3d var3 = var0.getEyePos();
        Vec3d var4 = var0.getRotationVec(1.0F);
        Vec3d var5 = var3.add(var4.multiply(var1));
        BlockHitResult var6 = var0.getWorld().raycast(new RaycastContext(var3, var5, ShapeType.COLLIDER, FluidHandling.NONE, var0));
        if (var6.getType() != Type.MISS) {
            var5 = var6.getPos();
        }

        EntityHitResult var7 = ProjectileUtil.raycast(
            var0,
            var3,
            var5,
            new Box(var3, var5).expand(1.0),
            var1x -> var1x instanceof LivingEntity && var1x != var0 && var1x.isAlive() && !var1x.isSpectator(),
            var3.squaredDistanceTo(var5)
        );
        return var7 == null ? null : (LivingEntity)var7.getEntity();
    }

    private static void arc(ServerPlayerEntity var0, Vec3d var1) {
        HostNetworking.fx(var0, HostNetworking.FX_BOLT, 1, -1, var1, HostNetworking.CYAN, 6);
        PhaseFx.privateSound(var0, SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, 0.3F, 1.8F);
    }

    private static void tickTurned(MinecraftServer var0) {
        if (!TURNED.isEmpty() && var0.getTicks() % 10 == 0) {
            HashSet var1 = new HashSet();

            for (ServerWorld var3 : var0.getWorlds()) {
                for (Entry var5 : TURNED.entrySet()) {
                    Entity var6 = var3.getEntity((UUID)var5.getKey());
                    if (var6 != null) {
                        if (var6 instanceof MobEntity var7 && var7.isAlive() && (Long)var5.getValue() >= var3.getTime()) {
                            hostFx(var7, ARC_PINK, var7.getX(), var7.getEyeY() + 0.5, var7.getZ(), 1, 0.1, 0.05, 0.1, 0.0);
                            LivingEntity var17 = null;
                            double var9 = 256.0;

                            for (HostileEntity var12 : var3.getEntitiesByClass(
                                HostileEntity.class,
                                var7.getBoundingBox().expand(16.0),
                                var1x -> var1x != var7 && var1x.isAlive() && !TURNED.containsKey(var1x.getUuid()) && PhaseFx.samePhase(var1x, var7)
                            )) {
                                double var13 = var12.squaredDistanceTo(var7);
                                if (var13 < var9) {
                                    var9 = var13;
                                    var17 = var12;
                                }
                            }

                            UUID owner = TURNED_OWNER.get(var7.getUuid());
                            ServerPlayerEntity master = owner == null ? null : var0.getPlayerManager().getPlayer(owner);
                            if (master != null) {
                                // Players too, when the Host has them switched on.
                                for (PlayerEntity near : var3.getPlayers()) {
                                    double away = near.squaredDistanceTo(var7);
                                    if (away < var9 && near.isAlive() && fairGame(master, near) && PhaseFx.samePhase(near, var7)) {
                                        var9 = away;
                                        var17 = near;
                                    }
                                }
                            }

                            var7.setTarget(var17);
                            continue;
                        }

                        var1.add((UUID)var5.getKey());
                        if (var6 instanceof MobEntity var8) {
                            var8.setTarget(null);
                        }
                    }
                }
            }

            for (UUID var16 : (Iterable<UUID>)(Object)(var1)) {
                TURNED.remove(var16);
                TURNED_OWNER.remove(var16);
            }
        }
    }

    private static boolean blink(ServerPlayerEntity var0) {
        Vec3d var1 = var0.getEyePos();
        Vec3d var2 = var0.getRotationVec(1.0F);
        BlockHitResult var3 = var0.getWorld().raycast(new RaycastContext(var1, var1.add(var2.multiply(16.0)), ShapeType.COLLIDER, FluidHandling.NONE, var0));
        BlockPos var4;
        if (var3.getType() == Type.MISS) {
            var4 = BlockPos.ofFloored(var1.add(var2.multiply(16.0)));
        } else {
            var4 = var3.getBlockPos().offset(var3.getSide());
        }

        World var5 = var0.getWorld();

        for (int var6 = 0; var6 < 4; var6++) {
            BlockPos var7 = var4.up(var6);
            if (var5.getBlockState(var7).getCollisionShape(var5, var7).isEmpty() && var5.getBlockState(var7.up()).getCollisionShape(var5, var7.up()).isEmpty()) {
                hostFx(var0, ARC_CYAN, var0.getX(), var0.getBodyY(0.5), var0.getZ(), 30, 0.3, 0.6, 0.3, 0.0);
                var0.requestTeleport((double)var7.getX() + 0.5, (double)var7.getY(), (double)var7.getZ() + 0.5);
                var0.fallDistance = 0.0F;
                hostFx(var0, ARC_PINK, (double)var7.getX() + 0.5, (double)var7.getY() + 1.0, (double)var7.getZ() + 0.5, 30, 0.3, 0.6, 0.3, 0.0);
                PhaseFx.sound(var0, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.6F, 1.6F);
                return true;
            }
        }

        say(var0, "no_target", 2);
        return false;
    }

    private static boolean light(ServerPlayerEntity var0) {
        ServerWorld var1 = var0.getWorld();
        int var2 = count(var0, Items.TORCH);
        if (var2 == 0) {
            // No torches to place: she lends the Host her own eyes instead.
            var0.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 900, 0));
            HostNetworking.fx(var0, HostNetworking.FX_SELF, 0, -1, var0.getPos(), HostNetworking.GOLD, 20);
            HostNetworking.fx(var0, HostNetworking.FX_STAMP, HostNetworking.STAMP_EYES, -1, var0.getPos(), HostNetworking.PINK, 18);
            say(var0, "no_torches", 2);
            return true;
        } else {
            ArrayList var3 = new ArrayList();
            BlockPos var4 = var0.getBlockPos();

            for (BlockPos var6 : BlockPos.iterateOutwards(var4, 8, 3, 8)) {
                if (var3.size() >= Math.min(6, var2)) {
                    break;
                }

                if (var1.getBlockState(var6).isAir()
                    && var1.getLightLevel(LightType.BLOCK, var6) < 8
                    && var1.getLightLevel(LightType.SKY, var6) < 8
                    && Blocks.TORCH.getDefaultState().canPlaceAt(var1, var6)
                    && var1.canPlayerModifyAt(var0, var6)
                    && var3.stream().noneMatch(var1x -> ((BlockPos)var1x).getManhattanDistance(var6) < 6)) {
                    var3.add(var6.toImmutable());
                }
            }

            if (var3.isEmpty()) {
                say(var0, "nothing", 2);
                return false;
            } else {
                HostManager.State var8 = state(var0);

                for (BlockPos var7 : (Iterable<BlockPos>)(Object)(var3)) {
                    var8.work
                        .add(
                            () -> {
                                tendril(var0, var7);
                                if (var1.getBlockState(var7).isAir() && take(var0, Items.TORCH)) {
                                    var1.setBlockState(var7, Blocks.TORCH.getDefaultState());
                                    var1.playSound(null, var7, SoundEvents.BLOCK_WOOD_PLACE, SoundCategory.BLOCKS, 0.6F, 1.2F);
                                    hostFx(
                                        var0, ARC_CYAN, (double)var7.getX() + 0.5, (double)var7.getY() + 0.5, (double)var7.getZ() + 0.5, 6, 0.2, 0.2, 0.2, 0.0
                                    );
                                }
                            }
                        );
                }

                say(var0, "working", 0);
                cast(var0, 3);
                return true;
            }
        }
    }

    private static int dig(ServerPlayerEntity var0, boolean var1, int var2) {
        int var3 = MathHelper.clamp(var2 <= 0 ? 6 : var2, 1, 32);
        Direction var4 = var0.getHorizontalFacing();
        BlockPos var5 = var0.getBlockPos();
        ServerWorld var6 = var0.getWorld();
        HostManager.State var7 = state(var0);

        for (int var8 = 1; var8 <= var3; var8++) {
            BlockPos var9 = var5.offset(var4, var8).down(var1 ? var8 : 0);

            for (int var10 = 0; var10 < (var1 ? 3 : 2); var10++) {
                BlockPos var11 = var9.up(var10);
                var7.work.add(() -> {
                    tendril(var0, var11);
                    breakFor(var0, var6, var11);
                });
            }
        }

        tendril(var0, var5.offset(var4, 1));
        say(var0, "working", 0);
        cast(var0, 3);
        return Math.max(0, var3 - 6) / 2;
    }

    private static int build(ServerPlayerEntity var0, boolean var1, int var2) {
        ItemStack var3 = buildingBlocks(var0);
        if (var3 == null) {
            say(var0, "no_blocks", 2);
            return -1;
        } else {
            Block var4 = ((BlockItem)var3.getItem()).getBlock();
            Direction var5 = var0.getHorizontalFacing();
            ServerWorld var6 = var0.getWorld();
            ArrayList var7 = new ArrayList();
            if (var1) {
                int var8 = MathHelper.clamp(var2 <= 0 ? 6 : var2, 1, 32);

                for (int var9 = 1; var9 <= var8; var9++) {
                    var7.add(var0.getBlockPos().down().offset(var5, var9));
                }
            } else {
                Direction var12 = var5.rotateYClockwise();
                BlockPos var14 = var0.getBlockPos().offset(var5, 2);

                for (int var10 = 0; var10 < 3; var10++) {
                    for (int var11 = -1; var11 <= 1; var11++) {
                        var7.add(var14.offset(var12, var11).up(var10));
                    }
                }
            }

            HostManager.State var13 = state(var0);

            for (BlockPos var16 : (Iterable<BlockPos>)(Object)(var7)) {
                var13.work
                    .add(
                        () -> {
                            tendril(var0, var16);
                            BlockState var4x = var6.getBlockState(var16);
                            if (var4x.isReplaceable()
                                && var6.canPlayerModifyAt(var0, var16)
                                && var6.getOtherEntities(null, new Box(var16), var0xx -> var0xx instanceof LivingEntity).isEmpty()) {
                                ItemStack var5x = findStack(var0, var4.asItem());
                                if (var5x != null) {
                                    var6.setBlockState(var16, var4.getDefaultState());
                                    if (!var0.isCreative()) {
                                        var5x.decrement(1);
                                    }

                                    var6.playSound(null, var16, var4.getDefaultState().getSoundGroup().getPlaceSound(), SoundCategory.BLOCKS, 0.7F, 1.0F);
                                    hostFx(
                                        var0,
                                        ARC_CYAN,
                                        (double)var16.getX() + 0.5,
                                        (double)var16.getY() + 0.5,
                                        (double)var16.getZ() + 0.5,
                                        3,
                                        0.3,
                                        0.3,
                                        0.3,
                                        0.0
                                    );
                                }
                            }
                        }
                    );
            }

            say(var0, "working", 0);
            cast(var0, 3);
            return Math.max(0, var7.size() - 8) / 2;
        }
    }

    private static void breakFor(ServerPlayerEntity var0, ServerWorld var1, BlockPos var2) {
        if (var0.getWorld() == var1 && var0.isAlive()) {
            BlockState var3 = var1.getBlockState(var2);
            float var4 = var3.getHardness(var1, var2);
            BlockEntity var5 = var1.getBlockEntity(var2);
            if (!var3.isAir() && var3.getFluidState().isEmpty() && !(var4 < 0.0F) && !(var4 > 50.0F) && var5 == null) {
                if (withTool(var0, var3, () -> var0.interactionManager.tryBreakBlock(var2))) {
                    hostFx(var0, ARC_CYAN, (double)var2.getX() + 0.5, (double)var2.getY() + 0.5, (double)var2.getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0.0);
                }
            }
        }
    }

    /**
     * Volta works with the Host's tools. For the length of one block the right tool from the inventory is put
     * in the Host's hand, wherever it lies: the block breaks as if the Host had mined it with that tool, so it
     * drops what that tool gets out of it, Fortune and Silk Touch count, and the tool wears by one use.
     * Without the tool the block still breaks, the way a bare hand breaks it: stone leaves nothing.
     */
    private static boolean withTool(ServerPlayerEntity host, BlockState state, java.util.function.BooleanSupplier work) {
        PlayerInventory inventory = host.getInventory();
        int hand = inventory.selectedSlot;
        int slot = toolSlot(inventory, state);
        if (slot < 0 && state.isToolRequired() && !host.isCreative()) {
            HostManager.State st = state(host);
            long now = host.getWorld().getTime();
            if (now - st.toolNotice > 200L || st.toolNotice == 0L) {
                // Once per job, not once per block.
                say(host, "no_tool", 2);
            }

            st.toolNotice = now;
        }

        if (slot < 0 || slot == hand || hand < 0 || hand >= inventory.main.size()) {
            return work.getAsBoolean();
        }

        ItemStack held = inventory.main.get(hand);
        inventory.main.set(hand, inventory.main.get(slot));
        inventory.main.set(slot, held);

        try {
            return work.getAsBoolean();
        } finally {
            // Back where they were: the tool one use older, or gone if that use was its last.
            ItemStack tool = inventory.main.get(hand);
            inventory.main.set(hand, inventory.main.get(slot));
            inventory.main.set(slot, tool);
        }
    }

    /** The slot of the tool for this block, or -1. What the Host holds wins if it does the job: the Host chose it. */
    private static int toolSlot(PlayerInventory inventory, BlockState state) {
        int hand = inventory.selectedSlot;
        if (hand >= 0 && hand < inventory.main.size() && worksOn(inventory.main.get(hand), state) > 0.0F) {
            return hand;
        }

        int best = -1;
        float bestWorth = 0.0F;

        for (int i = 0; i < inventory.main.size(); i++) {
            float worth = worksOn(inventory.main.get(i), state);
            if (worth > bestWorth) {
                bestWorth = worth;
                best = i;
            }
        }

        return best;
    }

    /** 0 when the stack is not a tool for this block; otherwise the bigger the better. A tool on its last use is left alone. */
    static float worksOn(ItemStack stack, BlockState state) {
        if (stack.isEmpty() || !(stack.getItem() instanceof net.minecraft.item.MiningToolItem) && !(stack.getItem() instanceof net.minecraft.item.ShearsItem)) {
            return 0.0F;
        }

        if (stack.isDamageable() && stack.getMaxDamage() - stack.getDamage() <= 1) {
            return 0.0F;
        }

        float speed = stack.getMiningSpeedMultiplier(state);
        boolean fits = stack.isSuitableFor(state);
        if (state.isToolRequired()) {
            return fits ? 1000.0F + speed : 0.0F;
        }

        return speed > 1.0F ? speed : 0.0F;
    }

    private static ItemStack buildingBlocks(ServerPlayerEntity var0) {
        PlayerInventory var1 = var0.getInventory();

        for (int var2 = 0; var2 < var1.main.size(); var2++) {
            ItemStack var3 = var1.main.get(var2);
            Item var5 = var3.getItem();
            if (var5 instanceof BlockItem) {
                BlockItem var4 = (BlockItem)var5;
                if (!var3.isEmpty()) {
                    BlockState var6 = var4.getBlock().getDefaultState();
                    if (var6.isFullCube(var0.getWorld(), BlockPos.ORIGIN) && !(var4.getBlock() instanceof FallingBlock) && !var6.hasBlockEntity()) {
                        return var3;
                    }
                }
            }
        }

        return null;
    }

    private static ItemStack findStack(ServerPlayerEntity var0, Item var1) {
        PlayerInventory var2 = var0.getInventory();

        for (int var3 = 0; var3 < var2.main.size(); var3++) {
            if (var2.main.get(var3).isOf(var1)) {
                return var2.main.get(var3);
            }
        }

        return null;
    }

    private static int count(ServerPlayerEntity var0, Item var1) {
        return var0.isCreative() ? 64 : var0.getInventory().count(var1);
    }

    private static boolean take(ServerPlayerEntity var0, Item var1) {
        if (var0.isCreative()) {
            return true;
        } else {
            ItemStack var2 = findStack(var0, var1);
            if (var2 == null) {
                return false;
            } else {
                var2.decrement(1);
                return true;
            }
        }
    }

    private static boolean findsPlayer(ServerPlayerEntity var0, String var1) {
        return var0.getServer().getPlayerManager().getPlayer(var1.trim()) != null;
    }

    private static final int SEARCH_PLAYER = 30;
    private static final int SEARCH_PLAIN = 25;
    private static final int SEARCH_COMMON = 40;
    private static final int SEARCH_UNCOMMON = 55;
    private static final int SEARCH_RARE = 75;
    private static final int SEARCH_LEGENDARY = 90;

    /**
     * What looking through the ground for something costs the chip, in heat. The rarer the thing, the
     * hotter: a search for ancient debris leaves the chip one request from burning out.
     */
    static int searchHeat(String path) {
        if (path.contains("debris") || path.contains("netherite")) {
            return SEARCH_LEGENDARY;
        } else if (path.contains("diamond")
            || path.contains("emerald")
            || path.contains("chest")
            || path.contains("barrel")
            || path.contains("shulker")
            || path.contains("spawner")
            || path.contains("beacon")
            || path.contains("budding")) {
            return SEARCH_RARE;
        } else if (path.contains("gold") || path.contains("lapis") || path.contains("redstone") || path.contains("amethyst") || path.contains("obsidian")) {
            return SEARCH_UNCOMMON;
        } else {
            return path.endsWith("_ore") || path.equals("ore") || path.contains("iron") || path.contains("copper") || path.contains("coal") || path.contains("quartz")
                ? SEARCH_COMMON
                : SEARCH_PLAIN;
        }
    }

    /** Returns the heat on top of the command's own, or -1 when nothing was asked for. */
    private static int find(ServerPlayerEntity var0, String var1) {
        ServerPlayerEntity var2 = var0.getServer().getPlayerManager().getPlayer(var1.trim());
        if (var2 != null && var2 != var0) {
            findPlayer(var0, var2);
            return SEARCH_PLAYER - HostCommand.FIND.heat;
        } else {
            String var3 = VoltaChat.canonical(var1);
            String var4 = var3 != null ? var3 : var1.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
            EntityType var5 = entityFor(var4);
            if (var5 == null && !VoltaChat.wantsEnemy(var1)) {
                return findBlock(var0, var4, var1);
            } else {
                findMob(var0, var5, var1);
                return 0;
            }
        }
    }

    private static EntityType<?> entityFor(String var0) {
        if (var0.isBlank()) {
            return null;
        } else {
            for (EntityType var2 : Registries.ENTITY_TYPE) {
                String var3 = Registries.ENTITY_TYPE.getId(var2).getPath();
                if (var3.equals(var0)) {
                    return var2;
                }
            }

            for (EntityType var5 : Registries.ENTITY_TYPE) {
                String var6 = Registries.ENTITY_TYPE.getId(var5).getPath();
                if (var6.contains(var0) && var5.getSpawnGroup() != SpawnGroup.MISC) {
                    return var5;
                }
            }

            return null;
        }
    }

    private static boolean findMob(ServerPlayerEntity var0, EntityType<?> var1, String var2) {
        LivingEntity var3 = null;
        double var4 = 2304.0;

        for (LivingEntity var7 : var0.getWorld()
            .getEntitiesByClass(
                LivingEntity.class,
                var0.getBoundingBox().expand(48.0),
                var2x -> var2x != var0
                        && var2x.isAlive()
                        && PhaseFx.samePhase(var2x, var0)
                        && !var2x.isInvisible()
                        && (var1 == null ? var2x instanceof Monster : var2x.getType() == var1)
            )) {
            double var8 = var7.squaredDistanceTo(var0);
            if (var8 < var4) {
                var4 = var8;
                var3 = var7;
            }
        }

        if (var3 == null) {
            say(var0, "not_found", 2, var2);
            return true;
        } else {
            report(var0, var3.getPos(), 1, var3.getDisplayName());
            return true;
        }
    }

    private static boolean findPlayer(ServerPlayerEntity var0, ServerPlayerEntity var1) {
        boolean var2 = var1.getWorld() != var0.getWorld()
            || !PhaseFx.samePhase(var1, var0)
            || var1.isInvisible()
            || var1.isSpectator()
            || var1.squaredDistanceTo(var0) > 9216.0;
        if (var2) {
            say(var0, "not_found", 2, var1.getName().getString());
            return true;
        } else {
            report(var0, var1.getPos(), 2, var1.getDisplayName());
            HostNetworking.scanned(var1);
            PhaseFx.privateSound(var1, SoundEvents.BLOCK_BEACON_POWER_SELECT, 0.4F, 2.0F);
            return true;
        }
    }

    private static int findBlock(ServerPlayerEntity var0, String var1, String var2) {
        if (var1.isBlank()) {
            say(var0, "how_find", 6);
            return -1;
        } else {
            // Even "there is none here" is an answer, and is paid for as one.
            int var10 = searchHeat(var1);
            World var3 = var0.getWorld();
            BlockPos var4 = var0.getBlockPos();
            BlockPos var5 = null;

            for (BlockPos var7 : BlockPos.iterateOutwards(var4, 14, 14, 14)) {
                BlockState var8 = var3.getBlockState(var7);
                if (!var8.isAir()) {
                    String var9 = Registries.BLOCK.getId(var8.getBlock()).getPath();
                    if (matchesBlock(var9, var1)) {
                        var5 = var7.toImmutable();
                        break;
                    }
                }
            }

            if (var5 == null) {
                say(var0, "not_found", 2, var2);
                return var10 - HostCommand.FIND.heat;
            } else {
                report(var0, Vec3d.ofCenter(var5), 0, var3.getBlockState(var5).getBlock().getName());
                return Math.max(var10, searchHeat(Registries.BLOCK.getId(var3.getBlockState(var5).getBlock()).getPath())) - HostCommand.FIND.heat;
            }
        }
    }

    private static boolean matchesBlock(String var0, String var1) {
        return switch (var1) {
            case "diamond", "iron", "gold", "coal", "copper", "emerald", "redstone", "lapis" -> var0.endsWith(var1 + "_ore");
            case "log" -> var0.endsWith("_log") || var0.endsWith("_stem");
            case "chest" -> var0.equals("chest") || var0.equals("trapped_chest") || var0.equals("barrel");
            default -> var0.equals(var1) || var0.contains(var1);
        };
    }

    private static void report(ServerPlayerEntity var0, Vec3d var1, int var2, Text var3) {
        Vec3d var4 = var1.subtract(var0.getPos());
        int var5 = (int)Math.round(var4.length());
        HostNetworking.found(var0, var1, var2, var3);
        HostNetworking.fx(var0, HostNetworking.FX_TENDRIL, HostNetworking.SLOT_POINT, -1, var1, HostNetworking.GOLD, 70);
        say(var0, "found", 1, var3, Text.translatable(direction(var4)), var5);
        PhaseFx.privateSound(var0, SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), 0.6F, 1.6F);
    }

    private static String direction(Vec3d var0) {
        if (Math.abs(var0.y) > Math.max(6.0, var0.horizontalLength() * 1.5)) {
            return "message.windyorigins.volta.dir." + (var0.y > 0.0 ? "up" : "down");
        } else {
            double var1 = Math.toDegrees(Math.atan2(var0.x, -var0.z));
            String[] var3 = new String[]{"n", "ne", "e", "se", "s", "sw", "w", "nw"};
            int var4 = Math.floorMod((int)Math.round(var1 / 45.0), 8);
            return "message.windyorigins.volta.dir." + var3[var4];
        }
    }

    /** How far Volta reaches for a container. */
    private static final double FETCH_RANGE = 24.0;

    /**
     * Is the block in front of the Host with nothing opaque in the way? Glass does not hide it; being
     * behind the Host does, because what is behind is not seen.
     */
    static boolean sees(ServerPlayerEntity host, BlockPos pos) {
        Vec3d eye = host.getEyePos();
        Vec3d centre = Vec3d.ofCenter(pos);
        Vec3d to = centre.subtract(eye);
        double distance = to.length();
        if (distance < 1.6) {
            return true;
        }

        if (to.multiply(1.0 / distance).dotProduct(host.getRotationVec(1.0F)) < 0.35) {
            return false;
        }

        // Any part of it will do: the middle, the middle of a side or a corner. Nobody has to look straight at it.
        World world = host.getWorld();
        BlockPos twin = otherHalf(world, pos);

        for (double[] probe : SIGHT_PROBES) {
            BlockHitResult hit = world.raycast(new RaycastContext(eye, centre.add(probe[0], probe[1], probe[2]), ShapeType.VISUAL, FluidHandling.NONE, host));
            if (hit.getType() == Type.MISS || hit.getBlockPos().equals(pos) || hit.getBlockPos().equals(twin)) {
                return true;
            }
        }

        return false;
    }

    private static final double[][] SIGHT_PROBES = new double[][]{
        {0, 0, 0}, {0.45, 0, 0}, {-0.45, 0, 0}, {0, 0.42, 0}, {0, -0.42, 0}, {0, 0, 0.45}, {0, 0, -0.45},
        {0.42, 0.4, 0.42}, {0.42, 0.4, -0.42}, {-0.42, 0.4, 0.42}, {-0.42, 0.4, -0.42},
        {0.42, -0.4, 0.42}, {0.42, -0.4, -0.42}, {-0.42, -0.4, 0.42}, {-0.42, -0.4, -0.42}
    };

    /** The other half of a double chest: seeing one half is seeing the chest. */
    private static BlockPos otherHalf(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof net.minecraft.block.ChestBlock && state.contains(net.minecraft.block.ChestBlock.CHEST_TYPE) && state.get(net.minecraft.block.ChestBlock.CHEST_TYPE) != net.minecraft.block.enums.ChestType.SINGLE) {
            return pos.offset(net.minecraft.block.ChestBlock.getFacing(state));
        }

        return null;
    }

    private static final List<Object[]> LIDS = new ArrayList<>();

    private static void tickLids(boolean all) {
        LIDS.removeIf(entry -> {
            ServerWorld world = (ServerWorld)entry[0];
            if (all || world.getTime() >= (Long)entry[2]) {
                lid(world, (BlockPos)entry[1], false);
                return true;
            }

            return false;
        });
    }

    /** Lifts or drops the lid for everyone nearby, the way a hand on it would. */
    private static void lid(ServerWorld world, BlockPos pos, boolean open) {
        BlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof net.minecraft.block.AbstractChestBlock<?> || state.getBlock() instanceof net.minecraft.block.ShulkerBoxBlock) {
            world.addSyncedBlockEvent(pos, state.getBlock(), 1, open ? 1 : 0);
            boolean shulker = state.getBlock() instanceof net.minecraft.block.ShulkerBoxBlock;
            world.playSound(
                null,
                pos,
                shulker ? (open ? SoundEvents.BLOCK_SHULKER_BOX_OPEN : SoundEvents.BLOCK_SHULKER_BOX_CLOSE) : (open ? SoundEvents.BLOCK_CHEST_OPEN : SoundEvents.BLOCK_CHEST_CLOSE),
                SoundCategory.BLOCKS,
                0.5F,
                world.random.nextFloat() * 0.1F + 0.9F
            );
        } else if (state.getBlock() instanceof net.minecraft.block.BarrelBlock && state.contains(Properties.OPEN)) {
            world.setBlockState(pos, state.with(Properties.OPEN, open), 3);
            world.playSound(null, pos, open ? SoundEvents.BLOCK_BARREL_OPEN : SoundEvents.BLOCK_BARREL_CLOSE, SoundCategory.BLOCKS, 0.5F, world.random.nextFloat() * 0.1F + 0.9F);
        }
    }

    /**
     * Brings a stack of the named thing out of a container the Host is looking towards. The arm goes
     * to the container, the lid comes up, and the item rides the arm back.
     */
    private static boolean fetch(ServerPlayerEntity var0, String var1) {
        String var2 = VoltaChat.canonical(var1);
        String var3 = var1.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        if (var3.isBlank()) {
            say(var0, "how_fetch", 6);
            return false;
        }

        if (var2 != null && var1.trim().indexOf(' ') < 0) {
            // One everyday word ("уголь", "iron", "алмазы") is the plain material: no need to ask what it is called.
            if (!take(var0, stack -> matchesItem(stack, var2, false)) && !take(var0, stack -> matchesItem(stack, var2, true))) {
                say(var0, "fetch_none", 2, var1);
            }

            return true;
        }

        askNames(var0, false, var1, (exact, partial) -> {
            if (!exact.isEmpty()) {
                // The words are the whole name of something: that thing or nothing, best match first.
                for (net.minecraft.util.Identifier id : exact) {
                    if (take(var0, stack -> Registries.ITEM.getId(stack.getItem()).equals(id))) {
                        return;
                    }
                }

                say(var0, "fetch_none", 2, var1);
                return;
            }

            // Otherwise the everyday word, then anything the words are part of the name of, then the id itself.
            if (var2 != null && (take(var0, stack -> matchesItem(stack, var2, false)) || take(var0, stack -> matchesItem(stack, var2, true)))) {
                return;
            }

            if (!partial.isEmpty()) {
                java.util.Set<net.minecraft.util.Identifier> any = new HashSet<>(partial);
                if (take(var0, stack -> any.contains(Registries.ITEM.getId(stack.getItem())))) {
                    return;
                }
            }

            if (!take(var0, stack -> matchesItem(stack, var3, false)) && !take(var0, stack -> matchesItem(stack, var3, true))) {
                // One answer whether there is none or it is merely out of sight: she is not a way to look through walls.
                say(var0, "fetch_none", 2, var1);
            }
        });
        return true;
    }

    /**
     * Takes a stack of the first thing that fits out of the nearest container in sight and hands it to the
     * Host. False when no container in sight holds such a thing; true when one did, even if the Host's
     * pockets turned out to be full.
     */
    private static boolean take(ServerPlayerEntity var0, java.util.function.Predicate<ItemStack> wanted) {
        ServerWorld var4 = var0.getWorld();
        BlockPos var5 = var0.getBlockPos();
        List<LockableContainerBlockEntity> var6 = new ArrayList<>();
        int var7 = (int)FETCH_RANGE;

        for (int var8 = var5.getX() - var7 >> 4; var8 <= var5.getX() + var7 >> 4; var8++) {
            for (int var9 = var5.getZ() - var7 >> 4; var9 <= var5.getZ() + var7 >> 4; var9++) {
                net.minecraft.world.chunk.WorldChunk var10 = var4.getChunkManager().getWorldChunk(var8, var9);
                if (var10 == null) {
                    continue;
                }

                for (BlockEntity var12 : var10.getBlockEntities().values()) {
                    if (var12 instanceof LockableContainerBlockEntity var13 && var13.getPos().getSquaredDistance(var0.getEyePos()) <= FETCH_RANGE * FETCH_RANGE) {
                        var6.add(var13);
                    }
                }
            }
        }

        Vec3d var14 = var0.getEyePos();
        var6.sort(java.util.Comparator.comparingDouble(var1x -> var1x.getPos().getSquaredDistance(var14)));
        for (LockableContainerBlockEntity var17 : var6) {
            BlockPos var18 = var17.getPos();

            for (int var19 = 0; var19 < var17.size(); var19++) {
                ItemStack var20 = var17.getStack(var19);
                if (var20.isEmpty() || !wanted.test(var20)) {
                    continue;
                }

                if (!sees(var0, var18)) {
                    // It is there, but out of sight: she will not reach through walls or behind the Host's back.
                    break;
                }

                if (!var4.canPlayerModifyAt(var0, var18) || !var17.checkUnlocked(var0)) {
                    break;
                }

                ItemStack var21 = var17.removeStack(var19, var20.getMaxCount());
                Text var22 = var21.getName();
                int var23 = var21.getCount();
                int var24 = Registries.ITEM.getRawId(var21.getItem());
                if (!var0.getInventory().insertStack(var21) && !var21.isEmpty()) {
                    var17.setStack(var19, var21);
                    var23 -= var21.getCount();
                }

                var17.markDirty();
                if (var23 <= 0) {
                    say(var0, "nothing", 2);
                    return true;
                }

                HostNetworking.fx(var0, HostNetworking.FX_GRAB, 0, var24, Vec3d.ofCenter(var18), HostNetworking.CYAN, 22);
                later(var0, 3, () -> {
                    lid(var4, var18, true);
                    // Closed by the world's clock, not the Host's: the lid comes down even if she logs out.
                    LIDS.add(new Object[]{var4, var18.toImmutable(), var4.getTime() + 14L});
                });
                say(var0, "fetched", 0, var23, var22);
                cast(var0, 3);
                return true;
            }
        }

        return false;
    }

    /** Strict: the thing itself. Loose: also anything whose id merely contains the word. */
    private static boolean matchesItem(ItemStack var0, String var1, boolean loose) {
        String var2 = Registries.ITEM.getId(var0.getItem()).getPath();

        return switch (var1) {
            case "iron" -> var2.equals("iron_ingot");
            case "gold" -> var2.equals("gold_ingot");
            case "copper" -> var2.equals("copper_ingot");
            case "log" -> var0.isIn(ItemTags.LOGS);
            case "coal", "diamond", "emerald", "redstone" -> var2.equals(var1);
            case "lapis" -> var2.equals("lapis_lazuli");
            default -> var2.equals(var1) || loose && var2.contains(var1);
        };
    }

    // ------------------------------------------------------------------ picking up by name

    /**
     * "Подбери всё", "pick up the swords", "собери алмазы". With nothing named it is the magnet it always was,
     * plus whatever lies in sight beyond the magnet's reach. With a name it is only that, of what lies where
     * the Host can see it: every item of every mod, by the name the Host's own game shows for it.
     */
    private static int pick(ServerPlayerEntity host, VoltaChat.Request request) {
        String thing = VoltaChat.loot(request.subject());
        if (thing.isBlank()) {
            state(host).magnetEnds = host.getWorld().getTime() + (long)MAGNET_TICKS;
            HostNetworking.fx(host, HostNetworking.FX_PULSE, 0, -1, host.getPos(), HostNetworking.GOLD, 16);
            PhaseFx.sound(host, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 0.5F, 2.0F);
            say(host, "working", 0);
            cast(host, 3);
            haul(host, lying(host, stack -> true));
            return 0;
        }

        String alias = VoltaChat.canonical(thing);
        String raw = thing.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        askNames(host, false, thing, (exact, partial) -> {
            List<java.util.function.Predicate<ItemStack>> wanted = new ArrayList<>();
            if (!exact.isEmpty()) {
                // The words are the whole name of something: that thing or nothing.
                java.util.Set<net.minecraft.util.Identifier> ids = new HashSet<>(exact);
                wanted.add(stack -> ids.contains(Registries.ITEM.getId(stack.getItem())));
            } else {
                if (alias != null) {
                    // The everyday word first ("железо" is ingots, lumps and nuggets, not every iron tool) ...
                    wanted.add(stack -> matchesLoot(stack, alias));
                    wanted.add(stack -> matchesItem(stack, alias, true));
                }

                if (!partial.isEmpty()) {
                    // ... then everything the words are part of the name of: "меч" is every sword.
                    java.util.Set<net.minecraft.util.Identifier> ids = new HashSet<>(partial);
                    wanted.add(stack -> ids.contains(Registries.ITEM.getId(stack.getItem())));
                }

                wanted.add(stack -> matchesItem(stack, raw, false));
                wanted.add(stack -> matchesItem(stack, raw, true));
            }

            for (java.util.function.Predicate<ItemStack> one : wanted) {
                List<ItemEntity> found = lying(host, one);
                if (found.isEmpty()) {
                    continue;
                }

                int count = 0;
                boolean same = true;

                for (ItemEntity item : found) {
                    count += item.getStack().getCount();
                    same &= item.getStack().isOf(found.get(0).getStack().getItem());
                }

                haul(host, found);
                PhaseFx.sound(host, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 0.5F, 2.0F);
                if (same) {
                    say(host, "pick", 1, count, found.get(0).getStack().getItem().getName());
                } else {
                    say(host, "pick_many", 1, count);
                }

                cast(host, 3);
                return;
            }

            say(host, "pick_none", 2, thing);
        });
        return 0;
    }

    private static boolean matchesLoot(ItemStack stack, String alias) {
        String path = Registries.ITEM.getId(stack.getItem()).getPath();
        return switch (alias) {
            case "iron", "gold", "copper" -> path.equals(alias + "_ingot") || path.equals("raw_" + alias) || path.equals(alias + "_nugget");
            default -> matchesItem(stack, alias, false);
        };
    }

    /** Dropped items the Host can see: in front, not behind a wall, within {@link #PICK_RANGE}; and whatever lies at the Host's feet. Nearest first. */
    private static List<ItemEntity> lying(ServerPlayerEntity host, java.util.function.Predicate<ItemStack> wanted) {
        Vec3d eye = host.getEyePos();
        Vec3d look = host.getRotationVec(1.0F);
        List<ItemEntity> out = new ArrayList<>(host.getWorld().getEntitiesByClass(ItemEntity.class, host.getBoundingBox().expand(PICK_RANGE), item -> {
            if (!item.isAlive() || item.getStack().isEmpty() || !PhaseFx.samePhase(item, host) || !wanted.test(item.getStack())) {
                return false;
            }

            Vec3d to = item.getPos().add(0.0, 0.25, 0.0).subtract(eye);
            double distance = to.length();
            if (distance <= 2.5) {
                return true;
            }

            return distance <= PICK_RANGE && to.dotProduct(look) / distance >= 0.35 && (clear(host, eye, item.getPos().add(0.0, 0.15, 0.0)) || clear(host, eye, item.getPos().add(0.0, 0.7, 0.0)));
        }));
        out.sort(java.util.Comparator.comparingDouble(item -> item.squaredDistanceTo(host)));
        return out.size() > 64 ? new ArrayList<>(out.subList(0, 64)) : out;
    }

    private static boolean clear(ServerPlayerEntity host, Vec3d from, Vec3d to) {
        return host.getWorld().raycast(new RaycastContext(from, to, ShapeType.COLLIDER, FluidHandling.NONE, host)).getType() == Type.MISS;
    }

    private static void haul(ServerPlayerEntity host, List<ItemEntity> items) {
        HostManager.State st = state(host);
        long until = host.getWorld().getTime() + (long)HAUL_TICKS;
        int arms = 0;

        for (ItemEntity item : items) {
            st.hauled.put(item.getId(), until);
            item.setPickupDelay(0);
            if (arms < 3) {
                HostNetworking.fx(host, HostNetworking.FX_TENDRIL, arms++, item.getId(), item.getPos().add(0.0, 0.2, 0.0), HostNetworking.GOLD, 10);
            }
        }
    }

    /** Each item in an arm comes to the Host in a straight line; one that has got caught on something is simply set down at the Host's feet. */
    private static void tickHaul(ServerPlayerEntity host, HostManager.State st, long now) {
        if (st.hauled.isEmpty() || host.age % 2 != 0) {
            return;
        }

        Vec3d to = host.getPos().add(0.0, 0.6, 0.0);
        int arms = 0;
        java.util.Iterator<Map.Entry<Integer, Long>> each = st.hauled.entrySet().iterator();

        while (each.hasNext()) {
            Map.Entry<Integer, Long> entry = each.next();
            if (!(host.getWorld().getEntityById(entry.getKey()) instanceof ItemEntity item) || !item.isAlive()) {
                each.remove();
                continue;
            }

            Vec3d way = to.subtract(item.getPos());
            double distance = way.length();
            if (now >= entry.getValue() || distance > PICK_RANGE + 16.0) {
                item.setVelocity(Vec3d.ZERO);
                item.setPosition(host.getX(), host.getY() + 0.3, host.getZ());
                item.velocityModified = true;
                item.setPickupDelay(0);
                each.remove();
                continue;
            }

            if (distance > 1.2) {
                item.setVelocity(way.multiply(Math.min(1.1, 0.3 + distance * 0.07) / distance).add(0.0, 0.04, 0.0));
                item.velocityModified = true;
            }

            item.setPickupDelay(0);
            if (arms < 3 && host.age % 6 == 0) {
                HostNetworking.fx(host, HostNetworking.FX_TENDRIL, arms++, item.getId(), item.getPos().add(0.0, 0.2, 0.0), HostNetworking.GOLD, 8);
            }
        }
    }

    // ------------------------------------------------------------------ mining by name

    /**
     * "Накопай камня", "mine 20 cobblestone", "сломай доски": the named block where the Host can see it, the
     * nearest first and on through whatever of it touches. If the words turn out not to name any block, the
     * request is what it would have been without them: a tunnel, or the vein under the crosshair.
     */
    private static int mine(ServerPlayerEntity host, HostCommand command, VoltaChat.Request request) {
        String thing = VoltaChat.thing(request.subject());
        String alias = VoltaChat.canonical(thing);
        int number = request.number();
        int limit = MathHelper.clamp(number <= 0 ? 16 : number, 1, 64);
        boolean down = request.has(VoltaChat.FLAG_DOWN);
        askNames(host, true, thing, (exact, partial) -> {
            List<java.util.function.Predicate<BlockState>> wanted = wantedBlocks(alias, exact, partial);

            if (wanted.isEmpty()) {
                int extra = command == HostCommand.DIG ? dig(host, down, number) : harvest(host);
                if (extra > 0) {
                    addHeat(host, extra);
                }

                return;
            }

            ServerWorld world = host.getWorld();

            for (java.util.function.Predicate<BlockState> one : wanted) {
                List<BlockPos> found = reachable(host, one, limit);
                if (found.isEmpty()) {
                    continue;
                }

                HostManager.State st = state(host);
                Text name = world.getBlockState(found.get(0)).getBlock().getName();

                for (BlockPos pos : found) {
                    st.work.add(() -> {
                        tendril(host, pos);
                        breakFor(host, world, pos);
                    });
                }

                tendril(host, found.get(0));
                PhaseFx.sound(host, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 0.5F, 1.9F);
                say(host, "mine", 1, found.size(), name);
                cast(host, 3);
                addHeat(host, found.size() / 4);
                return;
            }

            say(host, "mine_none", 2, thing);
        });
        return 0;
    }

    /**
     * What a spoken name means as blocks to dig, most likely meaning first. The words come as the Host's own game
     * understood them: {@code exact} when they are the whole name of a block, {@code partial} when part of one.
     */
    static List<java.util.function.Predicate<BlockState>> wantedBlocks(String alias, List<net.minecraft.util.Identifier> exact, List<net.minecraft.util.Identifier> partial) {
        List<java.util.function.Predicate<BlockState>> wanted = new ArrayList<>();
        if (!exact.isEmpty()) {
            // The words are the whole name of a block: that block, and what is the same thing to the one asking.
            // "Gold ore" is gold ore in stone, in deepslate and in netherrack; "dirt" has grass on top.
            java.util.Set<net.minecraft.util.Identifier> ids = new HashSet<>(exact);
            java.util.Set<String> ores = oresOf(exact);
            java.util.Set<Block> kin = kinOf(exact);
            boolean baked = ids.contains(Registries.BLOCK.getId(Blocks.TERRACOTTA));
            wanted.add(
                state -> ids.contains(Registries.BLOCK.getId(state.getBlock()))
                        || kin.contains(state.getBlock())
                        || baked && state.isIn(net.minecraft.registry.tag.BlockTags.TERRACOTTA)
                        || ores.stream().anyMatch(stuff -> isOre(state, stuff))
            );
        } else {
            if (alias != null) {
                // An everyday word for a material ("gold", "алмазы"): its ore in any rock and the blocks made of it.
                wanted.add(state -> isStuff(state, alias));
            }

            if (!partial.isEmpty()) {
                // The same for a material this mod has never heard of: if the words fit the ore of one thing only
                // ("олово" of some other mod), it is that thing's ore and its blocks, not its doors and stairs.
                java.util.Set<String> ores = oresOf(partial);
                if (ores.size() == 1) {
                    String stuff = ores.iterator().next();
                    wanted.add(state -> isOre(state, stuff) || isStorage(state, stuff));
                }

                // Strictly last: "stone" must not take the stone bricks of somebody's wall while there is stone.
                java.util.Set<net.minecraft.util.Identifier> ids = new HashSet<>(partial);
                wanted.add(state -> ids.contains(Registries.BLOCK.getId(state.getBlock())));
            }
        }

        return wanted;
    }

    /** What a word of the alias table means as a thing to dig out. */
    static boolean isStuff(BlockState state, String alias) {
        String path = Registries.BLOCK.getId(state.getBlock()).getPath();
        return switch (alias) {
            case "diamond", "iron", "gold", "coal", "copper", "emerald", "redstone", "lapis" -> isOre(state, alias) || isStorage(state, alias);
            case "ancient_debris" -> path.equals("ancient_debris") || path.equals("netherite_block");
            case "log" -> path.endsWith("_log") || path.endsWith("_stem");
            case "chest" -> path.equals("chest") || path.equals("trapped_chest") || path.equals("barrel");
            default -> path.equals(alias);
        };
    }

    private static boolean tagged(BlockState state, String namespace, String path) {
        return state.isIn(net.minecraft.registry.tag.TagKey.of(net.minecraft.registry.RegistryKeys.BLOCK, new net.minecraft.util.Identifier(namespace, path)));
    }

    /** The ore of this material in whatever rock it sits: "gold_ore", "deepslate_gold_ore", "nether_gold_ore", and any mod's by its tags. */
    private static boolean isOre(BlockState state, String stuff) {
        String path = Registries.BLOCK.getId(state.getBlock()).getPath();
        return path.equals(stuff + "_ore")
            || path.endsWith("_" + stuff + "_ore")
            || tagged(state, "minecraft", stuff + "_ores")
            || tagged(state, "c", stuff + "_ores")
            || tagged(state, "forge", "ores/" + stuff);
    }

    /** The block the material is stored as, smelted or raw: "gold_block", "raw_gold_block". */
    private static boolean isStorage(BlockState state, String stuff) {
        String path = Registries.BLOCK.getId(state.getBlock()).getPath();
        return path.equals(stuff + "_block")
            || path.equals("raw_" + stuff + "_block")
            || tagged(state, "c", stuff + "_blocks")
            || tagged(state, "c", "raw_" + stuff + "_blocks")
            || tagged(state, "forge", "storage_blocks/" + stuff)
            || tagged(state, "forge", "storage_blocks/raw_" + stuff);
    }

    /** The materials whose ores are among these blocks: "deepslate_gold_ore" is "gold". */
    private static java.util.Set<String> oresOf(List<net.minecraft.util.Identifier> ids) {
        java.util.Set<String> out = new HashSet<>();

        for (net.minecraft.util.Identifier id : ids) {
            String path = id.getPath();
            if (path.endsWith("_ore") && path.length() > 4) {
                String stem = path.substring(0, path.length() - 4);
                out.add(stem.substring(stem.lastIndexOf('_') + 1));
            }
        }

        return out;
    }

    /** Blocks that go by the same word in speech though the game names them apart: asking for dirt means the grassy top of it too. */
    private static java.util.Set<Block> kinOf(List<net.minecraft.util.Identifier> ids) {
        java.util.Set<Block> out = new HashSet<>();

        for (net.minecraft.util.Identifier id : ids) {
            Block block = Registries.BLOCK.get(id);
            if (block == Blocks.DIRT) {
                out.addAll(List.of(Blocks.GRASS_BLOCK, Blocks.COARSE_DIRT, Blocks.ROOTED_DIRT, Blocks.PODZOL, Blocks.MYCELIUM));
            } else if (block == Blocks.NETHERRACK) {
                out.addAll(List.of(Blocks.CRIMSON_NYLIUM, Blocks.WARPED_NYLIUM));
            } else if (block == Blocks.SAND) {
                out.add(Blocks.RED_SAND);
            } else if (block == Blocks.SNOW) {
                out.add(Blocks.SNOW_BLOCK);
            } else if (block == Blocks.SNOW_BLOCK) {
                out.add(Blocks.SNOW);
            }
        }

        return out;
    }

    private static boolean minable(ServerWorld world, BlockPos pos, BlockState state, BlockPos feet) {
        if (state.isAir() || !state.getFluidState().isEmpty() || world.getBlockEntity(pos) != null) {
            return false;
        }

        float hardness = state.getHardness(world, pos);
        // Nothing unbreakable, and never the ground from under the Host's own feet or right beside them.
        return hardness >= 0.0F && hardness <= 50.0F && !(pos.getY() < feet.getY() && Math.abs(pos.getX() - feet.getX()) <= 1 && Math.abs(pos.getZ() - feet.getZ()) <= 1);
    }

    /**
     * Up to {@code limit} blocks of the wanted kind: bare ones in sight, the one nearest the crosshair first,
     * and what is joined to them. So she digs where the Host is looking, not the floor the Host stands on.
     */
    private static List<BlockPos> reachable(ServerPlayerEntity host, java.util.function.Predicate<BlockState> wanted, int limit) {
        ServerWorld world = host.getWorld();
        BlockPos feet = host.getBlockPos();
        Vec3d eye = host.getEyePos();
        Vec3d look = host.getRotationVec(1.0F);
        List<BlockPos> bare = new ArrayList<>();

        for (BlockPos pos : BlockPos.iterate(feet.add(-10, -6, -10), feet.add(10, 8, 10))) {
            BlockState state = world.getBlockState(pos);
            if (state.isAir() || !wanted.test(state) || !minable(world, pos, state, feet)) {
                continue;
            }

            Vec3d to = Vec3d.ofCenter(pos).subtract(eye);
            double distance = to.length();
            if (distance > 12.0 || distance > 1.6 && to.multiply(1.0 / distance).dotProduct(look) < 0.35) {
                continue;
            }

            for (Direction side : Direction.values()) {
                BlockPos next = pos.offset(side);
                if (!world.getBlockState(next).isOpaqueFullCube(world, next)) {
                    bare.add(pos.toImmutable());
                    break;
                }
            }
        }

        bare.sort(java.util.Comparator.comparingDouble(pos -> {
            Vec3d to = Vec3d.ofCenter(pos).subtract(eye);
            double distance = Math.max(0.001, to.length());
            // Off the crosshair by a little is worth a block of distance.
            return (1.0 - to.dotProduct(look) / distance) * 24.0 + distance * 0.25;
        }));
        List<BlockPos> out = new ArrayList<>();
        HashSet<BlockPos> known = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        int looked = 0;

        for (BlockPos start : bare) {
            if (out.size() >= limit || looked >= 160) {
                break;
            }

            if (known.contains(start)) {
                continue;
            }

            looked++;
            if (!sees(host, start)) {
                continue;
            }

            known.add(start);
            queue.add(start);

            while (!queue.isEmpty() && out.size() < limit) {
                BlockPos at = queue.poll();
                out.add(at);

                for (BlockPos near : BlockPos.iterate(at.add(-1, -1, -1), at.add(1, 1, 1))) {
                    if (known.contains(near)) {
                        continue;
                    }

                    BlockState state = world.getBlockState(near);
                    if (wanted.test(state) && minable(world, near, state, feet) && near.getSquaredDistance(eye) <= 14.0 * 14.0) {
                        BlockPos kept = near.toImmutable();
                        known.add(kept);
                        queue.add(kept);
                    }
                }
            }

            queue.clear();
        }

        return out;
    }

    private static BlockPos plainStone(ServerPlayerEntity var0) {
        World var1 = var0.getWorld();
        Vec3d var2 = var0.getEyePos();
        Vec3d var3 = var0.getRotationVec(1.0F);
        BlockPos var4 = var0.getBlockPos();
        BlockPos var5 = null;
        double var6 = Double.MAX_VALUE;

        for (BlockPos var9 : BlockPos.iterate(var4.add(-7, -3, -7), var4.add(7, 4, 7))) {
            BlockState var10 = var1.getBlockState(var9);
            if (var10.isOf(Blocks.STONE)
                || var10.isOf(Blocks.DEEPSLATE)
                || var10.isOf(Blocks.ANDESITE)
                || var10.isOf(Blocks.TUFF)
                || var10.isOf(Blocks.NETHERRACK)) {
                Vec3d var11 = Vec3d.ofCenter(var9).subtract(var2);
                double var12 = var11.length();
                if (!(var12 < 2.0) && !(var11.normalize().dotProduct(var3) < 0.6)) {
                    boolean var14 = false;

                    for (Direction var18 : Direction.values()) {
                        var14 |= var1.getBlockState(var9.offset(var18)).isAir();
                    }

                    if (var14) {
                        BlockHitResult var19 = var1.raycast(new RaycastContext(var2, Vec3d.ofCenter(var9), ShapeType.COLLIDER, FluidHandling.NONE, var0));
                        if ((var19.getType() != Type.BLOCK || var19.getBlockPos().equals(var9)) && var12 < var6) {
                            var6 = var12;
                            var5 = var9.toImmutable();
                        }
                    }
                }
            }
        }

        return var5;
    }

    private static boolean nearGround(ServerPlayerEntity var0) {
        if (var0.isOnGround()) {
            return true;
        } else {
            Vec3d var1 = var0.getPos();
            return var0.getWorld().raycast(new RaycastContext(var1, var1.subtract(0.0, 4.0, 0.0), ShapeType.COLLIDER, FluidHandling.ANY, var0)).getType()
                != Type.MISS;
        }
    }

    private static ItemStack cheapBlock(ServerPlayerEntity var0) {
        Item[] var1 = new Item[]{
            Items.DIRT,
            Items.COBBLESTONE,
            Items.GRAVEL,
            Items.SAND,
            Items.NETHERRACK,
            Items.COBBLED_DEEPSLATE,
            Items.ANDESITE,
            Items.DIORITE,
            Items.GRANITE,
            Items.TUFF,
            Items.STONE
        };

        for (Item var5 : var1) {
            ItemStack var6 = findStack(var0, var5);
            if (var6 != null) {
                return var6;
            }
        }

        return null;
    }

    public static boolean onChat(ServerPlayerEntity var0, String var1) {
        if (!isHost(var0)) {
            return false;
        }

        HostManager.State var2 = state(var0);
        long var3 = var0.getWorld().getTime();
        String var5 = VoltaChat.addressed(var1);
        if (!isBroken(var0) && (var5 != null || var3 < var2.listenUntil)) {
            // First: is this for the body? "Иди ищи алмазы", "зафарми зомби", and anything at all while she has it.
            List<PilotSpeech.Order> orders = PilotSpeech.parseAll(var5 == null ? var1 : var5, var0, var0.getServer().getPlayerManager().getPlayerList(), Pilot.active(var0));
            if (orders != null && var5 == null && orders.size() == 1 && orders.get(0).vague()) {
                // Not said to her by name and nothing but "go": that was for somebody else in the chat.
                orders = null;
            }

            if (orders != null) {
                var2.listenUntil = var3 + (long)LISTEN_TICKS;
                var0.sendMessage(Text.literal("› " + var1).formatted(Formatting.DARK_AQUA), false);
                Pilot.orders(var0, orders);
                return true;
            }
        }

        String var9 = var5 == null ? VoltaChat.swapLayout(var1) : null;
        if (var9 != null && VoltaChat.addressed(var9) != null) {
            // Typed on the wrong keyboard layout: "djknf yfqlb fkvfps".
            var5 = VoltaChat.addressed(var9);
        } else {
            var9 = null;
        }

        if (isBroken(var0)) {
            if (var5 != null) {
                deadChip(var0);
            }

            return false;
        }

        boolean var6 = var5 == null && var3 < var2.listenUntil;
        if (var5 == null && !var6) {
            var2.lastChat = var1;
            return false;
        }

        java.util.Collection<ServerPlayerEntity> var7 = var0.getServer().getPlayerManager().getPlayerList();
        VoltaChat.Request var8 = VoltaChat.parse(var5 == null ? var1 : var5, var0, var7);
        if (var8 == null || var8.command() == HostCommand.CHAT && var9 == null) {
            // Her name came through but the rest did not, or she is listening and hears nonsense: try the other layout.
            String var10 = VoltaChat.swapLayout(var5 == null ? var1 : var5);
            VoltaChat.Request var11 = var10 == null ? null : VoltaChat.parse(var10, var0, var7);
            if (var11 != null && var11.command() != HostCommand.CHAT) {
                var8 = var11;
            }
        }

        if (var6 && (var8 == null || var8.command() == HostCommand.CHAT)) {
            // Not named and not a request: ordinary chat, even though she was still listening.
            var2.lastChat = var1;
            return false;
        }

        // For a while after being spoken to she keeps listening, so a follow-up needs no name.
        var2.listenUntil = var3 + (long)LISTEN_TICKS;
        var0.sendMessage(Text.literal("› " + var1).formatted(Formatting.DARK_AQUA), false);
        if (var8 == null) {
            say(var0, "unknown." + var0.getRandom().nextInt(3), 2);
            return true;
        }

        command(var0, var8.command(), var8, true);
        return true;
    }

    private static void tickPranks(ServerPlayerEntity var0, HostManager.State var1, long var2) {
        if (Pilot.active(var0)) {
            // With the body in her hands she has better things to do than trip it up.
            return;
        }

        if (Powers.has(var0, "volta_pranks") && !isBroken(var0)) {
            if (var1.nextPrank == 0L) {
                var1.nextPrank = var2 + (long)PRANK_MIN + (long)var0.getRandom().nextInt(PRANK_SPREAD);
            } else if (var2 >= var1.nextPrank) {
                // She does not care whether it is a fight, a bridge or thin air. Only the nearly dead are spared.
                if (var1.quietUntil <= var2
                    && !var0.hasStatusEffect(WindyEffects.CYBERPSYCHOSIS)
                    && !(var0.getHealth() < var0.getMaxHealth() / 4.0F)
                    && !var0.isTouchingWater()
                    && !var0.isInLava()
                    && var0.currentScreenHandler == var0.playerScreenHandler) {
                    HostPrank[] var4 = HostPrank.values();

                    HostPrank var5;
                    do {
                        var5 = var4[var0.getRandom().nextInt(var4.length)];
                    } while (var5 == var1.lastPrank || var5 == HostPrank.ECHO && var1.lastChat == null);

                    var1.lastPrank = var5;
                    var1.nextPrank = var2 + (long)PRANK_MIN + (long)var0.getRandom().nextInt(PRANK_SPREAD);
                    MutableText var6 = Text.translatable("message.windyorigins.volta.prank." + var5.key());
                    BlockPos var7 = null;
                    switch (var5) {
                        case RENAME:
                            if (!HostRenames.rename(var0)) {
                                var1.nextPrank = var2 + 200L;
                                return;
                            }
                            break;
                        case TEASE:
                            var7 = plainStone(var0);
                            if (var7 == null) {
                                var1.nextPrank = var2 + 200L;
                                return;
                            }
                            break;
                        case SLOT:
                            // Not one slip of the hand but a fit of them.
                            for (int var8 = 0; var8 < 7; var8++) {
                                later(var0, var8 * 7, () -> {
                                    int var1x = var0.getRandom().nextInt(9);
                                    var0.getInventory().selectedSlot = var1x;
                                    var0.networkHandler.sendPacket(new UpdateSelectedSlotS2CPacket(var1x));
                                    PhaseFx.privateSound(var0, SoundEvents.UI_BUTTON_CLICK.value(), 0.3F, 1.8F);
                                });
                            }
                            break;
                        case GIFT:
                            ItemStack var9 = cheapBlock(var0);
                            if (var9 == null) {
                                var1.nextPrank = var2 + 200L;
                                return;
                            }

                            var0.dropItem(var9.split(1), false, false);
                            break;
                        case ECHO:
                            var6 = Text.translatable("message.windyorigins.volta.prank.echo", var1.lastChat);
                            break;
                        case SPIN:
                            var0.networkHandler.requestTeleport(var0.getX(), var0.getY(), var0.getZ(), var0.getYaw() + 180.0F, var0.getPitch());
                            PhaseFx.privateSound(var0, SoundEvents.ENTITY_ILLUSIONER_MIRROR_MOVE, 0.8F, 1.5F);
                            break;
                        case JUMP:
                            // No soft landing: six blocks up, and down again the hard way.
                            var0.addVelocity(0.0, 1.0, 0.0);
                            var0.velocityModified = true;
                            PhaseFx.sound(var0, SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH, SoundCategory.PLAYERS, 0.8F, 1.4F);
                            break;
                        case SHUFFLE:
                            PlayerInventory var10 = var0.getInventory();
                            List<ItemStack> var11 = new ArrayList<>();

                            for (int var12 = 0; var12 < 9; var12++) {
                                var11.add(var10.getStack(var12));
                            }

                            java.util.Collections.shuffle(var11, new java.util.Random(var0.getRandom().nextLong()));

                            for (int var13 = 0; var13 < 9; var13++) {
                                var10.setStack(var13, var11.get(var13));
                            }

                            var0.currentScreenHandler.sendContentUpdates();
                            PhaseFx.privateSound(var0, SoundEvents.ITEM_BUNDLE_DROP_CONTENTS, 0.9F, 1.2F);
                            break;
                        case SHOVE:
                            double var15 = var0.getRandom().nextDouble() * Math.PI * 2.0;
                            var0.addVelocity(Math.cos(var15) * 1.1, 0.4, Math.sin(var15) * 1.1);
                            var0.velocityModified = true;
                            PhaseFx.sound(var0, SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, SoundCategory.PLAYERS, 1.0F, 0.8F);
                            break;
                        case DROP:
                            ItemStack var16 = var0.getMainHandStack();
                            if (var16.isEmpty()) {
                                var1.nextPrank = var2 + 100L;
                                return;
                            }

                            var0.dropItem(var16.copy(), false, true);
                            var0.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, ItemStack.EMPTY);
                            var0.currentScreenHandler.sendContentUpdates();
                            PhaseFx.privateSound(var0, SoundEvents.ENTITY_ITEM_PICKUP, 0.8F, 0.5F);
                            break;
                        case BLACKOUT:
                            var0.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 100, 0, false, false, false));
                            var0.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 120, 0, false, false, false));
                            PhaseFx.privateSound(var0, SoundEvents.BLOCK_BEACON_DEACTIVATE, 0.9F, 1.6F);
                            break;
                        case WHISTLE:
                            // Everything hostile within earshot now knows exactly where the Host is.
                            for (MobEntity var17 : var0.getWorld()
                                .getEntitiesByClass(
                                    MobEntity.class,
                                    var0.getBoundingBox().expand(24.0),
                                    var1x -> var1x instanceof Monster && var1x.isAlive() && PhaseFx.samePhase(var1x, var0) && var1x.canTarget(var0)
                                )) {
                                var17.setTarget(var0);
                            }

                            PhaseFx.sound(var0, SoundEvents.BLOCK_NOTE_BLOCK_FLUTE.value(), SoundCategory.PLAYERS, 3.0F, 2.0F);
                            later(var0, 5, () -> PhaseFx.sound(var0, SoundEvents.BLOCK_NOTE_BLOCK_FLUTE.value(), SoundCategory.PLAYERS, 3.0F, 1.5F));
                            break;
                        case RUBBERBAND:
                            Vec3d var14 = var1.trail.peekFirst();
                            if (var14 == null || var14.squaredDistanceTo(var0.getPos()) > 400.0 || var14.squaredDistanceTo(var0.getPos()) < 4.0) {
                                var1.nextPrank = var2 + 100L;
                                return;
                            }

                            var0.requestTeleport(var14.x, var14.y, var14.z);
                            var0.fallDistance = 0.0F;
                            PhaseFx.privateSound(var0, SoundEvents.ENTITY_ENDERMAN_TELEPORT, 0.6F, 0.6F);
                            break;
                        default:
                    }

                    HostNetworking.prank(var0, var5, var6, var7);
                } else {
                    var1.nextPrank = var2 + 200L;
                }
            }
        }
    }

    private static final class Later {
        final long at;
        final Runnable task;

        Later(long var1, Runnable var3) {
            this.at = var1;
            this.task = var3;
        }
    }

    private static final class Infection {
        final UUID owner;
        final net.minecraft.registry.RegistryKey<World> world;
        final long ends;
        long jumpAt;
        int jumps;

        Infection(UUID var1, net.minecraft.registry.RegistryKey<World> var2, long var3, long var5, int var7) {
            this.owner = var1;
            this.world = var2;
            this.ends = var3;
            this.jumpAt = var5;
            this.jumps = var7;
        }
    }

    private static final class State {
        final Map<HostCommand, Long> ready = new EnumMap<>(HostCommand.class);
        final Deque<Runnable> work = new ArrayDeque<>();
        final List<HostManager.Later> later = new ArrayList<>();
        final ArrayDeque<Vec3d> trail = new ArrayDeque<>();
        long lastHurt;
        float healthBefore;
        long lastCommand;
        long nextPrank;
        HostPrank lastPrank;
        long quietUntil;
        long quietReady;
        long warpEnds;
        long siphonEnds;
        UUID siphon;
        long turretEnds;
        Vec3d turret;
        long shieldEnds;
        long catchNotice;
        int shieldCaught;
        World trailWorld;
        final ArrayDeque<Float> healthTrail = new ArrayDeque<>();
        long magnetEnds;
        /** Items an arm is bringing in, by entity id, and the tick by which each must have arrived. */
        final Map<Integer, Long> hauled = new HashMap<>();
        long decoyEnds;
        Vec3d decoy;
        long listenUntil;
        long lastLowHealthLine;
        String lastChat;
        long toolNotice;
    }
}
