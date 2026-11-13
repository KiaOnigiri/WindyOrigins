package com.windykindy49.windyorigins.host;

import com.windykindy49.windyorigins.common.PhaseFx;
import com.windykindy49.windyorigins.common.WindyEffects;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.ai.pathing.PathNode;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.PickaxeItem;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.gen.structure.Structure;

/**
 * Volta with the Host's body. She does not get a body of her own for it and she does not get powers: she
 * decides where the feet go and what the hands do, and the Host's own game presses the keys (see
 * {@code PilotClient}). So she walks at walking speed, mines at the speed of the tool in the hand, hits when
 * the weapon has swung back, sees what can be seen from where the eyes are, and can fall, burn and die.
 *
 * It costs: the chip heats for as long as she holds the body, and at the top of the scale it does what an
 * overheated chip always does.
 */
public final class Pilot {
    public static final int L_HOLD = 0;
    public static final int L_WALK = 1;
    public static final int L_HUNT = 2;
    public static final int L_GUARD = 3;
    public static final int L_MINE = 4;
    public static final int L_SEARCH = 5;
    public static final int L_EXPLORE = 6;
    public static final int L_CLEAR = 7;
    public static final int L_COLLECT = 8;
    public static final int L_FLEE = 9;
    public static final int L_FOLLOW = 10;
    public static final int L_EAT = 11;
    public static final int L_HOME = 12;
    public static final int L_CHOP = 13;
    public static final int L_SLEEP = 14;
    public static final int L_CRAFT = 15;
    public static final int L_SMELT = 16;
    public static final int L_BUILD = 17;
    /** Not her Host's body: somebody else's, on strings, for a few seconds. */
    public static final int L_PUPPET = 18;
    public static final int L_STORE = 19;
    public static final int PUPPET_TICKS = 80;
    private static final int SPRINT = 1;
    private static final int SNEAK = 2;
    private static final int JUMP = 4;
    private static final int USE = 8;
    /** The inventory is open on the Host's screen: she is rummaging in the pockets. */
    private static final int BAG = 16;
    /** One click of the use button with what is in the hand: a bucket emptied or filled. */
    private static final int CLICK = 32;
    /** Several on her at once: the blows are struck from the ground, so that the sword sweeps. */
    private static final int SWEEP = 64;
    /** The creature named is to be got onto, not struck: a boat. */
    private static final int MOUNT = 128;
    /**
     * Two points of heat in this many ticks while she works, one while she only holds the body and waits: with
     * what taking it costs, a cold chip is at a hundred in forty-five seconds of work.
     */
    static final int HEAT_EVERY = 19;
    /** Taking the body is a request like any other. */
    static final int HEAT_TAKE = 5;
    /** She lets go of the body by herself before the chip cooks, and will not take it this near to that. */
    static final int HEAT_STOP = 95;
    static final int HEAT_REFUSE = 80;
    private static final double REACH = 4.0;
    /** -Dwindy.pilotlog=true: a line a second on what she is doing with the body, for whoever is chasing a bug. */
    private static final boolean LOG = Boolean.getBoolean("windy.pilotlog");
    private static final Map<UUID, Run> RUNS = new HashMap<>();
    /** What was asked for after the job in hand: "find a village, then kill everything there". */
    private static final Map<UUID, ArrayDeque<PilotSpeech.Order>> NEXT = new HashMap<>();
    /** The last thing each Host asked for, for "again". */
    private static final Map<UUID, List<PilotSpeech.Order>> LAST = new HashMap<>();
    /** Where each Host stood when they last asked for something: "...and come back" comes back here. */
    private static final Map<UUID, Vec3d> FROM = new HashMap<>();
    /** The request each job in hand came from. */
    private static final Map<UUID, PilotSpeech.Order> CURRENT = new HashMap<>();
    /** What was broken off, by the heat or by the Host's own hands, and what was still to come after it: for "carry on". */
    private static final Map<UUID, List<PilotSpeech.Order>> PAUSED = new HashMap<>();
    /** What each Host carried when she first took the body, or last put the takings away: what is over that is "the loot". */
    private static final Map<UUID, Map<net.minecraft.item.Item, Integer>> HAD = new HashMap<>();
    /** When she last had to dig a thing out of the inventory proper. */
    private static final Map<UUID, Long> BAGGED = new HashMap<>();
    /** When each slot of the hotbar was last what a job called for. */
    private static final Map<UUID, long[]> HANDS = new HashMap<>();

    private Pilot() {
    }

    /** One job. */
    interface Task {
        int label();

        /** One tick of the job. False once it is over. */
        boolean tick(Run run, ServerPlayerEntity host);

        /** Standing by while something gets done by itself: the chip works half as hard. */
        default boolean resting() {
            return false;
        }
    }

    static final class Run {
        Task task;
        /** Taken "until told otherwise": when a job ends she keeps the body and waits for the next. */
        boolean held;
        int ticks;
        /** Ticks spent with nothing to do and no leave to wait. */
        int idle;
        ZombieEntity scout;
        /** The ladder or vine she last picked to change floors by. */
        BlockPos ladder;
        int ladderAt;
        BlockPos climbGoal;
        int climbUntil;
        Path path;
        BlockPos pathGoal;
        long pathAt;
        Vec3d lastSpot;
        int stuck;
        int burrowFor;
        int turn;
        int turnFor;
        // What the Host's game is told to do next.
        final List<Vec3d> way = new ArrayList<>();
        BlockPos breakPos;
        BlockPos usePos;
        /** A block to be set against this face of that block, from the hand. */
        BlockPos placeAgainst;
        Direction placeFace;
        int attackId = -1;
        Vec3d look;
        int flags;
        int slot = -1;
        int label;
        int sentHash;
        long sentAt;
        /** Crossing water by the boat from the pockets: how far along, the boat, and when she may try again. */
        int sail;
        int sailTicks;
        int boatId = -1;
        int sailAgain;
        /** Shulker boxes out of the pockets that she has set down and has to take up again. */
        final List<BlockPos> boxes = new ArrayList<>();
        /** Whose Volta has this body on strings, when it is not the body's own; and until when. */
        UUID puppetOf;
        long puppetUntil;
        /** Badly hurt and getting out of the way of what did it. */
        boolean retreat;
        /** The bow is down for this many ticks more between one arrow and the next. */
        int drawRest;
        /** A screen of a table, an oven or a chest that she opened and has to shut. */
        boolean opened;
        /** Where the body last stood on dry ground, and whether she is on her way back there for air. */
        Vec3d dry;
        boolean gasp;
        /** Water she poured over the body's own feet, to be taken back into the bucket. */
        BlockPos water;
        int waterFor;
        /** A cliff she is going down by a waterfall of her own: the edge, which way, how deep, how far along. */
        BlockPos fallEdge;
        Direction fallDir;
        int fallDepth;
        int fallStage;
        int fallTicks;
        int fallAgain;
        /** Why the job ends, when it is not simply done. */
        String ending;
        Object[] endingArgs = new Object[0];

        void end(String key, Object... args) {
            this.ending = key;
            this.endingArgs = args;
        }

        void clear() {
            this.way.clear();
            this.breakPos = null;
            this.usePos = null;
            this.placeAgainst = null;
            this.placeFace = null;
            this.attackId = -1;
            this.look = null;
            this.flags = 0;
        }
    }

    // ------------------------------------------------------------------ taking and giving back

    public static boolean active(PlayerEntity host) {
        return RUNS.containsKey(host.getUuid());
    }

    public static void reset() {
        NEXT.clear();
        LAST.clear();
        FROM.clear();
        CURRENT.clear();
        HAD.clear();
        PAUSED.clear();
        BAGGED.clear();
        HANDS.clear();
        RUNS.clear();
    }

    static Run run(ServerPlayerEntity host) {
        return RUNS.get(host.getUuid());
    }

    /**
     * She takes the body, for this job or until told otherwise. False when she will not: the chip is cooking,
     * or the Host is in water, where she refuses everything.
     */
    static boolean begin(ServerPlayerEntity host, Task task, boolean held) {
        Run run = RUNS.get(host.getUuid());
        if (run != null && run.puppetOf != null) {
            return false;
        }

        if (run == null) {
            if (HostManager.isBroken(host) || host.hasStatusEffect(WindyEffects.CYBERPSYCHOSIS) || host.isSpectator() || !host.isAlive()) {
                return false;
            }

            if (HostManager.heat(host) >= HEAT_REFUSE) {
                HostManager.say(host, "pilot_hot", 3);
                return false;
            }

            run = new Run();
            run.lastSpot = host.getPos();
            RUNS.put(host.getUuid(), run);
            HAD.putIfAbsent(host.getUuid(), Recipes.stock(host));
            HostManager.addHeat(host, HEAT_TAKE);
            if (host.hasStatusEffect(WindyEffects.CYBERPSYCHOSIS)) {
                RUNS.remove(host.getUuid());
                return false;
            }

            PhaseFx.privateSound(host, net.minecraft.sound.SoundEvents.BLOCK_BEACON_POWER_SELECT, 0.6F, 1.6F);
            if (task == null) {
                HostManager.say(host, "pilot_on", 1);
            }
        }

        run.held |= held;
        run.task = task;
        run.ending = null;
        run.path = null;
        run.stuck = 0;
        run.burrowFor = 0;
        return true;
    }

    private static final Set<String> SAYS_IT_ALL = Set.of("pilot_off", "pilot_taken", "pilot_hurt", "pilot_sleep", "pilot_hot_stop", "pilot_hot");

    /** The body goes back to the Host. With a line when there is something to say about it. */
    public static void release(ServerPlayerEntity host, String line, Object... args) {
        NEXT.remove(host.getUuid());
        CURRENT.remove(host.getUuid());
        BAGGED.remove(host.getUuid());
        Run was = RUNS.remove(host.getUuid());
        if (was == null) {
            return;
        }

        shut(was, host);
        if (was.puppetOf != null) {
            // Somebody else's body, let go of: no word from her to them, only the strings gone.
            PacketByteBuf off = new PacketByteBuf(Unpooled.buffer());
            off.writeBoolean(false);
            ServerPlayNetworking.send(host, HostNetworking.PILOT, off);
            host.sendMessage(Text.translatable("message.windyorigins.puppet.free"), true);
            return;
        }


        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeBoolean(false);
        ServerPlayNetworking.send(host, HostNetworking.PILOT, buf);
        if (HostManager.isHost(host)) {
            // The body is only just the Host's again: half a minute before she starts tripping it up.
            HostManager.calm(host, 600);
        }

        PhaseFx.privateSound(host, net.minecraft.sound.SoundEvents.BLOCK_BEACON_DEACTIVATE, 0.6F, 1.6F);
        if (line != null && SAYS_IT_ALL.contains(line)) {
            HostManager.say(host, line, 2, args);
        } else if (line != null) {
            // How the job went, and that the body is the Host's again.
            String keys = "message.windyorigins.volta.";
            HostManager.say(host, "pilot_two", 2, Text.translatable(keys + line, args), Text.translatable(keys + "pilot_back"));
        }
    }

    /** The Host left the server: nothing of theirs is kept. */
    public static void forget(ServerPlayerEntity host) {
        RUNS.remove(host.getUuid());
        NEXT.remove(host.getUuid());
        LAST.remove(host.getUuid());
        FROM.remove(host.getUuid());
        CURRENT.remove(host.getUuid());
        PAUSED.remove(host.getUuid());
        BAGGED.remove(host.getUuid());
        HANDS.remove(host.getUuid());
        HAD.remove(host.getUuid());
    }

    /** The job in hand and what was to follow it are kept for "carry on". False when there was no job. */
    private static boolean remember(ServerPlayerEntity host) {
        Run run = RUNS.get(host.getUuid());
        PilotSpeech.Order now = CURRENT.get(host.getUuid());
        if (run == null || run.task == null || now == null) {
            return false;
        }

        List<PilotSpeech.Order> rest = new ArrayList<>();
        rest.add(now);
        ArrayDeque<PilotSpeech.Order> next = NEXT.get(host.getUuid());
        if (next != null) {
            rest.addAll(next);
        }

        PAUSED.put(host.getUuid(), rest);
        return true;
    }

    /** The Host put their hands back on the keys. */
    public static void taken(ServerPlayerEntity host) {
        Run run = RUNS.get(host.getUuid());
        if (run != null && run.puppetOf != null) {
            // Pulled free of the strings.
            ServerPlayerEntity master = host.getServer().getPlayerManager().getPlayer(run.puppetOf);
            release(host, null);
            if (master != null) {
                HostManager.say(master, "puppet_broke", 2, host.getDisplayName());
            }

            return;
        }

        remember(host);
        release(host, "pilot_taken");
    }

    /**
     * The strings on another player: for a few seconds their body is hers. She turns it on whatever enemy of her
     * Host's stands nearest, with the weapon it carries; with nobody to hit, she makes it bow and scrape. Its
     * owner pulls free by hammering the jump key. Nothing here can walk it off a cliff: it goes where a mob
     * would go, and only as far as the fight.
     */
    public static boolean puppet(ServerPlayerEntity master, ServerPlayerEntity victim, int ticks) {
        if (!victim.isAlive() || victim.isSpectator() || victim == master) {
            return false;
        }

        if (RUNS.containsKey(victim.getUuid())) {
            // Their own Volta had the body: she loses it to this one.
            release(victim, null);
        }

        Run run = new Run();
        run.lastSpot = victim.getPos();
        run.puppetOf = master.getUuid();
        run.puppetUntil = victim.getWorld().getTime() + (long)ticks;
        run.task = new Marionette(master);
        RUNS.put(victim.getUuid(), run);
        victim.sendMessage(Text.translatable("message.windyorigins.puppet.caught", master.getDisplayName()), true);
        PhaseFx.privateSound(victim, net.minecraft.sound.SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.7F, 1.9F);
        return true;
    }

    public static boolean puppeted(PlayerEntity player) {
        Run run = RUNS.get(player.getUuid());
        return run != null && run.puppetOf != null;
    }

    static final class Marionette implements Task {
        private final ServerPlayerEntity master;
        private LivingEntity foe;

        Marionette(ServerPlayerEntity master) {
            this.master = master;
        }

        @Override
        public int label() {
            return L_PUPPET;
        }

        @Override
        public boolean tick(Run run, ServerPlayerEntity host) {
            if (this.foe != null && (!this.foe.isAlive() || this.foe.squaredDistanceTo(host) > 16.0 * 16.0)) {
                this.foe = null;
            }

            if (this.foe == null || run.ticks % 10 == 0) {
                double best = this.foe == null ? 12.0 * 12.0 : this.foe.squaredDistanceTo(host);

                for (LivingEntity e : host.getWorld().getEntitiesByClass(LivingEntity.class, host.getBoundingBox().expand(12.0, 5.0, 12.0), x -> x != host && x != this.master && x.isAlive() && PhaseFx.samePhase(x, host))) {
                    boolean enemy = e instanceof Monster || e instanceof PlayerEntity && HostManager.fairGame(this.master, e);
                    double d = e.squaredDistanceTo(host);
                    if (enemy && d < best && host.canSee(e)) {
                        best = d;
                        this.foe = e;
                    }
                }
            }

            if (this.foe != null) {
                fight(run, host, this.foe);
                return true;
            }

            // Nobody to set it on: it faces her Host and bobs, like something on strings.
            run.look = this.master.getEyePos();
            if (run.ticks / 5 % 2 == 0) {
                run.flags |= SNEAK;
            }

            if (run.ticks % 20 == 10) {
                run.flags |= JUMP;
            }

            return true;
        }
    }

    private static void tickPuppet(Run run, ServerPlayerEntity host) {
        ServerPlayerEntity master = host.getServer().getPlayerManager().getPlayer(run.puppetOf);
        if (!host.isAlive() || host.isSpectator() || master == null || !master.isAlive() || master.getWorld() != host.getWorld() || host.getWorld().getTime() >= run.puppetUntil) {
            release(host, null);
            return;
        }

        run.ticks++;
        run.clear();
        run.label = L_PUPPET;
        run.task.tick(run, host);
        if (run.ticks % 4 == 0) {
            strings(host, 0.94F, 0.24F, 0.78F);
        }

        send(host, run);
    }

    /** What those around see: two dotted lines going straight up from the shoulders. */
    private static void strings(ServerPlayerEntity host, float r, float g, float b) {
        net.minecraft.particle.DustParticleEffect dust = new net.minecraft.particle.DustParticleEffect(new org.joml.Vector3f(r, g, b), 0.6F);
        double yaw = Math.toRadians((double)host.bodyYaw);

        for (int side = -1; side <= 1; side += 2) {
            double x = host.getX() + Math.cos(yaw) * 0.32 * (double)side;
            double z = host.getZ() + Math.sin(yaw) * 0.32 * (double)side;

            for (int i = 0; i < 5; i++) {
                PhaseFx.particles(host, dust, x, host.getY() + (double)host.getHeight() * 0.8 + 0.45 * (double)i + (double)(host.age % 8) * 0.05, z, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }

    /** From the wheel: take the body and wait for orders, or give it back. */
    public static void toggle(ServerPlayerEntity host) {
        if (active(host)) {
            release(host, "pilot_off");
        } else {
            begin(host, null, true);
        }
    }

    public static void tick(ServerPlayerEntity host) {
        Run run = RUNS.get(host.getUuid());
        if (run == null) {
            return;
        }

        if (run.puppetOf != null) {
            tickPuppet(run, host);
            return;
        }

        if (!HostManager.isHost(host) || !host.isAlive() || host.isSpectator() || HostManager.isBroken(host) || host.hasStatusEffect(WindyEffects.CYBERPSYCHOSIS)) {
            release(host, null);
            return;
        }

        run.ticks++;
        if (run.ticks * 2 % (run.task == null || run.task.resting() ? HEAT_EVERY * 2 : HEAT_EVERY) < 2) {
            if (HostManager.heat(host) >= HEAT_STOP) {
                // The chip is about to cook: she lets go by herself and remembers where she was.
                release(host, remember(host) ? "pilot_hot_stop" : "pilot_hot");
                return;
            }

            int heat = HostManager.heat(host);
            if (heat >= 85) {
                // She said it was running hot when it got there; not again for every point after.
                com.windykindy49.windyorigins.common.Powers.setResource(host, "heat", heat + 1);
            } else {
                HostManager.addHeat(host, 1);
            }

            if (host.hasStatusEffect(WindyEffects.CYBERPSYCHOSIS)) {
                release(host, null);
                return;
            }
        }

        run.clear();
        run.label = run.task == null ? L_HOLD : run.task.label();
        LivingEntity threat = threat(host);
        float low = Math.max(6.0F, host.getMaxHealth() * 0.3F);
        if (run.task != null && host.getHealth() <= 3.0F && (threat != null || host.isOnFire())) {
            // She is losing this one for good: better the Host's own hands than a dead Host.
            remember(host);
            release(host, "pilot_hurt");
            return;
        }

        LivingEntity danger = run.task == null ? null : danger(host);
        if (danger != null && host.getHealth() <= low) {
            run.retreat = true;
        } else if (danger == null || host.getHealth() > low + 5.0F) {
            run.retreat = false;
        }

        if (run.ticks % 60 == 1) {
            gear(host);
        }

        if (run.retreat) {
            // Badly hurt: out of reach first, a bite to mend, and back to it after.
            run.label = L_FLEE;
            retreat(run, host, danger);
        } else if (threat != null && !(run.task instanceof Hunt) && !(run.task instanceof Flee)) {
            // Whatever she was doing, something is chewing on the body.
            run.label = L_HUNT;
            fight(run, host, threat);
        } else if (run.task != null && !run.task.tick(run, host) && !tidy(run, host)) {
            String line = run.ending == null ? "pilot_done" : run.ending;
            Object[] args = run.ending == null ? new Object[0] : run.endingArgs;
            run.task = null;
            run.ending = null;
            run.clear();
            shut(run, host);
            CURRENT.remove(host.getUuid());
            ArrayDeque<PilotSpeech.Order> next = NEXT.get(host.getUuid());
            if (next != null && !next.isEmpty()) {
                // One thing done, on to the next that was asked for in the same breath.
                if (!line.equals("pilot_done")) {
                    HostManager.say(host, line, 1, args);
                }

                order(host, next.poll());
                if (!active(host)) {
                    return;
                }
            } else if (!run.held) {
                release(host, line, args);
                return;
            } else {
                HostManager.say(host, line, 2, args);
            }
        } else if (run.task != null && threat == null && !(run.task instanceof Eat)) {
            int hunger = host.getHungerManager().getFoodLevel();
            boolean hurt = host.getHealth() <= host.getMaxHealth() - 4.0F;
            boolean free = run.breakPos == null && run.attackId < 0 && run.placeAgainst == null && run.usePos == null && (run.flags & (BAG | CLICK | SNEAK)) == 0 && !run.opened;
            if (free && (hunger <= 12 || hunger <= 17 && hurt)) {
                // Hungry on the way: a bite without stopping, as anyone eats on a walk.
                int food = equip(host, Eat::foodWorth);
                if (food >= 0) {
                    run.slot = food;
                    run.look = null;
                    run.flags &= ~SPRINT;
                    if (host.getInventory().selectedSlot == food) {
                        run.flags |= USE;
                    }
                }
            } else if (!free && hunger <= 6 && !run.opened && host.getVelocity().horizontalLengthSquared() < 0.02) {
                // Starving with the hands full: the job waits for a bite.
                int food = equip(host, Eat::foodWorth);
                if (food >= 0) {
                    run.clear();
                    run.label = L_EAT;
                    run.slot = food;
                    if (host.getInventory().selectedSlot == food) {
                        run.flags |= USE;
                    }
                }
            }
        }

        douse(run, host);
        if (threat == null && !run.opened && (host.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.POISON) || host.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.WITHER))) {
            // Poisoned, and there is milk: the job waits a swallow.
            int milk = equip(host, stack -> stack.isOf(Items.MILK_BUCKET) ? 1.0 : 0.0);
            if (milk >= 0) {
                run.clear();
                run.slot = milk;
                if (host.getInventory().selectedSlot == milk) {
                    run.flags |= USE;
                }
            }
        }

        Long bagged = BAGGED.get(host.getUuid());
        if (bagged != null) {
            if (host.getWorld().getTime() - bagged > 14L || threat != null || run.attackId >= 0) {
                BAGGED.remove(host.getUuid());
            } else if (host.isOnGround() && (run.flags & SNEAK) == 0) {
                // The thing was deep in the inventory: a moment standing with it open, as it takes anyone.
                run.clear();
                run.flags = BAG;
            }
        }

        if (run.ticks % 6 == 0) {
            // What those around see of it: the chip throwing sparks at the Host's temple, and her strings going up from the shoulders.
            PhaseFx.particles(host, net.minecraft.particle.ParticleTypes.ELECTRIC_SPARK, host.getX(), host.getEyeY() + 0.25, host.getZ(), 2, 0.22, 0.12, 0.22, 0.03);
            strings(host, 0.27F, 0.94F, 0.9F);
        }

        if (run.task == null && !run.held) {
            // Nothing in hand and not told to wait (a name she asked about never came back): the body is the Host's.
            if (++run.idle > 140) {
                release(host, null);
                return;
            }
        } else {
            run.idle = 0;
        }

        if (run.ticks % 20 == 0) {
            Vec3d spot = host.getPos();
            boolean going = !run.way.isEmpty() && run.breakPos == null && run.attackId < 0;
            run.stuck = going && spot.squaredDistanceTo(run.lastSpot) < 0.09 ? run.stuck + 1 : 0;
            run.lastSpot = spot;
        }

        if (host.isOnGround() && !host.isTouchingWater()) {
            run.dry = host.getPos();
        }

        if (host.isInLava()) {
            run.flags |= JUMP;
        }

        if (host.getAir() < 200 && run.fallEdge == null) {
            run.gasp = true;
        }

        if (run.gasp) {
            // Out of breath: whatever she was doing, up for air, through the roof if there is one, and back to dry ground.
            if (host.getAir() >= host.getMaxAir() - 20 && !host.isSubmergedInWater() && (host.isOnGround() || !host.isTouchingWater())) {
                run.gasp = false;
            } else if (host.isSubmergedInWater()) {
                // Air beside the head, over ground near enough to step down to: out sideways, the short way.
                BlockPos head = BlockPos.ofFloored(host.getEyePos());
                BlockPos aside = null;

                for (Direction side : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
                    BlockPos cell = head.offset(side);
                    if (passable(host.getWorld(), cell) && host.getWorld().getFluidState(cell).isEmpty() && passable(host.getWorld(), cell.down()) && drop(host.getWorld(), cell.down()) <= 3) {
                        aside = cell.down();
                        break;
                    }
                }

                if (aside != null) {
                    run.clear();
                    run.way.add(new Vec3d((double)aside.getX() + 0.5, host.getY(), (double)aside.getZ() + 0.5));
                    run.flags = JUMP;
                    send(host, run);
                    return;
                }

                // Whatever the head would strike on the way up, under any corner of the shoulders, comes out.
                net.minecraft.util.math.Box box = host.getBoundingBox();
                int top = net.minecraft.util.math.MathHelper.floor(box.maxY + 0.5);
                BlockPos roof = null;

                for (int x = net.minecraft.util.math.MathHelper.floor(box.minX); x <= net.minecraft.util.math.MathHelper.floor(box.maxX) && roof == null; x++) {
                    for (int z = net.minecraft.util.math.MathHelper.floor(box.minZ); z <= net.minecraft.util.math.MathHelper.floor(box.maxZ) && roof == null; z++) {
                        BlockPos over = new BlockPos(x, top, z);
                        if (!passable(host.getWorld(), over) && breakable(host.getWorld(), over)) {
                            roof = over;
                        }
                    }
                }

                run.clear();
                run.flags = JUMP;
                if (roof != null) {
                    strike(run, host, roof);
                } else {
                    BlockPos feet = host.getBlockPos();
                    run.way.add(new Vec3d((double)feet.getX() + 0.5, host.getY() + 2.0, (double)feet.getZ() + 0.5));
                }
            } else if (run.dry != null && run.dry.squaredDistanceTo(host.getPos()) < 48.0 * 48.0 && host.isTouchingWater()) {
                run.clear();
                walk(run, host, run.dry, 1.0, ANYTHING, false);
                run.flags |= JUMP;
            }
        }

        if (host.isInsideWall()) {
            // Sand or gravel came down on the head: out first.
            BlockPos head = BlockPos.ofFloored(host.getEyePos());
            if (breakable(host.getWorld(), head)) {
                run.clear();
                strike(run, host, head);
            }
        }

        if (LOG && run.ticks % 20 == 0) {
            com.windykindy49.windyorigins.WindyOrigins.LOGGER.info(
                "PILOT {} at {} way {} {} break {} attack {} path {} stuck {} burrow {} flags {} place {} slot {} hand {}",
                run.task == null ? "-" : run.task.getClass().getSimpleName(),
                String.format("%.1f %.1f %.1f", host.getX(), host.getY(), host.getZ()),
                run.way.size(),
                run.way.isEmpty() ? "" : String.format("%.1f %.1f %.1f", run.way.get(0).x, run.way.get(0).y, run.way.get(0).z),
                run.breakPos == null ? "" : run.breakPos.toShortString(),
                run.attackId,
                run.path == null ? "none" : run.path.getLength() + (run.path.reachesTarget() ? " reaches" : " partial"),
                run.stuck,
                run.burrowFor,
                run.flags,
                run.placeAgainst == null ? "" : run.placeAgainst.toShortString() + " " + run.placeFace,
                run.slot,
                host.getMainHandStack().getItem()
            );
        }

        send(host, run);
    }

    private static void send(ServerPlayerEntity host, Run run) {
        int hash = run.way.hashCode() * 31 + java.util.Objects.hash(run.breakPos, run.usePos, run.attackId, run.look, run.flags, run.slot, run.label, run.placeAgainst, run.placeFace);
        long now = host.getWorld().getTime();
        if (hash == run.sentHash && now - run.sentAt < 10L) {
            return;
        }

        run.sentHash = hash;
        run.sentAt = now;
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeBoolean(true);
        buf.writeVarInt(run.label);
        buf.writeVarInt(run.flags);
        buf.writeVarInt(run.slot < 0 ? 9 : run.slot);
        buf.writeVarInt(run.way.size());

        for (Vec3d step : run.way) {
            buf.writeDouble(step.x);
            buf.writeDouble(step.y);
            buf.writeDouble(step.z);
        }

        buf.writeBoolean(run.breakPos != null);
        if (run.breakPos != null) {
            buf.writeBlockPos(run.breakPos);
        }

        buf.writeBoolean(run.usePos != null);
        if (run.usePos != null) {
            buf.writeBlockPos(run.usePos);
        }

        buf.writeInt(run.attackId);
        buf.writeBoolean(run.look != null);
        if (run.look != null) {
            buf.writeDouble(run.look.x);
            buf.writeDouble(run.look.y);
            buf.writeDouble(run.look.z);
        }

        buf.writeBoolean(run.placeAgainst != null);
        if (run.placeAgainst != null) {
            buf.writeBlockPos(run.placeAgainst);
            buf.writeByte(run.placeFace.getId());
        }

        ServerPlayNetworking.send(host, HostNetworking.PILOT, buf);
    }

    // ------------------------------------------------------------------ what she can make the body do

    /** On fire with a bucket of water in the pockets: over the feet with it, and back into the bucket once the fire is out. */
    private static void douse(Run run, ServerPlayerEntity host) {
        World world = host.getWorld();
        if (host.isOnFire() && !host.isInLava() && !host.isTouchingWater() && !world.getDimension().ultrawarm() && host.isOnGround() && carries(host, stack -> stack.isOf(Items.WATER_BUCKET))) {
            BlockPos floor = BlockPos.ofFloored(host.getX(), host.getY() - 0.2, host.getZ());
            if (solid(world, floor)) {
                run.clear();
                run.slot = equip(host, stack -> stack.isOf(Items.WATER_BUCKET) ? 1.0 : 0.0);
                run.look = Vec3d.ofCenter(floor).add(0.0, 0.5, 0.0);
                run.flags |= CLICK;
                run.water = floor.up();
                run.waterFor = 0;
                return;
            }
        }

        if (run.water != null) {
            FluidState fluid = world.getFluidState(run.water);
            boolean source = fluid.isIn(FluidTags.WATER) && fluid.isStill();
            if (++run.waterFor > 100 || !source && run.waterFor > 14 || !carries(host, stack -> stack.isOf(Items.BUCKET))) {
                run.water = null;
            } else if (source && !host.isOnFire()) {
                run.clear();
                run.slot = equip(host, stack -> stack.isOf(Items.BUCKET) ? 1.0 : 0.0);
                run.look = Vec3d.ofCenter(run.water);
                run.flags |= CLICK;
            }
        }
    }

    /** How many plain blocks for building with the body carries. */
    static int fillers(ServerPlayerEntity host) {
        int sum = 0;

        for (ItemStack stack : host.getInventory().main) {
            if (fillerWorth(stack) > 0.0) {
                sum += stack.getCount();
            }
        }

        return sum;
    }

    /**
     * Straight across instead of all the way round: what it would cost in steps to go at a place in a straight
     * line, laying blocks over the gaps. -1 when the line is walled off, has no gap in it, or takes more blocks
     * than she has.
     */
    private static int straight(ServerPlayerEntity host, Vec3d to) {
        World world = host.getWorld();
        BlockPos feet = host.getBlockPos();
        double dx = to.x - host.getX();
        double dz = to.z - host.getZ();
        if (Math.min(Math.abs(dx), Math.abs(dz)) > 3.5 || Math.abs(to.y - host.getY()) > 2.5) {
            return -1;
        }

        Direction dir = Math.abs(dx) > Math.abs(dz) ? (dx > 0.0 ? Direction.EAST : Direction.WEST) : (dz > 0.0 ? Direction.SOUTH : Direction.NORTH);
        int far = (int)Math.min(Math.max(Math.abs(dx), Math.abs(dz)), 30.0);
        int holes = 0;

        for (int i = 1; i <= far; i++) {
            BlockPos cell = feet.offset(dir, i);
            if (!passable(world, cell) || !passable(world, cell.up())) {
                return -1;
            }

            if (drop(world, cell) > 3) {
                holes++;
            }
        }

        if (holes == 0 || holes > fillers(host) || drop(world, feet.offset(dir, far)) > 3) {
            return -1;
        }

        return far + holes * 4;
    }

    /** How far down the first floor under this cell is; 99 when there is none within reach or it is lava. */
    private static int depth(World world, BlockPos from) {
        for (int i = 1; i <= 40; i++) {
            BlockPos below = from.down(i);
            if (world.getFluidState(below).isIn(FluidTags.LAVA)) {
                return 99;
            }

            if (solid(world, below)) {
                return i - 1;
            }
        }

        return 99;
    }

    /**
     * Down a cliff by water: the bucket is emptied on the edge from a step back, the fall of it is given time to
     * reach the bottom, and the body goes down inside it. The water stays where it was poured.
     */
    private static boolean waterfall(Run run, ServerPlayerEntity host) {
        World world = host.getWorld();
        BlockPos edge = run.fallEdge;
        Direction dir = run.fallDir;
        BlockPos over = edge.offset(dir);
        BlockPos back = edge.offset(dir.getOpposite());
        boolean stepBack = passable(world, back) && passable(world, back.up()) && solid(world, back.down());
        BlockPos stand = stepBack ? back : edge;
        Vec3d spot = new Vec3d((double)stand.getX() + 0.5, (double)edge.getY(), (double)stand.getZ() + 0.5);
        run.fallTicks++;
        if (run.fallStage == 0) {
            if (world.getFluidState(edge).isIn(FluidTags.WATER)) {
                run.fallStage = 1;
                run.fallTicks = 0;
                return true;
            }

            if (run.fallTicks > 120 || !carries(host, stack -> stack.isOf(Items.WATER_BUCKET))) {
                return false;
            }

            double dx = spot.x - host.getX();
            double dz = spot.z - host.getZ();
            if (dx * dx + dz * dz > 0.09) {
                run.way.add(spot);
                run.flags |= SNEAK;
                return true;
            }

            run.slot = equip(host, stack -> stack.isOf(Items.WATER_BUCKET) ? 1.0 : 0.0);
            run.look = Vec3d.ofCenter(edge.down()).add(0.0, 0.5, 0.0);
            run.flags |= SNEAK;
            if (host.getMainHandStack().isOf(Items.WATER_BUCKET)) {
                // One press, with the full bucket in the hand: a second would only take the water up again.
                run.flags |= CLICK;
            }

            return true;
        }

        if (run.fallStage == 1) {
            // Given time, the fall reaches the bottom; until then she stands clear of it.
            boolean down = world.getFluidState(over.down(run.fallDepth)).isIn(FluidTags.WATER);
            if (down || run.fallTicks > run.fallDepth * 6 + 60) {
                if (!down) {
                    return false;
                }

                run.fallStage = 2;
                run.fallTicks = 0;
            }

            run.flags |= SNEAK;
            return true;
        }

        double bottom = (double)(over.getY() - run.fallDepth);
        if (host.getY() <= bottom + 2.0 || run.fallTicks > run.fallDepth * 14 + 100) {
            return false;
        }

        run.way.add(new Vec3d((double)over.getX() + 0.5, bottom, (double)over.getZ() + 0.5));
        return true;
    }

    /**
     * Over water in a boat: it goes onto the water from the bank, she gets in, steers for the place, gets out at
     * the far side, breaks the boat and takes it along. False when that is over, or did not come off.
     */
    private static boolean sailing(Run run, ServerPlayerEntity host, Vec3d to) {
        World world = host.getWorld();
        run.sailTicks++;
        Entity boat = run.boatId < 0 ? null : world.getEntityById(run.boatId);
        if (run.sail == 1) {
            // Onto the water: the nearest of it in front of her.
            if (run.sailTicks > 80) {
                return false;
            }

            for (net.minecraft.entity.vehicle.BoatEntity near : world.getEntitiesByClass(net.minecraft.entity.vehicle.BoatEntity.class, host.getBoundingBox().expand(6.0), x -> x.isAlive() && !x.hasPassengers())) {
                run.boatId = near.getId();
                run.sail = 2;
                run.sailTicks = 0;
                return true;
            }

            Vec3d ahead = to.subtract(host.getPos()).multiply(1.0, 0.0, 1.0);
            if (ahead.lengthSquared() < 0.01) {
                return false;
            }

            ahead = ahead.normalize();
            Vec3d spot = null;

            // A boat needs room: not the water right under the bank, the next of it out.
            for (double d = 1.5; d <= 4.0 && spot == null; d += 0.5) {
                BlockPos cell = BlockPos.ofFloored(host.getX() + ahead.x * d, host.getY() - 0.5, host.getZ() + ahead.z * d);
                BlockPos nearer = BlockPos.ofFloored(host.getX() + ahead.x * (d - 1.0), host.getY() - 0.5, host.getZ() + ahead.z * (d - 1.0));
                if (world.getFluidState(cell).isIn(FluidTags.WATER) && world.getFluidState(nearer).isIn(FluidTags.WATER) && !cell.equals(nearer)) {
                    spot = Vec3d.ofCenter(cell).add(0.0, 0.4, 0.0);
                }
            }

            if (spot == null) {
                // Not at the bank yet.
                run.way.add(host.getPos().add(ahead.multiply(2.0)));
                return true;
            }

            run.slot = equip(host, stack -> stack.getItem() instanceof net.minecraft.item.BoatItem ? 1.0 : 0.0);
            run.look = spot;
            run.flags |= CLICK;
            return true;
        }

        if (run.sail == 2) {
            if (host.getVehicle() instanceof net.minecraft.entity.vehicle.BoatEntity) {
                run.sail = 3;
                run.sailTicks = 0;
                return true;
            }

            if (boat == null || !boat.isAlive() || run.sailTicks > 200) {
                return false;
            }

            run.attackId = boat.getId();
            run.flags |= MOUNT;
            return true;
        }

        if (run.sail == 3) {
            if (!(host.getVehicle() instanceof net.minecraft.entity.vehicle.BoatEntity riding)) {
                run.sail = 4;
                run.sailTicks = 0;
                return true;
            }

            double dx = to.x - riding.getX();
            double dz = to.z - riding.getZ();
            double flat = Math.sqrt(dx * dx + dz * dz);
            boolean aground = !riding.isTouchingWater() || flat > 0.5 && solid(world, BlockPos.ofFloored(riding.getX() + dx / flat * 1.6, riding.getY() + 0.3, riding.getZ() + dz / flat * 1.6));
            if (flat < 2.5 || run.sailTicks > 40 && aground || run.sailTicks > 3000) {
                // Land, or as near as the boat will go: out.
                run.flags |= SNEAK;
                return true;
            }

            run.way.add(new Vec3d(to.x, riding.getY(), to.z));
            return true;
        }

        // Ashore: the boat is not left behind.
        if (host.hasVehicle()) {
            run.flags |= SNEAK;
            return run.sailTicks < 60;
        }

        if (boat != null && boat.isAlive() && run.sailTicks < 300) {
            // From the very edge of the bank, where it can be seen and reached; a crouch keeps the feet dry.
            run.slot = equip(host, Pilot::weaponWorth);
            run.attackId = boat.getId();
            run.way.add(new Vec3d(boat.getX(), host.getY(), boat.getZ()));
            run.flags |= SWEEP | SNEAK;
            return true;
        }

        ItemEntity dropped = lying(host, 8.0, stack -> stack.getItem() instanceof net.minecraft.item.BoatItem);
        if (dropped != null && run.sailTicks < 500) {
            run.way.add(dropped.getPos());
            return true;
        }

        return false;
    }

    /** Something hostile that has the body for its target and is close enough to hurt it. */
    private static LivingEntity threat(ServerPlayerEntity host) {
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;

        for (MobEntity mob : host.getWorld().getEntitiesByClass(MobEntity.class, host.getBoundingBox().expand(16.0, 8.0, 16.0), x -> x.isAlive() && x.getTarget() == host && PhaseFx.samePhase(x, host))) {
            double d = mob.squaredDistanceTo(host);
            boolean shoots = mob instanceof net.minecraft.entity.ai.RangedAttackMob;
            if (d < bestDistance && (d < 36.0 || shoots && host.canSee(mob))) {
                bestDistance = d;
                best = mob;
            }
        }

        return best;
    }

    /** The nearest thing that is after the body, however far off: what a badly hurt body gets away from. */
    private static LivingEntity danger(ServerPlayerEntity host) {
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;

        for (MobEntity mob : host.getWorld().getEntitiesByClass(MobEntity.class, host.getBoundingBox().expand(16.0, 8.0, 16.0), x -> x.isAlive() && x.getTarget() == host && PhaseFx.samePhase(x, host))) {
            double d = mob.squaredDistanceTo(host);
            if (d < bestDistance) {
                bestDistance = d;
                best = mob;
            }
        }

        return best;
    }

    /** Away from what is after the body; once out of its reach, something to eat. Cornered, she fights. */
    private static void retreat(Run run, ServerPlayerEntity host, LivingEntity from) {
        double away = from == null ? 99.0 : (double)host.distanceTo(from);
        if (from != null && away < 10.0) {
            if (run.stuck >= 3) {
                fight(run, host, from);
                return;
            }

            Vec3d off = host.getPos().subtract(from.getPos()).multiply(1.0, 0.0, 1.0);
            if (off.lengthSquared() < 0.01) {
                off = new Vec3d(1.0, 0.0, 0.0);
            }

            Vec3d to = host.getPos().add(off.normalize().multiply(12.0));
            walk(run, host, surface(host.getWorld(), to.x, to.z, host.getY()), 1.0, NONE, true);
            run.flags |= SPRINT;
            if (shielded(host) && from instanceof net.minecraft.entity.ai.RangedAttackMob) {
                run.flags |= USE;
            }

            return;
        }

        int food = equip(host, Pilot::aidWorth);
        if (food >= 0) {
            run.slot = food;
            if (host.getInventory().selectedSlot == food) {
                run.flags |= USE;
            }
        }
    }

    /** What mends a hurt body fastest: a golden apple before bread. */
    private static double aidWorth(ItemStack stack) {
        if (stack.isOf(Items.GOLDEN_APPLE) || stack.isOf(Items.ENCHANTED_GOLDEN_APPLE)) {
            return 50.0;
        }

        if (stack.isOf(Items.POTION)) {
            // A bottle that mends is better still.
            for (net.minecraft.entity.effect.StatusEffectInstance effect : net.minecraft.potion.PotionUtil.getPotionEffects(stack)) {
                if (effect.getEffectType() == net.minecraft.entity.effect.StatusEffects.INSTANT_HEALTH || effect.getEffectType() == net.minecraft.entity.effect.StatusEffects.REGENERATION) {
                    return 60.0;
                }
            }

            return 0.0;
        }

        return Eat.foodWorth(stack);
    }

    static boolean shielded(ServerPlayerEntity host) {
        return host.getOffHandStack().getItem() instanceof net.minecraft.item.ShieldItem;
    }

    /** What a player would have put on before a fight: a shield from the pockets into the off hand, armour onto whatever is bare. */
    private static void gear(ServerPlayerEntity host) {
        PlayerInventory inventory = host.getInventory();
        boolean changed = false;

        for (int i = 0; i < inventory.main.size(); i++) {
            ItemStack stack = inventory.main.get(i);
            if (stack.isEmpty()) {
                continue;
            }

            if (stack.getItem() instanceof net.minecraft.item.ShieldItem && !shielded(host) && host.getOffHandStack().isEmpty()) {
                inventory.offHand.set(0, stack);
                inventory.main.set(i, ItemStack.EMPTY);
                changed = true;
            } else if (stack.getItem() instanceof net.minecraft.item.ArmorItem armor && host.getEquippedStack(armor.getSlotType()).isEmpty()) {
                host.equipStack(armor.getSlotType(), stack);
                inventory.main.set(i, ItemStack.EMPTY);
                changed = true;
            }
        }

        if (changed) {
            inventory.markDirty();
            host.playerScreenHandler.sendContentUpdates();
            BAGGED.put(host.getUuid(), host.getWorld().getTime());
        }
    }

    private static boolean hasBow(ServerPlayerEntity host) {
        return carries(host, stack -> stack.getItem() instanceof net.minecraft.item.BowItem) && carries(host, stack -> stack.isIn(net.minecraft.registry.tag.ItemTags.ARROWS));
    }

    /** An arrow at it: the string held back until it is full, aimed ahead of where it is going and over it by the drop. */
    private static void shoot(Run run, ServerPlayerEntity host, LivingEntity foe) {
        run.attackId = -1;
        run.way.clear();
        run.slot = equip(host, stack -> stack.getItem() instanceof net.minecraft.item.BowItem ? 1.0 + (double)(stack.getMaxDamage() - stack.getDamage()) / 1000.0 : 0.0);
        Vec3d eye = host.getEyePos();
        double dx = foe.getX() - eye.x;
        double dz = foe.getZ() - eye.z;
        double time = Math.sqrt(dx * dx + dz * dz) / 2.7;
        Vec3d lead = foe.getVelocity().multiply(time, 0.0, time);
        run.look = new Vec3d(foe.getX() + lead.x, foe.getY() + (double)foe.getHeight() * 0.6 + 0.03 * time * time, foe.getZ() + lead.z);
        boolean inHand = host.getInventory().selectedSlot == run.slot && host.getMainHandStack().getItem() instanceof net.minecraft.item.BowItem;
        if (!inHand) {
            return;
        }

        if (host.isUsingItem()) {
            if (host.getItemUseTime() < 22) {
                run.flags |= USE;
            } else {
                run.drawRest = 5;
            }
        } else if (run.drawRest > 0) {
            run.drawRest--;
        } else {
            run.flags |= USE;
        }
    }

    private static ZombieEntity scout(Run run, ServerPlayerEntity host) {
        if (run.scout == null || run.scout.getWorld() != host.getWorld()) {
            // Never spawned: a stand-in of the body's size that the game's own pathfinder plans a walk for.
            ZombieEntity scout = new ZombieEntity(EntityType.ZOMBIE, host.getWorld());
            EntityAttributeInstance range = scout.getAttributeInstance(EntityAttributes.GENERIC_FOLLOW_RANGE);
            if (range != null) {
                range.setBaseValue(48.0);
            }

            scout.getNavigation().setCanSwim(true);
            scout.getNavigation().getNodeMaker().setCanOpenDoors(true);
            scout.getNavigation().getNodeMaker().setCanEnterOpenDoors(true);
            run.scout = scout;
            run.path = null;
        }

        return run.scout;
    }

    private static void plan(Run run, ServerPlayerEntity host, BlockPos goal) {
        ZombieEntity scout = scout(run, host);
        scout.refreshPositionAndAngles(host.getX(), host.getY(), host.getZ(), host.getYaw(), 0.0F);
        scout.setOnGround(true);
        run.path = scout.getNavigation().findPathTo(goal, 1);
        run.pathGoal = goal;
        run.pathAt = host.getWorld().getTime();
    }

    /** Lays the next few steps of the planned walk in front of the body. False when the walk has nothing more to give. */
    private static boolean follow(Run run, ServerPlayerEntity host) {
        Path path = run.path;
        if (path == null || path.getLength() == 0) {
            return false;
        }

        int at = -1;
        double best = Double.MAX_VALUE;

        for (int i = 0; i < path.getLength(); i++) {
            PathNode node = path.getNode(i);
            double dx = (double)node.x + 0.5 - host.getX();
            double dy = (double)node.y - host.getY();
            double dz = (double)node.z + 0.5 - host.getZ();
            double d = dx * dx + dz * dz + dy * dy * 0.5;
            if (d <= best) {
                best = d;
                at = i;
            }
        }

        // The nearest step is taken as made once the body stands on it.
        PathNode near = path.getNode(at);
        double dx = (double)near.x + 0.5 - host.getX();
        double dz = (double)near.z + 0.5 - host.getZ();
        int from = dx * dx + dz * dz < 0.42 && Math.abs((double)near.y - host.getY()) < 1.2 ? at + 1 : at;
        if (from >= path.getLength()) {
            return false;
        }

        World world = host.getWorld();

        for (int i = from; i < Math.min(path.getLength(), from + 6); i++) {
            PathNode node = path.getNode(i);
            BlockPos pos = new BlockPos(node.x, node.y, node.z);
            run.way.add(new Vec3d((double)node.x + 0.5, (double)node.y, (double)node.z + 0.5));
            if (i <= from + 1 && run.usePos == null) {
                for (BlockPos door : new BlockPos[]{pos, pos.up()}) {
                    BlockState state = world.getBlockState(door);
                    if (state.getBlock() instanceof DoorBlock && state.getBlock() != Blocks.IRON_DOOR && !state.get(DoorBlock.OPEN)) {
                        run.usePos = door;
                        break;
                    }
                }
            }
        }

        return true;
    }

    /**
     * Sets the body walking to a place. True once it is there. With {@code dig} she goes through what is in the
     * way, a block at a time, when there is no way round.
     */
    static boolean walk(Run run, ServerPlayerEntity host, Vec3d to, double near, int dig, boolean flat) {
        double dx = to.x - host.getX();
        double dz = to.z - host.getZ();
        if (dx * dx + dz * dz <= near * near && (flat || Math.abs(to.y - host.getY()) <= 2.2)) {
            return true;
        }

        if (flat && dx * dx + dz * dz < 36.0) {
            // A spot on the map, not a height: close to it she stops caring what stands on it.
            to = new Vec3d(to.x, host.getY(), to.z);
        }

        if (run.sail != 0) {
            if (sailing(run, host, to)) {
                return false;
            }

            run.sail = 0;
            run.boatId = -1;
            run.sailAgain = run.ticks + 100;
            run.path = null;
            run.way.clear();
        }

        if (run.fallEdge != null) {
            if (waterfall(run, host)) {
                return false;
            }

            run.fallEdge = null;
            run.fallAgain = run.ticks + 2400;
            run.path = null;
            run.way.clear();
        }

        BlockPos goal = BlockPos.ofFloored(to);
        if (!flat) {
            // Another floor, and no stairs to it: a ladder, if there is one.
            double rise = to.y - host.getY();
            boolean on = host.isClimbing() && Math.abs(rise) > 0.7;
            boolean lost = Math.abs(rise) > 2.5
                && (run.ticks < run.climbUntil && run.climbGoal != null && goal.isWithinDistance(run.climbGoal, 1.5)
                    || run.path != null && run.pathGoal != null && goal.isWithinDistance(run.pathGoal, 1.5) && !run.path.reachesTarget());
            if ((on || lost) && climb(run, host, to.y)) {
                run.burrowFor = 0;
                run.climbGoal = goal;
                run.climbUntil = run.ticks + 40;
                return false;
            }
        }

        if (dig > NONE && run.burrowFor > 0) {
            run.burrowFor--;
            burrow(run, host, to, null, dig == ANYTHING);
            return false;
        }

        long now = host.getWorld().getTime();
        if (run.path == null || run.pathGoal == null || !goal.isWithinDistance(run.pathGoal, 1.5) || now - run.pathAt > 30L) {
            plan(run, host, goal);
            if (dig > NONE && run.path != null && run.path.getLength() > 16 && host.isOnGround()) {
                // A long way round, or none: is it quicker straight across on blocks of her own?
                int direct = straight(host, to);
                if (direct > 0 && (!run.path.reachesTarget() || run.path.getLength() > direct * 3 / 2 + 8)) {
                    run.burrowFor = 40;
                    burrow(run, host, to, null, dig == ANYTHING);
                    return false;
                }
            }
        }

        if (!follow(run, host)) {
            boolean close = dx * dx + dz * dz < 2.5 * 2.5 && Math.abs(to.y - host.getY()) < 2.5 && run.path != null && run.path.reachesTarget();
            if (dig > NONE && !close) {
                run.burrowFor = 30;
                burrow(run, host, to, null, dig == ANYTHING);
            } else {
                // No walk planned: straight at it, the way anyone crosses open ground.
                run.way.add(to);
            }

            return false;
        }

        if (run.sail == 0 && run.ticks >= run.sailAgain && run.way.size() >= 3 && host.isOnGround() && !host.isTouchingWater() && !host.hasVehicle()) {
            // The way leads out over water, and there is a boat in the pockets: dry across, as the chip likes it.
            World world = host.getWorld();
            int wet = 0;

            for (int i = 0; i < Math.min(4, run.way.size()); i++) {
                wet += world.getFluidState(BlockPos.ofFloored(run.way.get(i))).isIn(FluidTags.WATER) || world.getFluidState(BlockPos.ofFloored(run.way.get(i)).down()).isIn(FluidTags.WATER) ? 1 : 0;
            }

            if (wet >= 2 && carries(host, stack -> stack.getItem() instanceof net.minecraft.item.BoatItem)) {
                run.sail = 1;
                run.sailTicks = 0;
                run.boatId = -1;
                return false;
            }
        }

        if (run.stuck >= 3) {
            if (dig > NONE) {
                run.burrowFor = 60;
                run.stuck = 0;
            } else {
                run.flags |= JUMP;
                run.path = null;
            }
        }

        if (dx * dx + dz * dz > 100.0) {
            run.flags |= SPRINT;
        }

        return false;
    }

    /** How she may deal with what is in the way of a walk: not at all, through earth and rock only, or through anything. */
    static final int NONE = 0;
    static final int NATURAL = 1;
    static final int ANYTHING = 2;

    /** Ground, as opposed to something somebody built: what she will cut steps into on the way somewhere. */
    private static boolean natural(BlockState state) {
        return state.isIn(net.minecraft.registry.tag.BlockTags.DIRT)
            || state.isIn(net.minecraft.registry.tag.BlockTags.BASE_STONE_OVERWORLD)
            || state.isIn(net.minecraft.registry.tag.BlockTags.BASE_STONE_NETHER)
            || state.isIn(net.minecraft.registry.tag.BlockTags.SAND)
            || state.isIn(net.minecraft.registry.tag.BlockTags.LEAVES)
            || state.isIn(net.minecraft.registry.tag.BlockTags.SNOW)
            || state.isIn(net.minecraft.registry.tag.BlockTags.TERRACOTTA)
            || state.isIn(net.minecraft.registry.tag.BlockTags.NYLIUM)
            || state.isOf(Blocks.GRAVEL)
            || state.isOf(Blocks.CLAY)
            || state.isOf(Blocks.SANDSTONE)
            || state.isOf(Blocks.RED_SANDSTONE)
            || state.isOf(Blocks.SOUL_SAND)
            || state.isOf(Blocks.SOUL_SOIL)
            || state.isOf(Blocks.END_STONE)
            || state.isOf(Blocks.CALCITE)
            || state.isOf(Blocks.DRIPSTONE_BLOCK)
            || state.isOf(Blocks.MAGMA_BLOCK)
            || state.isOf(Blocks.COBWEB)
            || state.isOf(Blocks.VINE);
    }

    private static boolean passable(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return state.getCollisionShape(world, pos).isEmpty() && !state.getFluidState().isIn(FluidTags.LAVA);
    }

    private static boolean solid(World world, BlockPos pos) {
        return !world.getBlockState(pos).getCollisionShape(world, pos).isEmpty();
    }

    /** Can she take this block out at all, and without letting lava in? */
    static boolean breakable(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        float hardness = state.getHardness(world, pos);
        if (state.isAir() || hardness < 0.0F || hardness > 50.0F || world.getBlockEntity(pos) != null) {
            return false;
        }

        for (Direction side : Direction.values()) {
            if (side != Direction.DOWN && world.getFluidState(pos.offset(side)).isIn(FluidTags.LAVA)) {
                return false;
            }
        }

        return true;
    }

    /** How far the body would fall from this cell; 99 when it would land in lava or keep falling. */
    private static int drop(World world, BlockPos from) {
        for (int i = 0; i < 12; i++) {
            BlockPos below = from.down(i + 1);
            FluidState fluid = world.getFluidState(below);
            if (fluid.isIn(FluidTags.LAVA)) {
                return 99;
            }

            if (fluid.isIn(FluidTags.WATER) || solid(world, below)) {
                return i;
            }
        }

        return 99;
    }

    /**
     * One step of a tunnel towards a place: what is in the way of the next cell is broken, then the body steps
     * into it. Rises a block a step when the place is above and sinks a block a step when it is below. A given
     * direction is kept for a straight shaft.
     */
    static void burrow(Run run, ServerPlayerEntity host, Vec3d to, Direction keep, boolean anything) {
        World world = host.getWorld();
        BlockPos feet = host.getBlockPos();
        double dx = to.x - host.getX();
        double dz = to.z - host.getZ();
        double dy = to.y - host.getY();
        Direction dir = keep != null ? keep : (Math.abs(dx) > Math.abs(dz) ? (dx > 0.0 ? Direction.EAST : Direction.WEST) : (dz > 0.0 ? Direction.SOUTH : Direction.NORTH));
        if (keep == null && dx * dx + dz * dz < 1.0) {
            dir = host.getHorizontalFacing();
        }

        if (run.turnFor > 0) {
            run.turnFor--;

            for (int i = 0; i < run.turn; i++) {
                dir = dir.rotateYClockwise();
            }
        }

        // First to the middle of the cell: a tunnel is cut from where the body stands.
        double offX = (double)feet.getX() + 0.5 - host.getX();
        double offZ = (double)feet.getZ() + 0.5 - host.getZ();
        if (offX * offX + offZ * offZ > 0.16 && passable(world, feet) && passable(world, feet.up())) {
            run.way.add(new Vec3d((double)feet.getX() + 0.5, host.getY(), (double)feet.getZ() + 0.5));
            return;
        }

        BlockPos next = feet.offset(dir);
        int rise = dy > 0.9 ? 1 : (dy < -0.9 ? -1 : 0);
        if (dy > 1.6 && dx * dx + dz * dz < 2.3 && !solid(world, next) && pillar(run, host)) {
            return;
        }

        List<BlockPos> open = new ArrayList<>();
        BlockPos goal;
        if (rise > 0 && solid(world, next)) {
            open.add(feet.up(2));
            open.add(next.up(2));
            open.add(next.up());
            goal = next.up();
        } else if (rise < 0 && drop(world, next.down()) <= 3) {
            open.add(next.up());
            open.add(next);
            open.add(next.down());
            goal = next.down();
        } else {
            open.add(next.up());
            open.add(next);
            goal = next;
            if (drop(world, next) > 3) {
                // A hole or lava ahead. With ground beyond it and blocks in the pockets it is bridged; otherwise not that way.
                boolean beyond = false;

                int reach = Math.min(24, fillers(host) + 1);

                for (int i = 2; i <= reach && !beyond; i++) {
                    BlockPos far = feet.offset(dir, i);
                    beyond = solid(world, far.down()) && !world.getFluidState(far).isIn(FluidTags.LAVA);
                }

                if (beyond && put(run, host, next.down(), (net.minecraft.item.Item)null) == 0) {
                    run.flags |= SNEAK;
                    return;
                }

                int deep = depth(world, next);
                if (dy < -3.0 && deep >= 4 && deep <= 16 && run.ticks >= run.fallAgain && host.isOnGround() && !world.getDimension().ultrawarm() && HostManager.heat(host) < 60 && carries(host, stack -> stack.isOf(Items.WATER_BUCKET))) {
                    // A cliff, the place is below, and there is a bucket of water: down by a waterfall.
                    run.fallEdge = feet;
                    run.fallDir = dir;
                    run.fallDepth = deep;
                    run.fallStage = 0;
                    run.fallTicks = 0;
                    return;
                }

                run.turn = run.turn % 3 + 1;
                run.turnFor = 40;
                return;
            }
        }

        if (!host.isTouchingWater()) {
            // No tunnel into water, and none that lets it in: the chip and water do not mix. Some other way.
            for (BlockPos pos : open) {
                boolean wet = world.getFluidState(pos).isIn(FluidTags.WATER);
                if (!wet && !passable(world, pos)) {
                    for (Direction side : new Direction[]{Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
                        wet |= world.getFluidState(pos.offset(side)).isIn(FluidTags.WATER);
                    }
                }

                if (wet) {
                    run.turn = run.turn % 3 + 1;
                    run.turnFor = 40;
                    return;
                }
            }
        }

        for (BlockPos pos : open) {
            if (passable(world, pos)) {
                continue;
            }

            if (!breakable(world, pos) || !anything && !natural(world.getBlockState(pos))) {
                if (rise > 0 && dy > 1.6 && pillar(run, host)) {
                    // A wall that is not hers to break, and the place is higher: up on blocks of her own.
                    return;
                }

                run.turn = run.turn % 3 + 1;
                run.turnFor = 40;
                return;
            }

            strike(run, host, pos);
            return;
        }

        run.way.add(new Vec3d((double)goal.getX() + 0.5, (double)goal.getY(), (double)goal.getZ() + 0.5));
    }

    /** The body is told to break this block, with the tool for it in the hand. */
    private static void strike(Run run, ServerPlayerEntity host, BlockPos pos) {
        BlockState state = host.getWorld().getBlockState(pos);
        run.slot = equip(host, stack -> (double)HostManager.worksOn(stack, state));
        run.breakPos = pos;
        run.look = Vec3d.ofCenter(pos);
    }

    /**
     * Works on one block: brings the body within reach, then breaks it, and first whatever stands in front
     * of it. True once it is gone.
     */
    static boolean dig(Run run, ServerPlayerEntity host, BlockPos pos, boolean mayBurrow) {
        World world = host.getWorld();
        if (world.getBlockState(pos).isAir()) {
            return true;
        }

        Vec3d eye = host.getEyePos();
        Vec3d centre = Vec3d.ofCenter(pos);
        if (eye.squaredDistanceTo(centre) <= REACH * REACH) {
            BlockHitResult hit = world.raycast(new RaycastContext(eye, centre, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, host));
            BlockPos first = hit.getType() == HitResult.Type.MISS ? pos : hit.getBlockPos();
            if (first.equals(pos) || breakable(world, first)) {
                strike(run, host, first);
                return false;
            }
        }

        double flatX = centre.x - host.getX();
        double flatZ = centre.z - host.getZ();
        if (mayBurrow && centre.y - eye.y > 3.2 && flatX * flatX + flatZ * flatZ < 6.3 && pillar(run, host)) {
            return false;
        }

        walk(run, host, Vec3d.ofBottomCenter(pos), 0.4, mayBurrow ? ANYTHING : NONE, false);
        return false;
    }

    // ------------------------------------------------------------------ setting blocks down

    /** What a pocketful of this is worth as something to stand on: plain earth and rock, never sand that falls or anything of value. */
    private static double fillerWorth(ItemStack stack) {
        if (!(stack.getItem() instanceof net.minecraft.item.BlockItem item)) {
            return 0.0;
        }

        net.minecraft.block.Block block = item.getBlock();
        BlockState state = block.getDefaultState();
        if (block instanceof net.minecraft.block.FallingBlock || state.isIn(net.minecraft.registry.tag.BlockTags.LEAVES) || state.hasBlockEntity()) {
            return 0.0;
        }

        String path = Registries.BLOCK.getId(block).getPath();
        boolean plain = state.isIn(net.minecraft.registry.tag.BlockTags.DIRT) && !state.isOf(Blocks.GRASS_BLOCK) && !state.isOf(Blocks.MUD)
            || state.isIn(net.minecraft.registry.tag.BlockTags.BASE_STONE_OVERWORLD)
            || state.isIn(net.minecraft.registry.tag.BlockTags.BASE_STONE_NETHER)
            || state.isIn(net.minecraft.registry.tag.BlockTags.PLANKS)
            || path.equals("cobblestone")
            || path.equals("cobbled_deepslate")
            || path.equals("end_stone");
        return plain ? 1.0 + (double)stack.getCount() / 64.0 : 0.0;
    }

    static boolean hasFiller(ServerPlayerEntity host) {
        return carries(host, stack -> fillerWorth(stack) > 0.0);
    }

    /**
     * Has the body set a block into this cell: a given thing, or whatever plain block it carries. 1 once the cell
     * is filled, 0 while she is at it, -1 when it cannot be done: nothing to set, or nothing to set it against.
     */
    static int put(Run run, ServerPlayerEntity host, BlockPos cell, net.minecraft.item.Item item) {
        return put(run, host, cell, item == null ? null : stack -> stack.isOf(item));
    }

    static int put(Run run, ServerPlayerEntity host, BlockPos cell, Predicate<ItemStack> with) {
        World world = host.getWorld();
        BlockState there = world.getBlockState(cell);
        if (!there.isAir() && !there.isReplaceable()) {
            return 1;
        }

        int slot = with != null ? equip(host, stack -> with.test(stack) ? 1.0 : 0.0) : equip(host, Pilot::fillerWorth);
        if (slot < 0) {
            return -1;
        }

        BlockPos against = null;
        Direction face = null;

        for (Direction side : new Direction[]{Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.UP}) {
            BlockPos near = cell.offset(side);
            if (world.getBlockState(near).isSideSolidFullSquare(world, near, side.getOpposite()) || with != null && side == Direction.DOWN && solid(world, near)) {
                against = near;
                face = side.getOpposite();
                break;
            }
        }

        if (against == null) {
            return -1;
        }

        Vec3d point = Vec3d.ofCenter(against).add((double)face.getOffsetX() * 0.5, (double)face.getOffsetY() * 0.5, (double)face.getOffsetZ() * 0.5);
        if (host.getEyePos().squaredDistanceTo(point) > 4.3 * 4.3) {
            walk(run, host, Vec3d.ofBottomCenter(cell), 2.0, NATURAL, false);
            return 0;
        }

        BlockPos feet = host.getBlockPos();
        if ((cell.getX() != feet.getX() || cell.getZ() != feet.getZ()) && host.getBoundingBox().intersects(new net.minecraft.util.math.Box(cell))) {
            // A shoulder is in the cell: nothing goes where the body stands. Back to the middle of her own first.
            run.way.add(new Vec3d((double)feet.getX() + 0.5, host.getY(), (double)feet.getZ() + 0.5));
            return 0;
        }

        run.slot = slot;
        run.placeAgainst = against;
        run.placeFace = face;
        run.look = point;
        return 0;
    }

    /** One block higher: a jump, and a block set under the feet. False when there is nothing to set or no room overhead. */
    static boolean pillar(Run run, ServerPlayerEntity host) {
        World world = host.getWorld();
        BlockPos feet = host.getBlockPos();
        if (!hasFiller(host)) {
            return false;
        }

        // In the air of a jump the cell to fill is still the one she jumped out of.
        for (int i = 0; i < 2 && !solid(world, feet.down()); i++) {
            feet = feet.down();
        }

        BlockPos above = feet.up(2);
        if (!passable(world, above)) {
            if (!breakable(world, above)) {
                return false;
            }

            strike(run, host, above);
            return true;
        }

        // Centred first, or the block lands under one foot only.
        double offX = (double)feet.getX() + 0.5 - host.getX();
        double offZ = (double)feet.getZ() + 0.5 - host.getZ();
        if (offX * offX + offZ * offZ > 0.05 && host.isOnGround()) {
            run.way.add(new Vec3d((double)feet.getX() + 0.5, host.getY(), (double)feet.getZ() + 0.5));
            return true;
        }

        if (put(run, host, feet, (net.minecraft.item.Item)null) < 0) {
            return false;
        }

        run.way.clear();
        run.flags |= JUMP;
        return true;
    }

    /** The body is told to fight this creature with the best weapon it carries. */
    static void fight(Run run, ServerPlayerEntity host, LivingEntity foe) {
        double away = (double)host.distanceTo(foe);
        boolean sees = host.canSee(foe);
        if (sees && away > 7.0 && away < 40.0 && hasBow(host) && !(foe instanceof net.minecraft.entity.mob.EndermanEntity)) {
            // Too far for the arm, near enough for an arrow.
            shoot(run, host, foe);
            return;
        }

        // The weapon for this fight: the most harm a second; a sword when they come in a crowd, for the sweep of it;
        // an axe when the other holds a shield up, to knock it down.
        boolean crowd = host.getWorld().getEntitiesByClass(MobEntity.class, host.getBoundingBox().expand(3.5, 2.0, 3.5), x -> x.isAlive() && x.getTarget() == host).size() >= 2;
        boolean guarded = foe.isBlocking();
        run.slot = equip(host, stack -> {
            double worth = weaponWorth(stack);
            if (crowd && stack.getItem() instanceof net.minecraft.item.SwordItem) {
                worth *= 1.35;
            }

            if (guarded && stack.getItem() instanceof net.minecraft.item.AxeItem) {
                worth *= 3.0;
            }

            return worth;
        });
        run.attackId = foe.getId();
        if (crowd) {
            // Feet on the ground for a sweep: no jumping into the blow.
            run.flags |= SWEEP;
        }

        if (shielded(host) && sees) {
            // The shield goes up at what shoots, at a creeper about to go off, and between one swing and the next.
            boolean shoots = foe instanceof net.minecraft.entity.ai.RangedAttackMob && away > 3.5;
            boolean fuse = foe instanceof CreeperEntity creeper && creeper.getFuseSpeed() > 0;
            boolean between = away < 3.8 && host.getAttackCooldownProgress(0.0F) < 0.7F;
            if (shoots || fuse || between) {
                run.flags |= USE;
            }
        }

        if (away > 3.0 && host.getWorld().getFluidState(BlockPos.ofFloored(foe.getX(), foe.getEyeY() + 1.0, foe.getZ())).isIn(FluidTags.WATER)) {
            // It has gone under. The body does not go after it: she waits on the bank for it to come out.
            run.attackId = -1;
            run.look = foe.getEyePos();
            return;
        }

        if (foe instanceof CreeperEntity creeper && creeper.getFuseSpeed() > 0 && away < 7.0) {
            // About to go off: nobody stands there swinging.
            Vec3d back = host.getPos().subtract(foe.getPos()).multiply(1.0, 0.0, 1.0);
            if (back.lengthSquared() > 0.01) {
                run.attackId = -1;
                run.way.add(host.getPos().add(back.normalize().multiply(5.0)));
                run.flags |= SPRINT;
                return;
            }
        }

        if (away > 3.2 || !host.canSee(foe)) {
            walk(run, host, foe.getPos(), 1.2, NONE, false);
        }

        if (away > 8.0) {
            run.flags |= SPRINT;
        }
    }

    private static double weaponWorth(ItemStack stack) {
        if (stack.isEmpty() || stack.isDamageable() && stack.getMaxDamage() - stack.getDamage() <= 1) {
            return 0.0;
        }

        double damage = 1.0;
        double speed = 4.0;
        boolean weapon = false;

        for (EntityAttributeModifier modifier : stack.getAttributeModifiers(EquipmentSlot.MAINHAND).get(EntityAttributes.GENERIC_ATTACK_DAMAGE)) {
            damage += modifier.getValue();
            weapon = true;
        }

        for (EntityAttributeModifier modifier : stack.getAttributeModifiers(EquipmentSlot.MAINHAND).get(EntityAttributes.GENERIC_ATTACK_SPEED)) {
            speed += modifier.getValue();
        }

        if (!weapon) {
            return 0.0;
        }

        // Harm done in a second of swinging as fast as the thing lets itself be swung, with what is enchanted into it.
        damage += (double)net.minecraft.enchantment.EnchantmentHelper.getAttackDamage(stack, net.minecraft.entity.EntityGroup.DEFAULT);
        return damage * Math.max(0.25, speed);
    }

    /**
     * The hotbar slot of the best thing for the job. What lies deeper in the inventory changes places with what
     * the hand holds, as the Host would do it. -1 leaves the hand as it is.
     */
    static int equip(ServerPlayerEntity host, ToDoubleFunction<ItemStack> worth) {
        PlayerInventory inventory = host.getInventory();
        int best = -1;
        double bestWorth = 0.0;

        for (int i = 0; i < inventory.main.size(); i++) {
            double w = worth.applyAsDouble(inventory.main.get(i));
            if (w > bestWorth + 1.0E-4 || w > 0.0 && w >= bestWorth - 1.0E-4 && i == inventory.selectedSlot) {
                bestWorth = w;
                best = i;
            }
        }

        if (best < 0) {
            return -1;
        }

        long now = host.getWorld().getTime();
        long[] used = HANDS.computeIfAbsent(host.getUuid(), key -> new long[9]);
        if (best < 9) {
            used[best] = now;
            return best;
        }

        int hand = inventory.selectedSlot;

        for (int i = 0; i < 9; i++) {
            if (inventory.main.get(i).isEmpty()) {
                hand = i;
                break;
            }

            if (used[i] < used[hand]) {
                hand = i;
            }
        }

        used[hand] = now;
        if (RUNS.containsKey(host.getUuid())) {
            BAGGED.put(host.getUuid(), now);
        }

        ItemStack held = inventory.main.get(hand);
        inventory.main.set(hand, inventory.main.get(best));
        inventory.main.set(best, held);
        inventory.markDirty();
        host.playerScreenHandler.sendContentUpdates();
        return hand;
    }

    /** Can this block be seen from where the body's eyes are? Any side of it will do. */
    static boolean visible(ServerPlayerEntity host, BlockPos pos) {
        Vec3d eye = host.getEyePos();
        Vec3d centre = Vec3d.ofCenter(pos);
        World world = host.getWorld();

        for (Direction side : Direction.values()) {
            Vec3d face = centre.add((double)side.getOffsetX() * 0.46, (double)side.getOffsetY() * 0.46, (double)side.getOffsetZ() * 0.46);
            if (face.squaredDistanceTo(eye) > centre.squaredDistanceTo(eye) + 0.3) {
                continue;
            }

            BlockHitResult hit = world.raycast(new RaycastContext(eye, face, RaycastContext.ShapeType.VISUAL, RaycastContext.FluidHandling.NONE, host));
            if (hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(pos)) {
                return true;
            }
        }

        return false;
    }

    /** The nearest block of the wanted kind that lies bare and in sight. */
    static BlockPos seen(ServerPlayerEntity host, Predicate<BlockState> wanted, int radius, Set<BlockPos> skip) {
        World world = host.getWorld();
        BlockPos eye = BlockPos.ofFloored(host.getEyePos());
        List<BlockPos> bare = new ArrayList<>();

        for (BlockPos pos : BlockPos.iterate(eye.add(-radius, -Math.min(radius, 10), -radius), eye.add(radius, Math.min(radius, 12), radius))) {
            BlockState state = world.getBlockState(pos);
            if (state.isAir() || !wanted.test(state) || skip.contains(pos)) {
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

        bare.sort(java.util.Comparator.comparingDouble(pos -> pos.getSquaredDistance(eye)));
        int looked = 0;

        for (BlockPos pos : bare) {
            if (looked++ >= 48) {
                break;
            }

            if (world.getBlockEntity(pos) == null && visible(host, pos)) {
                return pos;
            }
        }

        return null;
    }

    /** The nearest block of a kind in sight, whatever it is: a chest counts, and so does water. */
    static BlockPos spotted(ServerPlayerEntity host, Predicate<BlockState> wanted, int radius) {
        World world = host.getWorld();
        BlockPos eye = BlockPos.ofFloored(host.getEyePos());
        List<BlockPos> all = new ArrayList<>();

        for (BlockPos pos : BlockPos.iterate(eye.add(-radius, -8, -radius), eye.add(radius, 8, radius))) {
            BlockState state = world.getBlockState(pos);
            if (!state.isAir() && wanted.test(state)) {
                all.add(pos.toImmutable());
            }
        }

        all.sort(java.util.Comparator.comparingDouble(pos -> pos.getSquaredDistance(eye)));
        int looked = 0;

        for (BlockPos pos : all) {
            if (looked++ >= 32) {
                break;
            }

            if (visible(host, pos)) {
                return pos;
            }
        }

        return null;
    }

    private static boolean hasRoom(ServerPlayerEntity host) {
        for (ItemStack stack : host.getInventory().main) {
            if (stack.isEmpty()) {
                return true;
            }
        }

        return false;
    }

    private static Vec3d surface(World world, double x, double z, double fallbackY) {
        BlockPos column = BlockPos.ofFloored(x, 0.0, z);
        if (!world.isChunkLoaded(column)) {
            return new Vec3d(x, fallbackY, z);
        }

        int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
        return new Vec3d(x, world.getDimension().hasCeiling() ? fallbackY : (double)top, z);
    }

    // ------------------------------------------------------------------ jobs

    /** Stands where she is and does nothing, in case the body is only to be held. */
    static final class Goto implements Task {
        private final Supplier<Vec3d> where;
        private final double near;
        private final int label;
        private final boolean forever;
        /** The place is a spot on the map: how high it lies is whatever the ground there is. */
        boolean flat;
        private int lost;

        Goto(Supplier<Vec3d> where, double near, int label, boolean forever) {
            this.where = where;
            this.near = near;
            this.label = label;
            this.forever = forever;
        }

        @Override
        public int label() {
            return this.label;
        }

        @Override
        public boolean tick(Run run, ServerPlayerEntity host) {
            Vec3d to = this.where.get();
            if (to == null) {
                run.end("pilot_none");
                return false;
            }

            if (walk(run, host, to, this.near, NATURAL, this.flat)) {
                this.lost = 0;
                return this.forever;
            }

            if (run.stuck >= 2) {
                this.lost++;
            }

            if (this.lost > 240) {
                run.end("pilot_lost");
                return false;
            }

            return true;
        }
    }

    /** Kills what fits, one after another. As a guard she stays by one spot and waits for it to come. */
    static final class Hunt implements Task {
        private final Predicate<LivingEntity> prey;
        private final int want;
        private final boolean guard;
        private final Vec3d post;
        /** How long she goes on looking once nothing of the kind is in sight. */
        private final int linger;
        private LivingEntity foe;
        private int kills;
        private int after;
        private int idle;
        private int chase;
        private float heading;
        private Vec3d roam;

        Hunt(ServerPlayerEntity host, Predicate<LivingEntity> prey, int want, boolean guard, int linger) {
            this.linger = linger;
            this.prey = prey;
            this.want = want;
            this.guard = guard;
            this.post = host.getPos();
            this.heading = host.getYaw();
        }

        @Override
        public int label() {
            return this.guard ? L_GUARD : L_HUNT;
        }

        private LivingEntity pick(ServerPlayerEntity host) {
            double reach = this.guard ? 14.0 : 32.0;
            LivingEntity best = null;
            double bestDistance = Double.MAX_VALUE;

            for (LivingEntity e : host.getWorld().getEntitiesByClass(LivingEntity.class, host.getBoundingBox().expand(reach, 12.0, reach), x -> x != host && x.isAlive() && !x.isSpectator() && PhaseFx.samePhase(x, host) && this.prey.test(x))) {
                double d = e.squaredDistanceTo(host);
                if (this.guard && e.getPos().squaredDistanceTo(this.post) > 16.0 * 16.0) {
                    continue;
                }

                // Seen, or near enough to be heard.
                if (d < bestDistance && (d < 100.0 || host.canSee(e))) {
                    bestDistance = d;
                    best = e;
                }
            }

            return best;
        }

        @Override
        public boolean tick(Run run, ServerPlayerEntity host) {
            if (this.foe != null && (!this.foe.isAlive() || this.foe.isRemoved())) {
                if (this.foe.isDead()) {
                    this.kills++;
                }

                this.foe = null;
                this.chase = 0;
            }

            if (this.foe != null && (this.foe.getWorld() != host.getWorld() || this.foe.squaredDistanceTo(host) > 48.0 * 48.0 || ++this.chase > 900)) {
                this.foe = null;
                this.chase = 0;
            }

            if (this.want > 0 && this.kills >= this.want) {
                // The count is made. What the last of them dropped is still hers to pick up.
                ItemEntity last = ++this.after < 100 && hasRoom(host) ? lying(host, 8.0, null) : null;
                if (last == null && this.after > 12) {
                    run.end("pilot_hunted", this.kills);
                    return false;
                }

                if (last != null) {
                    walk(run, host, last.getPos(), 0.3, NONE, false);
                }

                return true;
            }

            if (run.ticks % 5 == 0 && (this.foe == null || run.ticks % 20 == 0 && this.foe.squaredDistanceTo(host) > 16.0)) {
                // The nearest first: whatever has come up close is dealt with before a chase goes on.
                LivingEntity nearer = this.pick(host);
                if (nearer != null) {
                    this.foe = nearer;
                }
            }

            if (this.foe != null) {
                this.idle = 0;
                fight(run, host, this.foe);
                return true;
            }

            // Between fights: what they dropped.
            ItemEntity loot = lying(host, 7.0, null);
            if (loot != null && hasRoom(host)) {
                walk(run, host, loot.getPos(), 0.3, NONE, false);
                return true;
            }

            this.idle++;
            if (this.guard) {
                walk(run, host, this.post, 2.0, NATURAL, true);
                return true;
            }

            if (this.idle > this.linger) {
                run.end(this.kills > 0 ? "pilot_hunted" : "pilot_none_prey", this.kills);
                return false;
            }

            if (this.linger < 200) {
                // Asked to kill what is here, not to go looking for more.
                return true;
            }

            // Nothing in sight: she goes looking, a stretch at a time.
            if (this.roam == null || run.stuck >= 6 || walk(run, host, this.roam, 2.5, NATURAL, true)) {
                if (this.roam != null) {
                    this.heading += run.stuck >= 6 ? 110.0F : (host.getRandom().nextFloat() - 0.5F) * 80.0F;
                }

                double rad = Math.toRadians((double)this.heading);
                this.roam = surface(host.getWorld(), host.getX() - Math.sin(rad) * 18.0, host.getZ() + Math.cos(rad) * 18.0, host.getY());
                run.stuck = 0;
            }

            return true;
        }
    }

    /** The nearest dropped item within reach of a short walk. */
    /** Gone under: in water over its head, or riding a fall of it down. Nothing the body dives after. */
    static boolean sunk(ItemEntity item) {
        World world = item.getWorld();
        BlockPos at = item.getBlockPos();
        return world.getFluidState(at.up()).isIn(FluidTags.WATER) || world.getFluidState(at).isIn(FluidTags.WATER) && world.getFluidState(at.down()).isIn(FluidTags.WATER);
    }

    static ItemEntity lying(ServerPlayerEntity host, double within, Predicate<ItemStack> wanted) {
        ItemEntity best = null;
        double bestDistance = Double.MAX_VALUE;

        for (ItemEntity item : host.getWorld().getEntitiesByClass(ItemEntity.class, host.getBoundingBox().expand(within), x -> x.isAlive() && !x.getStack().isEmpty() && PhaseFx.samePhase(x, host))) {
            double d = item.squaredDistanceTo(host);
            if (d < bestDistance && !sunk(item) && (wanted == null || wanted.test(item.getStack())) && (d < 25.0 || host.canSee(item))) {
                bestDistance = d;
                best = item;
            }
        }

        return best;
    }

    /** Walks over what lies about until nothing is left. */
    static final class Collect implements Task {
        private final Predicate<ItemStack> wanted;
        private final Set<Integer> given = new HashSet<>();
        private ItemEntity item;
        private int spent;
        private int taken;

        Collect(Predicate<ItemStack> wanted) {
            this.wanted = wanted;
        }

        @Override
        public int label() {
            return L_COLLECT;
        }

        @Override
        public boolean tick(Run run, ServerPlayerEntity host) {
            if (this.item != null && !this.item.isAlive()) {
                this.taken++;
                this.item = null;
            }

            if (this.item != null && ++this.spent > 200) {
                this.given.add(this.item.getId());
                this.item = null;
            }

            if (this.item == null) {
                this.spent = 0;
                this.item = lying(host, 20.0, stack -> this.wanted == null || this.wanted.test(stack));
                if (this.item != null && this.given.contains(this.item.getId())) {
                    this.item = null;
                }

                if (this.item == null || !hasRoom(host)) {
                    run.end(this.taken > 0 ? "pilot_done" : "pilot_none");
                    return false;
                }
            }

            walk(run, host, this.item.getPos(), 0.2, NONE, false);
            return true;
        }
    }

    /**
     * Gets so many blocks of a kind: the ones in sight first, then the vein behind them. With {@code search}
     * she goes looking when there are none: down to the depth the ore lies at and along a tunnel there, or over
     * the surface for what lies on it.
     */
    static final class Mine implements Task {
        private final List<Predicate<BlockState>> tiers;
        private final int want;
        private final boolean search;
        private final int label;
        private final Set<BlockPos> skip = new HashSet<>();
        private final ArrayDeque<BlockPos> vein = new ArrayDeque<>();
        private Predicate<BlockState> kind;
        private Integer depth;
        private boolean deep;
        private BlockPos target;
        private Text name;
        private int spent;
        private int got;
        private int idle;
        private int walked;
        private int wrap;
        private int settle;
        private int pause;
        /** Where a crop was cut and with what it is sown again. */
        private final List<BlockPos> beds = new ArrayList<>();
        private final Map<BlockPos, net.minecraft.item.Item> seeds = new HashMap<>();
        private net.minecraft.block.Block cutting;
        private int sowing;
        private int lit;
        private int lighting;
        private ItemEntity loot;
        private int lootTicks;
        private final Set<Integer> lost = new HashSet<>();
        private BlockPos lastCell;
        private Direction shaft;
        private float heading;
        private Vec3d roam;
        private boolean searching;

        Mine(ServerPlayerEntity host, List<Predicate<BlockState>> tiers, int want, boolean search, int label) {
            this.tiers = tiers;
            this.want = want;
            this.search = search;
            this.label = label;
            this.heading = host.getYaw();
            this.shaft = host.getHorizontalFacing();
            this.lastCell = host.getBlockPos();

            // Where does this lie? Decided by the first block of the registry that fits.
            search:
            for (Predicate<BlockState> tier : tiers) {
                for (net.minecraft.block.Block block : Registries.BLOCK) {
                    if (tier.test(block.getDefaultState())) {
                        String path = Registries.BLOCK.getId(block).getPath();
                        this.depth = depthOf(path);
                        this.deep = this.depth != null;
                        break search;
                    }
                }
            }
        }

        private static Integer depthOf(String path) {
            if (path.equals("ancient_debris")) {
                return 15;
            } else if (!path.endsWith("_ore")) {
                return null;
            } else if (path.contains("diamond") || path.contains("redstone")) {
                return -54;
            } else if (path.contains("gold")) {
                return -16;
            } else if (path.contains("lapis")) {
                return -1;
            } else if (path.contains("iron")) {
                return 15;
            } else if (path.contains("copper")) {
                return 44;
            } else if (path.contains("coal") || path.contains("emerald")) {
                return 80;
            } else if (path.contains("quartz")) {
                return 30;
            } else {
                return -20;
            }
        }

        @Override
        public int label() {
            return this.searching ? L_SEARCH : this.label;
        }

        /**
         * What she has knocked out and not yet picked up. She will dig her way to it, since a drop often lies in
         * the hole its block left; one she cannot get to in five seconds is given up.
         */
        private ItemEntity drop(ServerPlayerEntity host) {
            if (!hasRoom(host)) {
                return null;
            }

            if (this.loot != null && this.loot.isAlive() && this.lootTicks++ < 100) {
                return this.loot;
            }

            if (this.loot != null && this.loot.isAlive()) {
                this.lost.add(this.loot.getId());
            }

            this.loot = null;
            this.lootTicks = 0;
            double best = Double.MAX_VALUE;

            for (ItemEntity item : host.getWorld().getEntitiesByClass(ItemEntity.class, host.getBoundingBox().expand(8.0), x -> x.isAlive() && !x.getStack().isEmpty() && PhaseFx.samePhase(x, host) && !this.lost.contains(x.getId()) && !sunk(x))) {
                double d = item.squaredDistanceTo(host);
                if (d < best) {
                    best = d;
                    this.loot = item;
                }
            }

            return this.loot;
        }

        /** The foot of a stalk, which is left standing so that it grows back. */
        private static boolean foot(World world, BlockPos pos) {
            BlockState state = world.getBlockState(pos);
            return stalk(state) && !world.getBlockState(pos.down()).isOf(state.getBlock()) && !(state.isOf(Blocks.KELP_PLANT) && world.getBlockState(pos.down()).isOf(Blocks.KELP_PLANT));
        }

        private boolean fits(BlockState state) {
            if (this.kind != null) {
                return this.kind.test(state);
            }

            for (Predicate<BlockState> tier : this.tiers) {
                if (tier.test(state)) {
                    return true;
                }
            }

            return false;
        }

        @Override
        public boolean tick(Run run, ServerPlayerEntity host) {
            World world = host.getWorld();
            if (this.got >= this.want) {
                // What fell out of the last one, then that is all.
                ItemEntity drop = this.wrap++ < 360 ? this.drop(host) : null;
                if (drop != null) {
                    this.settle = 0;
                    walk(run, host, drop.getPos(), 0.3, ANYTHING, false);
                    return true;
                }

                if (this.settle++ < 30 && this.wrap < 360) {
                    // The last of it may still be falling out of the wall.
                    return true;
                }

                run.end("pilot_mined", this.got, this.name == null ? Text.literal("?") : this.name);
                return false;
            }

            if (run.ticks % 40 == 0 && !hasRoom(host)) {
                run.end("pilot_full", this.got);
                return false;
            }

            if (this.target != null) {
                BlockState state = world.getBlockState(this.target);
                if (!this.fits(state)) {
                    this.got++;
                    if (this.cutting instanceof net.minecraft.block.CropBlock || this.cutting == Blocks.NETHER_WART) {
                        this.beds.add(this.target);
                        this.seeds.put(this.target, this.cutting.asItem());
                    }

                    for (BlockPos near : BlockPos.iterate(this.target.add(-1, -1, -1), this.target.add(1, 1, 1))) {
                        if (!this.skip.contains(near) && !this.vein.contains(near) && this.fits(world.getBlockState(near)) && world.getBlockEntity(near) == null && !foot(world, near)) {
                            this.vein.add(near.toImmutable());
                        }
                    }

                    this.target = null;
                } else if (++this.spent > 400) {
                    this.skip.add(this.target);
                    this.target = null;
                } else {
                    if (state.isToolRequired() && run.ticks % 20 == 0 && equip(host, stack -> (double)HostManager.worksOn(stack, state)) < 0) {
                        run.end("pilot_tool");
                        return false;
                    }

                    dig(run, host, this.target, true);
                    return true;
                }
            }

            if (!this.vein.isEmpty()) {
                BlockPos eye = BlockPos.ofFloored(host.getEyePos());
                BlockPos best = null;

                for (BlockPos pos : this.vein) {
                    if (best == null || pos.getSquaredDistance(eye) < best.getSquaredDistance(eye)) {
                        best = pos;
                    }
                }

                this.vein.remove(best);
                if (this.fits(world.getBlockState(best))) {
                    this.target = best;
                    this.cutting = world.getBlockState(best).getBlock();
                    this.spent = 0;
                }

                return true;
            }

            ItemEntity drop = this.got > 0 && this.pause++ < 160 ? this.drop(host) : null;
            if (drop != null) {
                walk(run, host, drop.getPos(), 0.3, ANYTHING, false);
                return true;
            }

            if (!this.beds.isEmpty()) {
                // Sown again with what the crop itself gave.
                BlockPos bed = this.beds.get(0);
                net.minecraft.item.Item seed = this.seeds.get(bed);
                if (++this.sowing < 80 && world.getBlockState(bed).isAir() && carries(host, stack -> stack.isOf(seed)) && put(run, host, bed, seed) == 0) {
                    return true;
                }

                this.beds.remove(0);
                this.sowing = 0;
                return true;
            }

            if (run.ticks % 10 == 0) {
                for (Predicate<BlockState> tier : this.kind != null ? List.of(this.kind) : this.tiers) {
                    BlockPos found = seen(host, tier, 14, this.skip);
                    if (found != null && foot(world, found)) {
                        this.skip.add(found);
                        found = null;
                    }

                    if (found != null) {
                        this.cutting = world.getBlockState(found).getBlock();
                        this.kind = tier;
                        this.target = found;
                        this.spent = 0;
                        this.idle = 0;
                        this.pause = 0;
                        this.searching = false;
                        if (this.name == null) {
                            this.name = world.getBlockState(found).getBlock().getName();
                        }

                        return true;
                    }
                }
            }

            if (!this.search) {
                if (++this.idle > 30) {
                    if (this.got > 0) {
                        run.end("pilot_mined", this.got, this.name == null ? Text.literal("?") : this.name);
                    } else {
                        run.end("pilot_none");
                    }

                    return false;
                }

                return true;
            }

            if (this.got > 0 && this.cutting != null && (stalk(this.cutting.getDefaultState()) || this.cutting instanceof net.minecraft.block.CropBlock)) {
                // A bed or a stand of cane is cut and that is that: she does not go looking for the next one unasked.
                if (++this.idle > 40) {
                    run.end("pilot_mined", this.got, this.name == null ? Text.literal("?") : this.name);
                    return false;
                }

                return true;
            }

            this.searching = true;
            BlockPos cell = host.getBlockPos();
            if (!cell.equals(this.lastCell)) {
                this.lastCell = cell;
                this.walked++;
            }

            if (this.walked > (this.deep ? 260 : 500)) {
                run.end(this.got > 0 ? "pilot_mined_some" : "pilot_none_far", this.got);
                return false;
            }

            if (this.deep) {
                if (run.ticks % 40 == 0 && equip(host, stack -> stack.getItem() instanceof PickaxeItem && stack.getMaxDamage() - stack.getDamage() > 2 ? 1.0 : 0.0) < 0) {
                    run.end("pilot_tool");
                    return false;
                }

                // Down to where it lies, a step a block, then straight on at that depth.
                boolean overworld = world.getRegistryKey() == World.OVERWORLD;
                int level = overworld || this.depth == 15 ? this.depth : host.getBlockY();
                int below = host.getBlockY() > level + 2 ? -3 : 0;
                Vec3d ahead = new Vec3d(host.getX() + (double)this.shaft.getOffsetX() * 6.0, host.getY() + (double)below, host.getZ() + (double)this.shaft.getOffsetZ() * 6.0);
                if (run.turnFor == 39) {
                    // Turned away from lava or a drop: the shaft goes on in the new direction.
                    for (int i = 0; i < run.turn; i++) {
                        this.shaft = this.shaft.rotateYClockwise();
                    }

                    run.turnFor = 0;
                }

                if (this.walked - this.lit >= 7 && host.isOnGround() && world.getLightLevel(cell) < 6 && carries(host, stack -> stack.isOf(Items.TORCH))) {
                    // A torch behind her every few steps, if she has them.
                    if (++this.lighting < 40 && put(run, host, cell, Items.TORCH) == 0) {
                        return true;
                    }

                    this.lit = this.walked;
                    this.lighting = 0;
                }

                burrow(run, host, ahead, this.shaft, true);
                return true;
            }

            if (this.roam == null || run.stuck >= 6 || walk(run, host, this.roam, 3.0, NATURAL, true)) {
                if (this.roam != null && run.stuck >= 6) {
                    this.heading += 70.0F;
                }

                double rad = Math.toRadians((double)this.heading);
                this.roam = surface(world, host.getX() - Math.sin(rad) * 20.0, host.getZ() + Math.cos(rad) * 20.0, host.getY());
                run.stuck = 0;
            }

            return true;
        }
    }

    /** Takes down what stands on the ground around one spot: a hill, a mound, a wall of earth. */
    static final class Clear implements Task {
        private final List<BlockPos> left = new ArrayList<>();
        private final int all;
        private BlockPos target;
        private int spent;
        private int done;

        Clear(ServerPlayerEntity host, BlockPos at) {
            World world = host.getWorld();
            int floor = host.getBlockY();
            Set<BlockPos> known = new HashSet<>();
            ArrayDeque<BlockPos> queue = new ArrayDeque<>();
            BlockPos start = at.getY() < floor ? new BlockPos(at.getX(), floor, at.getZ()) : at;
            queue.add(start);
            known.add(start);

            while (!queue.isEmpty() && this.left.size() < 600) {
                BlockPos pos = queue.poll();
                BlockState state = world.getBlockState(pos);
                if (state.isAir() || !state.getFluidState().isEmpty() || world.getBlockEntity(pos) != null) {
                    continue;
                }

                float hardness = state.getHardness(world, pos);
                if (hardness < 0.0F || hardness > 50.0F) {
                    continue;
                }

                this.left.add(pos);

                for (Direction side : Direction.values()) {
                    BlockPos next = pos.offset(side);
                    if (next.getY() >= floor && next.getY() <= at.getY() + 20 && Math.abs(next.getX() - at.getX()) <= 7 && Math.abs(next.getZ() - at.getZ()) <= 7 && known.add(next)) {
                        queue.add(next);
                    }
                }
            }

            this.all = this.left.size();
        }

        @Override
        public int label() {
            return L_CLEAR;
        }

        @Override
        public boolean tick(Run run, ServerPlayerEntity host) {
            World world = host.getWorld();
            if (this.target != null && (world.getBlockState(this.target).isAir() || ++this.spent > 300)) {
                if (world.getBlockState(this.target).isAir()) {
                    this.done++;
                }

                this.left.remove(this.target);
                this.target = null;
            }

            if (this.target == null) {
                this.left.removeIf(pos -> world.getBlockState(pos).isAir());
                if (this.left.isEmpty()) {
                    run.end(this.done > 0 || this.all > 0 ? "pilot_cleared" : "pilot_none", this.all);
                    return false;
                }

                Vec3d eye = host.getEyePos();
                double best = Double.MAX_VALUE;

                for (BlockPos pos : this.left) {
                    // The top of the heap first where it can be reached: nothing is undermined.
                    double score = Math.sqrt(pos.getSquaredDistance(eye)) - (double)(pos.getY() - host.getBlockY()) * 0.35;
                    if (score < best) {
                        best = score;
                        this.target = pos;
                    }
                }

                this.spent = 0;
            }

            dig(run, host, this.target, true);
            return true;
        }
    }

    /** Away from whatever is after the body, for a few seconds. */
    static final class Flee implements Task {
        private int ticks;

        @Override
        public int label() {
            return L_FLEE;
        }

        @Override
        public boolean tick(Run run, ServerPlayerEntity host) {
            Vec3d from = Vec3d.ZERO;
            int count = 0;

            for (LivingEntity e : host.getWorld().getEntitiesByClass(LivingEntity.class, host.getBoundingBox().expand(20.0), x -> x.isAlive() && x != host && (x instanceof Monster || x instanceof MobEntity mob && mob.getTarget() == host))) {
                from = from.add(e.getPos());
                count++;
            }

            if (++this.ticks > 240 || count == 0 && this.ticks > 40) {
                run.end("pilot_safe");
                return false;
            }

            Vec3d away = count == 0 ? host.getRotationVec(1.0F) : host.getPos().subtract(from.multiply(1.0 / (double)count));
            away = new Vec3d(away.x, 0.0, away.z);
            if (away.lengthSquared() < 0.01) {
                away = new Vec3d(1.0, 0.0, 0.0);
            }

            Vec3d to = host.getPos().add(away.normalize().multiply(14.0));
            walk(run, host, surface(host.getWorld(), to.x, to.z, host.getY()), 1.0, NONE, true);
            run.flags |= SPRINT;
            return true;
        }
    }

    /** Eats what the body carries until it is full. */
    static final class Eat implements Task {
        private int ticks;

        @Override
        public int label() {
            return L_EAT;
        }

        static double foodWorth(ItemStack stack) {
            if (!stack.isFood() || stack.isOf(Items.ROTTEN_FLESH) || stack.isOf(Items.SPIDER_EYE) || stack.isOf(Items.POISONOUS_POTATO) || stack.isOf(Items.PUFFERFISH) || stack.isOf(Items.CHORUS_FRUIT) || stack.isOf(Items.SUSPICIOUS_STEW) || stack.isOf(Items.ENCHANTED_GOLDEN_APPLE) || stack.isOf(Items.GOLDEN_APPLE)) {
                return 0.0;
            }

            return 1.0 + (double)stack.getItem().getFoodComponent().getHunger();
        }

        @Override
        public boolean tick(Run run, ServerPlayerEntity host) {
            if (!host.getHungerManager().isNotFull() || ++this.ticks > 400) {
                run.end("pilot_fed");
                return false;
            }

            int slot = equip(host, Eat::foodWorth);
            if (slot < 0) {
                run.end("pilot_no_food");
                return false;
            }

            run.slot = slot;
            if (host.getInventory().selectedSlot == slot) {
                run.flags |= USE;
            }

            return true;
        }
    }

    /** What she is out to find on foot. */
    static final class Quest {
        String[] structures;
        String[] biomes;
        Predicate<LivingEntity> creature;
        /** Blocks to walk up to and leave alone: a chest, a spawner, water. */
        Predicate<BlockState> blocks;
        Text name;
    }

    /**
     * Goes and looks: walks on in one direction, round what is in the way, until the thing comes into view.
     * There is no knowing where anything is beforehand; a village is found when its houses are near enough to see.
     */
    static final class Explore implements Task {
        private Quest quest;
        private final int far;
        private final BlockPos from;
        float heading;
        private Vec3d leg;
        private Vec3d found;
        private int walked;
        private BlockPos lastCell;
        private int turns;

        Explore(ServerPlayerEntity host, Quest quest, int far) {
            this.quest = quest;
            this.far = far;
            this.from = host.getBlockPos();
            this.heading = host.getYaw();
            this.lastCell = host.getBlockPos();
        }

        @Override
        public int label() {
            return L_EXPLORE;
        }

        @Override
        public boolean tick(Run run, ServerPlayerEntity host) {
            ServerWorld world = host.getWorld();
            if (this.found != null) {
                double offX = this.found.x - host.getX();
                double offZ = this.found.z - host.getZ();
                boolean sunk = this.quest.blocks == null && this.quest.creature == null && world.getFluidState(BlockPos.ofFloored(this.found.x, this.found.y - 1.0, this.found.z)).isIn(FluidTags.WATER);
                if (sunk && (host.isTouchingWater() || offX * offX + offZ * offZ < 40.0 * 40.0)) {
                    // It lies under the water: found from the shore, and that is as near as the chip cares to go.
                    run.end("pilot_found", this.quest.name, (int)this.found.x, (int)this.found.y, (int)this.found.z);
                    return false;
                }

                if (walk(run, host, this.found, this.quest.blocks != null ? 3.0 : 6.0, NATURAL, this.quest.blocks == null) || run.stuck >= 8) {
                    run.end("pilot_found", this.quest.name, (int)this.found.x, (int)this.found.y, (int)this.found.z);
                    return false;
                }

                return true;
            }

            if (this.quest == null && run.ticks % 20 == 0) {
                // Out for a look around: any place worth the name, as long as it is not the one she set out from.
                BlockPos at = structureNear(world, host.getBlockPos(), NOTABLE, 4);
                if (at != null && at.getSquaredDistance(this.from.getX(), at.getY(), this.from.getZ()) > 64.0 * 64.0) {
                    this.quest = new Quest();
                    this.quest.name = Text.literal(lastStructure.replace('_', ' '));
                    this.found = surface(world, (double)at.getX() + 0.5, (double)at.getZ() + 0.5, host.getY());
                    run.path = null;
                    return true;
                }
            }

            if (this.quest != null && run.ticks % 20 == 0) {
                if (this.quest.structures != null) {
                    BlockPos at = structureNear(world, host.getBlockPos(), this.quest.structures, 5);
                    if (at != null) {
                        this.found = surface(world, (double)at.getX() + 0.5, (double)at.getZ() + 0.5, host.getY());
                    }
                }

                if (this.found == null && this.quest.biomes != null) {
                    String biome = world.getBiome(host.getBlockPos()).getKey().map(key -> key.getValue().getPath()).orElse("");

                    for (String word : this.quest.biomes) {
                        if (biome.contains(word)) {
                            run.end("pilot_found", this.quest.name, host.getBlockX(), host.getBlockY(), host.getBlockZ());
                            return false;
                        }
                    }
                }

                if (this.found == null && this.quest.creature != null) {
                    for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, host.getBoundingBox().expand(48.0, 16.0, 48.0), x -> x != host && x.isAlive() && this.quest.creature.test(x))) {
                        if (host.canSee(e) || e.squaredDistanceTo(host) < 144.0) {
                            this.found = e.getPos();
                            break;
                        }
                    }
                }

                if (this.found == null && this.quest.blocks != null) {
                    BlockPos at = spotted(host, this.quest.blocks, 20);
                    if (at != null) {
                        this.found = Vec3d.ofBottomCenter(at);
                    }
                }

                if (this.found != null) {
                    run.path = null;
                    return true;
                }
            }

            BlockPos cell = host.getBlockPos();
            if (!cell.equals(this.lastCell)) {
                this.lastCell = cell;
                this.walked++;
            }

            if (this.walked > this.far) {
                run.end(this.quest == null ? "pilot_walked" : "pilot_none_far", this.walked);
                return false;
            }

            if (this.leg == null || run.stuck >= 6 || walk(run, host, this.leg, 3.0, NATURAL, true)) {
                if (this.leg != null && run.stuck >= 6) {
                    // No way on: she bears off, to one side and then the other.
                    this.turns++;
                    this.heading += this.turns % 2 == 1 ? 60.0F : -120.0F;
                }

                double rad = Math.toRadians((double)this.heading);
                this.leg = surface(world, host.getX() - Math.sin(rad) * 24.0, host.getZ() + Math.cos(rad) * 24.0, host.getY());
                run.stuck = 0;
            } else {
                run.flags |= SPRINT;
            }

            return true;
        }
    }

    /** What stands above ground and is worth walking up to. */
    private static final String[] NOTABLE = {"village", "pyramid", "temple", "pillager_outpost", "mansion", "igloo", "swamp_hut", "shipwreck", "ruined_portal", "ruin"};
    private static String lastStructure = "";

    /** A structure of one of these kinds standing in the chunks around: its starting chunk, or null. */
    static BlockPos structureNear(ServerWorld world, BlockPos at, String[] words, int chunks) {
        Registry<Structure> registry = world.getRegistryManager().get(RegistryKeys.STRUCTURE);
        ChunkPos here = new ChunkPos(at);
        BlockPos best = null;

        for (int cx = here.x - chunks; cx <= here.x + chunks; cx++) {
            for (int cz = here.z - chunks; cz <= here.z + chunks; cz++) {
                WorldChunk chunk = world.getChunkManager().getWorldChunk(cx, cz);
                if (chunk == null) {
                    continue;
                }

                for (Map.Entry<Structure, LongSet> entry : chunk.getStructureReferences().entrySet()) {
                    Identifier id = registry.getId(entry.getKey());
                    if (id == null || entry.getValue().isEmpty()) {
                        continue;
                    }

                    for (String word : words) {
                        if (id.getPath().contains(word)) {
                            ChunkPos start = new ChunkPos(entry.getValue().iterator().nextLong());
                            BlockPos centre = start.getCenterAtY(at.getY());
                            if (best == null || centre.getSquaredDistance(at) < best.getSquaredDistance(at)) {
                                best = centre;
                                lastStructure = id.getPath();
                            }
                        }
                    }
                }
            }
        }

        return best;
    }

    // ------------------------------------------------------------------ orders

    private static final Set<String> MOBS = Set.of(
        "zombie", "skeleton", "creeper", "spider", "witch", "pillager", "slime", "blaze", "phantom", "drowned", "iron_golem", "villager", "cow", "pig", "sheep",
        "chicken", "horse", "enderman", "wolf", "cat"
    );
    private static final Set<String> MEAT = Set.of("cow", "pig", "sheep", "chicken", "rabbit", "mooshroom");
    /** Words for places, and the part of a structure's id they stand for. */
    private static final String[][] STRUCTURES = {
        {"village", "деревн", "село$", "села$", "селищ", "поселен", "village", "dorf", "aldea", "pueblo", "wios", "wies$", "wsi$", "vila$", "aldeia", "村"},
        {"pyramid|temple", "храм", "пирамид", "temple", "pyramid", "tempel", "pyramide", "templo", "piramide", "swiatyn", "piramid", "神殿", "ピラミッド", "神庙", "金字塔"},
        {"ruined_portal", "портал", "portal", "ポータル", "传送门"},
        {"mineshaft", "заброшенн", "mineshaft", "mine shaft", "stollen", "mina abandonada", "mine abandonnee", "kopalni", "廃坑", "矿井"},
        {"stronghold|fortress", "крепост", "фортец", "stronghold", "fortress", "festung", "fortaleza", "forteresse", "twierdz", "要塞"},
        {"pillager_outpost", "аванпост", "форпост", "outpost", "aussenposten", "puesto de avanzada", "avant poste", "posterun", "前哨"},
        {"mansion", "особняк", "mansion", "anwesen", "manoir", "rezydencj", "mansao", "邸宅", "府邸"},
        {"monument", "монумент", "monument", "monumento", "モニュメント", "海底神殿", "遗迹"},
        {"shipwreck", "кораб", "shipwreck", "wreck", "schiffswrack", "naufrag", "wrak", "難破船", "沉船"},
        {"igloo", "иглу", "igloo", "iglu", "イグルー", "冰屋"},
        {"swamp_hut", "хижин", "witch hut", "hexenhutte", "cabana de bruja", "cabane de sorciere", "chatka", "魔女の小屋", "女巫小屋"},
        {"ancient_city", "древний город", "ancient city", "antike stadt", "ciudad antigua", "cite antique", "starozytne miasto", "cidade antiga", "古代都市", "远古城市"},
        {"bastion", "бастион", "bastion", "砦", "堡垒"},
        {"buried_treasure", "клад", "сокровищ", "скарб", "treasure", "schatz", "tesoro", "tresor", "skarb$", "tesouro", "宝", "宝藏"},
        {"end_city", "город края", "город энда", "місто краю", "end city", "endstadt", "ciudad del end", "cite de l end", "miasto kresu", "cidade do end", "エンドシティ", "末地城"},
        {"nether_fossil", "окаменел", "скамьянiл", "fossil", "fosil", "fossile", "skamielin", "化石"},
        {"trail_ruins", "руины тропы", "trail ruins", "pfadruinen", "ruinas de sendero", "ruines de sentier", "トレイル遺跡", "古迹废墟"},
        {"jungle_pyramid", "джунгл", "jungle temple", "dschungeltempel", "templo de la jungla", "temple de la jungle", "ジャングルの寺院", "丛林神庙"},
        {"ruin", "руин", "развалин", "ruin", "ruinen", "ruinas", "ruines", "ruiny", "遺跡", "废墟"}
    };
    private static final String[][] BIOMES = {
        {"desert", "пустын", "пустел", "desert", "wuste", "desierto", "pustyn", "deserto", "砂漠", "沙漠"},
        {"jungle", "джунгл", "jungle", "dschungel", "selva", "dzungl", "ジャングル", "丛林"},
        {"snowy|frozen|ice", "снег", "снеж", "зим", "тундр", "ледник", "snow", "tundra", "schnee", "nieve", "neige", "snieg", "neve$", "雪"},
        {"ocean", "океан", "море$", "моря$", "морю$", "ocean", "sea$", "meer", "mar$", "mer$", "morze", "海"},
        {"swamp", "болот", "swamp", "sumpf", "pantano", "marais", "bagn", "沼"},
        {"forest", "лес$", "леса$", "лесу$", "ліс$", "forest", "woods", "wald$", "bosque", "foret", "las$", "lasu$", "floresta", "森"},
        {"peaks|slopes|hills|mountain", "горы$", "горах", "гор$", "mountains", "gebirge", "montanas", "montagnes", "gory$", "montanhas", "山脈", "山脉"},
        {"plains", "равнин", "plains", "ebene", "llanura", "plaine", "rownin", "planicie", "平原"},
        {"savanna", "саванн", "savanna", "savanne", "sabana", "サバンナ", "热带草原"},
        {"taiga", "тайг", "taiga", "タイガ", "针叶林"},
        {"beach", "пляж", "берег", "beach", "shore", "strand", "playa", "plage", "plaz", "praia", "海岸", "沙滩"},
        {"river", "рек$", "реку$", "реки$", "речк", "river", "fluss", "rio$", "riviere", "rzek", "川", "河"},
        {"mushroom", "грибн", "mushroom", "pilz", "champinon", "champignon", "grzyb", "cogumelo", "キノコ", "蘑菇"},
        {"badlands", "меса", "мезу", "бесплод", "badlands", "mesa$", "tafelberg", "荒野", "恶地"}
    };

    /** A structure by the words of its own id: "trial chambers", "ancient city", whatever a mod calls its tower. */
    private static String[] anyStructure(ServerWorld world, String subject) {
        String text = VoltaChat.normalize(subject);
        List<String> words = new ArrayList<>();

        for (String token : text.split(" ")) {
            if (token.length() >= 4) {
                words.add(token);
            }
        }

        String best = null;
        int bestScore = 0;

        for (Identifier id : world.getRegistryManager().get(RegistryKeys.STRUCTURE).getIds()) {
            String[] parts = id.getPath().split("[_/]");
            int score = 0;

            for (String part : parts) {
                for (String word : words) {
                    if (part.length() >= 4 && (word.equals(part) || word.startsWith(part) || part.startsWith(word))) {
                        score += part.length();
                    }
                }
            }

            if (score > bestScore) {
                bestScore = score;
                best = id.getPath();
            }
        }

        return best == null ? null : new String[]{best};
    }

    private static String[] place(String subject, String[][] table) {
        String text = VoltaChat.normalize(subject);
        String[] tokens = text.isEmpty() ? new String[0] : text.split(" ");

        for (String[] row : table) {
            for (int i = 1; i < row.length; i++) {
                VoltaChat.guessed = false;
                if (VoltaChat.find(text, tokens, row[i]) >= 0 && !VoltaChat.guessed) {
                    return row[0].split("\\|");
                }
            }
        }

        return null;
    }

    private static Predicate<LivingEntity> hostile(ServerPlayerEntity host) {
        // Monsters, whatever is after the body, and players for as long as the Host has players switched on.
        return e -> HostManager.enemy(host, e);
    }

    private static Predicate<LivingEntity> ofKind(String alias) {
        return e -> {
            String path = Registries.ENTITY_TYPE.getId(e.getType()).getPath();
            return path.equals(alias) || path.contains(alias) || alias.equals("zombie") && (path.equals("husk") || path.equals("drowned"));
        };
    }

    private static Predicate<LivingEntity> meat() {
        return e -> MEAT.contains(Registries.ENTITY_TYPE.getId(e.getType()).getPath()) && !e.isBaby();
    }

    /** What the Host is looking at, block or creature, a long way off. */
    private static HitResult pointed(ServerPlayerEntity host) {
        LivingEntity seen = HostManager.lookTarget(host, 48.0);
        if (seen != null) {
            return new net.minecraft.util.hit.EntityHitResult(seen);
        }

        Vec3d eye = host.getEyePos();
        return host.getWorld().raycast(new RaycastContext(eye, eye.add(host.getRotationVec(1.0F).multiply(64.0)), RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, host));
    }

    private static void go(ServerPlayerEntity host, Task task, boolean held) {
        if (begin(host, task, held)) {
            HostManager.say(host, "pilot_go", 1, Text.translatable("hud.windyorigins.pilot.task." + task.label()));
        }
    }

    /** Several requests in one sentence: the first now, the rest as each job ends. */
    public static void orders(ServerPlayerEntity host, List<PilotSpeech.Order> all) {
        if (all.get(0).kind().equals("resume")) {
            List<PilotSpeech.Order> paused = PAUSED.remove(host.getUuid());
            if (paused == null) {
                HostManager.say(host, "pilot_where", 2);
                return;
            }

            all = paused;
        } else if (!all.get(0).kind().equals("take")) {
            PAUSED.remove(host.getUuid());
        }

        if (all.get(0).kind().equals("again")) {
            List<PilotSpeech.Order> last = LAST.get(host.getUuid());
            if (last == null) {
                HostManager.say(host, "pilot_where", 2);
                return;
            }

            all = last;
        } else if (!all.get(0).kind().equals("release") && !all.get(0).kind().equals("take")) {
            LAST.put(host.getUuid(), List.copyOf(all));
        }

        if (!all.get(0).kind().equals("return")) {
            FROM.put(host.getUuid(), host.getPos());
        }

        NEXT.remove(host.getUuid());
        order(host, all.get(0));
        if (all.size() > 1 && !all.get(0).kind().equals("release")) {
            NEXT.put(host.getUuid(), new ArrayDeque<>(all.subList(1, all.size())));
        }
    }

    /** A spoken request for the body. */
    public static void order(ServerPlayerEntity host, PilotSpeech.Order order) {
        if (puppeted(host)) {
            return;
        }

        if (HostManager.isBroken(host)) {
            HostManager.deadChip(host);
            return;
        }

        boolean held = active(host) && run(host).held;
        ServerWorld world = host.getWorld();
        String subject = order.subject();
        if (!order.kind().equals("release") && !order.kind().equals("take")) {
            CURRENT.put(host.getUuid(), order);
        }

        String alias = VoltaChat.canonical(subject);
        ServerPlayerEntity named = order.player() == null ? null : host.getServer().getPlayerManager().getPlayer(order.player());
        switch (order.kind()) {
            case "release" -> release(host, "pilot_off");
            case "take" -> {
                if (active(host)) {
                    run(host).held = true;
                    HostManager.say(host, "pilot_on", 1);
                } else {
                    begin(host, null, true);
                }
            }
            case "flee" -> go(host, new Flee(), held);
            case "eat" -> go(host, new Eat(), held);
            case "home" -> {
                BlockPos bed = host.getSpawnPointPosition() != null && host.getSpawnPointDimension() == world.getRegistryKey() ? host.getSpawnPointPosition() : world.getSpawnPos();
                Vec3d home = Vec3d.ofBottomCenter(bed);
                go(host, new Goto(() -> home, 2.0, L_HOME, false), held);
            }
            case "return" -> {
                Vec3d back = FROM.get(host.getUuid());
                if (back == null) {
                    HostManager.say(host, "pilot_where", 2);
                } else {
                    go(host, new Goto(() -> back, 2.0, L_WALK, false), held);
                }
            }
            case "guard" -> go(host, new Hunt(host, hostile(host), 0, true, 0), held);
            case "stash" -> stash(host, order, held);
            case "withdraw" -> withdraw(host, order, held);
            case "craft" -> craft(host, order, held);
            case "smelt" -> smelt(host, order, held);
            case "bridge" -> go(host, new Bridge(host, order.number() > 0 ? Math.min(order.number(), 64) : 0), held);
            case "pillar" -> go(host, new Climb(host, order.number() > 0 ? Math.min(order.number(), 64) : 4), held);
            case "torch" -> go(host, new PutOne(host.getBlockPos(), Items.TORCH, "pilot_no_torch"), held);
            case "shelter" -> go(host, new Shelter(host), held);
            case "sleep" -> {
                BlockPos bed = bedNear(host);
                if (!world.isNight() && !world.isThundering()) {
                    HostManager.say(host, "pilot_not_night", 2);
                } else if (bed == null) {
                    HostManager.say(host, "pilot_no_bed", 2);
                } else {
                    go(host, new Sleep(bed), held);
                }
            }
            case "hunt", "farm" -> hunt(host, order, named, alias, held);
            case "follow" -> {
                Entity found = named != null ? named : (pointed(host) instanceof net.minecraft.util.hit.EntityHitResult hit ? hit.getEntity() : null);
                if (found == null && alias != null && MOBS.contains(alias)) {
                    // Not under the crosshair, but named by its kind: the nearest one in sight.
                    Predicate<LivingEntity> kind = ofKind(alias);
                    double best = Double.MAX_VALUE;

                    for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, host.getBoundingBox().expand(32.0), x -> x != host && x.isAlive() && kind.test(x) && host.canSee(x))) {
                        if (e.squaredDistanceTo(host) < best) {
                            best = e.squaredDistanceTo(host);
                            found = e;
                        }
                    }
                }

                Entity whom = found;
                if (whom == null) {
                    HostManager.say(host, "pilot_where", 2);
                } else {
                    go(host, new Goto(() -> whom.isAlive() && whom.getWorld() == host.getWorld() ? whom.getPos() : null, 3.0, L_FOLLOW, true), held);
                }
            }
            case "clear" -> {
                if (pointed(host) instanceof BlockHitResult hit && hit.getType() != HitResult.Type.MISS) {
                    go(host, new Clear(host, hit.getBlockPos()), held);
                } else {
                    HostManager.say(host, "pilot_where", 2);
                }
            }
            case "mine" -> {
                if (order.what().equals("crops")) {
                    go(host, new Mine(host, List.of(Pilot::ripe), order.number() > 0 ? order.number() : 128, false, L_MINE), held);
                } else if (order.what().equals("food")) {
                    go(host, new Hunt(host, meat(), order.number() > 0 ? order.number() : 3, false, 900), held);
                } else if (order.what().equals("resources")) {
                    resources(host, held);
                } else if (VoltaChat.thing(subject).isBlank() && alias != null) {
                    // "Наруби дерева": a tree is not the name of a block, but the everyday word says which blocks.
                    mineNamed(host, order, subject, List.of(), List.of(), held, true);
                } else if (VoltaChat.thing(subject).isBlank()) {
                    if (pointed(host) instanceof BlockHitResult hit && hit.getType() != HitResult.Type.MISS) {
                        net.minecraft.block.Block block = world.getBlockState(hit.getBlockPos()).getBlock();
                        go(host, new Mine(host, List.of(state -> state.isOf(block)), order.number() > 0 ? order.number() : 16, false, L_MINE), held);
                    } else {
                        HostManager.say(host, "pilot_where", 2);
                    }
                } else {
                    String thing = VoltaChat.thing(subject);
                    HostManager.askNames(host, 1, thing, (exact, partial) -> mineNamed(host, order, thing, exact, partial, held, true));
                }
            }
            case "collect" -> {
                String thing = VoltaChat.loot(subject);
                if (thing.isBlank()) {
                    go(host, new Collect(null), held);
                } else {
                    HostManager.askNames(host, 0, thing, (exact, partial) -> {
                        Set<Identifier> ids = new HashSet<>(exact.isEmpty() ? partial : exact);
                        String everyday = VoltaChat.canonical(thing);
                        go(host, new Collect(stack -> ids.contains(Registries.ITEM.getId(stack.getItem())) || ids.isEmpty() && everyday != null && Registries.ITEM.getId(stack.getItem()).getPath().contains(everyday)), held);
                    });
                }
            }
            case "find", "goto" -> seek(host, order, named, alias, held);
            case "auto" -> auto(host, held);
            default -> {
            }
        }
    }

    private static void hunt(ServerPlayerEntity host, PilotSpeech.Order order, ServerPlayerEntity named, String alias, boolean held) {
        boolean farm = order.kind().equals("farm");
        // "Killer mode": she goes on looking for the next one until told to stop.
        int linger = order.what().equals("killer") ? 6000 : (farm ? 900 : 100);
        int want = order.number() > 0 ? order.number() : 0;
        LivingEntity one = named;
        if (one == null && (order.pointed() || order.anyPlayer()) && pointed(host) instanceof net.minecraft.util.hit.EntityHitResult hit && hit.getEntity() instanceof LivingEntity seen) {
            one = seen;
        }

        if (one == null && order.anyPlayer()) {
            // "That player" with nobody under the crosshair: the nearest one in sight.
            double best = Double.MAX_VALUE;

            for (ServerPlayerEntity other : host.getWorld().getPlayers()) {
                double d = other.squaredDistanceTo(host);
                if (other != host && other.isAlive() && d < 48.0 * 48.0 && d < best && host.canSee(other)) {
                    best = d;
                    one = other;
                }
            }
        }

        if (one != null) {
            if (one instanceof PlayerEntity && !HostManager.fairGame(host, one)) {
                HostManager.say(host, "spared", 2);
                return;
            }

            LivingEntity target = one;
            go(host, new Hunt(host, e -> e == target, 1, false, 60), held);
        } else if (order.anyPlayer()) {
            HostManager.say(host, "pilot_none", 2);
        } else if (order.what().equals("food") || order.what().equals("animals")) {
            go(host, new Hunt(host, meat(), want > 0 ? want : (farm ? 0 : 3), false, 900), held);
        } else if (alias != null && MOBS.contains(alias)) {
            go(host, new Hunt(host, ofKind(alias), want, false, linger), held);
        } else if (order.what().equals("hostile") || order.what().equals("killer") || order.subject().isBlank()) {
            go(host, new Hunt(host, hostile(host), want, false, linger), held);
        } else {
            HostManager.askNames(host, 2, order.subject(), (exact, partial) -> {
                Set<Identifier> ids = new HashSet<>(exact.isEmpty() ? partial : exact);
                if (ids.isEmpty()) {
                    HostManager.say(host, "pilot_unknown", 2, order.subject());
                } else {
                    go(host, new Hunt(host, e -> ids.contains(Registries.ENTITY_TYPE.getId(e.getType())), want, false, linger), held);
                }
            });
        }
    }

    /** What a thing is got from when it is not a block lying in the ground: the creature that drops it, or the block it falls out of. */
    private static final Map<String, String> SOURCES = Map.ofEntries(
        Map.entry("leather", "cow"), Map.entry("beef", "cow"), Map.entry("porkchop", "pig"), Map.entry("mutton", "sheep"), Map.entry("chicken", "chicken"),
        Map.entry("feather", "chicken"), Map.entry("rotten_flesh", "zombie"), Map.entry("bone", "skeleton"), Map.entry("arrow", "skeleton"),
        Map.entry("string", "spider"), Map.entry("spider_eye", "spider"), Map.entry("gunpowder", "creeper"), Map.entry("ender_pearl", "enderman"),
        Map.entry("slime_ball", "slime"), Map.entry("blaze_rod", "blaze"), Map.entry("phantom_membrane", "phantom"), Map.entry("rabbit_hide", "rabbit"),
        Map.entry("rabbit", "rabbit"), Map.entry("rabbit_foot", "rabbit"), Map.entry("ink_sac", "squid"), Map.entry("magma_cream", "magma_cube"),
        Map.entry("ghast_tear", "ghast"), Map.entry("prismarine_shard", "guardian"), Map.entry("cod", "cod"), Map.entry("salmon", "salmon"),
        Map.entry("flint", "#gravel"), Map.entry("cobblestone", "#stone"), Map.entry("stick", "#log"), Map.entry("apple", "#leaves"),
        Map.entry("sapling", "#leaves"), Map.entry("wheat_seeds", "#grass"), Map.entry("clay_ball", "#clay"), Map.entry("snowball", "#snow"),
        Map.entry("glowstone_dust", "#glowstone"), Map.entry("raw_iron", "#iron"), Map.entry("raw_gold", "#gold"), Map.entry("raw_copper", "#copper"),
        Map.entry("iron_ingot", "#iron"), Map.entry("gold_ingot", "#gold"), Map.entry("copper_ingot", "#copper"), Map.entry("quartz", "#nether_quartz_ore"),
        Map.entry("lapis_lazuli", "#lapis"), Map.entry("amethyst_shard", "#amethyst_cluster"), Map.entry("wheat", "#wheat"), Map.entry("carrot", "#carrots"),
        Map.entry("potato", "#potatoes"), Map.entry("beetroot", "#beetroots"), Map.entry("sugar_cane", "#sugar_cane"), Map.entry("cactus", "#cactus"),
        Map.entry("diamond", "#diamond"), Map.entry("coal", "#coal"), Map.entry("redstone", "#redstone"), Map.entry("emerald", "#emerald"),
        Map.entry("gravel", "#gravel"), Map.entry("sand", "#sand")
    );
    private static final Set<String> ORES = Set.of("iron", "gold", "copper", "lapis", "diamond", "coal", "redstone", "emerald");

    /** The blocks a source of that name stands for. */
    private static Predicate<BlockState> tier(String block) {
        return switch (block) {
            case "iron", "gold", "copper", "lapis", "diamond", "coal", "redstone", "emerald", "log" -> state -> HostManager.isStuff(state, block);
            case "leaves" -> state -> state.isIn(net.minecraft.registry.tag.BlockTags.LEAVES);
            case "grass" -> state -> Registries.BLOCK.getId(state.getBlock()).getPath().endsWith("grass") && !state.isOf(Blocks.GRASS_BLOCK);
            case "snow" -> state -> state.isOf(Blocks.SNOW) || state.isOf(Blocks.SNOW_BLOCK);
            case "wheat", "carrots", "potatoes", "beetroots" -> state -> Registries.BLOCK.getId(state.getBlock()).getPath().equals(block) && ripe(state);
            default -> state -> Registries.BLOCK.getId(state.getBlock()).getPath().equals(block);
        };
    }

    /** A thing that is no block: the item by that name, and where such items come from. False when that leads nowhere either. */
    private static boolean fromItem(ServerPlayerEntity host, PilotSpeech.Order order, List<Identifier> exact, List<Identifier> partial, boolean held) {
        List<Identifier> ids = new ArrayList<>(exact);
        ids.addAll(partial);

        for (Identifier id : ids) {
            String source = SOURCES.get(id.getPath());
            if (source == null) {
                continue;
            }

            if (!source.startsWith("#")) {
                go(host, new Hunt(host, ofKind(source), order.number() > 0 ? order.number() : 3, false, 900), held);
                return true;
            }

            String block = source.substring(1);
            Predicate<BlockState> tier = tier(block);
            boolean ore = ORES.contains(block) || block.endsWith("_ore");
            go(host, new Mine(host, List.of(tier), order.number() > 0 ? Math.min(order.number(), 128) : (ore ? 8 : 16), true, block.equals("log") ? L_CHOP : L_MINE), held);
            return true;
        }

        return false;
    }

    private static void mineNamed(ServerPlayerEntity host, PilotSpeech.Order order, String thing, List<Identifier> exact, List<Identifier> partial, boolean held, boolean complain) {
        List<Predicate<BlockState>> tiers = HostManager.wantedBlocks(VoltaChat.canonical(thing), exact, partial);
        if (tiers.isEmpty()) {
            // No block of that name. An item, then: leather comes off cows, flint out of gravel.
            HostManager.askNames(host, 0, thing, (items, itemsInPart) -> {
                if (!fromItem(host, order, items, items.isEmpty() ? itemsInPart : List.of(), held) && complain) {
                    HostManager.say(host, "pilot_unknown", 2, thing);
                }
            });
            return;
        }

        // Things with something inside them, and water, are walked up to and left as they are.
        Predicate<BlockState> first = tiers.get(0);
        for (net.minecraft.block.Block block : Registries.BLOCK) {
            BlockState sample = block.getDefaultState();
            if (first.test(sample)) {
                if (sample.hasBlockEntity() || !sample.getFluidState().isEmpty()) {
                    Quest quest = new Quest();
                    quest.blocks = first;
                    quest.name = block.getName();
                    go(host, new Explore(host, quest, 600), held);
                    return;
                }

                if (block instanceof net.minecraft.block.CropBlock) {
                    tiers = List.of(state -> first.test(state) && ripe(state));
                }

                break;
            }
        }

        boolean logs = false;
        boolean ore = false;

        for (net.minecraft.block.Block block : Registries.BLOCK) {
            if (first.test(block.getDefaultState())) {
                String path = Registries.BLOCK.getId(block).getPath();
                logs = path.endsWith("_log") || path.endsWith("_stem");
                ore = path.endsWith("_ore") || path.equals("ancient_debris");
                break;
            }
        }

        int want = order.number() > 0 ? Math.min(order.number(), 128) : (ore ? 8 : 16);
        go(host, new Mine(host, tiers, want, true, logs ? L_CHOP : L_MINE), held);
    }

    /** "Go to...", "go and find...": a place by its numbers, a direction, a player, or a thing to be looked for on foot. */
    private static void seek(ServerPlayerEntity host, PilotSpeech.Order order, ServerPlayerEntity named, String alias, boolean held) {
        ServerWorld world = host.getWorld();
        boolean go = order.kind().equals("goto");
        if (order.coords() != null) {
            double[] c = order.coords();
            Vec3d to = Double.isNaN(c[1]) ? surface(world, c[0] + 0.5, c[2] + 0.5, host.getY()) : new Vec3d(c[0] + 0.5, c[1], c[2] + 0.5);
            boolean high = Double.isNaN(c[1]);
            Goto task = new Goto(() -> high ? surface(world, to.x, to.z, host.getY()) : to, 2.0, L_WALK, false);
            task.flat = high;
            go(host, task, held);
            return;
        }

        if (named != null) {
            go(host, new Goto(() -> named.isAlive() && named.getWorld() == host.getWorld() ? named.getPos() : null, 3.0, L_WALK, false), held);
            return;
        }

        if (order.what().contains("storage")) {
            // "Go to the storeroom": where the chests stand thickest.
            BlockPos room = storeroom(host);
            if (room == null) {
                HostManager.say(host, "pilot_no_storage", 2);
            } else {
                Vec3d at = Vec3d.ofBottomCenter(room);
                go(host, new Goto(() -> at, 2.5, L_WALK, false), held);
            }

            return;
        }

        String subject = order.subject();
        String[] structure = place(subject, STRUCTURES);
        String[] biome = structure == null ? place(subject, BIOMES) : null;
        if (structure == null && biome == null) {
            // Not one she has an everyday word for: any structure of the game or a mod, by the name it goes under.
            structure = anyStructure(world, subject);
        }

        if (structure != null || biome != null) {
            Quest quest = new Quest();
            quest.structures = structure;
            quest.biomes = biome;
            quest.name = Text.literal(subject);
            Explore task = new Explore(host, quest, 900);
            if (!Float.isNaN(order.bearing()) && order.bearing() < 999.0F) {
                task.heading = order.bearing();
            }

            go(host, task, held);
            return;
        }

        if (!Float.isNaN(order.bearing()) && VoltaChat.thing(subject).isBlank()) {
            // "Go north 100", "иди вперёд 50".
            float yaw = order.bearing() > 999.0F ? host.getYaw() : order.bearing();
            double rad = Math.toRadians((double)yaw);
            int far = order.number() > 0 ? Math.min(order.number(), 2000) : 100;
            double x = host.getX() - Math.sin(rad) * (double)far;
            double z = host.getZ() + Math.cos(rad) * (double)far;
            Goto task = new Goto(() -> surface(world, x, z, host.getY()), 2.5, L_WALK, false);
            task.flat = true;
            go(host, task, held);
            return;
        }

        if (go && order.pointed() && VoltaChat.thing(subject).isBlank()) {
            HitResult hit = pointed(host);
            if (hit.getType() == HitResult.Type.MISS) {
                HostManager.say(host, "pilot_where", 2);
            } else {
                Vec3d to = hit.getPos();
                go(host, new Goto(() -> to, 2.0, L_WALK, false), held);
            }

            return;
        }

        if (order.what().equals("food") || order.what().equals("animals")) {
            go(host, new Hunt(host, meat(), order.number() > 0 ? order.number() : 3, false, 900), held);
            return;
        }

        if (order.what().equals("resources")) {
            resources(host, held);
            return;
        }

        String thing = VoltaChat.thing(subject);
        if (thing.isBlank()) {
            if (go) {
                HostManager.say(host, "pilot_where", 2);
            } else {
                Explore task = new Explore(host, null, order.number() > 0 ? Math.min(order.number(), 2000) : 300);
                if (!Float.isNaN(order.bearing()) && order.bearing() < 999.0F) {
                    task.heading = order.bearing();
                }

                go(host, task, held);
            }

            return;
        }

        if (alias != null && MOBS.contains(alias) || order.what().equals("hostile")) {
            Quest quest = new Quest();
            quest.creature = order.what().equals("hostile") ? hostile(host) : ofKind(alias);
            quest.name = Text.literal(thing);
            go(host, new Explore(host, quest, 600), held);
            return;
        }

        // A creature by the name the Host's own game has for it, and failing that a block to be dug for.
        HostManager.askNames(host, 2, thing, (creatures, creaturesInPart) -> {
            if (!creatures.isEmpty()) {
                Set<Identifier> ids = new HashSet<>(creatures);
                Quest quest = new Quest();
                quest.creature = e -> ids.contains(Registries.ENTITY_TYPE.getId(e.getType()));
                quest.name = Text.literal(thing);
                go(host, new Explore(host, quest, 600), held);
                return;
            }

            HostManager.askNames(host, 1, thing, (exact, partial) -> {
                if (HostManager.wantedBlocks(VoltaChat.canonical(thing), exact, partial).isEmpty() && !creaturesInPart.isEmpty()) {
                    Set<Identifier> ids = new HashSet<>(creaturesInPart);
                    Quest quest = new Quest();
                    quest.creature = e -> ids.contains(Registries.ENTITY_TYPE.getId(e.getType()));
                    quest.name = Text.literal(thing);
                    go(host, new Explore(host, quest, 600), held);
                } else {
                    mineNamed(host, order, thing, exact, partial, held, true);
                }
            });
        });
    }

    /** A crop that has finished growing: the only kind worth cutting. */
    private static boolean ripe(BlockState state) {
        if (state.getBlock() instanceof net.minecraft.block.CropBlock crop) {
            return crop.isMature(state);
        }

        return state.isOf(Blocks.NETHER_WART) && state.get(net.minecraft.block.NetherWartBlock.AGE) >= 3
            || state.isOf(Blocks.COCOA) && state.get(net.minecraft.block.CocoaBlock.AGE) >= 2
            || state.isOf(Blocks.MELON)
            || state.isOf(Blocks.PUMPKIN)
            || stalk(state);
    }

    /** What grows as a stalk and grows back if the foot of it is left standing. */
    private static boolean stalk(BlockState state) {
        return state.isOf(Blocks.SUGAR_CANE) || state.isOf(Blocks.CACTUS) || state.isOf(Blocks.BAMBOO) || state.isOf(Blocks.KELP_PLANT);
    }

    /** The nearest bed: one close by, or the Host's own if it is not far. */
    private static BlockPos bedNear(ServerPlayerEntity host) {
        World world = host.getWorld();
        BlockPos at = host.getBlockPos();
        BlockPos best = null;

        for (BlockPos pos : BlockPos.iterate(at.add(-20, -5, -20), at.add(20, 5, 20))) {
            if (world.getBlockState(pos).getBlock() instanceof net.minecraft.block.BedBlock && (best == null || pos.getSquaredDistance(at) < best.getSquaredDistance(at))) {
                best = pos.toImmutable();
            }
        }

        BlockPos own = host.getSpawnPointPosition();
        if (best == null && own != null && host.getSpawnPointDimension() == world.getRegistryKey() && own.isWithinDistance(at, 128.0) && world.getBlockState(own).getBlock() instanceof net.minecraft.block.BedBlock) {
            best = own;
        }

        return best;
    }

    /** To bed: she walks the body there and lies it down. */
    static final class Sleep implements Task {
        private final BlockPos bed;
        private int ticks;

        Sleep(BlockPos bed) {
            this.bed = bed;
        }

        @Override
        public int label() {
            return L_SLEEP;
        }

        @Override
        public boolean tick(Run run, ServerPlayerEntity host) {
            if (host.isSleeping()) {
                run.end("pilot_sleep");
                return false;
            }

            if (++this.ticks > 1200 || !(host.getWorld().getBlockState(this.bed).getBlock() instanceof net.minecraft.block.BedBlock)) {
                run.end("pilot_lost");
                return false;
            }

            if (host.getEyePos().squaredDistanceTo(Vec3d.ofCenter(this.bed)) > 9.0) {
                walk(run, host, Vec3d.ofBottomCenter(this.bed), 1.5, NATURAL, false);
            } else if (this.ticks % 20 == 0) {
                run.usePos = this.bed;
                run.look = Vec3d.ofCenter(this.bed);
            }

            return true;
        }
    }

    private static boolean carries(ServerPlayerEntity host, Predicate<ItemStack> what) {
        for (ItemStack stack : host.getInventory().main) {
            if (!stack.isEmpty() && what.test(stack)) {
                return true;
            }
        }

        return false;
    }

    /** "Get resources": iron if there is a pickaxe to get it with, wood if there is not. */
    private static void resources(ServerPlayerEntity host, boolean held) {
        if (carries(host, stack -> stack.getItem() instanceof PickaxeItem)) {
            go(host, new Mine(host, List.of(state -> HostManager.isStuff(state, "iron"), state -> HostManager.isStuff(state, "coal")), 8, true, L_MINE), held);
        } else {
            go(host, new Mine(host, List.of(state -> HostManager.isStuff(state, "log")), 8, true, L_CHOP), held);
        }
    }

    /** "Do something": she looks at how things stand and picks the job herself. */
    private static void auto(ServerPlayerEntity host, boolean held) {
        boolean danger = !host.getWorld().getEntitiesByClass(LivingEntity.class, host.getBoundingBox().expand(14.0), e -> e instanceof Monster && e.isAlive() && host.canSee(e)).isEmpty();
        int food = host.getHungerManager().getFoodLevel();
        if (danger) {
            go(host, new Hunt(host, hostile(host), 0, false, 100), held);
        } else if (food <= 14 && carries(host, stack -> Eat.foodWorth(stack) > 0.0)) {
            go(host, new Eat(), held);
        } else if (food <= 10) {
            go(host, new Hunt(host, meat(), 2, false, 900), held);
        } else if (host.getWorld().isNight() && host.getWorld().isSkyVisible(host.getBlockPos())) {
            go(host, new Hunt(host, hostile(host), 0, true, 0), held);
        } else {
            resources(host, held);
        }
    }

    // ------------------------------------------------------------------ building, crafting, smelting

    /** A free cell beside the body with a floor under it: where a table or a furnace is set down. */
    private static BlockPos freeSpot(ServerPlayerEntity host) {
        World world = host.getWorld();
        BlockPos feet = host.getBlockPos();
        Direction dir = host.getHorizontalFacing();

        for (int i = 0; i < 4; i++) {
            BlockPos cell = feet.offset(dir);
            BlockState there = world.getBlockState(cell);
            if ((there.isAir() || there.isReplaceable()) && there.getFluidState().isEmpty() && solid(world, cell.down())) {
                return cell;
            }

            dir = dir.rotateYClockwise();
        }

        return null;
    }

    /** A walkway laid straight ahead over whatever is missing underfoot. */
    static final class Bridge implements Task {
        private final Direction dir;
        private final int want;
        private BlockPos laying;
        private int laid;
        private int ticks;
        private int steps;
        private BlockPos lastCell;

        Bridge(ServerPlayerEntity host, int want) {
            this.dir = host.getHorizontalFacing();
            this.want = want;
            this.lastCell = host.getBlockPos();
        }

        @Override
        public int label() {
            return L_BUILD;
        }

        @Override
        public boolean tick(Run run, ServerPlayerEntity host) {
            World world = host.getWorld();
            BlockPos feet = host.getBlockPos();
            if (!feet.equals(this.lastCell)) {
                this.lastCell = feet;
                this.steps++;
            }

            if (this.laying != null && solid(world, this.laying)) {
                this.laid++;
                this.laying = null;
            }

            BlockPos next = feet.offset(this.dir);
            BlockPos floor = next.down();
            boolean landed = this.laid > 0 && solid(world, floor) && solid(world, floor.offset(this.dir)) && solid(world, floor.offset(this.dir, 2));
            if (++this.ticks > 2400 || (this.want > 0 ? this.laid >= this.want : landed || this.steps > 40)) {
                run.end(this.laid > 0 ? "pilot_built" : "pilot_none", this.laid);
                return false;
            }

            for (BlockPos pos : new BlockPos[]{next.up(), next}) {
                if (!passable(world, pos)) {
                    if (!breakable(world, pos) || !natural(world.getBlockState(pos))) {
                        run.end(this.laid > 0 ? "pilot_built" : "pilot_lost", this.laid);
                        return false;
                    }

                    strike(run, host, pos);
                    return true;
                }
            }

            if (solid(world, floor)) {
                run.way.add(new Vec3d((double)next.getX() + 0.5, (double)next.getY(), (double)next.getZ() + 0.5));
                if (!solid(world, floor.offset(this.dir))) {
                    run.flags |= SNEAK;
                }

                return true;
            }

            int state = put(run, host, floor, (net.minecraft.item.Item)null);
            if (state < 0) {
                run.end("pilot_no_blocks");
                return false;
            }

            this.laying = floor;
            run.flags |= SNEAK;
            return true;
        }
    }

    /** Straight up on a column of her own making. */
    static final class Climb implements Task {
        private final int from;
        private final int want;
        private int ticks;

        Climb(ServerPlayerEntity host, int want) {
            this.from = host.getBlockY();
            this.want = want;
        }

        @Override
        public int label() {
            return L_BUILD;
        }

        @Override
        public boolean tick(Run run, ServerPlayerEntity host) {
            int risen = host.getBlockY() - this.from;
            if (risen >= this.want && host.isOnGround() || ++this.ticks > 60 * this.want + 200) {
                run.end("pilot_built", risen);
                return false;
            }

            if (!pillar(run, host)) {
                run.end(risen > 0 ? "pilot_built" : "pilot_no_blocks", risen);
                return false;
            }

            return true;
        }
    }

    /** One thing set down in one cell: a torch at the feet. */
    static final class PutOne implements Task {
        private final BlockPos cell;
        private final net.minecraft.item.Item item;
        private final String without;
        private int ticks;

        PutOne(BlockPos cell, net.minecraft.item.Item item, String without) {
            this.cell = cell;
            this.item = item;
            this.without = without;
        }

        @Override
        public int label() {
            return L_BUILD;
        }

        @Override
        public boolean tick(Run run, ServerPlayerEntity host) {
            int state = put(run, host, this.cell, this.item);
            if (state != 0 || ++this.ticks > 100) {
                run.end(state == 1 ? "pilot_done" : (state < 0 ? this.without : "pilot_lost"));
                return false;
            }

            return true;
        }
    }

    /** Walls and a lid around where the body stands: somewhere to sit out a night. */
    static final class Shelter implements Task {
        private final List<BlockPos> cells = new ArrayList<>();
        private int ticks;

        Shelter(ServerPlayerEntity host) {
            BlockPos feet = host.getBlockPos();

            for (Direction side : new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
                this.cells.add(feet.offset(side));
                this.cells.add(feet.offset(side).up());
            }

            this.cells.add(feet.north().up(2));
            this.cells.add(feet.up(2));
        }

        @Override
        public int label() {
            return L_BUILD;
        }

        @Override
        public boolean tick(Run run, ServerPlayerEntity host) {
            World world = host.getWorld();
            if (++this.ticks > 900) {
                run.end("pilot_lost");
                return false;
            }

            for (BlockPos cell : this.cells) {
                if (solid(world, cell)) {
                    continue;
                }

                if (put(run, host, cell, (net.minecraft.item.Item)null) < 0) {
                    run.end("pilot_no_blocks");
                    return false;
                }

                // From the middle of the cell, or a wall goes up through a shoulder.
                BlockPos feet = host.getBlockPos();
                run.way.clear();
                double offX = (double)feet.getX() + 0.5 - host.getX();
                double offZ = (double)feet.getZ() + 0.5 - host.getZ();
                if (offX * offX + offZ * offZ > 0.03) {
                    run.placeAgainst = null;
                    run.way.add(new Vec3d((double)feet.getX() + 0.5, host.getY(), (double)feet.getZ() + 0.5));
                }

                return true;
            }

            run.end("pilot_done");
            return false;
        }
    }

    /** The screen she opened on a table, an oven or a chest is shut again. */
    static void shut(Run run, ServerPlayerEntity host) {
        if (run.opened) {
            run.opened = false;
            if (host.currentScreenHandler != host.playerScreenHandler) {
                host.closeHandledScreen();
            }
        }
    }

    /** Carries out a worked-out list of recipes: at a table or a stonecutter for those that need one, a moment for each. */
    static final class Craft implements Task {
        private final List<Recipes.Step> steps;
        private final Text name;
        private final int count;
        private BlockPos bench;
        private boolean open;
        private int at;
        private int wait;
        private int ticks;

        Craft(List<Recipes.Step> steps, Text name) {
            this.steps = steps;
            this.name = name;
            Recipes.Step last = steps.get(steps.size() - 1);
            this.count = last.makes().getCount() * last.times();
        }

        @Override
        public int label() {
            return L_CRAFT;
        }

        @Override
        public boolean tick(Run run, ServerPlayerEntity host) {
            World world = host.getWorld();
            if (this.at >= this.steps.size()) {
                shut(run, host);
                run.end("pilot_crafted", this.count, this.name);
                return false;
            }

            if (++this.ticks > 1800) {
                shut(run, host);
                run.end("pilot_lost");
                return false;
            }

            Recipes.Step step = this.steps.get(this.at);
            if (step.station() != Recipes.HAND) {
                net.minecraft.block.Block block = step.station() == Recipes.CUTTER ? Blocks.STONECUTTER : Blocks.CRAFTING_TABLE;
                if (this.bench == null || !world.getBlockState(this.bench).isOf(block)) {
                    shut(run, host);
                    this.open = false;
                    this.bench = spotted(host, state -> state.isOf(block), 16);
                }

                if (this.bench == null) {
                    BlockPos spot = carries(host, stack -> stack.isOf(block.asItem())) ? freeSpot(host) : null;
                    if (spot == null || put(run, host, spot, block.asItem()) < 0) {
                        run.end("pilot_no_table");
                        return false;
                    }

                    return true;
                }

                if (host.getEyePos().squaredDistanceTo(Vec3d.ofCenter(this.bench)) > 3.5 * 3.5) {
                    walk(run, host, Vec3d.ofBottomCenter(this.bench), 2.0, NATURAL, false);
                    return true;
                }

                run.look = Vec3d.ofCenter(this.bench);
                if (!this.open) {
                    // Hands on it, as anyone would: the Host watches their own screen open.
                    this.open = true;
                    run.opened = true;
                    run.usePos = this.bench;
                    this.wait = 0;
                    return true;
                }
            } else {
                if (this.open) {
                    shut(run, host);
                    this.open = false;
                }

                // Small things are put together in the pockets.
                run.flags |= BAG;
            }

            if (++this.wait < 16) {
                return true;
            }

            this.wait = 0;
            host.swingHand(net.minecraft.util.Hand.MAIN_HAND, true);
            if (!Recipes.apply(host, step)) {
                shut(run, host);
                run.end("pilot_lost");
                return false;
            }

            PhaseFx.sound(host, step.station() == Recipes.CUTTER ? net.minecraft.sound.SoundEvents.UI_STONECUTTER_TAKE_RESULT : net.minecraft.sound.SoundEvents.ENTITY_ITEM_PICKUP, net.minecraft.sound.SoundCategory.PLAYERS, 0.4F, 1.4F);
            this.at++;
            return true;
        }
    }

    static boolean smeltable(World world, ItemStack stack) {
        return !Recipes.cooked(world, stack, net.minecraft.recipe.RecipeType.SMELTING).isEmpty();
    }

    private static boolean oven(BlockState state) {
        return state.isOf(Blocks.FURNACE) || state.isOf(Blocks.BLAST_FURNACE) || state.isOf(Blocks.SMOKER);
    }

    /**
     * Loads an oven from the pockets, waits by it and takes what comes out. Ore goes into a blast furnace and
     * food into a smoker when one stands near: she looks round for one first, not for long, and settles for a
     * plain furnace.
     */
    static final class Smelt implements Task {
        private final Predicate<ItemStack> what;
        private final int want;
        private BlockPos oven;
        private boolean looked;
        private boolean open;
        private boolean loaded;
        private int put;
        private int taken;
        private int wait;
        private int ticks;
        private Text name = Text.literal("?");

        Smelt(Predicate<ItemStack> what, int want) {
            this.what = what;
            this.want = want;
        }

        @Override
        public int label() {
            return L_SMELT;
        }

        @Override
        public boolean resting() {
            return this.loaded;
        }

        /** The oven made for what she carries: 1 a blast furnace, 2 a smoker, 0 neither. */
        private int special(ServerPlayerEntity host) {
            World world = host.getWorld();
            boolean blast = true;
            boolean smoke = true;
            boolean any = false;

            for (ItemStack stack : host.getInventory().main) {
                if (!stack.isEmpty() && this.what.test(stack)) {
                    any = true;
                    blast &= !Recipes.cooked(world, stack, net.minecraft.recipe.RecipeType.BLASTING).isEmpty();
                    smoke &= !Recipes.cooked(world, stack, net.minecraft.recipe.RecipeType.SMOKING).isEmpty();
                }
            }

            return !any ? 0 : (blast ? 1 : (smoke ? 2 : 0));
        }

        @Override
        public boolean tick(Run run, ServerPlayerEntity host) {
            World world = host.getWorld();
            if (++this.ticks > 220 * this.want + 1200) {
                shut(run, host);
                run.end(this.taken > 0 ? "pilot_smelted" : "pilot_lost", this.taken, this.name);
                return false;
            }

            if (this.oven == null || !(world.getBlockEntity(this.oven) instanceof net.minecraft.block.entity.AbstractFurnaceBlockEntity)) {
                int special = this.special(host);
                net.minecraft.block.Block best = special == 1 ? Blocks.BLAST_FURNACE : (special == 2 ? Blocks.SMOKER : Blocks.FURNACE);
                this.oven = spotted(host, state -> state.isOf(best), 16);
                if (this.oven == null && special != 0 && !this.looked) {
                    // A look round for the better oven: a turn of the head, a second and a half of it.
                    if (this.ticks < 30) {
                        double turn = Math.toRadians((double)host.getYaw() + 50.0);
                        run.look = host.getEyePos().add(-Math.sin(turn) * 6.0, -0.5, Math.cos(turn) * 6.0);
                        return true;
                    }

                    this.looked = true;
                }

                if (this.oven == null) {
                    this.oven = spotted(host, state -> state.isOf(Blocks.FURNACE), 16);
                }

                if (this.oven == null) {
                    net.minecraft.item.Item carried = carries(host, stack -> stack.isOf(best.asItem())) ? best.asItem() : Items.FURNACE;
                    BlockPos spot = carries(host, stack -> stack.isOf(carried)) ? freeSpot(host) : null;
                    if (spot == null || put(run, host, spot, carried) < 0) {
                        run.end("pilot_no_furnace");
                        return false;
                    }

                    return true;
                }
            }

            if (host.getEyePos().squaredDistanceTo(Vec3d.ofCenter(this.oven)) > 3.5 * 3.5) {
                walk(run, host, Vec3d.ofBottomCenter(this.oven), 2.0, NATURAL, false);
                return true;
            }

            run.look = Vec3d.ofCenter(this.oven);
            net.minecraft.block.entity.AbstractFurnaceBlockEntity furnace = (net.minecraft.block.entity.AbstractFurnaceBlockEntity)world.getBlockEntity(this.oven);
            boolean lit = world.getBlockState(this.oven).get(net.minecraft.block.AbstractFurnaceBlock.LIT);
            List<ItemStack> main = host.getInventory().main;
            if (!this.loaded) {
                if (!this.open) {
                    this.open = true;
                    run.opened = true;
                    run.usePos = this.oven;
                    this.wait = 0;
                    return true;
                }

                if (++this.wait < 14) {
                    return true;
                }

                // What a job that was broken off left in there comes out first.
                ItemStack done = furnace.getStack(2);
                if (!done.isEmpty()) {
                    this.name = done.getName();
                    this.taken += done.getCount();
                    host.getInventory().offerOrDrop(done.copy());
                    furnace.setStack(2, ItemStack.EMPTY);
                }

                Map<net.minecraft.item.Item, Integer> fuels = net.minecraft.block.entity.AbstractFurnaceBlockEntity.createFuelTimeMap();
                ItemStack fuel = furnace.getStack(1);
                int burn = fuel.isEmpty() ? 0 : fuels.getOrDefault(fuel.getItem(), 0) * fuel.getCount();
                boolean spare = false;

                for (ItemStack stack : main) {
                    spare |= !stack.isEmpty() && fuels.containsKey(stack.getItem()) && !stack.isDamageable() && !this.what.test(stack);
                }

                ItemStack in = furnace.getStack(0);
                boolean cooking = !in.isEmpty();
                if (burn == 0 && !lit && !spare) {
                    shut(run, host);
                    run.end(this.taken > 0 ? "pilot_smelted" : "pilot_no_fuel", this.taken, this.name);
                    return false;
                }

                for (ItemStack stack : main) {
                    if (this.put >= this.want || stack.isEmpty() || !this.what.test(stack) || !in.isEmpty() && !ItemStack.canCombine(in, stack)) {
                        continue;
                    }

                    int move = Math.min(Math.min(this.want - this.put, stack.getCount()), stack.getMaxCount() - in.getCount());
                    if (move <= 0) {
                        continue;
                    }

                    if (in.isEmpty()) {
                        in = stack.split(move);
                    } else {
                        in.increment(move);
                        stack.decrement(move);
                    }

                    this.put += move;
                }

                if (this.put == 0 && !cooking) {
                    shut(run, host);
                    run.end(this.taken > 0 ? "pilot_smelted" : "pilot_none", this.taken, this.name);
                    return false;
                }

                furnace.setStack(0, in);
                int need = in.getCount() * 200;

                for (int pass = 0; pass < 2 && burn < need; pass++) {
                    for (ItemStack stack : main) {
                        boolean coal = stack.isOf(Items.COAL) || stack.isOf(Items.CHARCOAL);
                        if (stack.isEmpty() || !fuels.containsKey(stack.getItem()) || stack.isDamageable() || this.what.test(stack) || pass == 0 && !coal || !fuel.isEmpty() && !ItemStack.canCombine(fuel, stack)) {
                            continue;
                        }

                        while (burn < need && !stack.isEmpty() && fuel.getCount() < stack.getMaxCount()) {
                            if (fuel.isEmpty()) {
                                fuel = stack.split(1);
                            } else {
                                fuel.increment(1);
                                stack.decrement(1);
                            }

                            burn += fuels.get(fuel.getItem());
                        }
                    }
                }

                furnace.setStack(1, fuel);
                furnace.markDirty();
                host.getInventory().markDirty();
                host.currentScreenHandler.sendContentUpdates();
                host.playerScreenHandler.sendContentUpdates();
                host.swingHand(net.minecraft.util.Hand.MAIN_HAND, true);
                this.put = in.getCount();
                this.loaded = true;
                this.wait = 0;
                return true;
            }

            this.wait++;
            if (this.wait == 24) {
                shut(run, host);
            }

            if (this.wait % 20 != 0) {
                return true;
            }

            ItemStack out = furnace.getStack(2);
            if (!out.isEmpty()) {
                this.name = out.getName();
                this.taken += out.getCount();
                host.getInventory().offerOrDrop(out.copy());
                furnace.setStack(2, ItemStack.EMPTY);
                furnace.markDirty();
                host.swingHand(net.minecraft.util.Hand.MAIN_HAND, true);
            }

            if (furnace.getStack(0).isEmpty() && furnace.getStack(2).isEmpty()) {
                shut(run, host);
                run.end(this.taken > 0 ? "pilot_smelted" : "pilot_no_fuel", this.taken, this.name);
                return false;
            }

            if (!lit && this.wait > 120 && this.taken == 0) {
                shut(run, host);
                run.end("pilot_no_fuel");
                return false;
            }

            return true;
        }
    }

    // ------------------------------------------------------------------ getting what a recipe is short of

    /** A plan for one of the things that would do, or what the nearest plan lacks. */
    private static final class Plan {
        net.minecraft.item.Item item;
        List<Recipes.Step> steps;
        Map<net.minecraft.item.Item, Integer> after;
        Recipes.Lack lack;
        boolean known;
    }

    private static Plan plan(ServerPlayerEntity host, List<net.minecraft.item.Item> items, int count, Map<net.minecraft.item.Item, Integer> base) {
        World world = host.getWorld();
        boolean cutter = carries(host, stack -> stack.isOf(Items.STONECUTTER)) || spotted(host, state -> state.isOf(Blocks.STONECUTTER), 16) != null;
        boolean tableNear = spotted(host, state -> state.isOf(Blocks.CRAFTING_TABLE), 16) != null;
        Recipes.Book book = Recipes.book(world, cutter);
        Plan best = new Plan();
        int bestScore = Integer.MAX_VALUE;

        for (net.minecraft.item.Item item : items) {
            if (!book.knows(item)) {
                continue;
            }

            best.known = true;
            Map<net.minecraft.item.Item, Integer> have = new HashMap<>(base);
            List<Recipes.Step> steps = new ArrayList<>();
            Recipes.Lack lack = new Recipes.Lack();
            boolean ok = Recipes.make(book, item, count, have, steps, lack);
            if (ok && !tableNear && have.getOrDefault(Items.CRAFTING_TABLE, 0) <= 0 && steps.stream().anyMatch(step -> step.station() == Recipes.TABLE)) {
                // It takes a table and there is none: the table first, out of the same pockets.
                have = new HashMap<>(base);
                steps = new ArrayList<>();
                lack = new Recipes.Lack();
                ok = Recipes.make(book, Items.CRAFTING_TABLE, 1, have, steps, lack) && Recipes.make(book, item, count, have, steps, lack);
                if (!ok && lack.items.isEmpty()) {
                    lack.items.put(Items.CRAFTING_TABLE, 1);
                    lack.wants.add(net.minecraft.recipe.Ingredient.ofItems(Items.CRAFTING_TABLE));
                }
            }

            if (ok) {
                best.item = item;
                best.steps = steps;
                best.after = have;
                best.lack = null;
                return best;
            }

            String path = Registries.ITEM.getId(item).getPath();
            int score = lack.sum() * 10 + (path.startsWith("wooden_") ? 0 : (path.startsWith("stone_") ? 1 : 5));
            if (!lack.items.isEmpty() && score < bestScore) {
                bestScore = score;
                best.item = item;
                best.lack = lack;
            }
        }

        return best;
    }

    private static boolean storage(net.minecraft.block.entity.BlockEntity entity) {
        return entity instanceof net.minecraft.block.entity.ChestBlockEntity || entity instanceof net.minecraft.block.entity.BarrelBlockEntity || entity instanceof net.minecraft.block.entity.ShulkerBoxBlockEntity;
    }

    /** The nearest chest, barrel or shulker box about the place that she has not looked into yet. */
    private static BlockPos storageNear(ServerPlayerEntity host, Set<BlockPos> skip) {
        return storageNear(host, skip, 28);
    }

    private static BlockPos storageNear(ServerPlayerEntity host, Set<BlockPos> skip, int far) {
        ServerWorld world = host.getWorld();
        BlockPos feet = host.getBlockPos();
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;

        for (int cx = (feet.getX() - far >> 4); cx <= (feet.getX() + far >> 4); cx++) {
            for (int cz = (feet.getZ() - far >> 4); cz <= (feet.getZ() + far >> 4); cz++) {
                if (!world.isChunkLoaded(cx, cz)) {
                    continue;
                }

                for (Map.Entry<BlockPos, net.minecraft.block.entity.BlockEntity> entry : world.getChunk(cx, cz).getBlockEntities().entrySet()) {
                    BlockPos pos = entry.getKey();
                    int dy = Math.abs(pos.getY() - feet.getY());
                    double flat = Math.sqrt((double)((pos.getX() - feet.getX()) * (pos.getX() - feet.getX()) + (pos.getZ() - feet.getZ()) * (pos.getZ() - feet.getZ())));
                    if (!storage(entry.getValue()) || skip.contains(pos) || dy > 12 || flat > (double)far) {
                        continue;
                    }

                    double score = flat + (double)dy * 3.0;
                    if (score < bestScore) {
                        bestScore = score;
                        best = pos.toImmutable();
                    }
                }
            }
        }

        return best;
    }

    /** Where a thing a recipe is short of can be got out in the world: a job that gets it, or null. */
    private static Task gather(ServerPlayerEntity host, net.minecraft.item.Item item, int n) {
        String path = Registries.ITEM.getId(item).getPath();
        ItemStack sample = new ItemStack(item);
        boolean pick = carries(host, stack -> stack.getItem() instanceof PickaxeItem);
        if (sample.isIn(net.minecraft.registry.tag.ItemTags.PLANKS) || sample.isIn(net.minecraft.registry.tag.ItemTags.LOGS) || path.equals("stick") || path.equals("crafting_table")) {
            // A tree while she is at it, not one log: the table and the handles come out of the same wood.
            int logs = sample.isIn(net.minecraft.registry.tag.ItemTags.LOGS) ? n : Math.max(3, (n + 3) / 4 + 1);
            return new Mine(host, List.of(state -> HostManager.isStuff(state, "log")), logs, true, L_CHOP);
        }

        if (sample.isIn(net.minecraft.registry.tag.ItemTags.STONE_TOOL_MATERIALS) || sample.isIn(net.minecraft.registry.tag.ItemTags.STONE_CRAFTING_MATERIALS)) {
            return pick ? new Mine(host, List.of(state -> state.isOf(Blocks.STONE) || state.isOf(Blocks.COBBLESTONE) || state.isOf(Blocks.DEEPSLATE) || state.isOf(Blocks.COBBLED_DEEPSLATE) || state.isOf(Blocks.BLACKSTONE)), n, true, L_MINE) : null;
        }

        String source = SOURCES.get(path);
        if (source == null && item instanceof net.minecraft.item.BlockItem block) {
            BlockState state = block.getBlock().getDefaultState();
            if (state.hasBlockEntity() || state.isToolRequired() && !pick) {
                return null;
            }

            net.minecraft.block.Block wanted = block.getBlock();
            return new Mine(host, List.of(other -> other.isOf(wanted)), n, true, L_MINE);
        }

        if (source == null) {
            return null;
        }

        if (!source.startsWith("#")) {
            return new Hunt(host, ofKind(source), n, false, 900);
        }

        String block = source.substring(1);
        boolean ore = ORES.contains(block) || block.endsWith("_ore");
        if ((ore || block.equals("stone")) && !pick) {
            return null;
        }

        return new Mine(host, List.of(tier(block)), Math.min(n, 64), true, block.equals("log") ? L_CHOP : L_MINE);
    }

    /**
     * Gets a thing made, whatever it takes. With everything for it in the pockets it is simply made, the parts
     * first. Short of something, and with chests about, she goes round them, upstairs and down, and takes what
     * the recipe needs. What is still missing after that she smelts from what she carries, or goes out and gets.
     */
    static final class Obtain implements Task {
        private final List<net.minecraft.item.Item> items;
        private final int count;
        private final String said;
        private final Set<BlockPos> visited = new HashSet<>();
        private final Map<net.minecraft.item.Item, Integer> tried = new HashMap<>();
        private Task sub;
        private BlockPos chest;
        private boolean open;
        private boolean told;
        private int wait;
        private int reach;
        private int search;
        private int fails;
        private int ticks;
        private boolean unboxed;

        Obtain(List<net.minecraft.item.Item> items, int count, String said) {
            this.items = items;
            this.count = count;
            this.said = said;
        }

        @Override
        public int label() {
            return this.sub != null ? this.sub.label() : (this.chest != null ? L_SEARCH : L_CRAFT);
        }

        @Override
        public boolean resting() {
            return this.sub != null && this.sub.resting();
        }

        @Override
        public boolean tick(Run run, ServerPlayerEntity host) {
            World world = host.getWorld();
            if (this.sub != null) {
                if (this.sub.tick(run, host)) {
                    return true;
                }

                boolean made = this.sub instanceof Craft && "pilot_crafted".equals(run.ending);
                boolean craft = this.sub instanceof Craft;
                this.sub = null;
                if (made || craft && ++this.fails >= 2) {
                    // Made; or the making itself went wrong twice over, and why is already said.
                    return false;
                }

                // Back with whatever that got: the sums are done again.
                run.ending = null;
                run.path = null;
                run.clear();
                return true;
            }

            if (++this.ticks > 12000) {
                run.end("pilot_lost");
                return false;
            }

            if (this.chest != null) {
                this.rummage(run, host);
                return true;
            }

            Plan plan = plan(host, this.items, this.count, Recipes.stock(host));
            if (plan.steps != null) {
                this.sub = new Craft(plan.steps, plan.item.getName());
                return true;
            }

            if (!plan.known || plan.lack == null) {
                run.end(plan.known ? "pilot_lost" : "pilot_no_recipe", this.said);
                return false;
            }

            if (!this.unboxed && boxed(host, stack -> plan.lack.wants.stream().anyMatch(want -> want.test(stack)))) {
                // What is missing lies in a box she carries: down with it, and it is one more chest to go through.
                this.unboxed = true;
                this.sub = new SetBox(box -> inside(box).stream().anyMatch(inner -> plan.lack.wants.stream().anyMatch(want -> want.test(inner))));
                return true;
            }

            if (this.search < 1500) {
                this.chest = storageNear(host, this.visited);
                if (this.chest != null) {
                    if (!this.told) {
                        this.told = true;
                        HostManager.say(host, "pilot_rummage", 1, list(plan.lack.items));
                    }

                    this.open = false;
                    this.wait = 0;
                    this.reach = 0;
                    return true;
                }
            }

            for (Map.Entry<net.minecraft.item.Item, Integer> entry : plan.lack.items.entrySet()) {
                net.minecraft.item.Item missing = entry.getKey();
                Predicate<ItemStack> raw = stack -> Recipes.cooked(world, stack, net.minecraft.recipe.RecipeType.SMELTING).isOf(missing);
                boolean ovenNear = carries(host, stack -> stack.isOf(Items.FURNACE) || stack.isOf(Items.BLAST_FURNACE) || stack.isOf(Items.SMOKER)) || spotted(host, Pilot::oven, 16) != null;
                if (carries(host, raw) && ovenNear && this.tried.merge(missing, 1, Integer::sum) <= 3) {
                    this.sub = new Smelt(raw, entry.getValue());
                    return true;
                }
            }

            for (Map.Entry<net.minecraft.item.Item, Integer> entry : plan.lack.items.entrySet()) {
                net.minecraft.item.Item missing = entry.getKey();
                if (this.tried.getOrDefault(missing, 0) >= 2) {
                    continue;
                }

                Task job = gather(host, missing, entry.getValue());
                if (job != null) {
                    this.tried.merge(missing, 1, Integer::sum);
                    HostManager.say(host, "pilot_gather", 1, missing.getName());
                    this.sub = job;
                    return true;
                }
            }

            run.end("pilot_missing", list(plan.lack.items));
            return false;
        }

        private void next(Run run, ServerPlayerEntity host) {
            shut(run, host);
            World world = host.getWorld();
            this.visited.add(this.chest);

            // The other half of a double chest is the same chest.
            for (Direction side : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
                BlockPos beside = this.chest.offset(side);
                if (world.getBlockState(beside).isOf(world.getBlockState(this.chest).getBlock()) && world.getBlockState(beside).getBlock() instanceof net.minecraft.block.ChestBlock) {
                    this.visited.add(beside);
                }
            }

            this.chest = null;
            this.open = false;
            this.wait = 0;
            this.reach = 0;
            run.stuck = 0;
            run.path = null;
        }

        /** One chest: up to it, open it, take out what the recipe is short of, shut it. */
        private void rummage(Run run, ServerPlayerEntity host) {
            World world = host.getWorld();
            this.search++;
            if (!storage(world.getBlockEntity(this.chest))) {
                this.next(run, host);
                return;
            }

            Vec3d centre = Vec3d.ofCenter(this.chest);
            if (host.getEyePos().squaredDistanceTo(centre) > 3.4 * 3.4) {
                if (++this.reach > 500 || run.stuck >= 8) {
                    // No way to that one.
                    this.next(run, host);
                    return;
                }

                walk(run, host, Vec3d.ofBottomCenter(this.chest), 1.5, NONE, false);
                return;
            }

            run.look = centre;
            this.wait++;
            if (!this.open) {
                this.open = true;
                run.opened = true;
                run.usePos = this.chest;
                this.wait = 0;
                return;
            }

            net.minecraft.screen.ScreenHandler handler = host.currentScreenHandler;
            if (handler == host.playerScreenHandler) {
                // It would not open: something sits on the lid, or it is not hers to open.
                if (this.wait > 40) {
                    this.next(run, host);
                } else if (this.wait % 12 == 0) {
                    run.usePos = this.chest;
                }

                return;
            }

            if (this.wait < 14) {
                return;
            }

            this.take(host, handler);
            this.next(run, host);
        }

        private void take(ServerPlayerEntity host, net.minecraft.screen.ScreenHandler handler) {
            List<net.minecraft.screen.slot.Slot> theirs = new ArrayList<>();
            Map<net.minecraft.item.Item, Integer> inside = new HashMap<>();

            for (net.minecraft.screen.slot.Slot slot : handler.slots) {
                if (!(slot.inventory instanceof PlayerInventory) && slot.hasStack()) {
                    theirs.add(slot);
                    inside.merge(slot.getStack().getItem(), slot.getStack().getCount(), Integer::sum);
                }
            }

            if (theirs.isEmpty()) {
                return;
            }

            // With what lies in here, would it come together? Then exactly what the sums use up comes out.
            Map<net.minecraft.item.Item, Integer> mine = Recipes.stock(host);
            Map<net.minecraft.item.Item, Integer> both = new HashMap<>(mine);
            inside.forEach((item, n) -> both.merge(item, n, Integer::sum));
            Plan with = plan(host, this.items, this.count, both);
            Map<net.minecraft.item.Item, Integer> wanted = new HashMap<>();
            if (with.steps != null) {
                for (Map.Entry<net.minecraft.item.Item, Integer> entry : inside.entrySet()) {
                    int used = both.get(entry.getKey()) - with.after.getOrDefault(entry.getKey(), 0);
                    int from = Math.min(entry.getValue(), used - mine.getOrDefault(entry.getKey(), 0));
                    if (from > 0) {
                        wanted.put(entry.getKey(), from);
                    }
                }
            } else {
                // Not all of it, but some of what is missing: that much nearer.
                Plan without = plan(host, this.items, this.count, mine);
                if (without.lack != null) {
                    for (net.minecraft.recipe.Ingredient want : without.lack.wants) {
                        for (net.minecraft.screen.slot.Slot slot : theirs) {
                            net.minecraft.item.Item item = slot.getStack().getItem();
                            if (slot.hasStack() && want.test(slot.getStack()) && wanted.getOrDefault(item, 0) < inside.get(item)) {
                                wanted.merge(item, 1, Integer::sum);
                                break;
                            }
                        }
                    }
                }
            }

            boolean any = false;

            for (net.minecraft.screen.slot.Slot slot : theirs) {
                int left = slot.hasStack() ? wanted.getOrDefault(slot.getStack().getItem(), 0) : 0;
                if (left <= 0 || !slot.canTakeItems(host)) {
                    continue;
                }

                net.minecraft.item.Item item = slot.getStack().getItem();
                ItemStack out = slot.takeStack(Math.min(left, slot.getStack().getCount()));
                wanted.merge(item, -out.getCount(), Integer::sum);
                slot.markDirty();
                host.getInventory().offerOrDrop(out);
                any = true;
            }

            if (any) {
                handler.sendContentUpdates();
                host.playerScreenHandler.sendContentUpdates();
                PhaseFx.sound(host, net.minecraft.sound.SoundEvents.ENTITY_ITEM_PICKUP, net.minecraft.sound.SoundCategory.PLAYERS, 0.4F, 1.2F);
            }
        }
    }

    /**
     * Another floor by a ladder or a vine: to the foot of it, or the head of it, and along it. False when there
     * is nothing of the kind near, and the walk is left to the stairs.
     */
    static boolean climb(Run run, ServerPlayerEntity host, double toY) {
        World world = host.getWorld();
        BlockPos feet = host.getBlockPos();
        boolean up = toY > host.getY();
        net.minecraft.registry.tag.TagKey<net.minecraft.block.Block> rungs = net.minecraft.registry.tag.BlockTags.CLIMBABLE;
        if (host.isClimbing()) {
            boolean more = world.getBlockState(up ? feet.up() : feet.down()).isIn(rungs) || up && Math.abs(toY - host.getY()) < 1.8;
            if (!more) {
                return false;
            }

            run.way.add(new Vec3d((double)feet.getX() + 0.5, toY + (up ? 0.6 : 0.0), (double)feet.getZ() + 0.5));
            if (up) {
                run.flags |= JUMP;
            }

            return true;
        }

        if (run.ladder == null || run.ticks - run.ladderAt > 20 || !world.getBlockState(run.ladder).isIn(rungs)) {
            run.ladderAt = run.ticks;
            run.ladder = null;
            double bestDistance = Double.MAX_VALUE;

            for (BlockPos pos : BlockPos.iterateOutwards(up ? feet : feet.down(), 12, 1, 12)) {
                if (!world.getBlockState(pos).isIn(rungs)) {
                    continue;
                }

                // It has to lead the right way for at least a floor.
                int length = 0;

                for (int i = 1; i <= 12 && world.getBlockState(up ? pos.up(i) : pos.down(i)).isIn(rungs); i++) {
                    length++;
                }

                double d = pos.getSquaredDistance(feet);
                if (length >= 2 && d < bestDistance) {
                    bestDistance = d;
                    run.ladder = pos.toImmutable();
                }
            }
        }

        if (run.ladder == null) {
            return false;
        }

        BlockPos best = run.ladder;
        Vec3d foot = new Vec3d((double)best.getX() + 0.5, (double)best.getY(), (double)best.getZ() + 0.5);
        double dx = foot.x - host.getX();
        double dz = foot.z - host.getZ();
        if (dx * dx + dz * dz < 2.3 && Math.abs(foot.y + (up ? 0.0 : 1.0) - host.getY()) < 1.6) {
            // The last step is straight onto it.
            run.way.add(up ? foot : foot.add(0.0, -1.0, 0.0));
            if (up) {
                run.flags |= JUMP;
            }

            return true;
        }

        Vec3d to = up ? foot : foot.add(0.0, 1.0, 0.0);
        BlockPos at = BlockPos.ofFloored(to);
        if (run.path == null || !at.equals(run.pathGoal) || world.getTime() - run.pathAt > 30L) {
            plan(run, host, at);
        }

        if (!follow(run, host)) {
            run.way.add(to);
        }

        return true;
    }

    // ------------------------------------------------------------------ shulker boxes out of the pockets

    static boolean isBox(ItemStack stack) {
        return stack.getItem() instanceof net.minecraft.item.BlockItem block && block.getBlock() instanceof net.minecraft.block.ShulkerBoxBlock;
    }

    /** What lies in a shulker box that is being carried. */
    static List<ItemStack> inside(ItemStack box) {
        net.minecraft.nbt.NbtCompound tag = box.getSubNbt("BlockEntityTag");
        if (tag == null || !tag.contains("Items")) {
            return List.of();
        }

        net.minecraft.util.collection.DefaultedList<ItemStack> items = net.minecraft.util.collection.DefaultedList.ofSize(27, ItemStack.EMPTY);
        net.minecraft.inventory.Inventories.readNbt(tag, items);
        return items;
    }

    /** Is there such a thing in one of the boxes the body carries? What is in a box is in the pockets, a lid away. */
    static boolean boxed(ServerPlayerEntity host, Predicate<ItemStack> what) {
        return carries(host, stack -> isBox(stack) && inside(stack).stream().anyMatch(inner -> !inner.isEmpty() && what.test(inner)));
    }

    /** Sets one of the carried boxes down beside the body. */
    static final class SetBox implements Task {
        private final Predicate<ItemStack> which;
        BlockPos spot;
        private int ticks;

        SetBox(Predicate<ItemStack> which) {
            this.which = which;
        }

        @Override
        public int label() {
            return L_STORE;
        }

        boolean down(World world) {
            return this.spot != null && world.getBlockState(this.spot).getBlock() instanceof net.minecraft.block.ShulkerBoxBlock;
        }

        @Override
        public boolean tick(Run run, ServerPlayerEntity host) {
            World world = host.getWorld();
            if (this.down(world)) {
                run.boxes.add(this.spot);
                return false;
            }

            if (this.spot == null) {
                BlockPos free = freeSpot(host);
                this.spot = free != null && passable(world, free.up()) ? free : null;
            }

            if (this.spot == null || ++this.ticks > 120 || put(run, host, this.spot, stack -> isBox(stack) && this.which.test(stack)) < 0) {
                this.spot = null;
                return false;
            }

            return true;
        }
    }

    /** A job done out of a box of her own: the box set down first, then the job with it. */
    static final class WithBox implements Task {
        private final SetBox set;
        private final java.util.function.Function<BlockPos, Task> then;
        private Task inner;

        WithBox(Predicate<ItemStack> which, java.util.function.Function<BlockPos, Task> then) {
            this.set = new SetBox(which);
            this.then = then;
        }

        @Override
        public int label() {
            return this.inner != null ? this.inner.label() : L_STORE;
        }

        @Override
        public boolean tick(Run run, ServerPlayerEntity host) {
            if (this.inner == null) {
                if (this.set.tick(run, host)) {
                    return true;
                }

                if (!this.set.down(host.getWorld())) {
                    run.end("pilot_lost");
                    return false;
                }

                this.inner = this.then.apply(this.set.spot);
                return true;
            }

            return this.inner.tick(run, host);
        }
    }

    /** The boxes she set down are broken and picked up again; then the job is over the way it was going to be. */
    static final class Tidy implements Task {
        private final String ending;
        private final Object[] args;
        private int ticks;

        Tidy(String ending, Object[] args) {
            this.ending = ending;
            this.args = args;
        }

        @Override
        public int label() {
            return L_STORE;
        }

        @Override
        public boolean tick(Run run, ServerPlayerEntity host) {
            World world = host.getWorld();
            run.boxes.removeIf(pos -> !(world.getBlockState(pos).getBlock() instanceof net.minecraft.block.ShulkerBoxBlock));
            ItemEntity dropped = lying(host, 8.0, Pilot::isBox);
            if (++this.ticks > 400 || run.boxes.isEmpty() && dropped == null && this.ticks > 10) {
                run.ending = this.ending;
                run.endingArgs = this.args;
                return false;
            }

            if (!run.boxes.isEmpty()) {
                BlockPos box = run.boxes.get(0);
                if (host.getEyePos().squaredDistanceTo(Vec3d.ofCenter(box)) > 3.6 * 3.6) {
                    walk(run, host, Vec3d.ofBottomCenter(box), 1.5, NONE, false);
                } else {
                    strike(run, host, box);
                }
            } else if (dropped != null) {
                walk(run, host, dropped.getPos(), 0.3, NONE, false);
            }

            return true;
        }
    }

    /** A job has ended with boxes of hers still standing: they come first. True when that is now the job in hand. */
    private static boolean tidy(Run run, ServerPlayerEntity host) {
        if (run.boxes.isEmpty() || run.task instanceof Tidy) {
            return false;
        }

        shut(run, host);
        run.task = new Tidy(run.ending, run.endingArgs);
        run.ending = null;
        run.path = null;
        return true;
    }

    // ------------------------------------------------------------------ chests, by hand

    /** Every chest, barrel and shulker box about the place that fits, nearest first. */
    private static List<BlockPos> storages(ServerPlayerEntity host, Predicate<BlockPos> fits) {
        List<BlockPos> all = new ArrayList<>();
        Set<BlockPos> skip = new HashSet<>();

        for (int i = 0; i < 64; i++) {
            BlockPos next = storageNear(host, skip, 48);
            if (next == null) {
                break;
            }

            skip.add(next);
            if (fits.test(next)) {
                all.add(next);
            }
        }

        return all;
    }

    /** The chest with the most chests round it: the storeroom, if the place has one. */
    private static BlockPos storeroom(ServerPlayerEntity host) {
        List<BlockPos> all = storages(host, pos -> true);
        BlockPos best = null;
        int bestScore = -1;

        for (BlockPos one : all) {
            int score = 0;

            for (BlockPos other : all) {
                if (other.isWithinDistance(one, 6.0)) {
                    score++;
                }
            }

            if (score > bestScore) {
                bestScore = score;
                best = one;
            }
        }

        return best;
    }

    /** Which containers a request means: of the kind it names, on the side it names, or the one under the crosshair. */
    private static List<BlockPos> meant(ServerPlayerEntity host, PilotSpeech.Order order) {
        World world = host.getWorld();
        String what = order.what();
        if (pointed(host) instanceof BlockHitResult hit && hit.getType() != HitResult.Type.MISS && storage(world.getBlockEntity(hit.getBlockPos())) && !what.contains("left") && !what.contains("right")) {
            // Looking straight at one: that one, and after it the rest, should it be full.
            List<BlockPos> first = new ArrayList<>();
            first.add(hit.getBlockPos().toImmutable());
            first.addAll(storages(host, pos -> !pos.equals(hit.getBlockPos())));
            return first;
        }

        Vec3d look = host.getRotationVec(1.0F);
        return storages(host, pos -> {
            net.minecraft.block.entity.BlockEntity entity = world.getBlockEntity(pos);
            if (what.contains("barrel") && !(entity instanceof net.minecraft.block.entity.BarrelBlockEntity)
                || what.contains("chest") && !(entity instanceof net.minecraft.block.entity.ChestBlockEntity)
                || what.contains("shulker") && !(entity instanceof net.minecraft.block.entity.ShulkerBoxBlockEntity)) {
                return false;
            }

            // Left and right of where the Host is looking.
            double side = look.x * ((double)pos.getZ() + 0.5 - host.getZ()) - look.z * ((double)pos.getX() + 0.5 - host.getX());
            return !(what.contains("left") && side > -0.3) && !(what.contains("right") && side < 0.3);
        });
    }

    /** Up to a container and into it: the walk, the lid, the moment of looking. */
    private static final class Lid {
        private boolean open;
        private int wait;
        private int reach;

        /** 1 when the screen is up and settled, 0 while she is at it, -1 when this one cannot be got into. */
        int tick(Run run, ServerPlayerEntity host, BlockPos chest) {
            World world = host.getWorld();
            if (!storage(world.getBlockEntity(chest))) {
                return -1;
            }

            Vec3d centre = Vec3d.ofCenter(chest);
            if (host.getEyePos().squaredDistanceTo(centre) > 3.4 * 3.4) {
                if (++this.reach > 600 || run.stuck >= 8) {
                    return -1;
                }

                walk(run, host, Vec3d.ofBottomCenter(chest), 1.5, NONE, false);
                return 0;
            }

            run.look = centre;
            this.wait++;
            if (!this.open) {
                this.open = true;
                run.opened = true;
                run.usePos = chest;
                this.wait = 0;
                return 0;
            }

            if (host.currentScreenHandler == host.playerScreenHandler) {
                if (this.wait > 40) {
                    return -1;
                }

                if (this.wait % 12 == 0) {
                    run.usePos = chest;
                }

                return 0;
            }

            return this.wait < 14 ? 0 : 1;
        }
    }

    /** Puts things from the pockets away into chests: these many of that, or whatever she has come by. */
    static final class Stash implements Task {
        private final List<BlockPos> chests;
        private final Predicate<ItemStack> what;
        private final boolean backpack;
        private int left;
        private int moved;
        private int at;
        private Lid lid = new Lid();
        private Text into = Text.literal("?");
        /** The takings, by kind: no more of each than she came by. From then on the count starts afresh. */
        Map<net.minecraft.item.Item, Integer> budget;

        Stash(List<BlockPos> chests, Predicate<ItemStack> what, boolean backpack, int count) {
            this.chests = chests;
            this.what = what;
            this.backpack = backpack;
            this.left = count;
        }

        @Override
        public int label() {
            return L_STORE;
        }

        @Override
        public boolean tick(Run run, ServerPlayerEntity host) {
            if (this.at >= Math.min(this.chests.size(), 6) || this.left <= 0) {
                shut(run, host);
                if (this.budget != null) {
                    HAD.put(host.getUuid(), Recipes.stock(host));
                }

                run.end(this.moved > 0 ? "pilot_stashed" : (this.chests.isEmpty() ? "pilot_no_storage" : "pilot_no_room"), this.moved, this.into);
                return false;
            }

            BlockPos chest = this.chests.get(this.at);
            int state = this.lid.tick(run, host, chest);
            if (state == 0) {
                return true;
            }

            if (state > 0) {
                int before = this.moved;
                this.put(host);
                if (this.moved > before) {
                    this.into = host.getWorld().getBlockState(chest).getBlock().getName();
                    PhaseFx.sound(host, net.minecraft.sound.SoundEvents.ENTITY_ITEM_PICKUP, net.minecraft.sound.SoundCategory.PLAYERS, 0.4F, 0.9F);
                }
            }

            // Shut, and on to the next if there is still something in the hands.
            shut(run, host);
            this.lid = new Lid();
            this.at++;
            run.path = null;
            run.stuck = 0;
            if (!this.any(host)) {
                this.left = 0;
            }

            return true;
        }

        private boolean any(ServerPlayerEntity host) {
            List<ItemStack> main = host.getInventory().main;

            for (int i = this.backpack ? 9 : 0; i < main.size(); i++) {
                if (!main.get(i).isEmpty() && this.what.test(main.get(i))) {
                    return true;
                }
            }

            return false;
        }

        private void put(ServerPlayerEntity host) {
            net.minecraft.screen.ScreenHandler handler = host.currentScreenHandler;
            List<ItemStack> main = host.getInventory().main;

            for (int i = this.backpack ? 9 : 0; i < main.size() && this.left > 0; i++) {
                ItemStack stack = main.get(i);
                if (stack.isEmpty() || !this.what.test(stack)) {
                    continue;
                }

                for (net.minecraft.screen.slot.Slot slot : handler.slots) {
                    if (stack.isEmpty() || this.left <= 0) {
                        break;
                    }

                    if (slot.inventory instanceof PlayerInventory || !slot.canInsert(stack)) {
                        continue;
                    }

                    ItemStack there = slot.getStack();
                    int room = there.isEmpty() ? slot.getMaxItemCount(stack) : (ItemStack.canCombine(there, stack) ? Math.min(slot.getMaxItemCount(stack), there.getMaxCount()) - there.getCount() : 0);
                    int move = Math.min(Math.min(room, stack.getCount()), this.left);
                    if (this.budget != null) {
                        move = Math.min(move, this.budget.getOrDefault(stack.getItem(), 0));
                    }

                    if (move <= 0) {
                        continue;
                    }

                    if (this.budget != null) {
                        this.budget.merge(stack.getItem(), -move, Integer::sum);
                    }

                    if (there.isEmpty()) {
                        slot.setStack(stack.split(move));
                    } else {
                        there.increment(move);
                        stack.decrement(move);
                    }

                    slot.markDirty();
                    this.left -= move;
                    this.moved += move;
                }
            }

            host.getInventory().markDirty();
            handler.sendContentUpdates();
            host.playerScreenHandler.sendContentUpdates();
        }
    }

    /** Goes round the chests for a thing by its name and takes so many of it. */
    static final class Withdraw implements Task {
        private final List<BlockPos> chests;
        private final Predicate<ItemStack> what;
        private int left;
        private int got;
        private int at;
        private Lid lid = new Lid();
        private Text name = Text.literal("?");

        Withdraw(List<BlockPos> chests, Predicate<ItemStack> what, int count) {
            this.chests = chests;
            this.what = what;
            this.left = count;
        }

        @Override
        public int label() {
            return L_STORE;
        }

        @Override
        public boolean tick(Run run, ServerPlayerEntity host) {
            if (this.at >= Math.min(this.chests.size(), 24) || this.left <= 0) {
                shut(run, host);
                run.end(this.got > 0 ? "pilot_fetched" : (this.chests.isEmpty() ? "pilot_no_storage" : "pilot_none"), this.got, this.name);
                return false;
            }

            int state = this.lid.tick(run, host, this.chests.get(this.at));
            if (state == 0) {
                return true;
            }

            if (state > 0) {
                net.minecraft.screen.ScreenHandler handler = host.currentScreenHandler;

                for (net.minecraft.screen.slot.Slot slot : handler.slots) {
                    if (this.left <= 0) {
                        break;
                    }

                    if (slot.inventory instanceof PlayerInventory || !slot.hasStack() || !this.what.test(slot.getStack()) || !slot.canTakeItems(host)) {
                        continue;
                    }

                    this.name = slot.getStack().getName();
                    ItemStack out = slot.takeStack(Math.min(this.left, slot.getStack().getCount()));
                    this.left -= out.getCount();
                    this.got += out.getCount();
                    slot.markDirty();
                    host.getInventory().offerOrDrop(out);
                }

                handler.sendContentUpdates();
                host.playerScreenHandler.sendContentUpdates();
                if (this.got > 0) {
                    PhaseFx.sound(host, net.minecraft.sound.SoundEvents.ENTITY_ITEM_PICKUP, net.minecraft.sound.SoundCategory.PLAYERS, 0.4F, 1.2F);
                }
            }

            shut(run, host);
            this.lid = new Lid();
            this.at++;
            run.path = null;
            run.stuck = 0;
            return true;
        }
    }

    private static void stash(ServerPlayerEntity host, PilotSpeech.Order order, boolean held) {
        String thing = VoltaChat.loot(order.subject());
        List<BlockPos> chests = meant(host, order);
        if (chests.isEmpty() && carries(host, Pilot::isBox) && (order.what().contains("shulker") || meant(host, new PilotSpeech.Order("stash", "", -1, null, false, false, null, Float.NaN, "")).isEmpty())) {
            // Into a box of her own: set down, filled, taken up again.
            boolean loot = thing.isBlank() || order.what().contains("loot");
            if (loot) {
                go(host, new WithBox(box -> true, spot -> new Stash(List.of(spot), stack -> true, true, 9999)), held);
            } else {
                String everyday = VoltaChat.canonical(thing);
                HostManager.askNames(host, 0, thing, (exact, partial) -> {
                    Set<Identifier> ids = new HashSet<>(exact.isEmpty() ? partial : exact);
                    Predicate<ItemStack> what = stack -> {
                        Identifier id = Registries.ITEM.getId(stack.getItem());
                        return ids.contains(id) || ids.isEmpty() && everyday != null && id.getPath().contains(everyday);
                    };
                    go(host, new WithBox(box -> true, spot -> new Stash(List.of(spot), what, false, order.number() > 0 ? order.number() : 9999)), held);
                });
            }

            return;
        }

        if (chests.isEmpty()) {
            HostManager.say(host, "pilot_no_storage", 2);
            return;
        }

        if (thing.isBlank() || order.what().contains("loot")) {
            // The takings: what the pockets hold over what they held when she first took the body. With nothing to
            // go by, everything that is not on the hotbar.
            Map<net.minecraft.item.Item, Integer> had = HAD.get(host.getUuid());
            Map<net.minecraft.item.Item, Integer> now = Recipes.stock(host);
            Map<net.minecraft.item.Item, Integer> over = new HashMap<>();
            int sum = 0;
            if (had != null) {
                for (Map.Entry<net.minecraft.item.Item, Integer> entry : now.entrySet()) {
                    int more = entry.getValue() - had.getOrDefault(entry.getKey(), 0);
                    if (more > 0) {
                        over.put(entry.getKey(), more);
                        sum += more;
                    }
                }
            }

            if (sum > 0) {
                Stash task = new Stash(chests, stack -> over.getOrDefault(stack.getItem(), 0) > 0, false, sum);
                task.budget = over;
                go(host, task, held);
            } else {
                go(host, new Stash(chests, stack -> true, true, 9999), held);
            }

            return;
        }

        String alias = VoltaChat.canonical(thing);
        HostManager.askNames(host, 0, thing, (exact, partial) -> {
            Set<Identifier> ids = new HashSet<>(exact.isEmpty() ? partial : exact);
            Predicate<ItemStack> what = stack -> {
                Identifier id = Registries.ITEM.getId(stack.getItem());
                return ids.contains(id) || ids.isEmpty() && alias != null && id.getPath().contains(alias);
            };
            if (!carries(host, what)) {
                HostManager.say(host, "pilot_none", 2);
                return;
            }

            go(host, new Stash(chests, what, false, order.number() > 0 ? order.number() : 9999), held);
        });
    }

    private static void withdraw(ServerPlayerEntity host, PilotSpeech.Order order, boolean held) {
        String thing = VoltaChat.loot(order.subject());
        List<BlockPos> chests = meant(host, order);
        if (thing.isBlank()) {
            HostManager.say(host, "pilot_where", 2);
            return;
        }

        if (chests.isEmpty() && !carries(host, Pilot::isBox)) {
            HostManager.say(host, "pilot_no_storage", 2);
            return;
        }

        String alias = VoltaChat.canonical(thing);
        HostManager.askNames(host, 0, thing, (exact, partial) -> {
            Set<Identifier> ids = new HashSet<>(exact.isEmpty() ? partial : exact);
            Predicate<ItemStack> what = stack -> {
                Identifier id = Registries.ITEM.getId(stack.getItem());
                return ids.contains(id) || ids.isEmpty() && alias != null && id.getPath().contains(alias);
            };
            int count = order.number() > 0 ? order.number() : 1;
            if (boxed(host, what)) {
                // It is in a box she carries: that lid first.
                go(host, new WithBox(box -> inside(box).stream().anyMatch(what), spot -> new Withdraw(List.of(spot), what, count)), held);
                return;
            }

            go(host, new Withdraw(chests, what, count), held);
        });
    }

    private static net.minecraft.text.MutableText list(Map<net.minecraft.item.Item, Integer> items) {
        net.minecraft.text.MutableText list = Text.empty();
        boolean first = true;

        for (Map.Entry<net.minecraft.item.Item, Integer> entry : items.entrySet()) {
            list.append(first ? "" : ", ").append(entry.getValue() + " × ").append(entry.getKey().getName());
            first = false;
        }

        return list;
    }

    private static void craft(ServerPlayerEntity host, PilotSpeech.Order order, boolean held) {
        String thing = VoltaChat.loot(order.subject());
        if (thing.isBlank()) {
            HostManager.say(host, "pilot_where", 2);
            return;
        }

        HostManager.askNames(host, 0, thing, (exact, partial) -> {
            List<net.minecraft.item.Item> items = new ArrayList<>();
            if (LOG) {
                com.windykindy49.windyorigins.WindyOrigins.LOGGER.info("PILOT craft '{}' exact {} partial {}", thing, exact, partial);
            }

            // The thing by its own name, if that is a thing that gets made; otherwise whatever has the word in its
            // name, which is how "a pickaxe" finds the pickaxes.
            Recipes.Book book = Recipes.book(host.getWorld(), true);

            for (List<Identifier> ids : List.of(exact, partial)) {
                for (Identifier id : ids) {
                    net.minecraft.item.Item item = Registries.ITEM.get(id);
                    if (book.knows(item) && !items.contains(item) && items.size() < 48) {
                        items.add(item);
                    }
                }

                if (!items.isEmpty()) {
                    break;
                }
            }

            if (items.isEmpty()) {
                HostManager.say(host, "pilot_no_recipe", 2, thing);
                return;
            }

            // "A pickaxe": the best one that can be made out of what there is.
            items.sort(java.util.Comparator.comparingInt((net.minecraft.item.Item item) -> -item.getMaxDamage()));
            go(host, new Obtain(items, Math.max(1, Math.min(order.number(), 64)), thing), held);
        });
    }

    private static void smelt(ServerPlayerEntity host, PilotSpeech.Order order, boolean held) {
        World world = host.getWorld();
        String thing = VoltaChat.loot(order.subject());
        String alias = VoltaChat.canonical(thing);
        int want = order.number() > 0 ? Math.min(order.number(), 64) : 16;
        if (thing.isBlank()) {
            Predicate<ItemStack> raw = stack -> {
                String path = Registries.ITEM.getId(stack.getItem()).getPath();
                return (path.startsWith("raw_") || path.endsWith("_ore") || stack.isFood()) && smeltable(world, stack);
            };
            if (carries(host, raw)) {
                go(host, new Smelt(raw, want), held);
            } else {
                HostManager.say(host, "pilot_none", 2);
            }

            return;
        }

        HostManager.askNames(host, 0, thing, (exact, partial) -> {
            Set<Identifier> ids = new HashSet<>(exact.isEmpty() ? partial : exact);
            Predicate<ItemStack> what = stack -> {
                Identifier id = Registries.ITEM.getId(stack.getItem());
                return (ids.contains(id) || alias != null && id.getPath().contains(alias)) && smeltable(world, stack);
            };
            if (carries(host, what)) {
                go(host, new Smelt(what, want), held);
            } else {
                HostManager.say(host, "pilot_none", 2);
            }
        });
    }
}
