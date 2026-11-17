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
import net.minecraft.util.math.MathHelper;
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
    public static final int L_SOW = 20;
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
    /** A straight, level stretch ahead and a long way to go: sprinting with a jump in it, which is faster. */
    private static final int HOP = 256;
    /** A block is being set from above onto the side of the one she stands on: back to the gap, crouched at the edge, as a player bridges. */
    private static final int BACK = 512;
    /** A gap to be jumped: the jump comes at the very edge. */
    private static final int LEAP = 1024;
    /** Wings: off the ground, and the jump key again on the way down to open them. */
    private static final int FLY = 2048;
    /** The other is a player: she circles as she fights, and lets the sprint go for a moment after each blow so that the next one throws them back too. */
    private static final int DUEL = 4096;
    private static final int PRESS = 8192;
    private static final int KITE = 16384;
    /** What is set down comes out of the off hand: the main hand keeps its weapon and its ready arm. */
    private static final int OFFHAND = 32768;
    /** The next blow is to be a jump into it: off the ground in time to be coming down when the arm is ready, and not at a run. */
    private static final int CRIT = 65536;
    /** Eyes on him and the arm held: he is behind a shield that would take the blow and leave her open after it. */
    private static final int HOLD = 131072;
    /** A blow at the air, on purpose: he is waiting behind his shield for her arm to be spent, and here it is, with her own shield up after it. */
    private static final int FEINT = 262144;
    /** Off an edge on purpose: on the way down the eyes go to the ground and what is in the hand is put under her as she lands. */
    private static final int DROP = 524288;
    /** Between two: the eyes, and the shield with them, go where she is told to look and not to the one she is fighting. */
    private static final int MID = 1048576;
    /**
     * How long a point of heat takes while she has the body, in twentieths of a tick; twice as long while she only
     * holds it and waits. Out of a fight with a player twenty-three and a half ticks: a minute and three quarters
     * from cold to letting go.
     */
    static final int HEAT_PEACE = 470;
    /** In a fight with a player ten and eight tenths: forty-eight seconds from cold to letting go. */
    static final int HEAT_EVERY = 216;
    /** Taking the body is a request like any other. */
    static final int HEAT_TAKE = HostCommand.PILOT.heat;
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
    /** Which way a search was heading when it was broken off: "carry on" goes on that way, not the way the body was turned meanwhile. */
    private static final Map<UUID, float[]> BEARING = new HashMap<>();
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
        /** A duel: the pearl last thrown, the block a crystal goes on, where the other was a tick ago and how fast it moves. */
        int pearlAt = -200;
        /** When a web last went down at his feet, or she last gave up trying. */
        int webAt = -200;
        int fireAt = -200;
        int fireTry;
        int webTry;
        /** With him caught in a web and her arm ready: how long she has stood waiting for his to be spent. */
        int webWait;
        /** Blows taken one after another without one of hers landing in between, and when the last came. */
        int taken;
        int takenAt = -200;
        /** How long since she last had him where a blade reaches; and whether there is a way to him on foot at all. */
        int apart;
        int siegeCheck;
        boolean noWay;
        float foeHp = -1.0F;
        int pearlAim;
        BlockPos crystalBase;
        int crystalTicks;
        int crystalAt = -200;
        Vec3d foeWas;
        Vec3d foeSpeed = Vec3d.ZERO;
        int foeId = -1;
        /** How far off the other was a tick ago, and how fast the gap is closing. */
        double foeAway = -1.0;
        double foeClosing;
        /** When his shield was last seen up; when his arm was last seen to swing; how ready it was a tick ago. */
        int guardSeenAt = -1000;
        int foeSwungAt = -1000;
        float foeCool = 1.0F;
        /** In how many ticks he can next land a blow: his arm ready and his feet near enough. */
        int strikeIn = 99;
        int lastFlags;
        int lastWay;
        /** His blow has just gone by and her arm is ready: the shield down and hers in, before his is ready again. */
        boolean counter;
        int counterFor = -1;
        /** When she last had a player to fight. */
        int duelAt = -1000;
        int duelFoe = -1;
        /** How long the creeper in front of her has been hissing: it goes off at thirty. */
        int fuse;
        /** How long she has stood off a raised shield; when she last swung at the air to draw it down, and whether she is about to. */
        /** The most one of his blows has cost her so far. */
        float blow;
        /** Blows of his that landed the moment his shield came down: the mark of one who fights by dropping it to strike. */
        /** A bottle on its way out of the hand: the slot it is in, since when, and whether it is for her own feet. */
        int bottleSlot = -1;
        int bottleSince;
        boolean bottleMend;
        int flashHits;
        /** For how many ticks on end his shield has been up. */
        int shieldUp;
        int holdFor;
        int feintAt = -200;
        int feintOff;
        int feintKite;
        boolean feinting;
        /** How long she has stood behind the shield with him in reach and not swinging. */
        int guardFor;
        int pokeAfter;
        int openAt = -100;
        boolean hisBrink;
        /** Another player who is on her while she fights the one she is fighting, when there is one within reach of mattering. */
        LivingEntity second;
        boolean pair;
        /** How many ticks before a blow of that other one can land. */
        int secondIn = 99;
        boolean turning;
        float hostHealth;
        int bottleAt = -200;
        int crystalRest;
        /** A flight on elytra: 1 taking off, 2 on the wing, 3 coming down; what was worn under the wings; the last rocket. */
        int fly;
        int flyTicks;
        double sailSpeed;
        Vec3d sailWas;
        int flyAgain;
        /** How steeply she has to go up from where she stands, in degrees; nothing when there is no room to. */
        double flyUp;
        int flyRocket;
        Vec3d flyLast;
        boolean flySwapped;
        int flyTries;
        /** A horse or a minecart she has taken for the journey: 1 getting on, 2 riding, 3 getting off. */
        int ride;
        int rideId = -1;
        int rideTicks;
        int rideAgain;
        /** A cart: where along the rails it comes nearest the place, and whether it came out of the pockets. */
        BlockPos railEnd;
        boolean ownCart;
        double railBest;
        /** A gap she is jumping: where from, where to, how far along. */
        BlockPos leapFrom;
        BlockPos leapTo;
        Direction leapDir;
        int leapGap;
        int leapStage;
        int leapTicks;
        int leapAgain;
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
        int retreatTicks;
        /** The bow is down for this many ticks more between one arrow and the next. */
        int drawRest;
        /** How long a loaded crossbow has been waiting for the eyes to come round. */
        int boltWait;
        /** A screen of a table, an oven or a chest that she opened and has to shut. */
        boolean opened;
        /** Where the body last stood on dry ground, and whether she is on her way back there for air. */
        Vec3d dry;
        boolean gasp;
        /** Water she poured over the body's own feet, to be taken back into the bucket. */
        BlockPos water;
        int waterFor;
        /** A cliff she is going down by a waterfall of her own: the edge, which way, how deep, how far along. */
        /** A height she is jumping from: the edge, which way, how far down, with what in the hand, and how far along. */
        /** How far ahead the ground is open in a straight line, when it is fit to be run in bounds. */
        double lineClear;
        /** How many blocks to stand on she is digging for; 0 when she is not. */
        int quarry;
        Direction quarryDir;
        BlockPos quarryFrom;
        int quarryTicks;
        int quarryLoot;
        int quarryLootId = -1;
        final Set<Integer> quarryLeft = new HashSet<>();
        int quarryHad;
        /** A slime block under her: the height of the last bounce, and where she was a tick ago. */
        double dropPeak;
        double dropLastY;
        int dropRest;
        boolean dropFalling;
        int quarryAgain;
        /** The last tick she was building her way somewhere: a bridge, a pillar, a dig for blocks. */
        int workAt = -1000;
        long pathKept;
        BlockPos dropEdge;
        Direction dropDir;
        int dropDepth;
        int dropStage;
        int dropGone;
        int dropTicks;
        net.minecraft.item.Item dropWith;
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
            this.second = null;
            this.pair = false;
            this.secondIn = 99;
            this.turning = false;
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
        BEARING.clear();
        BAGGED.clear();
        HANDS.clear();
        RUNS.clear();
        HABITS.clear();
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

        DuelLog.end(host, line);
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
        BEARING.remove(host.getUuid());
        BAGGED.remove(host.getUuid());
        HANDS.remove(host.getUuid());
        HAD.remove(host.getUuid());
    }

    /** Is there a job of this Host's that was broken off and can be gone on with? */
    public static boolean paused(PlayerEntity host) {
        return PAUSED.containsKey(host.getUuid());
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
        if (run.task instanceof Explore search) {
            BEARING.put(host.getUuid(), new float[]{search.heading, -1.0F});
        } else if (run.task instanceof Mine search) {
            BEARING.put(host.getUuid(), new float[]{search.heading, (float)search.shaft.getHorizontal()});
        } else {
            BEARING.remove(host.getUuid());
        }

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

        if (LOG && run.ticks % 10 == 0) {
            Entity aimed = run.attackId < 0 ? null : host.getWorld().getEntityById(run.attackId);
            com.windykindy49.windyorigins.WindyOrigins.LOGGER.info("PILOT strings on {} attack {} {} away {} flags {} swung {} cooldown {}", host.getGameProfile().getName(), run.attackId, aimed == null ? "-" : aimed.getName().getString(), aimed == null ? 0.0F : host.distanceTo(aimed), run.flags, host.handSwinging, host.getAttackCooldownProgress(0.0F));
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
        // A fight with a player is what cooks the chip; any other work it bears three times as long.
        boolean pvp = run.ticks - run.duelAt < 100;
        // Both are in twentieths of a tick: a point of heat every 10.8 ticks of a fight, every 23.5 of other work.
        if (run.ticks * 20 % ((pvp ? HEAT_EVERY : HEAT_PEACE) * (run.task == null || run.task.resting() ? 2 : 1)) < 20) {
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
        if (run.task != null && host.getHealth() <= 3.0F && (threat != null || host.isOnFire()) && !pvp) {
            // She is losing this one for good: better the Host's own hands than a dead Host.
            remember(host);
            release(host, "pilot_hurt");
            return;
        }

        LivingEntity danger = run.task == null ? null : danger(host);
        if (danger instanceof PlayerEntity || pvp) {
            // Nobody turns a back on a player to eat: he is as fast as the body is, and a back is all he needs.
            run.retreat = false;
            run.retreatTicks = 0;
        } else if (danger != null && host.getHealth() <= low) {
            run.retreat = true;
        } else if (danger == null || host.getHealth() > low + 5.0F) {
            run.retreat = false;
            run.retreatTicks = 0;
        }

        if (run.ticks % 60 == 1) {
            gear(host);
        }

        if (host.isFallFlying() && run.fly != 0 && run.task != null) {
            // On the wing there is the flying and nothing else.
            if (!run.task.tick(run, host)) {
                run.fly = 0;
            }

            if (LOG && run.ticks % 20 == 0) {
                com.windykindy49.windyorigins.WindyOrigins.LOGGER.info("PILOT flying at {} stage {} flags {} look {} hand {}", String.format("%.1f %.1f %.1f", host.getX(), host.getY(), host.getZ()), run.fly, run.flags, run.look == null ? "" : String.format("%.1f", run.look.y - host.getEyeY()), host.getMainHandStack().getItem());
            }

            send(host, run);
            return;
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
        if (run.duelAt == run.ticks) {
            DuelLog.tick(host, run);
        }

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
        run.lastFlags = run.flags;
        run.lastWay = run.way.size();
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

    /** How long one block takes to break with the best thing in the pockets for it, in ticks. */
    private static int breakTicks(ServerPlayerEntity host, World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        float hardness = state.getHardness(world, pos);
        if (hardness <= 0.0F) {
            return hardness < 0.0F ? 100000 : 1;
        }

        float best = 1.0F;
        boolean fit = !state.isToolRequired();

        for (int i = 0; i < host.getInventory().size(); i++) {
            ItemStack stack = host.getInventory().getStack(i);
            if (stack.isEmpty() || stack.isDamageable() && stack.getMaxDamage() - stack.getDamage() <= 2) {
                continue;
            }

            float speed = stack.getMiningSpeedMultiplier(state);
            if (speed > best) {
                best = speed;
                fit |= stack.isSuitableFor(state);
            }
        }

        return Math.max(1, (int)Math.ceil((double)(hardness * (fit ? 30.0F : 100.0F) / best)));
    }

    /**
     * What a way cut straight to a place would cost, in ticks: the steps of it, every block to be broken with what
     * is in the pockets, every hole to be filled. Step for step the way her own tunnel goes: along the longer side
     * first, a block up or down a step. -1 when it meets something she may not or cannot break, lava, more holes than
     * she has blocks for, or is more than forty steps.
     */
    private static int through(ServerPlayerEntity host, Vec3d to, boolean anything) {
        World world = host.getWorld();
        BlockPos at = host.getBlockPos();
        BlockPos goal = BlockPos.ofFloored(to);
        int cost = 0;
        int holes = 0;
        int broken = 0;

        for (int step = 0; step < 40; step++) {
            int dx = goal.getX() - at.getX();
            int dz = goal.getZ() - at.getZ();
            int dy = goal.getY() - at.getY();
            if (Math.abs(dx) + Math.abs(dz) <= 1 && Math.abs(dy) <= 1) {
                return broken == 0 || holes > fillers(host) ? -1 : cost;
            }

            Direction dir = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
            if (dx == 0 && dz == 0) {
                // Straight above or below: a stair has to go somewhere to rise or sink.
                dir = host.getHorizontalFacing();
            }

            BlockPos next = at.offset(dir);
            BlockPos[] open;
            BlockPos stand;
            if (dy > 0 && solid(world, next)) {
                open = new BlockPos[]{at.up(2), next.up(2), next.up()};
                stand = next.up();
            } else if (dy < 0 && drop(world, next.down()) <= 3) {
                open = new BlockPos[]{next.up(), next, next.down()};
                stand = next.down();
            } else {
                open = new BlockPos[]{next.up(), next};
                stand = next;
            }

            for (BlockPos cell : open) {
                if (world.getFluidState(cell).isIn(FluidTags.LAVA)) {
                    return -1;
                }

                if (passable(world, cell)) {
                    continue;
                }

                if (!breakable(world, cell) || !anything && !natural(world.getBlockState(cell))) {
                    return -1;
                }

                cost += breakTicks(host, world, cell);
                broken++;
            }

            int fall = passable(world, stand.down()) ? drop(world, stand) : 0;
            if (fall > 3) {
                holes++;
                cost += 20;
            } else if (fall > 0 && dy < 0) {
                stand = stand.down(Math.min(fall, -dy));
            }

            cost += world.getFluidState(stand).isIn(FluidTags.WATER) ? 12 : 5;
            if (cost > 4000) {
                return -1;
            }

            at = stand;
        }

        return -1;
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

    /** How many clear blocks lie under this one before something to land on, water counted as that; -1 for lava, or for nothing within sight. */
    private static int fathom(World world, BlockPos from) {
        for (int i = 1; i <= 160; i++) {
            BlockPos below = from.down(i);
            FluidState fluid = world.getFluidState(below);
            if (fluid.isIn(FluidTags.LAVA) || world.isOutOfHeightLimit(below)) {
                return -1;
            }

            if (fluid.isIn(FluidTags.WATER) || solid(world, below)) {
                return i - 1;
            }

            if (!passable(world, below)) {
                return -1;
            }
        }

        return -1;
    }

    /**
     * Off a height and down. The thing that breaks the fall goes into the hand on the edge; she steps off; on the
     * way down her own hands do the rest, since the last three blocks of a long fall pass in a tick or two: eyes to
     * the ground, and the bucket emptied or the block set the moment the ground is in reach. Landed, what was put
     * there is taken up again: the water back into the bucket, the block broken and picked up.
     */
    private static boolean plunge(Run run, ServerPlayerEntity host) {
        World world = host.getWorld();
        BlockPos edge = run.dropEdge;
        BlockPos over = edge.offset(run.dropDir);
        double bottom = (double)(over.getY() - run.dropDepth);
        net.minecraft.item.Item with = run.dropWith;
        run.dropTicks++;
        if (with == Items.ENDER_PEARL) {
            // One pearl, thrown from the very lip, and far enough out that it clears the wall on its way down: thrown
            // from a step back, or at the foot of the wall itself, it lands on the top she is standing on, and that
            // is a quarter of her health for nothing.
            if (host.getY() <= bottom + 2.0 || run.dropTicks > 200) {
                return false;
            }

            if (run.dropStage > 0) {
                // It is in the air; she waits where she is.
                run.flags |= SNEAK;
                return ++run.dropStage < 60;
            }

            Vec3d lip = new Vec3d((double)edge.getX() + 0.5 + (double)run.dropDir.getOffsetX() * 0.75, (double)edge.getY(), (double)edge.getZ() + 0.5 + (double)run.dropDir.getOffsetZ() * 0.75);
            double fx = lip.x - host.getX();
            double fz = lip.z - host.getZ();
            double short_ = fx * (double)run.dropDir.getOffsetX() + fz * (double)run.dropDir.getOffsetZ();
            run.flags |= SNEAK;
            if (short_ > 0.52 && run.dropTicks < 60) {
                run.way.add(lip);
                return true;
            }

            if (!carries(host, stack -> stack.isOf(Items.ENDER_PEARL))) {
                return false;
            }

            if (host.getItemCooldownManager().isCoolingDown(Items.ENDER_PEARL)) {
                run.dropStage = 1;
                return true;
            }

            double out = Math.max(3.5, (double)run.dropDepth / 4.5);
            pearl(run, host, new Vec3d((double)edge.getX() + 0.5 + (double)run.dropDir.getOffsetX() * (0.5 + out), bottom, (double)edge.getZ() + 0.5 + (double)run.dropDir.getOffsetZ() * (0.5 + out)));
            run.flags |= SNEAK;
            return true;
        }

        if (run.dropStage == 0) {
            if (run.dropTicks > 200) {
                return false;
            }

            if (with != null) {
                if (!carries(host, stack -> stack.isOf(with))) {
                    return false;
                }

                run.slot = equip(host, stack -> stack.isOf(with) ? 1.0 : 0.0);
                if (!host.getMainHandStack().isOf(with)) {
                    run.flags |= SNEAK;
                    return true;
                }
            }

            if (host.getY() < (double)edge.getY() - 0.6) {
                run.dropStage = 1;
                run.dropTicks = 0;
            } else {
                run.way.add(new Vec3d((double)over.getX() + 0.5 + (double)run.dropDir.getOffsetX() * 0.3, (double)edge.getY(), (double)over.getZ() + 0.5 + (double)run.dropDir.getOffsetZ() * 0.3));
            }

            run.flags |= DROP;
            return true;
        }

        run.slot = host.getInventory().selectedSlot;
        if (run.dropStage == 1) {
            run.flags |= DROP;
            run.look = host.getPos().add(0.0, -6.0, 0.0);
            if (host.isOnGround() || host.isTouchingWater() || host.inPowderSnow || webbed(host) || run.dropTicks > 400) {
                run.dropStage = 2;
                run.dropTicks = 0;
            }

            return true;
        }

        if (with == Items.SLIME_BLOCK && run.dropTicks < 400) {
            // It throws her back up, half as high each time. She stays over it, and when the next landing is a small
            // one she comes down on it with her knees bent, which stops the bouncing; then it is broken and taken.
            BlockPos.Mutable under = host.getBlockPos().mutableCopy();
            int gap = 0;

            while (gap < 24 && !world.getBlockState(under).isOf(Blocks.SLIME_BLOCK) && passable(world, under)) {
                under.move(Direction.DOWN);
                gap++;
            }

            if (world.getBlockState(under).isOf(Blocks.SLIME_BLOCK)) {
                double up = host.getY() - (double)under.getY() - 1.0;
                // At rest on it means on it for a few ticks running. The instant of a bounce is also "feet on the block":
                // to break it then is to come down from the top of that bounce onto bare ground.
                run.dropRest = up <= 0.05 && host.isOnGround() ? run.dropRest + 1 : 0;
                if (run.dropRest < 4) {
                    // How high this bounce went, and whether she is on the way down from it.
                    boolean falling = host.getY() < run.dropLastY - 1.0E-3;
                    if (run.dropTicks <= 1 || run.dropFalling && !falling) {
                        // The bottom of a bounce, known by her having stopped coming down: the next one is measured
                        // from here. Not by her being near the block: a tick before she lands she is near it too, and
                        // knees bent a tick before a landing from ten blocks up is every bone broken.
                        run.dropPeak = up;
                    }

                    run.dropPeak = Math.max(run.dropPeak, up);
                    run.dropFalling = falling;
                    run.dropLastY = host.getY();
                    // Knees bent for the whole of a small bounce, going up as well as coming down: a block of slime
                    // throws back even one who only stands on it, a finger's height at a time, and bent knees stop that.
                    // On a high bounce they are straight again before she is over the top of it.
                    if (run.dropPeak < 2.8) {
                        run.flags |= SNEAK;
                    }

                    // Over the middle of it: a bounce that drifts comes down beside the block, from however high it went.
                    double cx = (double)under.getX() + 0.5 - host.getX();
                    double cz = (double)under.getZ() + 0.5 - host.getZ();
                    if (cx * cx + cz * cz > 0.02) {
                        run.way.add(new Vec3d((double)under.getX() + 0.5, host.getY(), (double)under.getZ() + 0.5));
                    }

                    run.look = host.getPos().add(0.0, -6.0, 0.0);
                    return true;
                }

                run.flags |= SNEAK;
                strike(run, host, under.toImmutable());
                return true;
            }
        }

        if (run.dropTicks > 120 && with != Items.SLIME_BLOCK || run.dropTicks > 440) {
            return false;
        }

        if ((with == Items.WATER_BUCKET || with == Items.POWDER_SNOW_BUCKET) && host.getMainHandStack().isOf(Items.BUCKET)) {
            // The bucket is empty and she is standing in what was in it.
            run.flags |= DROP;
            run.look = host.getPos().add(0.0, -1.0, 0.0);
            return run.dropTicks < 60;
        }

        if (with == Items.SLIME_BLOCK || with == Items.HAY_BLOCK) {
            for (BlockPos at : new BlockPos[]{host.getBlockPos().down(), host.getBlockPos(), BlockPos.ofFloored(host.getX(), host.getY() - 0.6, host.getZ())}) {
                if (world.getBlockState(at).isOf(net.minecraft.block.Block.getBlockFromItem(with))) {
                    strike(run, host, at);
                    return true;
                }
            }

            // Broken: it lies there for half a second before it can be taken, and it is not left behind for the
            // sake of that half second. She waits by it, steps to it if it rolled, and goes on when it is in her
            // pockets, or when two seconds have brought nothing.
            if (carries(host, stack -> stack.isOf(with))) {
                return false;
            }

            if (run.dropGone == 0) {
                run.dropGone = run.dropTicks;
            }

            ItemEntity fallen = lying(host, 5.0, stack -> stack.isOf(with));
            if (fallen != null && fallen.squaredDistanceTo(host) > 0.6) {
                run.way.add(lies(host, fallen));
            }

            return run.dropTicks - run.dropGone < 40;
        }

        return false;
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

    private static boolean plainRocket(ItemStack stack) {
        if (!stack.isOf(Items.FIREWORK_ROCKET)) {
            return false;
        }

        // Not the kind that goes off in the face.
        net.minecraft.nbt.NbtCompound tag = stack.getSubNbt("Fireworks");
        return tag == null || tag.getList("Explosions", 10).isEmpty();
    }

    /**
     * Room to get up in: nothing overhead, and nothing in the way of the climb. A shallow climb towards the place
     * when the way is open; nearly straight up when there are trees or a hill in front; none from under leaves.
     */
    private static double runway(ServerPlayerEntity host, Vec3d to) {
        World world = host.getWorld();
        BlockPos feet = host.getBlockPos();

        for (int ox = -1; ox <= 1; ox++) {
            for (int oz = -1; oz <= 1; oz++) {
                if (world.getTopY(Heightmap.Type.MOTION_BLOCKING, feet.getX() + ox, feet.getZ() + oz) > feet.getY() + (ox == 0 && oz == 0 ? 0 : 1)) {
                    return 0.0;
                }
            }
        }

        double dx = to.x - host.getX();
        double dz = to.z - host.getZ();
        double flat = Math.max(0.01, Math.sqrt(dx * dx + dz * dz));

        for (int d = 2; d <= 26; d += 2) {
            int x = (int)Math.floor(host.getX() + dx / flat * (double)d);
            int z = (int)Math.floor(host.getZ() + dz / flat * (double)d);

            for (int side = -1; side <= 1; side++) {
                int sx = x + (int)Math.round(-dz / flat * (double)side);
                int sz = z + (int)Math.round(dx / flat * (double)side);
                if (world.getTopY(Heightmap.Type.MOTION_BLOCKING, sx, sz) > feet.getY() + (int)((double)d * 0.45) - 1) {
                    return 74.0;
                }
            }
        }

        return 33.0;
    }

    /** Wings to fly on, rockets enough for the distance, and open sky to do it in. */
    private static boolean wings(ServerPlayerEntity host, double far) {
        World world = host.getWorld();
        if (world.getDimension().hasCeiling() || !world.isSkyVisible(host.getBlockPos().up(2)) || host.isTouchingWater()) {
            return false;
        }

        Predicate<ItemStack> usable = stack -> stack.isOf(Items.ELYTRA) && net.minecraft.item.ElytraItem.isUsable(stack);
        if (!usable.test(host.getEquippedStack(EquipmentSlot.CHEST)) && !carries(host, usable)) {
            return false;
        }

        int rockets = 0;

        for (ItemStack stack : host.getInventory().main) {
            if (plainRocket(stack)) {
                rockets += stack.getCount();
            }
        }

        return rockets >= (int)(far / 45.0) + 3;
    }

    /**
     * A flight: the wings on, a jump and the wings open, a rocket; height first, then along; down in a long
     * shallow glide, nose up at the last, feet on the ground. False when it is over.
     */
    private static boolean flying(Run run, ServerPlayerEntity host, Vec3d to) {
        ServerWorld world = host.getServerWorld();
        run.flyTicks++;
        Vec3d was = run.flyLast == null ? host.getPos() : run.flyLast;
        double speed = host.getPos().subtract(was).horizontalLength();
        double sink = host.getY() - was.y;
        run.flyLast = host.getPos();
        double dx = to.x - host.getX();
        double dz = to.z - host.getZ();
        double flat = Math.sqrt(dx * dx + dz * dz);
        Vec3d ahead = flat < 0.01 ? new Vec3d(1.0, 0.0, 0.0) : new Vec3d(dx / flat, 0.0, dz / flat);
        if (run.fly == 1) {
            PlayerInventory inventory = host.getInventory();
            if (!host.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)) {
                // The wings go on over the shoulders; what was worn there waits in the pack.
                for (int i = 0; i < inventory.main.size(); i++) {
                    ItemStack stack = inventory.main.get(i);
                    if (stack.isOf(Items.ELYTRA) && net.minecraft.item.ElytraItem.isUsable(stack) && wearable(host, stack, EquipmentSlot.CHEST)) {
                        ItemStack worn = host.getEquippedStack(EquipmentSlot.CHEST);
                        host.equipStack(EquipmentSlot.CHEST, stack);
                        inventory.main.set(i, worn);
                        inventory.markDirty();
                        host.playerScreenHandler.sendContentUpdates();
                        BAGGED.put(host.getUuid(), world.getTime());
                        run.flySwapped = !worn.isEmpty();
                        break;
                    }
                }

                return true;
            }

            // The rocket is in the hand before the feet leave the ground: it has to go the moment the wings open.
            int rocket = equip(host, stack -> plainRocket(stack) ? 1.0 : 0.0);
            run.slot = rocket;
            run.look = host.getEyePos().add(ahead.x * 20.0, Math.tan(Math.toRadians(run.flyUp <= 0.0 ? 33.0 : run.flyUp)) * 20.0, ahead.z * 20.0);
            if (host.isFallFlying()) {
                run.fly = 2;
                run.flyTicks = 0;
                run.flyRocket = 0;
                run.flags |= FLY;
                return true;
            }

            if (LOG && run.flyTicks % 5 == 0) {
                com.windykindy49.windyorigins.WindyOrigins.LOGGER.info("PILOT takeoff t {} at {} ground {} vy {} chest {} hand {} sel {} rocket {}", run.flyTicks, String.format("%.2f %.2f %.2f", host.getX(), host.getY(), host.getZ()), host.isOnGround(), String.format("%.2f", host.getVelocity().y), host.getEquippedStack(EquipmentSlot.CHEST).getItem(), host.getMainHandStack().getItem(), host.getInventory().selectedSlot, rocket);
            }

            if (run.flyTicks > 90 || rocket < 0) {
                return false;
            }

            if (host.getInventory().selectedSlot == rocket) {
                run.flags |= FLY;
            }

            return true;
        }

        if (!host.isFallFlying() && run.fly == 2 && run.flyTicks < 40 && run.flyTries < 3) {
            // Down again before she was properly up: once more.
            run.flyTries++;
            run.fly = 1;
            run.flyTicks = 0;
            return true;
        }

        if (!host.isFallFlying()) {
            run.flyTries = 0;
            // Down. What the wings were worn over goes back on.
            if (run.flySwapped) {
                PlayerInventory inventory = host.getInventory();

                for (int i = 0; i < inventory.main.size(); i++) {
                    ItemStack stack = inventory.main.get(i);
                    if (stack.getItem() instanceof net.minecraft.item.ArmorItem armor && armor.getSlotType() == EquipmentSlot.CHEST) {
                        ItemStack wings = host.getEquippedStack(EquipmentSlot.CHEST);
                        host.equipStack(EquipmentSlot.CHEST, stack);
                        inventory.main.set(i, wings);
                        inventory.markDirty();
                        host.playerScreenHandler.sendContentUpdates();
                        break;
                    }
                }

                run.flySwapped = false;
            }

            return false;
        }

        // How high the ground stands under her, where she is going, and on the way between.
        int under = world.getTopY(Heightmap.Type.MOTION_BLOCKING, host.getBlockX(), host.getBlockZ());
        int there = world.isChunkLoaded(BlockPos.ofFloored(to)) ? world.getTopY(Heightmap.Type.MOTION_BLOCKING, (int)Math.floor(to.x), (int)Math.floor(to.z)) : under;
        int highest = under;

        for (double d = 16.0; d <= 64.0; d += 16.0) {
            BlockPos probe = BlockPos.ofFloored(host.getX() + ahead.x * d, 0.0, host.getZ() + ahead.z * d);
            if (world.isChunkLoaded(probe)) {
                highest = Math.max(highest, world.getTopY(Heightmap.Type.MOTION_BLOCKING, probe.getX(), probe.getZ()));
            }
        }

        double height = host.getY() - (double)under;
        double over = host.getY() - (double)there;
        double pitch;
        boolean rocket = false;
        if (run.fly == 2) {
            if (run.flyTicks < 8) {
                // The first moments: the take-off rocket is hers to fire by reflex.
                run.flags |= FLY;
            }

            double cruise = (double)Math.max(highest, there) + 36.0;
            if (flat < over * 4.2 + 24.0) {
                run.fly = 3;
                run.flyTicks = 0;
            }

            int close = under;

            for (double d = 3.0; d <= 24.0; d += 3.0) {
                BlockPos probe = BlockPos.ofFloored(host.getX() + ahead.x * d, 0.0, host.getZ() + ahead.z * d);
                if (world.isChunkLoaded(probe)) {
                    close = Math.max(close, world.getTopY(Heightmap.Type.MOTION_BLOCKING, probe.getX(), probe.getZ()));
                }
            }

            if (host.getY() < (double)close + 5.0) {
                // Trees or a hillside right in front: up over them first, nearly straight.
                pitch = -74.0;
                rocket = speed < 0.9 && sink < 0.5;
            } else if (host.getY() < cruise - 4.0) {
                // Height first.
                pitch = -32.0;
                rocket = speed < 1.3;
            } else {
                pitch = host.getY() > cruise + 12.0 ? 8.0 : -3.0;
                rocket = speed < 0.95;
            }
        } else {
            // The glide down: no steeper than the fall is kind to; nose up over the last of it.
            double slope = Math.toDegrees(Math.atan2(Math.max(0.0, over - 2.0), Math.max(1.0, flat)));
            pitch = MathHelper.clamp(slope, 4.0, 13.0);
            if (height < 6.0) {
                pitch = sink < -0.25 ? -30.0 : -8.0;
            } else if (height < 14.0 && flat > 40.0) {
                // Short of the place and low: one more push rather than a walk.
                pitch = -20.0;
                rocket = speed < 1.0;
            }
        }

        double rad = Math.toRadians(pitch);
        run.look = host.getEyePos().add(ahead.x * 20.0, -Math.tan(rad) * 20.0, ahead.z * 20.0);
        if (rocket && run.flyTicks - run.flyRocket > 24) {
            int slot = equip(host, stack -> plainRocket(stack) ? 1.0 : 0.0);
            if (slot >= 0) {
                run.slot = slot;
                if (host.getInventory().selectedSlot == slot) {
                    run.flags |= CLICK;
                    run.flyRocket = run.flyTicks;
                }
            }
        }

        return run.flyTicks < 6000;
    }

    /**
     * A long way to go: is there something to ride? A saddled horse of the Host's own (or nobody's) standing near;
     * or rails underfoot that lead towards the place, with a cart on them or in the pockets.
     */
    private static void mount(Run run, ServerPlayerEntity host, Vec3d to) {
        World world = host.getWorld();

        for (net.minecraft.entity.passive.AbstractHorseEntity horse : world.getEntitiesByClass(net.minecraft.entity.passive.AbstractHorseEntity.class, host.getBoundingBox().expand(10.0, 3.0, 10.0), x -> x.isAlive() && x.isTame() && x.isSaddled() && !x.hasPassengers() && !x.isBaby())) {
            if ((horse.getOwnerUuid() == null || horse.getOwnerUuid().equals(host.getUuid())) && host.canSee(horse)) {
                run.ride = 1;
                run.rideId = horse.getId();
                run.rideTicks = 0;
                run.railEnd = null;
                return;
            }
        }

        // Rails within a step or two: followed to see where they go.
        BlockPos rail = null;

        for (BlockPos pos : BlockPos.iterateOutwards(host.getBlockPos(), 3, 1, 3)) {
            if (world.getBlockState(pos).isIn(net.minecraft.registry.tag.BlockTags.RAILS)) {
                rail = pos.toImmutable();
                break;
            }
        }

        if (rail == null) {
            return;
        }

        net.minecraft.entity.vehicle.AbstractMinecartEntity standing = null;

        for (net.minecraft.entity.vehicle.AbstractMinecartEntity cart : world.getEntitiesByClass(net.minecraft.entity.vehicle.AbstractMinecartEntity.class, host.getBoundingBox().expand(5.0, 2.0, 5.0), x -> x.isAlive() && !x.hasPassengers() && x.getType() == EntityType.MINECART)) {
            standing = cart;
        }

        boolean carried = carries(host, stack -> stack.isOf(Items.MINECART));
        if (standing == null && !carried) {
            return;
        }

        // Along the line, as far as it goes: the rail that lies nearest the place.
        java.util.ArrayDeque<BlockPos> open = new java.util.ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        open.add(rail);
        seen.add(rail);
        BlockPos best = rail;
        double bestDistance = Vec3d.ofBottomCenter(rail).squaredDistanceTo(to);

        while (!open.isEmpty() && seen.size() < 600) {
            BlockPos at = open.poll();
            double d = Vec3d.ofBottomCenter(at).squaredDistanceTo(to);
            if (d < bestDistance) {
                bestDistance = d;
                best = at;
            }

            for (Direction side : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos next = at.offset(side).up(dy);
                    if (world.getBlockState(next).isIn(net.minecraft.registry.tag.BlockTags.RAILS) && seen.add(next)) {
                        open.add(next);
                    }
                }
            }
        }

        double now = Math.sqrt(host.getPos().squaredDistanceTo(to));
        if (Math.sqrt(bestDistance) > now - 24.0) {
            // They do not lead where she is going.
            run.rideAgain = run.ticks + 200;
            return;
        }

        run.ride = 1;
        run.rideTicks = 0;
        run.railEnd = best;
        run.railBest = Double.MAX_VALUE;
        run.ownCart = standing == null;
        run.rideId = standing == null ? -1 : standing.getId();
        if (standing == null) {
            // Her own cart goes down on the rail first.
            run.slot = equip(host, stack -> stack.isOf(Items.MINECART) ? 1.0 : 0.0);
            run.usePos = rail;
            run.look = Vec3d.ofCenter(rail);
        }
    }

    /** The ride itself. 1 while it lasts, 0 when it is over and the walk goes on, -1 when it came to nothing. */
    private static int riding(Run run, ServerPlayerEntity host, Vec3d to, double near) {
        World world = host.getWorld();
        run.rideTicks++;
        boolean cart = run.railEnd != null;
        Entity mount = run.rideId < 0 ? null : world.getEntityById(run.rideId);
        double dx = to.x - host.getX();
        double dz = to.z - host.getZ();
        if (run.ride == 1) {
            if (host.hasVehicle()) {
                run.ride = 2;
                run.rideTicks = 0;
                return 1;
            }

            if (run.rideTicks > 160) {
                return -1;
            }

            if (cart && mount == null) {
                // Waiting for the cart she set down to be there; setting it down again if it is not.
                for (net.minecraft.entity.vehicle.AbstractMinecartEntity near1 : world.getEntitiesByClass(net.minecraft.entity.vehicle.AbstractMinecartEntity.class, host.getBoundingBox().expand(5.0, 2.0, 5.0), x -> x.isAlive() && !x.hasPassengers())) {
                    run.rideId = near1.getId();
                    return 1;
                }

                if (run.rideTicks % 20 == 10) {
                    for (BlockPos pos : BlockPos.iterateOutwards(host.getBlockPos(), 3, 1, 3)) {
                        if (world.getBlockState(pos).isIn(net.minecraft.registry.tag.BlockTags.RAILS)) {
                            run.usePos = pos.toImmutable();
                            run.look = Vec3d.ofCenter(pos);
                            break;
                        }
                    }
                }

                run.slot = equip(host, stack -> stack.isOf(Items.MINECART) ? 1.0 : 0.0);
                return 1;
            }

            if (mount == null || !mount.isAlive()) {
                return -1;
            }

            run.attackId = mount.getId();
            run.flags |= MOUNT;
            return 1;
        }

        if (run.ride == 2) {
            if (!host.hasVehicle()) {
                run.ride = 3;
                run.rideTicks = 0;
                return 1;
            }

            if (cart) {
                // Forward along the rails; off where they come nearest, or where the cart will go no further.
                Vec3d end = Vec3d.ofBottomCenter(run.railEnd);
                double left = Math.sqrt((end.x - host.getX()) * (end.x - host.getX()) + (end.z - host.getZ()) * (end.z - host.getZ()));
                boolean still = host.getVehicle().getVelocity().horizontalLength() < 0.01;
                run.railBest = Math.min(run.railBest, left);
                if (left < 1.6 || run.rideTicks > 80 && still || left > run.railBest + 6.0 || run.rideTicks > 2400) {
                    run.flags |= SNEAK;
                    return 1;
                }

                run.way.add(end);
                run.look = new Vec3d(end.x, host.getEyeY(), end.z);
                return 1;
            }

            // A horse goes where the feet would have gone, only faster; and stops when the place is reached.
            if (dx * dx + dz * dz <= (near + 2.0) * (near + 2.0) || run.stuck >= 5 || run.rideTicks > 4800 || host.isTouchingWater()) {
                run.flags |= SNEAK;
                return 1;
            }

            BlockPos goal = BlockPos.ofFloored(to);
            if (run.path == null || run.pathGoal == null || !goal.isWithinDistance(run.pathGoal, 1.5) || world.getTime() - run.pathAt > 30L) {
                plan(run, host, goal);
            }

            if (!follow(run, host)) {
                run.way.add(to);
            }

            run.flags |= SPRINT;
            return 1;
        }

        // Off. A cart of her own is taken up again.
        if (host.hasVehicle()) {
            run.flags |= SNEAK;
            return run.rideTicks < 60 ? 1 : -1;
        }

        if (cart && run.ownCart) {
            if (mount != null && mount.isAlive() && run.rideTicks < 260) {
                run.slot = equip(host, Pilot::weaponWorth);
                run.attackId = mount.getId();
                run.flags |= SWEEP;
                if (host.squaredDistanceTo(mount) > 2.2 * 2.2) {
                    // Within arm's reach of it first.
                    run.way.add(new Vec3d(mount.getX(), host.getY(), mount.getZ()));
                }

                return 1;
            }

            ItemEntity dropped = lying(host, 6.0, stack -> stack.isOf(Items.MINECART));
            if (dropped != null && run.rideTicks < 400) {
                run.way.add(dropped.getPos());
                return 1;
            }
        }

        run.ride = 0;
        run.rideId = -1;
        run.railEnd = null;
        run.rideAgain = run.ticks + (cart ? 1200 : 200);
        run.path = null;
        return 0;
    }

    /** A gap jumped: back for a run at it, then at a sprint over the edge. False when it is over, landed or not. */
    private static boolean leaping(Run run, ServerPlayerEntity host) {
        World world = host.getWorld();
        Direction dir = run.leapDir;
        run.leapTicks++;
        Vec3d land = Vec3d.ofBottomCenter(run.leapTo);
        double ahead = (land.x - host.getX()) * (double)dir.getOffsetX() + (land.z - host.getZ()) * (double)dir.getOffsetZ();
        if (run.leapTicks > 120 || host.getY() < (double)run.leapFrom.getY() - 2.6 || host.isOnGround() && ahead < 0.7 && Math.abs(host.getY() - land.y) < 1.2) {
            return false;
        }

        if (run.leapStage == 0) {
            // The run-up: as far back as there is floor, two steps or three.
            int back = 1;

            for (int i = 2; i <= (run.leapGap >= 3 ? 3 : 2); i++) {
                BlockPos cell = run.leapFrom.offset(dir.getOpposite(), i);
                if (passable(world, cell) && passable(world, cell.up()) && solid(world, cell.down())) {
                    back = i;
                } else {
                    break;
                }
            }

            Vec3d start = Vec3d.ofBottomCenter(run.leapFrom.offset(dir.getOpposite(), back));
            double dx = start.x - host.getX();
            double dz = start.z - host.getZ();
            if (dx * dx + dz * dz > 0.12 && run.leapTicks < 50) {
                run.way.add(start);
                return true;
            }

            run.leapStage = 1;
        }

        run.way.add(land);
        run.flags |= LEAP;
        if (run.leapGap >= 2) {
            run.flags |= SPRINT;
        }

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
                } else if (world.getBlockState(cell).isIn(net.minecraft.registry.tag.BlockTags.ICE) && world.getBlockState(nearer).isIn(net.minecraft.registry.tag.BlockTags.ICE) && passable(world, cell.up())) {
                    // An ice road: the boat goes down on the ice itself.
                    spot = Vec3d.ofCenter(cell).add(0.0, 0.5, 0.0);
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
                run.sailWas = null;
                run.sailSpeed = 0.0;
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
            boolean ice = world.getBlockState(BlockPos.ofFloored(riding.getX(), riding.getY() - 0.2, riding.getZ())).isIn(net.minecraft.registry.tag.BlockTags.ICE);
            // The boat is rowed from her side of the wire: the server is told where it is, never how fast it goes.
            // Word of where it is comes in fits and starts, two ticks' worth at once and then none: the highest of late counts.
            double moved = run.sailWas == null ? 0.0 : riding.getPos().subtract(run.sailWas).horizontalLength();
            run.sailWas = riding.getPos();
            run.sailSpeed = Math.max(Math.min(moved, 4.0), run.sailSpeed * 0.92);
            double speed = run.sailSpeed;
            boolean land = !riding.isTouchingWater() && !ice;
            boolean aground = land || flat > 0.5 && solid(world, BlockPos.ofFloored(riding.getX() + dx / flat * 1.6, riding.getY() + 0.3, riding.getZ() + dz / flat * 1.6));
            if (flat < 2.5 && !ice || ice && flat < 8.0 && speed < 0.2 || land && run.sailTicks > 10 && speed < 0.3 || run.sailTicks > 40 && aground && speed < 0.05 || run.sailTicks > 3000) {
                // Land, or as near as the boat will go: out. On ice only once it is slow, and then the last few
                // blocks are walked: a boat left at speed is a boat lost, and one let run right out takes an age.
                run.flags |= SNEAK;
                return true;
            }

            if (ice) {
                // On ice a boat does not stop when the rowing does: it loses a fiftieth of its speed a tick, less
                // on blue ice, and no more. So the oars are lifted as far ahead as it takes to run the speed off.
                BlockPos under = BlockPos.ofFloored(riding.getX(), riding.getY() - 0.2, riding.getZ());
                double coast = 1.0 / Math.max(0.005, 1.0 - (double)world.getBlockState(under).getBlock().getSlipperiness()) + 2.0;
                int end = -1;

                for (int d = 1; d <= Math.min(160, (int)Math.ceil(3.0 + speed * coast)) && (double)d < flat; d++) {
                    if (!world.getBlockState(BlockPos.ofFloored(riding.getX() + dx / flat * (double)d, riding.getY() - 0.2, riding.getZ() + dz / flat * (double)d)).isIn(net.minecraft.registry.tag.BlockTags.ICE)) {
                        end = d;
                        break;
                    }
                }

                if (end > 0) {
                    // The ice gives out before the place. Level ground beyond takes the way off a boat in a couple
                    // of blocks, so it may come onto it at a fair lick; anything else, and it comes up at a crawl.
                    int ground = 0;

                    for (int d = end; d < end + 10; d++) {
                        BlockPos floor = BlockPos.ofFloored(riding.getX() + dx / flat * (double)d, riding.getY() - 0.2, riding.getZ() + dz / flat * (double)d);
                        if (!solid(world, floor) || !passable(world, floor.up()) || !passable(world, floor.up(2)) || world.getBlockState(floor).isIn(net.minecraft.registry.tag.BlockTags.ICE)) {
                            break;
                        }

                        ground++;
                    }

                    double allow = MathHelper.clamp(((double)ground - 2.0) / 3.2, 0.25, 1.2);
                    if ((double)end < 2.0 + coast * Math.max(0.0, speed - allow)) {
                        return true;
                    }
                } else if (flat < 5.0 + coast * Math.max(0.0, speed - 0.15)) {
                    return true;
                }
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

    /** Is there ice enough ahead to be worth a boat: a dozen blocks of it in a line from here? */
    private static boolean iceAhead(ServerPlayerEntity host, BlockPos from) {
        World world = host.getWorld();
        Direction dir = host.getHorizontalFacing();
        int ice = 0;

        for (int i = 0; i < 16; i++) {
            if (world.getBlockState(from.down().offset(dir, i)).isIn(net.minecraft.registry.tag.BlockTags.ICE)) {
                ice++;
            }
        }

        return ice >= 12;
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

        // And a player who has had a go at it in the last few seconds.
        if (host.getAttacker() instanceof PlayerEntity other && other.isAlive() && host.age - host.getLastAttackedTime() < 120 && other.squaredDistanceTo(host) < Math.min(bestDistance, 256.0)) {
            best = other;
        }

        return best;
    }

    /** How much a blast of a crystal at this spot would take off this creature: by the game's own sums, armour and all. */
    private static float blast(LivingEntity who, Vec3d at) {
        double d = Math.sqrt(who.squaredDistanceTo(at)) / 12.0;
        if (d > 1.0) {
            return 0.0F;
        }

        // Less of it arrives behind cover: the game's own measure of how much of the body the blast can see.
        double impact = (1.0 - d) * (double)net.minecraft.world.explosion.Explosion.getExposure(at, who);
        float raw = (float)((impact * impact + impact) / 2.0 * 7.0 * 12.0 + 1.0);
        net.minecraft.world.Difficulty difficulty = who.getWorld().getDifficulty();
        if (who instanceof PlayerEntity) {
            raw = difficulty == net.minecraft.world.Difficulty.PEACEFUL ? 0.0F : (difficulty == net.minecraft.world.Difficulty.EASY ? Math.min(raw / 2.0F + 1.0F, raw) : (difficulty == net.minecraft.world.Difficulty.HARD ? raw * 1.5F : raw));
        }

        float left = net.minecraft.entity.DamageUtil.getDamageLeft(raw, (float)who.getArmor(), (float)who.getAttributeValue(EntityAttributes.GENERIC_ARMOR_TOUGHNESS));
        int guard = net.minecraft.enchantment.EnchantmentHelper.getProtectionAmount(who.getArmorItems(), who.getDamageSources().explosion(null, null));
        return net.minecraft.entity.DamageUtil.getInflictedDamage(left, (float)guard);
    }

    /**
     * End crystals, the way they are fought with: obsidian down by the other's feet, a crystal on it, a blow, and
     * the next crystal on the same stone. Only when the sums say it costs the other dear and the body little, and
     * only where the blow has a clear way to the crystal: not past the other's shoulder. True while she is at it.
     */
    private static boolean crystal(Run run, ServerPlayerEntity host, LivingEntity foe) {
        World world = host.getWorld();
        Vec3d eye = host.getEyePos();
        net.minecraft.util.math.Box body = foe.getBoundingBox().expand(0.25);
        float spare = host.getHealth() + host.getAbsorptionAmount() - 4.0F;
        // One standing by the other already, hers or his own: it is hit, and she stays on it until it is gone.
        net.minecraft.entity.decoration.EndCrystalEntity pick = null;
        float pickGain = 2.0F;

        for (net.minecraft.entity.decoration.EndCrystalEntity lit : world.getEntitiesByClass(net.minecraft.entity.decoration.EndCrystalEntity.class, foe.getBoundingBox().expand(5.0), x -> x.isAlive())) {
            float mine = blast(host, lit.getPos());
            float theirs = blast(foe, lit.getPos());
            if (mine < spare && theirs - mine * 1.5F > pickGain && eye.squaredDistanceTo(lit.getPos().add(0.0, 1.0, 0.0)) < 5.5 * 5.5) {
                pick = lit;
                pickGain = theirs - mine * 1.5F;
            }
        }

        if (pick != null) {
            Vec3d mid = pick.getPos().add(0.0, 1.0, 0.0);
            run.attackId = pick.getId();
            run.way.clear();
            run.flags |= SWEEP;
            run.crystalTicks = 0;
            if (body.raycast(eye, mid).isPresent()) {
                // He stands between her and it, and the blow would land on him. A step to the side that opens the way.
                Vec3d line = mid.subtract(eye).multiply(1.0, 0.0, 1.0);
                if (line.lengthSquared() > 0.01) {
                    Vec3d side = new Vec3d(-line.z, 0.0, line.x).normalize();

                    for (double step : new double[]{1.4, -1.4, 2.4, -2.4}) {
                        Vec3d spot = host.getPos().add(side.multiply(step));
                        BlockPos cell = BlockPos.ofFloored(spot);
                        if (passable(world, cell) && passable(world, cell.up()) && solid(world, cell.down()) && !body.raycast(spot.add(0.0, (double)host.getStandingEyeHeight(), 0.0), mid).isPresent()) {
                            run.way.add(spot);
                            run.flags |= SPRINT;
                            break;
                        }
                    }
                }
            }

            return true;
        }

        if (!carries(host, stack -> stack.isOf(Items.END_CRYSTAL))) {
            run.crystalBase = null;
            return false;
        }

        if (run.ticks < run.crystalRest) {
            return false;
        }

        if (run.crystalBase != null && ++run.crystalTicks > 24) {
            // It will not go down: he keeps walking through the spot. Blades for a while, and another try later.
            run.crystalBase = null;
            run.crystalTicks = 0;
            run.crystalRest = run.ticks + 40;
            return false;
        }

        if (run.crystalBase == null || run.crystalBase.getSquaredDistance(foe.getPos()) > 3.2 * 3.2 || eye.squaredDistanceTo(Vec3d.ofCenter(run.crystalBase.up())) > 4.2 * 4.2) {
            run.crystalBase = null;
            // Where he will be standing by the time the stone and the crystal are down, not where he stands now.
            BlockPos feet = BlockPos.ofFloored(foe.getPos().add(run.foeSpeed.multiply(6.0).multiply(1.0, 0.0, 1.0)));
            float bestGain = 5.0F;
            boolean stone = carries(host, stack -> stack.isOf(Items.OBSIDIAN));

            for (BlockPos pos : BlockPos.iterate(feet.add(-2, -1, -2), feet.add(2, 0, 2))) {
                BlockState state = world.getBlockState(pos);
                boolean base = state.isOf(Blocks.OBSIDIAN) || state.isOf(Blocks.BEDROCK);
                // Stone goes down only where nobody stands or is about to: the game will not set a block into a body.
                boolean could = !base && stone && (state.isAir() || state.isReplaceable()) && solid(world, pos.down())
                    && world.getOtherEntities(null, new net.minecraft.util.math.Box(pos).expand(0.45, 0.0, 0.45), x -> x.isAlive() && !(x instanceof ItemEntity)).isEmpty();
                if (!base && !could || !world.getBlockState(pos.up()).isAir() || !world.getBlockState(pos.up(2)).isAir()) {
                    continue;
                }

                Vec3d burst = Vec3d.ofBottomCenter(pos.up());
                Vec3d mid = burst.add(0.0, 1.0, 0.0);
                if (!world.getOtherEntities(null, new net.minecraft.util.math.Box(pos.up()).stretch(0.0, 1.0, 0.0), x -> x.isAlive() && !(x instanceof ItemEntity)).isEmpty() || eye.squaredDistanceTo(burst) > 3.9 * 3.9) {
                    continue;
                }

                // Not behind him: the crystal has to be hers to hit the moment it is down.
                if (body.raycast(eye, mid).isPresent() || body.raycast(eye, Vec3d.ofCenter(pos).add(0.0, 0.5, 0.0)).isPresent()) {
                    continue;
                }

                float theirs = blast(foe, burst);
                float mine = blast(host, burst);
                float gain = theirs - mine * 1.5F + (base ? 2.0F : 0.0F);
                if (mine < spare && gain > bestGain) {
                    bestGain = gain;
                    run.crystalBase = pos.toImmutable();
                }
            }
        }

        if (run.crystalBase == null) {
            return false;
        }

        BlockState base = world.getBlockState(run.crystalBase);
        run.attackId = -1;
        run.way.clear();
        if (!base.isOf(Blocks.OBSIDIAN) && !base.isOf(Blocks.BEDROCK)) {
            if (put(run, host, run.crystalBase, Items.OBSIDIAN) < 0) {
                run.crystalBase = null;
                return false;
            }

            return true;
        }

        int slot = equip(host, stack -> stack.isOf(Items.END_CRYSTAL) ? 1.0 : 0.0);
        run.slot = slot;
        run.placeAgainst = run.crystalBase;
        run.placeFace = Direction.UP;
        run.look = Vec3d.ofCenter(run.crystalBase).add(0.0, 0.5, 0.0);
        return true;
    }

    /** The angle a thing thrown so fast, and falling so much a tick, has to leave the hand at to come down that far off; up is more. */
    private static double lob(double flat, double drop, double speed, double fall) {
        for (double deg = -60.0; deg <= 45.0; deg += 2.5) {
            double vx = speed * Math.cos(Math.toRadians(deg));
            double vy = speed * Math.sin(Math.toRadians(deg));
            double x = 0.0;
            double y = 0.0;

            for (int t = 0; t < 80 && y > drop; t++) {
                x += vx;
                y += vy;
                vx *= 0.99;
                vy = vy * 0.99 - fall;
            }

            if (x >= flat) {
                return deg;
            }
        }

        return 45.0;
    }

    /** What one of his blows costs her: the worst she has had from him, or before the first, what his weapon would do through her armour jumped into. */
    private static float blow(Run run, ServerPlayerEntity host, LivingEntity foe) {
        if (run.blow > 0.0F) {
            return run.blow;
        }

        float raw = foe.getAttributes().hasAttribute(net.minecraft.entity.attribute.EntityAttributes.GENERIC_ATTACK_DAMAGE) ? (float)foe.getAttributeValue(net.minecraft.entity.attribute.EntityAttributes.GENERIC_ATTACK_DAMAGE) : 4.0F;
        return net.minecraft.entity.DamageUtil.getDamageLeft(raw * 1.5F, (float)host.getArmor(), (float)host.getAttributeValue(net.minecraft.entity.attribute.EntityAttributes.GENERIC_ARMOR_TOUGHNESS));
    }

    /**
     * How much health a thing gives back: a golden apple four of mending and four on top, the enchanted one four
     * times that; a bottle of healing four, the strong one eight, at once; a bottle of regeneration a point every
     * two and a half seconds, the strong one twice as fast, for as long as it lasts. Nothing for what does not mend.
     */
    private static float mend(ItemStack stack) {
        if (stack.isOf(Items.ENCHANTED_GOLDEN_APPLE)) {
            return 32.0F;
        }

        if (stack.isOf(Items.GOLDEN_APPLE)) {
            return 8.0F;
        }

        if (!stack.isOf(Items.POTION) && !stack.isOf(Items.SPLASH_POTION) && !stack.isOf(Items.LINGERING_POTION)) {
            return 0.0F;
        }

        float sum = 0.0F;

        for (net.minecraft.entity.effect.StatusEffectInstance effect : net.minecraft.potion.PotionUtil.getPotionEffects(stack)) {
            if (!effect.getEffectType().isBeneficial()) {
                return 0.0F;
            }

            if (effect.getEffectType() == net.minecraft.entity.effect.StatusEffects.INSTANT_HEALTH) {
                sum += (float)(4 << Math.min(4, effect.getAmplifier()));
            } else if (effect.getEffectType() == net.minecraft.entity.effect.StatusEffects.REGENERATION) {
                sum += (float)effect.getDuration() / (float)Math.max(1, 50 >> Math.min(5, effect.getAmplifier()));
            }
        }

        return sum;
    }

    /** Whether a bottle that mends is worth opening now: not when most of it would be wasted, sooner when the next two blows would finish her. */
    private static boolean mendNow(ServerPlayerEntity host, ItemStack stack, boolean danger) {
        float gives = Math.min(8.0F, mend(stack));
        float missing = host.getMaxHealth() - host.getHealth();
        boolean slow = true;

        for (net.minecraft.entity.effect.StatusEffectInstance effect : net.minecraft.potion.PotionUtil.getPotionEffects(stack)) {
            slow &= effect.getEffectType() != net.minecraft.entity.effect.StatusEffects.INSTANT_HEALTH;
        }

        if (gives <= 0.0F || slow && host.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.REGENERATION)) {
            return false;
        }

        return missing >= gives * (danger ? 0.6F : 0.85F);
    }

    /**
     * Whether an effect is worth a bottle in this fight. Strength is half as much again on every blow, speed is who
     * catches whom, resistance a fifth off every blow taken: against a player or something big they are worth it,
     * against a zombie they are a bottle thrown away. Fire resistance when she burns or he fights with fire. The
     * rest (sight in the dark, breath under water, a soft fall) do nothing for a fight.
     */
    private static boolean boon(net.minecraft.entity.effect.StatusEffect type, ServerPlayerEntity host, LivingEntity foe) {
        net.minecraft.entity.effect.StatusEffectInstance has = host.getStatusEffect(type);
        if (has != null && has.getDuration() > 100) {
            return false;
        }

        if (type == net.minecraft.entity.effect.StatusEffects.STRENGTH || type == net.minecraft.entity.effect.StatusEffects.SPEED || type == net.minecraft.entity.effect.StatusEffects.RESISTANCE) {
            return foe instanceof PlayerEntity || foe.getMaxHealth() >= 40.0F;
        }

        if (type == net.minecraft.entity.effect.StatusEffects.FIRE_RESISTANCE) {
            return host.isOnFire() || host.isInLava() || foe.isOnFire() && foe.isFireImmune() || foe.getMainHandStack().isOf(Items.FLINT_AND_STEEL) || foe.getMainHandStack().isOf(Items.LAVA_BUCKET)
                || net.minecraft.enchantment.EnchantmentHelper.getFireAspect(foe) > 0;
        }

        return false;
    }

    /** A bottle to drink for the fight ahead: every effect in it good, and one of them wanted. */
    private static boolean draught(ItemStack stack, ServerPlayerEntity host, LivingEntity foe) {
        if (!stack.isOf(Items.POTION)) {
            return false;
        }

        boolean wanted = false;

        for (net.minecraft.entity.effect.StatusEffectInstance effect : net.minecraft.potion.PotionUtil.getPotionEffects(stack)) {
            if (!effect.getEffectType().isBeneficial()) {
                return false;
            }

            wanted |= boon(effect.getEffectType(), host, foe);
        }

        return wanted;
    }

    /**
     * Milk takes everything off, the good with the bad. Worth it when what is on her weakens the arm, slows the
     * legs, takes the sight or eats at her, and there is nothing on her that she would rather keep.
     */
    private static boolean sour(ServerPlayerEntity host) {
        boolean bad = false;

        for (net.minecraft.entity.effect.StatusEffectInstance effect : host.getStatusEffects()) {
            net.minecraft.entity.effect.StatusEffect type = effect.getEffectType();
            if (effect.getDuration() < 100) {
                continue;
            }

            if (type == net.minecraft.entity.effect.StatusEffects.STRENGTH || type == net.minecraft.entity.effect.StatusEffects.SPEED || type == net.minecraft.entity.effect.StatusEffects.RESISTANCE || type == net.minecraft.entity.effect.StatusEffects.REGENERATION || type == net.minecraft.entity.effect.StatusEffects.ABSORPTION && host.getAbsorptionAmount() >= 2.0F) {
                return false;
            }

            bad |= type == net.minecraft.entity.effect.StatusEffects.WEAKNESS || type == net.minecraft.entity.effect.StatusEffects.SLOWNESS || type == net.minecraft.entity.effect.StatusEffects.POISON || type == net.minecraft.entity.effect.StatusEffects.WITHER || type == net.minecraft.entity.effect.StatusEffects.BLINDNESS || type == net.minecraft.entity.effect.StatusEffects.LEVITATION;
        }

        return bad;
    }

    /**
     * Bottles that are thrown. What mends is broken over her own feet when she has lost about what it gives back,
     * and sooner when two more blows would finish her; a bottle is never opened to put back a heart. What makes her
     * stronger or faster goes on before the blades meet, against a player or something big. What harms is thrown at
     * his feet from far enough off that she does not stand in it herself: weakness first, since it takes most of a
     * sword away, then poison, then the rest; a bottle of harming from nearer when it would finish him. Never
     * harming or poison at the undead, whom the first mends and the second does nothing to. True while she is throwing.
     */
    private static boolean bottles(Run run, ServerPlayerEntity host, LivingEntity foe, double away, boolean sees) {
        if (run.bottleSlot >= 0) {
            // One is on its way out of the hand, and it is seen through. Taking a bottle out costs the arm its
            // readiness; to put it back because he has stepped nearer is to have paid that for nothing, twice.
            ItemStack out = host.getInventory().getStack(run.bottleSlot);
            boolean thrown = !out.isOf(Items.SPLASH_POTION) && !out.isOf(Items.LINGERING_POTION);
            if (thrown || run.ticks - run.bottleSince > 34) {
                run.bottleSlot = -1;
                run.bottleAt = run.ticks;
                return false;
            }

            if (!run.bottleMend && away < 3.2) {
                // Meant for his feet from a way off, and he is on her already: thrown now it breaks over both of them.
                run.bottleSlot = -1;
                run.bottleAt = run.ticks;
                return false;
            }

            lob(run, host, foe, away);
            return true;
        }

        if (run.ticks - run.bottleAt < 14) {
            return false;
        }

        PlayerInventory inventory = host.getInventory();
        ItemStack mend = null;
        ItemStack harm = null;
        double mendWorth = 0.0;
        double harmWorth = 0.0;
        boolean finisher = false;
        float missing = host.getMaxHealth() - host.getHealth();
        float have = host.getHealth() + host.getAbsorptionAmount();
        float blow = blow(run, host, foe);
        boolean danger = have <= Math.max(6.0F, blow * 2.0F + 0.5F);
        boolean apart = away > 5.0 || run.strikeIn >= 8 || !sees;
        // How long before he is on her, at the pace he is closing.
        int eta = run.noWay || webbed(foe) ? 99 : (int)(Math.max(0.0, away - 3.3) / Math.max(0.1, run.foeClosing));
        // A bottle takes the hand, and the arm with it, for half a second and more: the hand goes to it, the eyes go
        // down, it breaks, the blade comes back with the arm at nothing. Against one who jumps into his blows that is
        // two of them let in for one bottle, and measured against him she came out worse with bottles than without.
        // So bottles are for when he is not there: thrown back by her blow, held by a web, out of sight, or far.
        boolean free = !sees || eta >= 14 && away > 5.0;

        for (ItemStack stack : inventory.main) {
            if (!stack.isOf(Items.SPLASH_POTION) && !stack.isOf(Items.LINGERING_POTION)) {
                continue;
            }

            List<net.minecraft.entity.effect.StatusEffectInstance> effects = net.minecraft.potion.PotionUtil.getPotionEffects(stack);
            if (effects.isEmpty()) {
                continue;
            }

            boolean good = true;
            boolean bad = true;
            boolean wanted = false;
            boolean news = false;
            double worth = 0.0;

            for (net.minecraft.entity.effect.StatusEffectInstance effect : effects) {
                net.minecraft.entity.effect.StatusEffect type = effect.getEffectType();
                good &= type.isBeneficial();
                bad &= !type.isBeneficial();
                wanted |= boon(type, host, foe);
                news |= type.isInstant() || !foe.hasStatusEffect(type);
                if (type == net.minecraft.entity.effect.StatusEffects.INSTANT_DAMAGE) {
                    float dealt = (float)(6 << Math.min(4, effect.getAmplifier()));
                    worth = Math.max(worth, foe.getHealth() <= dealt * 0.8F ? 100.0 : 20.0 + (double)dealt);
                    news &= !foe.isUndead();
                } else if (type == net.minecraft.entity.effect.StatusEffects.WEAKNESS) {
                    worth = Math.max(worth, 40.0);
                } else if (type == net.minecraft.entity.effect.StatusEffects.POISON) {
                    worth = Math.max(worth, 30.0);
                    news &= !foe.isUndead() && !(foe instanceof net.minecraft.entity.mob.SpiderEntity);
                } else if (type == net.minecraft.entity.effect.StatusEffects.SLOWNESS) {
                    worth = Math.max(worth, 15.0);
                } else {
                    worth = Math.max(worth, 5.0);
                }
            }

            if (good) {
                float gives = mend(stack);
                if (gives > 0.0F) {
                    if (mendNow(host, stack, danger) && (free || blow <= 4.0F && run.strikeIn >= 8 && Math.min(gives, missing) > blow + 1.0F)) {
                        // The one that puts back the most of what is missing and wastes the least.
                        double fit = 100.0 + (double)Math.min(gives, missing) - (double)Math.max(0.0F, Math.min(8.0F, gives) - missing) * 0.5 + (mendNow(host, stack, false) ? 0.0 : -1.0);
                        if (fit > mendWorth) {
                            mendWorth = fit;
                            mend = stack;
                        }
                    }
                } else if (wanted && free && away < 20.0 && mendWorth < 10.0) {
                    mendWorth = 10.0;
                    mend = stack;
                }
            } else if (bad && news && worth > harmWorth && !(foe instanceof net.minecraft.entity.mob.WitchEntity)) {
                harmWorth = worth;
                harm = stack;
                finisher = worth >= 100.0;
            }
        }

        // Begun only with time to finish: six or seven ticks from the hand going to the bottle to the bottle breaking.
        // Straight after a blow of his, or with him out of reach.
        ItemStack pick = mend != null ? mend : (harm != null && sees && away >= (finisher ? 3.4 : 5.0) && away <= 7.5 && (finisher ? run.strikeIn >= 6 : free) ? harm : null);
        if (pick == null) {
            return false;
        }

        ItemStack chosen = pick;
        int slot = equip(host, stack -> stack == chosen ? 1.0 : 0.0);
        if (slot < 0) {
            return false;
        }

        run.bottleSlot = slot;
        run.bottleSince = run.ticks;
        run.bottleMend = pick == mend;
        lob(run, host, foe, away);
        return true;
    }

    /** The bottle in hand goes where it is meant to: at her own feet, or at his. The button is held until it has left the hand. */
    private static void lob(Run run, ServerPlayerEntity host, LivingEntity foe, double away) {
        int slot = run.bottleSlot;
        Vec3d eye = host.getEyePos();
        run.attackId = -1;
        run.way.clear();
        run.slot = slot;
        if (run.bottleMend) {
            // Straight down: it breaks at her own feet.
            run.look = new Vec3d(eye.x, eye.y - 4.0, eye.z).add(host.getRotationVec(1.0F).multiply(0.4, 0.0, 0.4));
        } else {
            // A bottle leaves the hand a fifth of a right angle above where she looks, and not fast.
            Vec3d at = foe.getPos().add(run.foeSpeed.multiply(Math.min(14.0, away / 0.45)).multiply(1.0, 0.0, 1.0));
            double dx = at.x - eye.x;
            double dz = at.z - eye.z;
            double flat = Math.max(0.5, Math.sqrt(dx * dx + dz * dz));
            double up = Math.toRadians(lob(flat, at.y - eye.y, 0.5, 0.05) - 20.0);
            run.look = new Vec3d(eye.x + dx / flat * 10.0, eye.y + Math.tan(up) * 10.0, eye.z + dz / flat * 10.0);
        }

        Vec3d want = run.look.subtract(eye).normalize();
        if (host.getInventory().selectedSlot == slot && host.getRotationVec(1.0F).dotProduct(want) > 0.99) {
            run.flags |= CLICK;
        }
    }

    /** An ender pearl thrown to land so far off in that direction. */
    private static void pearl(Run run, ServerPlayerEntity host, Vec3d at) {
        int slot = equip(host, stack -> stack.isOf(Items.ENDER_PEARL) ? 1.0 : 0.0);
        if (slot < 0) {
            return;
        }

        Vec3d eye = host.getEyePos();
        double dx = at.x - eye.x;
        double dz = at.z - eye.z;
        double flat = Math.max(1.0, Math.sqrt(dx * dx + dz * dz));
        // What a thing thrown at a block and a half a tick has to be lifted by to come down that far away.
        double lift = 0.5 * Math.asin(Math.min(1.0, 0.03 * flat * 1.25 / 2.25)) + Math.atan2(at.y - eye.y, flat);
        run.attackId = -1;
        run.way.clear();
        run.slot = slot;
        run.look = new Vec3d(at.x, eye.y + Math.tan(lift) * flat, at.z);
        if (host.getItemCooldownManager().isCoolingDown(Items.ENDER_PEARL)) {
            // It is in the air.
            run.pearlAt = run.ticks;
            run.pearlAim = 0;
            return;
        }

        if (++run.pearlAim > 40) {
            // The throw would not come off: on foot, then, and another try later.
            run.pearlAt = run.ticks;
            run.pearlAim = 0;
            return;
        }

        // Let go only once the eyes are really where the throw has to go: a pearl lands where she looks.
        Vec3d want = run.look.subtract(eye).normalize();
        if (host.getInventory().selectedSlot == slot && host.getRotationVec(1.0F).dotProduct(want) > 0.998) {
            run.flags |= CLICK;
        }
    }

    /**
     * Which way to back off: as near to straight away as there is ground to run on. Never over an edge: a turn
     * to one side or the other first. Null when there is nowhere to go.
     */
    private static Vec3d away(ServerPlayerEntity host, Vec3d off, double far) {
        World world = host.getWorld();
        Vec3d dir = new Vec3d(off.x, 0.0, off.z);
        if (dir.lengthSquared() < 0.01) {
            dir = new Vec3d(1.0, 0.0, 0.0);
        }

        dir = dir.normalize();

        for (double turn : new double[]{0.0, 40.0, -40.0, 80.0, -80.0, 125.0, -125.0}) {
            double rad = Math.toRadians(turn);
            Vec3d way = new Vec3d(dir.x * Math.cos(rad) - dir.z * Math.sin(rad), 0.0, dir.x * Math.sin(rad) + dir.z * Math.cos(rad));
            boolean ground = true;

            for (double d = 1.5; d <= 6.0 && ground; d += 1.5) {
                BlockPos cell = BlockPos.ofFloored(host.getX() + way.x * d, host.getY() + 0.5, host.getZ() + way.z * d);
                ground = drop(world, cell) <= 3 && !world.getFluidState(cell).isIn(FluidTags.LAVA);
            }

            if (ground) {
                return host.getPos().add(way.multiply(far));
            }
        }

        return null;
    }

    /** Away from what is after the body; once out of its reach, something to eat. Cornered, she fights. */
    private static void retreat(Run run, ServerPlayerEntity host, LivingEntity from) {
        double away = from == null ? 99.0 : (double)host.distanceTo(from);
        boolean aid = carries(host, stack -> aidWorth(stack) > 0.0);
        run.retreatTicks++;
        if (from != null && away < 10.0) {
            // Nobody outruns an arrow with their back to it; and with nothing to mend with, running only puts it off.
            boolean shoots = from instanceof net.minecraft.entity.ai.RangedAttackMob;
            if (shoots || run.stuck >= 3 || !aid && run.retreatTicks > 160) {
                fight(run, host, from);
                return;
            }

            // A few steps clear is enough to get a bottle or an apple down; if it will not be shaken off, she takes it anyway.
            if (!aid || away < 6.0 && run.retreatTicks < 70) {
                Vec3d to = away(host, host.getPos().subtract(from.getPos()), 12.0);
                if (to == null) {
                    // Nowhere to run but over an edge: she stands and fights.
                    fight(run, host, from);
                    return;
                }

                walk(run, host, surface(host.getWorld(), to.x, to.z, host.getY()), 1.0, NONE, true);
                run.flags |= SPRINT;
                return;
            }
        }

        if (from instanceof PlayerEntity && !aid && away < 8.0 && run.ticks - run.pearlAt > 60 && carries(host, stack -> stack.isOf(Items.ENDER_PEARL))) {
            // Nothing to mend with and a player on top of the body: a pearl the other way.
            Vec3d to = away(host, host.getPos().subtract(from.getPos()), 22.0);
            if (to != null) {
                pearl(run, host, to);
                return;
            }
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

    /**
     * Whether the body may wear this there. What the origin forbids the player it forbids her as well: the Host's
     * chip sits where a helmet would go, and the armour slot in the inventory will not take one.
     */
    private static boolean wearable(ServerPlayerEntity host, ItemStack stack, EquipmentSlot slot) {
        for (io.github.apace100.apoli.power.RestrictArmorPower power : io.github.apace100.apoli.component.PowerHolderComponent.getPowers(host, io.github.apace100.apoli.power.RestrictArmorPower.class)) {
            if (!power.canEquip(stack, slot)) {
                return false;
            }
        }

        for (io.github.apace100.apoli.power.ConditionedRestrictArmorPower power : io.github.apace100.apoli.component.PowerHolderComponent.getPowers(host, io.github.apace100.apoli.power.ConditionedRestrictArmorPower.class)) {
            if (!power.canEquip(stack, slot)) {
                return false;
            }
        }

        return true;
    }

    /** What a player would have put on before a fight: a shield from the pockets into the off hand, armour onto whatever is bare. */
    private static void gear(ServerPlayerEntity host) {
        PlayerInventory inventory = host.getInventory();
        boolean changed = false;

        // Anything on the body that it may not wear comes off again: an earlier version of her put helmets on the Host.
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack worn = host.getEquippedStack(slot);
            if (!worn.isEmpty() && worn.getItem() instanceof net.minecraft.item.ArmorItem && !wearable(host, worn, slot)) {
                host.equipStack(slot, ItemStack.EMPTY);
                inventory.offerOrDrop(worn);
                changed = true;
            }
        }

        for (int i = 0; i < inventory.main.size(); i++) {
            ItemStack stack = inventory.main.get(i);
            if (stack.isEmpty()) {
                continue;
            }

            if (stack.getItem() instanceof net.minecraft.item.ShieldItem && !shielded(host) && host.getOffHandStack().isEmpty()) {
                inventory.offHand.set(0, stack);
                inventory.main.set(i, ItemStack.EMPTY);
                changed = true;
            } else if (stack.getItem() instanceof net.minecraft.item.ArmorItem armor && host.getEquippedStack(armor.getSlotType()).isEmpty() && wearable(host, stack, armor.getSlotType())) {
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

    private static boolean ranged(ItemStack stack) {
        return stack.getItem() instanceof net.minecraft.item.BowItem || stack.getItem() instanceof net.minecraft.item.CrossbowItem;
    }

    private static boolean hasBow(ServerPlayerEntity host) {
        // A crossbow that is loaded already needs no arrow in the pockets.
        return carries(host, stack -> stack.getItem() instanceof net.minecraft.item.CrossbowItem && net.minecraft.item.CrossbowItem.isCharged(stack))
            || carries(host, Pilot::ranged) && carries(host, stack -> stack.isIn(net.minecraft.registry.tag.ItemTags.ARROWS));
    }

    /**
     * An arrow at it, aimed ahead of where it is going and over it by the drop. A bow's string is held back until it
     * is full. A crossbow is loaded with the button held, the button let go, and then one press sends the bolt; a
     * loaded one is the first thing she takes, for it is a shot at once.
     */
    private static void shoot(Run run, ServerPlayerEntity host, LivingEntity foe) {
        run.attackId = -1;
        run.way.clear();
        boolean arrows = carries(host, stack -> stack.isIn(net.minecraft.registry.tag.ItemTags.ARROWS));
        run.slot = equip(host, stack -> {
            double left = (double)(stack.getMaxDamage() - stack.getDamage()) / 1000.0;
            if (stack.getItem() instanceof net.minecraft.item.CrossbowItem) {
                // To load one takes longer than to draw a bow, unless it is charmed to load fast.
                return net.minecraft.item.CrossbowItem.isCharged(stack) ? 3.0 + left : !arrows ? 0.0 : net.minecraft.item.CrossbowItem.getPullTime(stack) < 20 ? 2.5 + left : 1.0 + left;
            }

            return stack.getItem() instanceof net.minecraft.item.BowItem && arrows ? 2.0 + left : 0.0;
        });
        ItemStack held = host.getMainHandStack();
        boolean crossbow = held.getItem() instanceof net.minecraft.item.CrossbowItem;
        Vec3d eye = host.getEyePos();
        double dx = foe.getX() - eye.x;
        double dz = foe.getZ() - eye.z;
        double time = Math.sqrt(dx * dx + dz * dz) / (crossbow ? 2.9 : 2.7);
        // A player's speed the server is never told: it is what she has seen of where he was a tick ago.
        Vec3d lead = (foe instanceof PlayerEntity ? run.foeSpeed : foe.getVelocity()).multiply(time, 0.0, time);
        run.look = new Vec3d(foe.getX() + lead.x, foe.getY() + (double)foe.getHeight() * 0.6 + 0.03 * time * time, foe.getZ() + lead.z);
        boolean inHand = host.getInventory().selectedSlot == run.slot && ranged(held);
        if (!inHand) {
            return;
        }

        if (crossbow) {
            if (net.minecraft.item.CrossbowItem.isCharged(held)) {
                // One press and it is gone, so the eyes are on him first; not for ever, though.
                run.boltWait++;
                if (run.drawRest > 0) {
                    run.drawRest--;
                } else if (run.boltWait > 20 || host.getRotationVec(1.0F).dotProduct(run.look.subtract(eye).normalize()) > 0.999) {
                    run.flags |= USE;
                }
            } else {
                run.boltWait = 0;
                if (host.isUsingItem()) {
                    if (host.getItemUseTime() < net.minecraft.item.CrossbowItem.getPullTime(held) + 2) {
                        run.flags |= USE;
                    } else {
                        run.drawRest = 2;
                    }
                } else if (run.drawRest > 0) {
                    run.drawRest--;
                } else {
                    run.flags |= USE;
                }
            }

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
                range.setBaseValue(64.0);
            }

            // A mob gives up on a way round after a few hundred steps of looking. She looks four times as far.
            scout.getNavigation().setRangeMultiplier(4.0F);
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
        Path fresh = scout.getNavigation().findPathTo(goal, 1);
        boolean same = run.path != null && run.pathGoal != null && goal.isWithinDistance(run.pathGoal, 1.5);
        long now = host.getWorld().getTime();
        if ((fresh == null || fresh.getLength() == 0) && same && run.path.getLength() > 0 && now - run.pathKept < 60L) {
            // Asked from a bad spot, in the air or half in a wall, the game finds no walk. The one she has is still
            // good for the next second: she goes on along it instead of stopping to think.
            run.pathAt = now - 20L;
            return;
        }

        run.path = fresh;
        run.pathGoal = goal;
        run.pathAt = now;
        run.pathKept = now;
    }

    /**
     * Whether the body can go straight from where it stands to a step further along the walk: every step up to it on
     * the level she is on, and under the whole width of her, all the way, a floor to walk on and nothing in the way.
     * 0 for no, 1 for yes, 2 when there is room to run it in bounds as well.
     */
    private static int line(ServerPlayerEntity host, Path path, int from, int to) {
        World world = host.getWorld();
        PathNode end = path.getNode(to);

        for (int i = from; i <= to; i++) {
            if (path.getNode(i).y != end.y) {
                return 0;
            }
        }

        double rise = (double)end.y - host.getY();
        if (rise > 0.6 || rise < -0.1) {
            return 0;
        }

        double px = host.getX();
        double pz = host.getZ();
        double sx = (double)end.x + 0.5 - px;
        double sz = (double)end.z + 0.5 - pz;
        double len = Math.sqrt(sx * sx + sz * sz);
        if (len < 1.3) {
            return 0;
        }

        int steps = (int)Math.ceil(len / 0.3);
        boolean bounds = true;
        LongSet seen = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
        BlockPos.Mutable at = new BlockPos.Mutable();

        for (int i = 0; i <= steps; i++) {
            double x = px + sx * (double)i / (double)steps;
            double z = pz + sz * (double)i / (double)steps;

            for (int corner = 0; corner < 4; corner++) {
                at.set(MathHelper.floor(x + ((corner & 1) == 0 ? -0.36 : 0.36)), end.y, MathHelper.floor(z + ((corner & 2) == 0 ? -0.36 : 0.36)));
                if (!seen.add(at.asLong())) {
                    continue;
                }

                BlockPos cell = at.toImmutable();
                BlockState feet = world.getBlockState(cell);
                net.minecraft.util.shape.VoxelShape shape = feet.getCollisionShape(world, cell);
                if (!shape.isEmpty() && shape.getMax(Direction.Axis.Y) > 0.5 || !feet.getFluidState().isEmpty() || !world.getBlockState(cell.up()).getCollisionShape(world, cell.up()).isEmpty()) {
                    return 0;
                }

                if (net.minecraft.entity.ai.pathing.LandPathNodeMaker.getLandNodeType(world, at.set(cell)) != net.minecraft.entity.ai.pathing.PathNodeType.WALKABLE) {
                    return 0;
                }

                bounds &= passable(world, cell.up(2)) && shape.isEmpty() && !(world.getBlockState(cell.down()).getBlock() instanceof net.minecraft.block.FarmlandBlock);
            }
        }

        return bounds ? 2 : 1;
    }

    /** Lays the next few steps of the planned walk in front of the body. False when the walk has nothing more to give. */
    private static boolean follow(Run run, ServerPlayerEntity host) {
        Path path = run.path;
        if (path == null || path.getLength() == 0) {
            return false;
        }

        // Where on the walk the body is: on the stretch between two steps that it is nearest to, and so headed for
        // the far end of that stretch. Measured to the middles of blocks instead, a body on the lip of a step down
        // was nearer the block behind it than the one below, turned back for it, came forward again, and hung there.
        int count = path.getLength();
        double px = host.getX();
        double py = host.getY();
        double pz = host.getZ();
        int from = 0;
        double best = Double.MAX_VALUE;
        run.lineClear = 0.0;

        for (int i = 0; i < count; i++) {
            PathNode node = path.getNode(i);
            double ax = (double)node.x + 0.5;
            double az = (double)node.z + 0.5;
            double bx = ax;
            double by = (double)node.y;
            double bz = az;
            double along = 0.0;
            if (i + 1 < count) {
                PathNode next = path.getNode(i + 1);
                double sx = (double)next.x + 0.5 - ax;
                double sz = (double)next.z + 0.5 - az;
                double len = sx * sx + sz * sz;
                if (len > 1.0E-4) {
                    along = MathHelper.clamp(((px - ax) * sx + (pz - az) * sz) / len, 0.0, 1.0);
                }

                bx = ax + sx * along;
                bz = az + sz * along;
                by = (double)node.y + (double)(next.y - node.y) * along;
            }

            double d = (bx - px) * (bx - px) + (bz - pz) * (bz - pz) + (by - py) * (by - py) * 0.5;
            if (d <= best) {
                best = d;
                from = along > 0.0 ? i + 1 : i;
            }
        }

        // A step is made once the body stands on it: over it, and not still on the lip above it.
        PathNode near = path.getNode(from);
        double dx = (double)near.x + 0.5 - px;
        double dz = (double)near.z + 0.5 - pz;
        double above = py - (double)near.y;
        if (dx * dx + dz * dz < 0.42 && above > -1.2 && (above < 0.6 || !host.isOnGround() && above < 1.2)) {
            from++;
        }

        if (from >= count) {
            return false;
        }

        if (host.isOnGround() && !host.isTouchingWater() && !host.isClimbing()) {
            // Open ground is crossed in a line, not from the middle of one block to the middle of the next: the
            // furthest of the next steps that she can walk straight to, on her own level, with floor all the way.
            for (int j = Math.min(count - 1, from + 7); j > from; j--) {
                int clear = line(host, path, from, j);
                if (clear > 0) {
                    PathNode far = path.getNode(j);
                    double fx = (double)far.x + 0.5 - px;
                    double fz = (double)far.z + 0.5 - pz;
                    run.lineClear = clear > 1 ? Math.sqrt(fx * fx + fz * fz) : 0.0;
                    from = j;
                    break;
                }
            }
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
        boolean busy = run.sail != 0 || run.leapTo != null || run.fallEdge != null || run.ride != 0 || run.fly != 0;
        if (!busy && dx * dx + dz * dz <= near * near && (flat || Math.abs(to.y - host.getY()) <= 2.2)) {
            return true;
        }

        if (flat && dx * dx + dz * dz < 36.0) {
            // A spot on the map, not a height: close to it she stops caring what stands on it.
            to = new Vec3d(to.x, host.getY(), to.z);
        }

        if (run.fly != 0) {
            if (flying(run, host, to)) {
                return false;
            }

            run.fly = 0;
            run.flyAgain = run.ticks + 400;
            run.path = null;
            run.way.clear();
        } else if (run.ticks >= run.flyAgain && run.ticks % 20 == 7 && dx * dx + dz * dz > 120.0 * 120.0 && host.isOnGround() && !host.hasVehicle() && run.ride == 0 && run.sail == 0 && wings(host, Math.sqrt(dx * dx + dz * dz)) && (run.flyUp = runway(host, to)) > 0.0) {
            run.fly = 1;
            run.flyTicks = 0;
            run.flyRocket = -100;
            run.flyLast = host.getPos();
            return false;
        }

        if (run.ride != 0) {
            int state = riding(run, host, to, near);
            if (state > 0) {
                return false;
            }

            if (state < 0) {
                // Off, and no more of that for a while: the rest on foot.
                run.ride = 0;
                run.rideId = -1;
                run.railEnd = null;
                run.rideAgain = run.ticks + 600;
                run.path = null;
                run.way.clear();
                return false;
            }
        } else if (run.ticks >= run.rideAgain && run.ticks % 20 == 3 && dx * dx + dz * dz > 40.0 * 40.0 && host.isOnGround() && !host.hasVehicle() && run.sail == 0 && run.leapTo == null) {
            mount(run, host, to);
        }

        if (run.leapTo != null) {
            if (leaping(run, host)) {
                return false;
            }

            run.leapTo = null;
            run.leapAgain = run.ticks + 60;
            run.path = null;
            run.way.clear();
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

        if (run.quarry > 0) {
            if (quarrying(run, host)) {
                return false;
            }

            run.quarryAgain = run.ticks + (fillers(host) >= run.quarry ? 0 : 1200);
            run.quarry = 0;
            run.path = null;
            run.way.clear();
        }

        if (run.dropEdge != null) {
            if (plunge(run, host)) {
                return false;
            }

            run.dropEdge = null;
            run.fallAgain = run.ticks + 200;
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
        // The walk is thought over again every second and a half with her feet on something, and not in the middle
        // of a jump or a fall, where the answer is worth nothing.
        boolean footed = host.isOnGround() || host.isTouchingWater() || host.isClimbing() || host.hasVehicle();
        if (run.path == null || run.pathGoal == null || !goal.isWithinDistance(run.pathGoal, 1.5) || now - run.pathAt > (footed ? 30L : 100L)) {
            plan(run, host, goal);
            if (dig > NONE && run.path != null && run.path.getLength() > 16 && host.isOnGround()) {
                // A long way round, or none: is it quicker straight across on blocks of her own?
                int direct = straight(host, to);
                if (direct > 0 && (!run.path.reachesTarget() || run.path.getLength() > direct * 3 / 2 + 8)) {
                    run.burrowFor = 40;
                    burrow(run, host, to, null, dig == ANYTHING);
                    return false;
                }

                // Or straight through what is in the way: a wall of earth a spade goes through in two seconds is not
                // walked round for twenty. Both ways are counted in ticks, the walk at four and a half a block, and
                // the cut has to come out well under the walk before she takes it: a tunnel has more ways to go wrong.
                // A block she is sent to is never "reached" by a walk, which stops beside it: near enough counts.
                net.minecraft.entity.ai.pathing.PathNode last = run.path.getEnd();
                boolean arrives = run.path.reachesTarget() || last != null && last.getBlockPos().isWithinDistance(goal, 3.5);
                int cut = arrives ? through(host, to, dig == ANYTHING) : -1;
                if (cut > 0 && cut * 10 < run.path.getLength() * 45 * 6 / 10) {
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
                BlockPos step = BlockPos.ofFloored(run.way.get(i));
                wet += world.getFluidState(step).isIn(FluidTags.WATER) || world.getFluidState(step.down()).isIn(FluidTags.WATER) || world.getBlockState(step.down()).isIn(net.minecraft.registry.tag.BlockTags.ICE) && iceAhead(host, step) ? 1 : 0;
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
            if (dx * dx + dz * dz > 49.0 && !run.way.isEmpty() && host.getHungerManager().getFoodLevel() > 6 && run.usePos == null) {
                // The next few steps in one line, on one level, with room overhead: she bounds along, as anyone
                // does who wants to get somewhere.
                World world = host.getWorld();
                int ahead = Math.min(5, run.way.size());
                Vec3d first = run.way.get(0);
                Vec3d last = run.way.get(ahead - 1);
                boolean level = run.way.size() >= 3;

                for (int i = 0; i < ahead && level; i++) {
                    Vec3d step = run.way.get(i);
                    double across = Math.abs((last.x - first.x) * (step.z - first.z) - (last.z - first.z) * (step.x - first.x));
                    BlockPos cell = BlockPos.ofFloored(step);
                    // Not across a field: a landing on a bed treads it back into earth.
                    level = Math.abs(step.y - host.getY()) < 0.1 && across < 2.0 && passable(world, cell.up(2)) && solid(world, cell.down()) && world.getFluidState(cell).isEmpty() && !(world.getBlockState(cell.down()).getBlock() instanceof net.minecraft.block.FarmlandBlock) && !(world.getBlockState(cell).getBlock() instanceof net.minecraft.block.FarmlandBlock);
                }

                if (level || run.lineClear >= 3.0) {
                    run.flags |= HOP;
                }
            }
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

    /**
     * The cell the body is standing in. Crouched out over an edge, its middle is already past the block that
     * holds it up: then it is that block's cell, not the air the middle hangs in.
     */
    static BlockPos standing(ServerPlayerEntity host) {
        World world = host.getWorld();
        BlockPos at = host.getBlockPos();
        if (!host.isOnGround() || solid(world, at.down())) {
            return at;
        }

        for (Direction side : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
            BlockPos beside = at.offset(side);
            double off = Math.abs(((double)beside.getX() + 0.5 - host.getX()) * (double)side.getOffsetX() + ((double)beside.getZ() + 0.5 - host.getZ()) * (double)side.getOffsetZ());
            if (solid(world, beside.down()) && off < 0.86) {
                return beside;
            }
        }

        return at;
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
     * Blocks are wanted, for a bridge or to stand on, and the pockets are empty of them: she will dig them. Back from
     * where she stands, a stair down into the ground, a few steps; every step of it is earth or stone in the pockets.
     */
    private static boolean needBlocks(Run run, ServerPlayerEntity host, int count, Direction back) {
        if (run.quarry > 0 || run.ticks < run.quarryAgain || !host.isOnGround() || host.isTouchingWater() || !hasRoom(host)) {
            return false;
        }

        run.quarry = Math.min(48, count);
        run.quarryDir = back;
        run.quarryFrom = host.getBlockPos();
        run.quarryTicks = 0;
        run.quarryLoot = 0;
        run.quarryLootId = -1;
        run.quarryLeft.clear();
        run.quarryHad = fillers(host);
        return true;
    }

    private static boolean quarrying(Run run, ServerPlayerEntity host) {
        run.quarryTicks++;
        run.workAt = run.ticks;
        int have = fillers(host);
        if (have >= run.quarry || run.quarryTicks > 900 || host.getY() < (double)run.quarryFrom.getY() - 9.0 || !hasRoom(host)) {
            return false;
        }

        if (have != run.quarryHad) {
            run.quarryHad = have;
            run.quarryLoot = 0;
        }

        // Digging is not being stuck, however little ground it covers.
        run.stuck = 0;
        // What she has knocked out lies in the step she cut: over it first. Not one that has slid under the ground
        // she stands on, where no walking gets to it, and not one she has already given two seconds to: she used to
        // give it two seconds, look away for a tick, and give it two more, for ever.
        ItemEntity loose = null;
        double nearest = Double.MAX_VALUE;

        for (ItemEntity item : host.getWorld().getEntitiesByClass(ItemEntity.class, host.getBoundingBox().expand(4.0, 2.5, 4.0), x -> x.isAlive() && fillerWorth(x.getStack()) > 0.0 && !run.quarryLeft.contains(x.getId()))) {
            double dx = item.getX() - host.getX();
            double dz = item.getZ() - host.getZ();
            if (dx * dx + dz * dz < 0.7 * 0.7 && item.getY() < host.getY() - 1.2) {
                continue;
            }

            double d = item.squaredDistanceTo(host);
            if (d < nearest) {
                nearest = d;
                loose = item;
            }
        }

        if (loose != null) {
            if (loose.getId() != run.quarryLootId) {
                run.quarryLootId = loose.getId();
                run.quarryLoot = 0;
            }

            if (++run.quarryLoot > 40) {
                run.quarryLeft.add(loose.getId());
            }

            run.way.add(lies(host, loose));
            return true;
        }

        Vec3d down = host.getPos().add((double)run.quarryDir.getOffsetX() * 8.0, -8.0, (double)run.quarryDir.getOffsetZ() * 8.0);
        burrow(run, host, down, run.quarryDir, false);
        return true;
    }

    /**
     * To a thing lying on the ground. One that lies where she cannot step, on the leaves of the tree it fell out of,
     * is not left there: whatever leaf holds it up is knocked out from below and it comes down to her; if it is
     * higher than her arm reaches she goes up to it on blocks of her own.
     */
    static void fetch(Run run, ServerPlayerEntity host, ItemEntity item, int dig) {
        World world = host.getWorld();
        if (item.isOnGround() && item.getY() - host.getY() > 1.3) {
            BlockPos under = BlockPos.ofFloored(item.getX(), item.getY() - 0.3, item.getZ());
            if (world.getBlockState(under).isIn(net.minecraft.registry.tag.BlockTags.LEAVES)) {
                Vec3d eye = host.getEyePos();
                double fx = item.getX() - host.getX();
                double fz = item.getZ() - host.getZ();
                run.workAt = run.ticks;
                if (eye.squaredDistanceTo(Vec3d.ofCenter(under)) <= REACH * REACH) {
                    dig(run, host, under, false);
                    return;
                }

                if (fx * fx + fz * fz > 4.0) {
                    walk(run, host, new Vec3d(item.getX(), host.getY(), item.getZ()), 1.5, dig, true);
                    return;
                }

                if (pillar(run, host) || needBlocks(run, host, (int)(item.getY() - host.getY()) + 2, host.getHorizontalFacing().getOpposite())) {
                    return;
                }
            }
        }

        walk(run, host, lies(host, item), 0.3, dig, false);
    }

    /**
     * One step of a tunnel towards a place: what is in the way of the next cell is broken, then the body steps
     * into it. Rises a block a step when the place is above and sinks a block a step when it is below. A given
     * direction is kept for a straight shaft.
     */
    static void burrow(Run run, ServerPlayerEntity host, Vec3d to, Direction keep, boolean anything) {
        World world = host.getWorld();
        // Over an edge it is the cell that still holds the body up; on a step down of her own stair, the cell she is stepping into.
        BlockPos feet = host.isSneaking() || drop(world, host.getBlockPos()) > 3 ? standing(host) : host.getBlockPos();
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
        boolean bridging = drop(world, feet.offset(dir)) > 3 && hasFiller(host);
        // Off the middle to one side, or short of it: back to the middle. Past the middle the way she is going is
        // not off it, it is on her way. Sent back from there she went forward again and was sent back again, for
        // ever, whenever she was going slowly enough to be caught at it: at a creep towards an edge.
        double past = -(offX * (double)dir.getOffsetX() + offZ * (double)dir.getOffsetZ());
        double aside = offX * offX + offZ * offZ - past * past;
        if (!bridging && (aside > 0.16 || past < -0.4) && passable(world, feet) && passable(world, feet.up())) {
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

                // A gap of one, two or three with ground to land on: she jumps it, with a run at the wider ones.
                if (run.ticks >= run.leapAgain && host.isOnGround() && !host.isTouchingWater()) {
                    for (int i = 2; i <= 4; i++) {
                        BlockPos over = feet.offset(dir, i - 1);
                        BlockPos land = feet.offset(dir, i);
                        if (!passable(world, over) || !passable(world, over.up()) || !passable(world, over.up(2)) || drop(world, over) <= 3) {
                            break;
                        }

                        if (!passable(world, land) || !passable(world, land.up()) || !passable(world, land.up(2))) {
                            break;
                        }

                        int fall = drop(world, land);
                        if (fall <= 1) {
                            int gap = i - 1;
                            BlockPos run1 = feet.offset(dir.getOpposite());
                            BlockPos run2 = feet.offset(dir.getOpposite(), 2);
                            boolean room1 = passable(world, run1) && passable(world, run1.up()) && solid(world, run1.down());
                            boolean room2 = room1 && passable(world, run2) && passable(world, run2.up()) && solid(world, run2.down());
                            boolean deadly = depth(world, over) >= 99;
                            boolean may = gap == 1 || gap == 2 && room1 || gap == 3 && room2 && host.getHungerManager().getFoodLevel() > 6 && !(deadly && hasFiller(host));
                            if (may) {
                                run.leapFrom = feet;
                                run.leapTo = land.down(fall);
                                run.leapDir = dir;
                                run.leapGap = gap;
                                run.leapStage = gap == 1 ? 1 : 0;
                                run.leapTicks = 0;
                                return;
                            }

                            break;
                        }
                    }
                }

                int reach = Math.min(40, fillers(host) + 1);
                int span = 0;

                for (int i = 2; i <= 40 && span == 0; i++) {
                    BlockPos far = feet.offset(dir, i);
                    if (solid(world, far.down()) && !world.getFluidState(far).isIn(FluidTags.LAVA)) {
                        span = i;
                    }
                }

                beyond = span > 0 && span <= reach;
                if (beyond && put(run, host, next.down(), (net.minecraft.item.Item)null) == 0) {
                    run.flags |= SNEAK;
                    run.workAt = run.ticks;
                    return;
                }

                if (span > 0 && !beyond && run.quarry == 0 && Math.abs(dy) <= 3.0 && needBlocks(run, host, span + 1, dir.getOpposite())) {
                    // Ground on the far side and nothing to lay across to it: she goes and digs it.
                    return;
                }

                int far = fathom(world, next);
                if (dy < -3.0 && far >= 4 && (double)far <= -dy + 4.0 && run.ticks >= run.fallAgain && host.isOnGround()) {
                    // A height, and the place is at the foot of it. What the fall would cost her, and what she has
                    // to take the cost away: water already there; a bucket of water or of powder snow, a bale of hay
                    // or a cobweb to put under her feet in the last blocks of the fall; a pearl thrown to the bottom.
                    // A few blocks she simply jumps, if she can spare the hearts. Not a slime block: it throws her back
                    // up, and riding the bounces out went wrong one time in six when it was tried, once to the death.
                    boolean wet = world.getFluidState(next.down(far + 1)).isIn(FluidTags.WATER);
                    boolean cool = HostManager.heat(host) < 60;
                    float soften = 1.0F - Math.min(0.8F, 0.12F * (float)net.minecraft.enchantment.EnchantmentHelper.getLevel(net.minecraft.enchantment.Enchantments.FEATHER_FALLING, host.getEquippedStack(EquipmentSlot.FEET)));
                    float harm = wet || host.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.SLOW_FALLING) ? 0.0F : (float)(far - 3) * soften;
                    net.minecraft.item.Item with = null;
                    if (harm > 0.0F) {
                        if (far >= 6 && far <= 150 && cool && !world.getDimension().ultrawarm() && carries(host, stack -> stack.isOf(Items.WATER_BUCKET))) {
                            with = Items.WATER_BUCKET;
                        } else if (far <= 60 && carries(host, stack -> stack.isOf(Items.POWDER_SNOW_BUCKET))) {
                            with = Items.POWDER_SNOW_BUCKET;
                        } else if (far <= 60 && carries(host, stack -> stack.isOf(Items.HAY_BLOCK)) && harm * 0.2F <= host.getHealth() - 10.0F) {
                            with = Items.HAY_BLOCK;
                        } else if (far <= 60 && carries(host, stack -> stack.isOf(Items.COBWEB))) {
                            with = Items.COBWEB;
                        } else if (far >= 6 && far <= 40 && host.getHealth() >= 12.0F && carries(host, stack -> stack.isOf(Items.ENDER_PEARL))
                            && !(far <= 16 && cool && !world.getDimension().ultrawarm() && carries(host, stack -> stack.isOf(Items.WATER_BUCKET)))) {
                            // Where it will come down, a few blocks out from the wall, has to be the same ground.
                            int out = (int)Math.round(Math.max(3.5, (double)far / 4.5));
                            boolean same = true;

                            for (int k = out - 1; k <= out + 1 && same; k++) {
                                same = Math.abs(fathom(world, feet.offset(dir, k)) - far) <= 2;
                            }

                            if (same) {
                                with = Items.ENDER_PEARL;
                            }
                        }
                    }

                    if (with != null || wet && cool || !wet && harm <= 4.0F && host.getHealth() - harm >= 10.0F) {
                        run.dropEdge = feet;
                        run.dropDir = dir;
                        run.dropDepth = far;
                        run.dropStage = 0;
                        run.dropGone = 0;
                        run.dropTicks = 0;
                        run.dropWith = with;
                        return;
                    }
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

        if (drop(world, goal.offset(dir)) > 3 && !host.isTouchingWater()) {
            // The cell she is stepping into is the last before a drop: into it at a creep, which no edge lets go of.
            run.flags |= SNEAK;
        }

        run.way.add(new Vec3d((double)goal.getX() + 0.5, (double)goal.getY(), (double)goal.getZ() + 0.5));
    }

    /** The body is told to break this block, with the tool for it in the hand. */
    private static void strike(Run run, ServerPlayerEntity host, BlockPos pos) {
        BlockState state = host.getWorld().getBlockState(pos);
        // A crop is cut with what has the most Fortune on it, whatever kind of tool that is.
        int lucky = HostManager.crop(state) ? equip(host, stack -> (double)HostManager.fortune(stack)) : -1;
        run.slot = lucky >= 0 ? lucky : equip(host, stack -> (double)HostManager.worksOn(stack, state));
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
        if (mayBurrow && centre.y - eye.y > 3.2 && flatX * flatX + flatZ * flatZ < 6.3) {
            if (pillar(run, host)) {
                return false;
            }

            // Too high for the arm and nothing to stand on: earth is dug for it.
            if (run.quarry == 0 && !hasFiller(host)) {
                needBlocks(run, host, (int)(centre.y - eye.y) + 1, host.getHorizontalFacing().getOpposite());
            }
        }

        walk(run, host, Vec3d.ofBottomCenter(pos), 0.4, mayBurrow ? ANYTHING : NONE, false);
        return false;
    }

    // ------------------------------------------------------------------ setting blocks down

    /**
     * Whether a hissing creeper has her: it goes off a second and a half after the first hiss unless she is seven
     * blocks away by then, and it stands still while it hisses. If the ticks left are not enough to run that far, the
     * shield goes up instead, while there is still time for it to come up. If they are enough, she runs, and comes
     * back to finish it: a shield that takes the blast leaves her whole and the creeper gone, with a hole in the ground.
     */
    private static boolean caught(Run run, double away) {
        int left = 30 - run.fuse;
        int need = (int)Math.ceil(Math.max(0.0, 7.2 - away) / 0.26);
        return away < 7.2 && left <= need + 3;
    }

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
            || path.equals("sandstone")
            || path.equals("red_sandstone")
            || path.equals("terracotta")
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

        BlockPos feet = standing(host);
        if (face.getAxis().isHorizontal() && against.getY() < feet.getY() && against.getX() == feet.getX() && against.getZ() == feet.getZ() && host.isOnGround()) {
            // Onto the side of the block she stands on, out over the drop. That face is seen only from past the
            // edge: she goes to it crouched, turns her back to the gap and looks down at it between her feet.
            run.way.add(new Vec3d((double)against.getX() + 0.5 + (double)face.getOffsetX() * 0.78, host.getY(), (double)against.getZ() + 0.5 + (double)face.getOffsetZ() * 0.78));
            run.slot = slot;
            run.placeAgainst = against;
            run.placeFace = face;
            run.look = Vec3d.ofCenter(against).add((double)face.getOffsetX() * 0.5, 0.22, (double)face.getOffsetZ() * 0.5);
            run.flags |= SNEAK | BACK;
            return 0;
        }

        ItemStack going = host.getInventory().main.get(slot);
        boolean bodiless = going.getItem() instanceof net.minecraft.item.BlockItem thing && thing.getBlock().getDefaultState().getCollisionShape(world, cell).isEmpty();
        if (!bodiless && (cell.getX() != feet.getX() || cell.getZ() != feet.getZ()) && host.getBoundingBox().intersects(new net.minecraft.util.math.Box(cell))) {
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

    /** Earth a hoe turns into farmland, with nothing on it. */
    static boolean tillable(World world, BlockPos soil) {
        BlockState state = world.getBlockState(soil);
        return (state.isOf(Blocks.DIRT) || state.isOf(Blocks.GRASS_BLOCK) || state.isOf(Blocks.DIRT_PATH)) && world.getBlockState(soil.up()).isAir();
    }

    /**
     * A bed that was trodden back into earth: plain dirt with nothing on it and tilled ground on two sides
     * or more. Earth that merely lies beside a field has it on one side, and is not hers to turn into field.
     */
    static boolean trodden(World world, BlockPos soil) {
        if (!world.getBlockState(soil).isOf(Blocks.DIRT) || !world.getBlockState(soil.up()).isAir()) {
            return false;
        }

        int beds = 0;

        for (Direction side : Direction.Type.HORIZONTAL) {
            beds += world.getBlockState(soil.offset(side)).getBlock() instanceof net.minecraft.block.FarmlandBlock ? 1 : 0;
        }

        return beds >= 2;
    }

    /** A hoe from the pockets on the top of a block of earth. 0 while she is at it, -1 with no hoe to do it with. */
    static int till(Run run, ServerPlayerEntity host, BlockPos soil) {
        int slot = equip(host, stack -> stack.getItem() instanceof net.minecraft.item.HoeItem ? 1.0 : 0.0);
        if (slot < 0) {
            return -1;
        }

        Vec3d point = Vec3d.ofCenter(soil).add(0.0, 0.5, 0.0);
        if (host.getEyePos().squaredDistanceTo(point) > 4.3 * 4.3) {
            walk(run, host, Vec3d.ofBottomCenter(soil.up()), 2.0, NATURAL, false);
            return 0;
        }

        run.slot = slot;
        run.placeAgainst = soil;
        run.placeFace = Direction.UP;
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
        run.workAt = run.ticks;
        return true;
    }

    /** The body is told to fight this creature with the best weapon it carries. */
    /**
     * What she has learned of one man, kept from fight to fight for as long as the server runs: how often she has
     * lowered her shield to strike at him, and how often he made her pay for it. Not how often he has caught her
     * by dropping his own shield: that makes her stand off, standing off wins nothing, and carried from one fight
     * into the next it turned fights she was winning into fights nobody won.
     */
    static final class Habit {
        int opens;
        int punished;

        /** He has made her pay for at least half of the times she opened. */
        boolean punishes() {
            return this.opens >= 2 && this.punished * 2 >= this.opens;
        }

        /** Three times and never once. */
        boolean lets() {
            return this.opens >= 3 && this.punished == 0;
        }
    }

    private static final Map<UUID, Habit> HABITS = new HashMap<>();

    static Habit habit(LivingEntity foe) {
        return HABITS.computeIfAbsent(foe.getUuid(), id -> new Habit());
    }

    static void fight(Run run, ServerPlayerEntity host, LivingEntity foe) {
        double away = (double)host.distanceTo(foe);
        boolean sees = host.canSee(foe);
        boolean duel = foe instanceof PlayerEntity;
        if (duel) {
            // A fight with a player, whatever she does this tick of it: a blow, a bite, a step back. It is all in the record.
            run.duelAt = run.ticks;
            run.duelFoe = foe.getId();
        }

        // Where it is going: by where it was a tick ago, since nobody tells the server how fast a player means to move.
        if (run.foeId != foe.getId() || run.foeWas == null) {
            run.foeId = foe.getId();
            run.foeWas = foe.getPos();
            run.foeSpeed = Vec3d.ZERO;
            run.apart = 0;
            run.noWay = false;
            run.fuse = 0;
            run.holdFor = 0;
            run.bottleSlot = -1;
            run.flashHits = 0;
            run.shieldUp = 0;
            run.blow = 0.0F;
            run.feinting = false;
            run.openAt = -100;
            if (run.counterFor != foe.getId()) {
                // The blow she was about to answer was another man's: this one has not swung.
                run.counter = false;
            }
        } else {
            run.foeSpeed = run.foeSpeed.multiply(0.6).add(foe.getPos().subtract(run.foeWas).multiply(0.4));
            run.foeWas = foe.getPos();
        }

        run.strikeIn = 99;
        if (foe instanceof PlayerEntity him) {
            // What he is doing right now. His arm: a swing shows as the bar dropping, and until it fills again his blow is a weak one.
            float cool = him.getAttackCooldownProgress(0.0F);
            if (cool < run.foeCool - 0.3F) {
                // He has swung. If her own arm is ready, this is the moment that is hers.
                // Hers only if his blow went into the shield or the air: thrown back by it, she is in no place to answer.
                run.foeSwungAt = run.ticks;
                run.counter = host.getAttackCooldownProgress(0.0F) >= 0.8F && host.getHealth() + host.getAbsorptionAmount() >= run.hostHealth - 0.01F;
                run.counterFor = foe.getId();
            }

            if (host.getAttackCooldownProgress(0.0F) < 0.5F || run.ticks - run.foeSwungAt > 7) {
                run.counter = false;
            }

            // A beating: his blows landing one after another with none of hers in between.
            float mine = host.getHealth() + host.getAbsorptionAmount();
            if (mine < run.hostHealth - 0.01F) {
                if (run.ticks - run.guardSeenAt <= 6) {
                    run.flashHits++;
                }

                if (run.ticks - run.openAt <= 14) {
                    // She had lowered her shield to strike, and he struck first or struck back: that is counted against the next time.
                    habit(foe).punished++;
                    run.openAt = -100;
                }

                run.blow = Math.max(run.blow, run.hostHealth - mine);
                run.taken = run.ticks - run.takenAt <= 60 ? run.taken + 1 : 1;
                run.takenAt = run.ticks;
            }

            if (run.foeHp >= 0.0F && him.getHealth() < run.foeHp - 0.01F || run.ticks - run.takenAt > 70) {
                run.taken = 0;
            }

            run.foeHp = him.getHealth();
            run.foeCool = cool;
            run.hostHealth = host.getHealth() + host.getAbsorptionAmount();
            run.foeClosing = run.foeAway < 0.0 ? 0.0 : run.foeClosing * 0.6 + (run.foeAway - away) * 0.4;
            run.foeAway = away;
            if (him.isBlocking() || him.isUsingItem() && him.getActiveItem().getItem() instanceof net.minecraft.item.ShieldItem) {
                run.guardSeenAt = run.ticks;
                run.shieldUp++;
            } else {
                run.shieldUp = 0;
            }

            int arm = Math.max(0, (int)Math.ceil((double)((0.9F - cool) * him.getAttackCooldownProgressPerTick())));
            double gap = away - 3.7;
            // His feet: by how fast the gap has been closing; and a man at a run straight at her is taken to keep running.
            Vec3d toward = host.getPos().subtract(him.getPos());
            boolean charging = him.isSprinting() && toward.lengthSquared() > 0.01 && him.getRotationVec(1.0F).dotProduct(toward.normalize()) > 0.7;
            double closing = Math.max(run.foeClosing, charging ? 0.3 : 0.0);
            int legs = gap <= 0.0 ? 0 : (closing > 0.03 ? (int)(gap / closing) : 99);
            Vec3d toHer = host.getPos().subtract(him.getPos());
            boolean faces = toHer.lengthSquared() < 0.01 || him.getRotationVec(1.0F).dotProduct(toHer.normalize()) > 0.1;
            boolean armed = !him.isUsingItem() || him.getActiveItem().getItem() instanceof net.minecraft.item.ShieldItem;
            run.strikeIn = faces && armed ? Math.max(arm, legs) : 99;
        }

        if (duel && LOG && run.ticks % 2 == 0) {
            com.windykindy49.windyorigins.WindyOrigins.LOGGER.info("PILOT duel t {} away {} in {} arm {} his {} hp {} his {} use {} block {} sprint {} hurt {} his {} flags {} hand {} at {} him {} way {}", run.ticks, String.format("%.1f", away), run.strikeIn,
                String.format("%.2f", host.getAttackCooldownProgress(0.0F)), String.format("%.2f", run.foeCool), String.format("%.1f", host.getHealth()), String.format("%.1f", foe.getHealth()), host.isUsingItem(), host.isBlocking(), host.isSprinting(), host.hurtTime, foe.hurtTime, run.lastFlags, host.getMainHandStack().getItem(), String.format("%.1f,%.1f,%.1f", host.getX(), host.getY(), host.getZ()), String.format("%.1f,%.1f,%.1f", foe.getX(), foe.getY(), foe.getZ()), run.lastWay);
        }


        if (duel) {
            // The off hand: a shield while the fight is blade to blade; what brings the body back from the dead when it
            // is going badly, when there are crystals about, or when there is no shield to hold there.
            boolean crystals = foe.getMainHandStack().isOf(Items.END_CRYSTAL) || foe.getOffHandStack().isOf(Items.END_CRYSTAL) || foe.getMainHandStack().isOf(Items.RESPAWN_ANCHOR)
                || carries(host, stack -> stack.isOf(Items.END_CRYSTAL)) || !host.getWorld().getEntitiesByClass(net.minecraft.entity.decoration.EndCrystalEntity.class, host.getBoundingBox().expand(9.0), x -> x.isAlive()).isEmpty();
            boolean hasShield = shielded(host) || carries(host, stack -> stack.getItem() instanceof net.minecraft.item.ShieldItem);
            boolean wantTotem = crystals || host.getHealth() <= 7.0F || !hasShield;
            if (!wantTotem && host.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
                PlayerInventory inventory = host.getInventory();

                for (int i = 0; i < inventory.main.size(); i++) {
                    if (inventory.main.get(i).getItem() instanceof net.minecraft.item.ShieldItem) {
                        ItemStack was = host.getOffHandStack();
                        inventory.offHand.set(0, inventory.main.get(i));
                        inventory.main.set(i, was);
                        inventory.markDirty();
                        host.playerScreenHandler.sendContentUpdates();
                        break;
                    }
                }
            }

            if (!wantTotem && !hasShield && !host.getOffHandStack().isOf(Items.COBWEB) || wantTotem && !hasShield && !host.getOffHandStack().isOf(Items.COBWEB) && !carries(host, stack -> stack.isOf(Items.TOTEM_OF_UNDYING)) && !host.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
                // Nothing better for the off hand: webs go there, to be set down without letting go of the blade.
                PlayerInventory inventory = host.getInventory();

                for (int i = 0; i < inventory.main.size(); i++) {
                    if (inventory.main.get(i).isOf(Items.COBWEB)) {
                        ItemStack was = host.getOffHandStack();
                        inventory.offHand.set(0, inventory.main.get(i));
                        inventory.main.set(i, was);
                        inventory.markDirty();
                        host.playerScreenHandler.sendContentUpdates();
                        break;
                    }
                }
            }

            if (wantTotem && !host.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
                PlayerInventory inventory = host.getInventory();

                for (int i = 0; i < inventory.main.size(); i++) {
                    if (inventory.main.get(i).isOf(Items.TOTEM_OF_UNDYING)) {
                        ItemStack was = host.getOffHandStack();
                        inventory.offHand.set(0, inventory.main.get(i));
                        inventory.main.set(i, was);
                        inventory.markDirty();
                        host.playerScreenHandler.sendContentUpdates();
                        break;
                    }
                }
            }

            // Too far to run down: a pearl at their feet.
            if (sees && away > 14.0 && away < 46.0 && run.ticks - run.pearlAt > 50 && carries(host, stack -> stack.isOf(Items.ENDER_PEARL))) {
                Vec3d spot = foe.getPos().add(run.foeSpeed.multiply(Math.min(30.0, away / 1.3)));
                pearl(run, host, spot);
                return;
            }

            if (sees && away < 6.5 && crystal(run, host, foe)) {
                return;
            }

            if (run.second != null && shielded(host) && host.distanceTo(run.second) < 9.0F && away < 9.0
                && host.getItemCooldownManager().getCooldownProgress(host.getOffHandStack().getItem(), 0.0F) > 0.15F) {
                // Two on her, and an axe has knocked her shield down for five seconds. Those five seconds are not
                // fought: she is off at a run, away from the pair of them, and comes back when it is a shield again.
                Vec3d mid = foe.getPos().add(run.second.getPos()).multiply(0.5);
                Vec3d to = away(host, host.getPos().subtract(mid), 10.0);
                if (to != null) {
                    run.attackId = -1;
                    walk(run, host, surface(host.getWorld(), to.x, to.z, host.getY()), 1.0, NONE, true);
                    run.flags |= SPRINT | HOP;
                    return;
                }
            }

            if (scorch(run, host, foe, away, sees)) {
                return;
            }

            if (webs(run, host, foe, away, sees)) {
                return;
            }

            if (bottles(run, host, foe, away, sees)) {
                return;
            }

            // Eating takes a second and a half, and a player covers a dozen blocks in that. She eats only when he
            // cannot be on her before she is done: far enough off at the best pace a player makes, the further for a
            // web he is caught in or a meal of his own, or with no way to her at all. If he turns out quicker than
            // that she stops with the mouth full and fights: an apple half eaten is better than a blow taken eating.
            boolean biting = host.isUsingItem() && (host.getActiveItem().isFood() || host.getActiveItem().isOf(Items.POTION) || host.getActiveItem().isOf(Items.MILK_BUCKET));
            int eta = run.noWay ? 999 : (int)(Math.max(0.0, away - 3.3) / Math.max(0.36, run.foeClosing)) + (webbed(foe) ? 13 : 0)
                + (foe.isUsingItem() && foe.getActiveItem().isFood() ? foe.getItemUseTimeLeft() : 0);
            int left = biting ? host.getItemUseTimeLeft() : 32;
            boolean clear = eta >= left + 3;
            // If he can be on her while she eats, it is a sum. What one of his blows costs her: the worst she has had
            // from him, or before the first, what his weapon would do through her armour jumped into. How many he
            // lands before she is done. If one or two would finish her she does not eat, whatever she is holding. If
            // what is left after them is still most of half her health, and the thing is a golden apple, she eats
            // through them, as anyone in good armour does.
            float blow = blow(run, host, foe);
            float have = host.getHealth() + host.getAbsorptionAmount();
            float missing = host.getMaxHealth() - host.getHealth();
            // Hurt enough to mend: four tenths of her health gone, or within two and a half of his blows of the end
            // with something worth putting back. Not while the last apple is still working.
            boolean hurt = !host.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.REGENERATION) && (host.getHealth() <= host.getMaxHealth() * 0.6F || have <= blow * 2.5F && missing >= 4.0F);
            boolean danger = have <= Math.max(6.0F, blow * 2.0F + 0.5F);
            int hits = eta >= left ? 0 : 1 + (int)((float)(left - eta) / Math.max(5.0F, foe instanceof PlayerEntity swinger ? swinger.getAttackCooldownProgressPerTick() : 20.0F));
            boolean golden = carries(host, stack -> stack.isOf(Items.GOLDEN_APPLE) || stack.isOf(Items.ENCHANTED_GOLDEN_APPLE));
            boolean afford = golden && have > blow * 2.0F + 0.5F && have - (float)hits * blow >= Math.max(6.0F, host.getMaxHealth() * 0.4F);
            boolean time = clear || afford;
            if (biting && time && (clear || host.getActiveItem().isOf(Items.GOLDEN_APPLE) || host.getActiveItem().isOf(Items.ENCHANTED_GOLDEN_APPLE))) {
                // What is in the mouth is finished.
                run.slot = host.getInventory().selectedSlot;
                run.attackId = -1;
                run.way.clear();
                run.look = foe.getEyePos();
                run.flags |= USE;
                return;
            }

            if (hurt && time && (clear ? carries(host, stack -> aidWorth(stack) > 0.0) : golden)) {
                // Which: the plain golden apple as a rule. The enchanted one, four times as much, is kept for when the
                // end is two blows off, or there is nothing else. A bottle of healing drunk, with time for it, when
                // what it gives is what is missing. Bread last: it only feeds the slow mending.
                boolean plain = carries(host, stack -> stack.isOf(Items.GOLDEN_APPLE));
                int apple = equip(host, stack -> stack.isOf(Items.ENCHANTED_GOLDEN_APPLE) ? (danger || !plain ? 1000.0 : 300.0)
                    : (stack.isOf(Items.GOLDEN_APPLE) ? 500.0 : (!clear ? 0.0 : (stack.isOf(Items.POTION) ? (mendNow(host, stack, danger) ? 800.0 : 0.0) : aidWorth(stack)))));
                if (apple >= 0) {
                    run.slot = apple;
                    run.attackId = -1;
                    run.way.clear();
                    run.look = foe.getEyePos();
                    if (host.getInventory().selectedSlot == apple) {
                        run.flags |= USE;
                    }

                    return;
                }
            }

            if (clear && eta >= 40) {
                // He is far off, or cannot get to her: time for what is drunk before a fight. Milk if what is on her
                // is worse than what it would wash off; then the bottles that make the arm heavier and the legs faster.
                boolean milk = sour(host);
                int sip = equip(host, stack -> milk && stack.isOf(Items.MILK_BUCKET) ? 50.0 : (draught(stack, host, foe) ? 30.0 : 0.0));
                if (sip >= 0) {
                    run.slot = sip;
                    run.attackId = -1;
                    run.way.clear();
                    run.look = foe.getEyePos();
                    if (host.getInventory().selectedSlot == sip) {
                        run.flags |= USE;
                    }

                    return;
                }
            }
        }

        if (sees && away > 7.0 && away < 40.0 && hasBow(host) && !(foe instanceof net.minecraft.entity.mob.EndermanEntity)) {
            // Too far for the arm, near enough for an arrow.
            shoot(run, host, foe);
            return;
        }

        // A creature on its feet that she cannot get to is got to the same way a player is. Not what flies.
        if (duel || foe.isOnGround() && !(foe instanceof net.minecraft.entity.mob.FlyingEntity) && !(foe instanceof net.minecraft.entity.Flutterer) && !(foe instanceof CreeperEntity)) {
            run.apart = sees && away <= 3.6 ? 0 : run.apart + 1;
            if (run.apart <= 50) {
                run.noWay = false;
                run.siegeCheck = 0;
            } else if (away < 24.0 && siege(run, host, foe, away, sees)) {
                return;
            }
        }

        // The weapon for this fight: the most harm a second; a sword when they come in a crowd, for the sweep of it;
        // an axe when the other holds a shield up, to knock it down.
        boolean crowd = host.getWorld().getEntitiesByClass(MobEntity.class, host.getBoundingBox().expand(3.5, 2.0, 3.5), x -> x.isAlive() && x.getTarget() == host).size() >= 2;
        // An axe for as long as he fights from behind a shield, not for as long as it happens to be up: every change
        // of what is in the hand sets the arm back to nothing.
        boolean hisShield = foe.getOffHandStack().getItem() instanceof net.minecraft.item.ShieldItem || foe.getMainHandStack().getItem() instanceof net.minecraft.item.ShieldItem;
        boolean hisDown = foe instanceof PlayerEntity player && player.getItemCooldownManager().isCoolingDown(Items.SHIELD);
        // One who has caught her by dropping his shield and striking in the same breath is not walked up to with an
        // axe again: that is what he is waiting for. Him she stands off, a step out of reach, and strikes when the
        // shield comes down. That is with a shield of her own to stand behind, where she loses nothing by waiting.
        // With none the axe is all there is, and standing off with a sword only got her struck where she stood: she
        // goes on taking the axe to him.
        // Nor is it for a man who has an axe in his own hand: he is coming for her shield, not waiting behind his,
        // and standing still behind hers is the one thing not to do about that.
        boolean wary = duel && run.flashHits > 0 && shielded(host) && !host.getItemCooldownManager().isCoolingDown(host.getOffHandStack().getItem())
            && !(foe.getMainHandStack().getItem() instanceof net.minecraft.item.AxeItem);
        boolean guarded = duel ? hisShield && !hisDown && run.ticks - run.guardSeenAt < 200 && !wary : foe.isBlocking();
        // Blade to blade in the open, each blow throws the two of them apart and it is a second before they meet again:
        // what counts then is how hard one blow lands, not how many the arm could manage. From behind a shield, where
        // she answers every blow of his, it is the quick weapon again.
        boolean open = duel && !(shielded(host) && !host.getItemCooldownManager().isCoolingDown(host.getOffHandStack().getItem()));
        // What is in the hand is changed only when it costs nothing: straight after a blow of her own, when the arm
        // is spent anyway, or with him too far off to matter. A ready arm is never thrown away for a better blade,
        // unless the blade in it is no use at all: a sword against a shield that is up.
        ItemStack held = host.getMainHandStack();
        boolean keep = duel && away < 8.0 && host.getAttackCooldownProgress(0.0F) >= 0.25F && weaponWorth(held) > 0.0 && !(guarded && !(held.getItem() instanceof net.minecraft.item.AxeItem))
            && !(wary && foe.isBlocking() && held.getItem() instanceof net.minecraft.item.AxeItem);
        run.slot = keep ? host.getInventory().selectedSlot : equip(host, stack -> {
            double worth = open ? blowWorth(stack) : weaponWorth(stack);
            if (crowd && stack.getItem() instanceof net.minecraft.item.SwordItem) {
                worth *= 1.35;
            }

            if (guarded && stack.getItem() instanceof net.minecraft.item.AxeItem) {
                worth *= 3.0;
            }

            if (wary && foe.isBlocking() && stack.getItem() instanceof net.minecraft.item.AxeItem) {
                worth *= 0.2;
            }

            return worth;
        });
        run.attackId = foe.getId();
        if (crowd || foe instanceof CreeperEntity) {
            // Feet on the ground for a sweep: no jumping into the blow. Nor at a creeper: the jump takes longer than the hiss allows.
            run.flags |= SWEEP;
        }

        if (duel) {
            boolean cover = shielded(host) && !host.getItemCooldownManager().isCoolingDown(host.getOffHandStack().getItem());
            int mine = Math.max(0, (int)Math.ceil((double)((0.95F - host.getAttackCooldownProgress(0.0F)) * host.getAttackCooldownProgressPerTick())));
            // Two blows. From the ground at a run, the moment the arm is ready: it is the run behind it that throws him
            // out of reach. Or a jump into it, landed on the way down, which is half as much harm again but takes
            // seven ticks off the ground and is not at a run. The jump whenever his own blow cannot come first: he is
            // behind her shield's reach, his arm is spent, he is eating, he is in a web. The run otherwise.
            int air = Math.max(mine, 7);
            boolean hisReady = (foe.getOffHandStack().getItem() instanceof net.minecraft.item.ShieldItem || foe.getMainHandStack().getItem() instanceof net.minecraft.item.ShieldItem)
                && !(foe instanceof PlayerEntity owner && owner.getItemCooldownManager().isCoolingDown(Items.SHIELD));
            // A shield lowered is a shield again a quarter of a second later, and the jump takes longer than that:
            // at a man with a shield to raise the blow goes in from the ground, at once.
            // Having a shield of her own is no reason to jump: it is down for as long as she is in the air, and a man
            // with a ready arm who has been waiting for that has all the time he needs.
            boolean leap = !foe.isBlocking() && !hisReady && !host.isTouchingWater() && !host.isClimbing() && !webbed(host) && !aloft(foe)
                // With nothing to stand behind and his blow coming first whatever she did, she used to jump so that
                // hers should at least be the heavy one. Struck in the air she is thrown twice as far, and the heavy
                // blow lands on nothing: it is the ground blow there too.
                && (run.counter || run.strikeIn >= air + 2 || webbed(foe) || foe.isUsingItem());
            // An edge. With a drop or lava close behind him, the blow that counts is the one that throws: from the
            // ground, at a run, and never mind that the other is half as heavy again. An edge behind her changes
            // nothing in how she strikes: keeping her feet on the ground for it was tried, and without the heavy blow
            // she lost more rounds than the edge ever cost her.
            double flatX = foe.getX() - host.getX();
            double flatZ = foe.getZ() - host.getZ();
            double flat = Math.sqrt(flatX * flatX + flatZ * flatZ);
            run.hisBrink = flat > 0.3 && away < 6.0 && !foe.isBlocking() && brink(host.getWorld(), foe.getPos(), flatX / flat, flatZ / flat);
            if (run.hisBrink || run.pair) {
                // Nor between two: in the air her shield is down and there is one of them to either side of it.
                leap = false;
            }

            run.flags |= leap ? CRIT : SWEEP;
            if (host.getAttackCooldownProgress(0.0F) >= 0.9F && (run.strikeIn >= 4 || !cover) || !cover && mine <= run.strikeIn + 2 || run.hisBrink && !cover && mine <= 2) {
                // Her arm is ready: straight in, no circling. Each sees the other a moment late, so of two who close
                // it is the one still coming on who gets the blow in first, and the one who slows who takes it.
                run.flags |= PRESS;
            }
        }

        // Counted the way the creeper counts it: a tick up for every tick it hisses, a tick down for every tick it does not.
        run.fuse = foe instanceof CreeperEntity hissing ? (hissing.getFuseSpeed() > 0 ? run.fuse + 1 : Math.max(0, run.fuse - 1)) : 0;
        if (shielded(host) && sees) {
            // The shield goes up at what shoots, at a creeper about to go off, and between one swing and the next.
            // Up while the bow is drawn at her; down, and in at a run, while it is being nocked again.
            boolean shoots = foe instanceof net.minecraft.entity.ai.RangedAttackMob && away > 2.5 && (foe.isUsingItem() || !(foe instanceof MobEntity));
            boolean fuse = foe instanceof CreeperEntity lit && lit.getFuseSpeed() > 0 && caught(run, away);
            // Against a player it goes up for the blow he is about to land while her own arm is not ready, and comes
            // down the moment it is: his blade on the shield, hers on him.
            // A player she meets shield first: up for the blow he is about to land, whatever her own arm is doing,
            // since he sees her a moment sooner than she sees him. Down the instant his blow has gone by, hers in
            // while his arm is still coming back, and up again. If he stands there and will not swing, she does.
            // That is for a man who stands in the open. One behind a shield is not standing there: he is waiting for
            // hers to come down, and the wait is not counted against him. It used to be, so that the moment he
            // dropped his shield to strike she dropped hers to strike too, and his blow was the one already on its way.
            boolean hisUp = foe.isBlocking() || foe.isUsingItem() && foe.getActiveItem().getItem() instanceof net.minecraft.item.ShieldItem;
            if (hisUp) {
                run.guardFor = 0;
            }

            // And when she does, it is after a wait he cannot count: four to seven seconds, not the same second and
            // a half every time, which a patient man learns and is ready for.
            // And by what he has done about it before: with a man who has made her pay for it she waits twice as
            // long, and with one who never has, a second and a half is enough.
            Habit known = duel ? habit(foe) : new Habit();
            if (run.guardFor == 0) {
                run.pokeAfter = known.punishes() ? 160 + host.getRandom().nextInt(120) : (known.lets() ? 30 + host.getRandom().nextInt(30) : 80 + host.getRandom().nextInt(60));
            }

            // With a second man on her the shield is not lowered for anything but an answer.
            boolean poke = run.guardFor > run.pokeAfter && !hisUp && !run.pair;
            if (poke && run.ticks - run.openAt > 40 && duel) {
                run.openAt = run.ticks;
                known.opens++;
            }

            // Her answer to a blow of his is for a blow struck within reach of her. One struck at the air from further
            // off is an invitation to lower the shield and walk into the next.
            // With a second man on her, only if his own blow cannot land before the shield is a shield again: down,
            // the blow, up, and the quarter of a second a raised shield takes to count. And she turns to the one she
            // answers before she lowers it, since between two she was looking at neither.
            boolean answer = run.counter && away < 4.2 && !(run.pair && run.secondIn < 8);
            run.turning = run.pair && answer && !faces(host, foe, 25.0);
            boolean between = duel
                ? (run.strikeIn <= 12 && away < 9.0 || run.pair && run.secondIn <= 12) && (!answer || run.turning) && !poke && !host.getItemCooldownManager().isCoolingDown(host.getOffHandStack().getItem())
                : away < 3.8 && host.getAttackCooldownProgress(0.0F) < 0.7F;
            if (duel && foe.isBlocking() && !wary && host.getMainHandStack().getItem() instanceof net.minecraft.item.AxeItem) {
                // The axe is in her hand and his shield is up. Behind it he cannot strike first, but he can drop it and
                // strike in less time than she takes to cross the last two blocks. So she crosses them behind her own
                // shield, and lowers it only where the axe reaches and only when the arm is at its full: a blow of his
                // on the way lands on it, and hers is out the tick after her shield is down.
                boolean full = host.getAttackCooldownProgress(-1.0F) >= 1.0F;
                if (away < 5.0) {
                    // Not with a second man on her. One behind a shield with a ready arm answers the axe with his blade,
                    // and that is a trade; with two of them to her one, trades are how she loses.
                    between = !(full && away <= 3.0 && !run.pair) && !answer && !host.getItemCooldownManager().isCoolingDown(host.getOffHandStack().getItem());
                } else if (host.getAttackCooldownProgress(0.0F) >= 0.9F && !run.pair) {
                    between = false;
                }

                if (host.getAttackCooldownProgress(0.0F) >= 0.9F) {
                    run.flags |= PRESS;
                }
            }

            run.guardFor = hisUp ? 0 : (duel && between && away < 3.6 ? run.guardFor + 1 : (poke && run.guardFor < run.pokeAfter + 8 ? run.guardFor + 1 : 0));

            if (duel && (answer || poke)) {
                run.flags |= PRESS;
            }

            shoots |= duel && foe.isUsingItem() && (foe.getActiveItem().getItem() instanceof net.minecraft.item.BowItem || foe.getActiveItem().getItem() instanceof net.minecraft.item.CrossbowItem || foe.getActiveItem().getItem() instanceof net.minecraft.item.TridentItem) && away > 2.5;
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

        if (foe instanceof CreeperEntity creeper && away < 8.0 && (creeper.getFuseSpeed() > 0 || run.fuse > 2)) {
            // It hisses for a second and a half before it goes off, and that is time for a blow. She used to back off
            // at the first hiss, it calmed, she came back, and so round for ever. Now: with the arm ready and the hiss
            // young she strikes, at a run so that it is thrown back, and then gets clear until it calms. A shield is
            // for when she cannot get clear in time.
            boolean guard = shielded(host) && !host.getItemCooldownManager().isCoolingDown(host.getOffHandStack().getItem());
            boolean ready = host.getAttackCooldownProgress(0.0F) >= 0.9F;
            // One that has stopped hissing has not forgotten: its fuse runs down as slowly as it ran up, and it takes
            // up where it left off. She keeps off until it has run right down, and goes in on a fresh one. And the
            // blow is for the first third of a second of the hiss only: after that there is no getting clear in time.
            boolean hissing = creeper.getFuseSpeed() > 0;
            if (!hissing || run.fuse >= 8 || !ready) {
                Vec3d back = away(host, host.getPos().subtract(foe.getPos()), 7.0);
                if (guard && (back == null || hissing && caught(run, away))) {
                    // No getting clear in time, or nowhere to go: the shield takes it.
                    run.attackId = -1;
                    run.look = foe.getEyePos();
                    run.flags |= USE;
                    return;
                }

                if (back != null) {
                    run.attackId = -1;
                    run.way.add(back);
                    run.flags |= SPRINT;
                    return;
                }
            }
        }

        boolean behind = shielded(host) && !host.getItemCooldownManager().isCoolingDown(host.getOffHandStack().getItem());
        Vec3d off = host.getPos().subtract(foe.getPos()).multiply(1.0, 0.0, 1.0);
        boolean room = off.lengthSquared() > 0.01 && solid(host.getWorld(), BlockPos.ofFloored(host.getPos().add(off.normalize().multiply(1.2))).down()) && solid(host.getWorld(), BlockPos.ofFloored(host.getPos().add(off.normalize().multiply(2.4))).down())
            && passable(host.getWorld(), BlockPos.ofFloored(host.getPos().add(off.normalize().multiply(1.2))));
        if (duel && !behind && webbed(foe) && host.getAttackCooldownProgress(0.0F) < 0.9F && away < 3.7 && room) {
            // He is caught and slow, and her arm is not ready: out of his reach until it is. A web is time, no more.
            run.flags &= ~PRESS;
            run.flags |= KITE | DUEL;
            return;
        }

        if (duel && run.ticks - run.feintAt < 30 && !foe.isBlocking() && away < 5.8 && host.getAttackCooldownProgress(0.0F) >= 0.75F) {
            // He has dropped his shield and come for the arm she showed him spent, and it is all but ready again. At
            // him, from the ground and at a run: a shield lowered takes a quarter of a second to be a shield again,
            // and her blow is there first.
            run.flags &= ~(USE | CRIT | KITE | HOLD);
            run.flags |= PRESS | SWEEP | SPRINT | DUEL;
        }

        if (duel && behind && hisShield && !hisDown && !foe.isBlocking() && run.ticks - run.guardSeenAt <= 20 && away < 5.5 && run.foeCool >= 0.8F
            && !(run.counter && away < 4.2) && run.ticks - run.feintAt >= 30 && !(foe.isUsingItem() && !(foe.getActiveItem().getItem() instanceof net.minecraft.item.ShieldItem))) {
            // He was behind his shield a moment ago, it is down, his arm is ready and he is within a lunge of her: the
            // blow he lowered it for is already on its way, and nothing of hers gets there first. Her shield stays
            // up and takes it. Then his arm is spent and his shield is a quarter of a second from being a shield
            // again, and her blow is in before it is.
            run.flags |= USE | HOLD | DUEL;
            run.flags &= ~(PRESS | CRIT | KITE);
            return;
        }

        if (duel && foe.isBlocking() && !(host.getMainHandStack().getItem() instanceof net.minecraft.item.AxeItem)) {
            // He stands behind his shield and waits for her blade to bounce off it: for the half second after a blow
            // her arm is spent and her own shield is not yet up, and that is when he strikes. So she does not give it
            // to him. A shield covers only what is in front of it: she goes round him a step beyond his reach, one
            // way for a while, and from behind it the blow goes in. And with a shield of her own she offers him what
            // he is waiting for: a blow at the air from well out of his reach, her shield up straight after it. He
            // drops his to punish it, steps in, and his blow goes into her shield with his own down.
            Vec3d front = host.getPos().subtract(foe.getPos()).multiply(1.0, 0.0, 1.0);
            if (front.lengthSquared() < 0.01 || foe.getRotationVec(1.0F).multiply(1.0, 0.0, 1.0).normalize().dotProduct(front.normalize()) > -0.15) {
                boolean mine = shielded(host) && !host.getItemCooldownManager().isCoolingDown(host.getOffHandStack().getItem());
                float arm = host.getAttackCooldownProgress(0.0F);
                run.holdFor++;
                run.flags |= HOLD | DUEL;
                run.flags &= ~(PRESS | CRIT | KITE);
                if (run.feinting && arm < 0.5F) {
                    run.feinting = false;
                    run.feintAt = run.ticks;
                }

                // The moment he chooses he can drop his shield and strike, and his blow is there before hers can be.
                // So with a shield of her own she stands behind it, just at his reach: his blow goes into it, and hers
                // is in before his shield is a shield again. With none she keeps out of a lunge's reach altogether,
                // five blocks off: to get at her he has to come without his shield, and then the blow is hers to time.
                if (mine) {
                    run.flags |= USE;
                }

                if (mine && run.holdFor > 100 && run.ticks - run.feintAt > 120 && arm >= 1.0F && run.ticks >= run.feintOff && !run.pair) {
                    // The blow at the air is struck from six blocks off. He needs most of a second to cover that with
                    // his shield down, which is what her arm needs to be ready again. She backs off to it behind her
                    // shield while he is near, and at a walk once he is not.
                    if (away < 6.3) {
                        if (room) {
                            run.flags |= KITE;
                            if (away > 4.4) {
                                run.flags &= ~USE;
                            }

                            // He walks after her as fast as she backs away: there is no six blocks to be had from
                            // him, and all she is doing is being walked to the wall. No more of it for half a minute.
                            if (++run.feintKite > 60 && away < 5.0) {
                                run.feintKite = 0;
                                run.feintOff = run.ticks + 600;
                            }
                        }
                    } else if (away < 7.4) {
                        run.feintKite = 0;
                        run.feinting = true;
                        run.flags |= FEINT;
                        run.flags &= ~USE;
                    }
                }

                return;
            }
        }

        run.holdFor = 0;
        run.feinting = false;
        if (duel && aloft(foe) && !(shielded(host) && !host.getItemCooldownManager().isCoolingDown(host.getOffHandStack().getItem())) && room && away < 5.2 && host.getAttackCooldownProgress(0.0F) < 0.9F) {
            // He has jumped into his blow, which lands on the way down and counts half as much again. With the arm
            // ready she would have struck him out of the air. It is not: she steps back, and he comes down short.
            run.flags &= ~(PRESS | CRIT);
            run.flags |= KITE | DUEL;
            return;
        }

        if (!duel && crowd && !(foe instanceof CreeperEntity)) {
            // Several on her at once. A blow, and a step back while the arm comes round, eyes on them: they string out
            // in front of her, where a sword sweeps and a shield covers, and none of them gets behind.
            run.flags |= DUEL;
            if (room && away < 3.0 && host.getAttackCooldownProgress(0.0F) < 0.8F) {
                run.flags |= KITE;
                return;
            }
        }

        int late = (int)Math.ceil((double)((0.95F - host.getAttackCooldownProgress(0.0F)) * host.getAttackCooldownProgressPerTick())) - run.strikeIn;
        if (duel && !behind && room && run.strikeIn <= 5 && away < 5.5 && late > 3) {
            // No shield to stand behind, his blow due and hers a good while off: backwards, eyes on him, so that he is
            // slow to arrive and her arm is ready when he does. When hers is nearly ready too she goes in instead.
            run.flags |= KITE | DUEL;
            return;
        }

        if (away > 3.2 || !host.canSee(foe)) {
            // Not at where it stands but at where it will be by the time the body gets there.
            Vec3d meet = foe.getPos().add(run.foeSpeed.multiply(Math.min(16.0, away / 0.28)).multiply(1.0, 0.0, 1.0));
            walk(run, host, away > 6.0 ? meet : foe.getPos(), 1.2, NONE, false);
        }

        if (away > 8.0 || away > 3.2 && foe instanceof net.minecraft.entity.ai.RangedAttackMob || duel) {
            run.flags |= SPRINT;
        }

        if (duel) {
            run.flags |= DUEL;
        }
    }

    /**
     * A man who has put himself where a blade does not reach: up on a column, or shut in behind a wall. If there is
     * a way to him on foot she takes it, and this is not called for. If there is none: an arrow, when she has a bow
     * and can see him; the block he stands on, broken from under him; a way cut through what he hides behind; and,
     * when what he stands on is not hers to break, up beside him on blocks of her own.
     */
    private static boolean siege(Run run, ServerPlayerEntity host, LivingEntity foe, double away, boolean sees) {
        World world = host.getWorld();
        if (run.ticks >= run.siegeCheck) {
            plan(run, host, foe.getBlockPos());
            run.noWay = run.path == null || !run.path.reachesTarget();
            run.siegeCheck = run.ticks + 40;
        }

        if (!run.noWay) {
            return false;
        }

        if (sees && away > 3.4 && hasBow(host)) {
            shoot(run, host, foe);
            return true;
        }

        double rise = foe.getY() - host.getY();
        double fx = foe.getX() - host.getX();
        double fz = foe.getZ() - host.getZ();
        double flat = Math.sqrt(fx * fx + fz * fz);
        run.attackId = -1;
        if (rise >= 1.8 && flat < 9.0) {
            BlockPos under = BlockPos.ofFloored(foe.getX(), foe.getY() - 0.2, foe.getZ());
            if (!solid(world, under)) {
                // In the air: he is coming down by himself.
                run.look = foe.getEyePos();
                return true;
            }

            if (breakable(world, under)) {
                if (host.getEyePos().squaredDistanceTo(Vec3d.ofCenter(under)) <= 4.2 * 4.2) {
                    run.way.clear();
                    strike(run, host, under);
                    return true;
                }

                if (!walk(run, host, new Vec3d(foe.getX(), host.getY(), foe.getZ()), 2.6, NONE, true)) {
                    return true;
                }
            }
        }

        walk(run, host, foe.getPos(), 1.2, ANYTHING, false);
        run.flags |= SPRINT;
        return true;
    }

    /** Off the ground by a jump or a blow, not by a ladder or by water. */
    private static boolean aloft(LivingEntity who) {
        return !who.isClimbing() && !who.isTouchingWater() && !who.hasVehicle() && !webbed(who)
            && !solid(who.getWorld(), BlockPos.ofFloored(who.getX(), who.getY() - 0.3, who.getZ()));
    }

    /** Whether the body is turned to it, to within so many degrees, flat. */
    private static boolean faces(ServerPlayerEntity host, Entity it, double within) {
        double want = Math.toDegrees(Math.atan2(it.getZ() - host.getZ(), it.getX() - host.getX())) - 90.0;
        return Math.abs(net.minecraft.util.math.MathHelper.wrapDegrees(want - (double)host.getYaw())) <= within;
    }

    /**
     * Two on her and her shield up. A shield covers the half of the world in front of it, so it is turned to the
     * middle of the two and takes the blows of both. With one to either side of her that is no longer enough, and she
     * backs out from between them, side on to the pair, until both are in front of her again.
     */
    private static void amid(Run run, ServerPlayerEntity host, LivingEntity foe) {
        LivingEntity second = run.second;
        if (!run.pair || second == null || run.turning || run.attackId != foe.getId() || (run.flags & USE) == 0 || (run.flags & (FEINT | CRIT)) != 0 || !host.isBlocking() && !shielded(host)) {
            return;
        }

        Vec3d a = new Vec3d(foe.getX() - host.getX(), 0.0, foe.getZ() - host.getZ());
        Vec3d b = new Vec3d(second.getX() - host.getX(), 0.0, second.getZ() - host.getZ());
        if (a.lengthSquared() < 0.04 || b.lengthSquared() < 0.04) {
            return;
        }

        a = a.normalize();
        b = b.normalize();
        double spread = a.dotProduct(b);
        if (spread > 0.94) {
            // One behind the other, near enough: the shield that faces one faces both.
            return;
        }

        Vec3d mid = a.add(b);
        run.flags &= ~(PRESS | HOLD | KITE);
        if (mid.lengthSquared() < 0.25) {
            // One on each side: no way of standing covers both. The shield goes to the one whose blow is due first,
            // and she moves sideways out from between them. Side on to both, it covered neither.
            mid = run.secondIn < run.strikeIn ? b : a;
            run.flags |= HOLD;
        } else if (spread < 0.2) {
            run.flags |= KITE;
        }

        mid = mid.normalize();
        run.look = host.getEyePos().add(mid.multiply(4.0));
        run.flags |= MID | USE | DUEL;
    }

    /** Whether fire takes to it, by the game's own table of what burns. */
    private static boolean burns(BlockState state) {
        if (state.isAir()) {
            return false;
        }

        try {
            net.fabricmc.fabric.api.registry.FlammableBlockRegistry.Entry entry = net.fabricmc.fabric.api.registry.FlammableBlockRegistry.getDefaultInstance().get(state.getBlock());
            return entry != null && entry.getBurnChance() > 0;
        } catch (Throwable problem) {
            // No table to ask on this loader: wood, wool, leaves and what grows are what burns.
            return state.isIn(net.minecraft.registry.tag.BlockTags.LOGS) || state.isIn(net.minecraft.registry.tag.BlockTags.PLANKS) || state.isIn(net.minecraft.registry.tag.BlockTags.WOOL)
                || state.isIn(net.minecraft.registry.tag.BlockTags.LEAVES) || state.isIn(net.minecraft.registry.tag.BlockTags.WOODEN_STAIRS) || state.isIn(net.minecraft.registry.tag.BlockTags.WOODEN_SLABS)
                || state.isIn(net.minecraft.registry.tag.BlockTags.WOODEN_FENCES) || state.isIn(net.minecraft.registry.tag.BlockTags.WOODEN_DOORS) || state.isIn(net.minecraft.registry.tag.BlockTags.WOOL_CARPETS);
        }
    }

    /**
     * An edge at somebody's back: within two and a half blocks the way given, with no wall between, the floor is four
     * or more down, or there is lava. Water to land in is a floor.
     */
    static boolean brink(World world, Vec3d at, double dx, double dz) {
        for (double far = 1.0; far < 2.6; far += 0.75) {
            BlockPos cell = BlockPos.ofFloored(at.x + dx * far, at.y + 0.2, at.z + dz * far);
            if (solid(world, cell) || solid(world, cell.up())) {
                return false;
            }

            if (world.getFluidState(cell).isIn(FluidTags.LAVA)) {
                return true;
            }

            int down = 0;
            while (down < 4) {
                BlockPos under = cell.down(down + 1);
                if (world.getFluidState(under).isIn(FluidTags.LAVA)) {
                    return true;
                }

                if (solid(world, under) || !world.getFluidState(under).isEmpty()) {
                    break;
                }

                down++;
            }

            if (down >= 4) {
                return true;
            }
        }

        return false;
    }

    static boolean webbed(LivingEntity who) {
        World world = who.getWorld();
        BlockPos feet = who.getBlockPos();
        return world.getBlockState(feet).isOf(Blocks.COBWEB) || world.getBlockState(feet.up()).isOf(Blocks.COBWEB);
    }

    /**
     * Fire. A shield stops a blade and an arrow and does nothing at all about the ground its owner stands on. A man
     * who has settled down behind one, with her in front of him and neither of them striking, has the floor under
     * his feet set alight, if there is flint and steel or a fire charge in the pockets: he burns for eight seconds
     * whatever he holds up, and has to come out from behind it or stand there and burn.
     */
    private static boolean scorch(Run run, ServerPlayerEntity host, LivingEntity foe, double away, boolean sees) {
        World world = host.getWorld();
        boolean lit = run.ticks - run.fireTry < 12 && run.fireTry > 0;
        if (!(foe instanceof PlayerEntity) || !sees || away > 4.6 || away < 2.8 || run.ticks - run.fireAt < 120 || foe.isOnFire() || foe.isFireImmune()
            || foe.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.FIRE_RESISTANCE) || foe.isTouchingWaterOrRain()
            || !lit && (!foe.isBlocking() || run.holdFor < 25)
            || !carries(host, stack -> stack.isOf(Items.FLINT_AND_STEEL) && stack.getMaxDamage() - stack.getDamage() > 1 || stack.isOf(Items.FIRE_CHARGE))) {
            if (foe.isOnFire() && lit) {
                run.fireAt = run.ticks;
            }

            run.fireTry = 0;
            return false;
        }

        BlockPos cell = foe.getBlockPos();
        Vec3d top = Vec3d.ofCenter(cell.down()).add(0.0, 0.5, 0.0);
        if (world.getBlockState(cell).isIn(net.minecraft.registry.tag.BlockTags.FIRE)) {
            // It is burning under him: enough.
            run.fireAt = run.ticks;
            run.fireTry = 0;
            return false;
        }

        if (cell.equals(host.getBlockPos()) || !world.getBlockState(cell).isAir() || !world.getBlockState(cell.down()).isSideSolidFullSquare(world, cell.down(), Direction.UP)
            || host.getEyePos().squaredDistanceTo(top) > 4.3 * 4.3) {
            run.fireTry = 0;
            return false;
        }

        // Not where it would spread: nothing that burns within two blocks of it. A fight is no reason to burn a house down.
        if (run.fireTry == 0) {
            for (BlockPos near : BlockPos.iterate(cell.add(-2, -1, -2), cell.add(2, 3, 2))) {
                if (burns(world.getBlockState(near))) {
                    run.fireAt = run.ticks;
                    return false;
                }
            }
        }

        if (run.fireTry == 0) {
            run.fireTry = run.ticks;
        } else if (run.ticks - run.fireTry >= 12) {
            // Half a second and no fire: not this time. The blade again, and no more of this for a while.
            run.fireAt = run.ticks - 60;
            run.fireTry = 0;
            return false;
        }

        run.slot = equip(host, stack -> stack.isOf(Items.FLINT_AND_STEEL) && stack.getMaxDamage() - stack.getDamage() > 1 ? 2.0 : (stack.isOf(Items.FIRE_CHARGE) ? 1.0 : 0.0));
        run.attackId = -1;
        run.placeAgainst = cell.down();
        run.placeFace = Direction.UP;
        run.look = top;
        run.flags |= DUEL;
        return true;
    }

    /**
     * Cobwebs. Caught in one herself, she cuts it before anything else. With webs in the pockets she sets one
     * where his feet are going when she needs him slow: a man in a web cannot follow and cannot run.
     */
    private static boolean webs(Run run, ServerPlayerEntity host, LivingEntity foe, double away, boolean sees) {
        World world = host.getWorld();
        BlockPos mine = host.getBlockPos();
        if (webbed(host)) {
            // Caught in one. A web holds the feet, not the arm: with him near enough to strike she fights from where
            // she stands, shield and blade as ever. She used to look down and start cutting, and was killed looking
            // down. The web is cut when he is out of reach, or caught himself.
            if (foe.isAlive() && away < 5.5 && !webbed(foe)) {
                return false;
            }

            BlockPos web = world.getBlockState(mine).isOf(Blocks.COBWEB) ? mine : mine.up();
            int blade = equip(host, stack -> stack.getItem() instanceof net.minecraft.item.ShearsItem ? 3.0 : (stack.getItem() instanceof net.minecraft.item.SwordItem ? 2.0 : 0.0));
            if (blade >= 0) {
                run.slot = blade;
                run.attackId = -1;
                run.breakPos = web;
                run.look = Vec3d.ofCenter(web);
                return true;
            }

            return false;
        }

        if (webbed(foe)) {
            run.webAt = run.ticks;
            run.webTry = 0;
            return false;
        }

        // Setting anything down swings the arm, and a swing sets it back to nothing: a web is set only when the arm is
        // spent anyway, and only when there is something to gain by his standing still. He is running from her; he has
        // landed two in a row and she has had none in; she is hurt and has something to eat.
        boolean spent = host.getAttackCooldownProgress(0.0F) <= 0.6F;
        boolean hungry = host.getHealth() <= host.getMaxHealth() * 0.6F && !host.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.REGENERATION) && carries(host, stack -> stack.isOf(Items.GOLDEN_APPLE) || stack.isOf(Items.ENCHANTED_GOLDEN_APPLE));
        boolean flees = run.foeClosing < -0.2 && foe.hurtTime == 0 && away > 4.0;
        // One who runs is out of the blade's reach already: the arm is no loss then.
        // And as a matter of course, straight after a blow of her own: the arm is spent anyway, he is a step or two
        // off, and a man in a web can be struck and stepped back from for as long as it holds him.
        boolean fresh = host.getAttackCooldownProgress(0.0F) <= 0.45F && away < 4.6 && run.ticks - run.webAt > 70;
        if (!(flees || run.taken >= 2 || hungry || fresh) || !spent && !flees) {
            run.webTry = 0;
            return false;
        }

        boolean off = host.getOffHandStack().isOf(Items.COBWEB);
        if (off) {
            // Out of the off hand the blade stays where it is.
            if (!sees || away < 1.5 || away > 4.6 || run.ticks - run.webAt < 40) {
                run.webTry = 0;
                return false;
            }

            BlockPos under = BlockPos.ofFloored(foe.getPos().add(run.foeSpeed.multiply(1.0, 0.0, 1.0).multiply(2.0)));
            Vec3d top = Vec3d.ofCenter(under.down()).add(0.0, 0.5, 0.0);
            if (under.equals(mine) || !world.getBlockState(under).isAir() || !world.getBlockState(under.down()).isSideSolidFullSquare(world, under.down(), Direction.UP) || host.getEyePos().squaredDistanceTo(top) > 4.3 * 4.3 || ++run.webTry > 10) {
                if (run.webTry > 10) {
                    run.webAt = run.ticks;
                }

                run.webTry = 0;
                return false;
            }

            run.attackId = -1;
            run.placeAgainst = under.down();
            run.placeFace = Direction.UP;
            run.look = top;
            run.flags |= OFFHAND | DUEL;
            return true;
        }

        if (!sees || away < 1.5 || away > 4.4 || run.ticks - run.webAt < 50 || !carries(host, stack -> stack.isOf(Items.COBWEB))) {
            run.webTry = 0;
            return false;
        }

        // Under his feet, a step ahead of where they are now.
        BlockPos cell = BlockPos.ofFloored(foe.getPos().add(run.foeSpeed.multiply(1.0, 0.0, 1.0).multiply(2.0)));
        if (cell.equals(mine) || !world.getBlockState(cell).isAir() || !solid(world, cell.down()) || ++run.webTry > 14 || put(run, host, cell, Items.COBWEB) != 0) {
            if (run.webTry > 14) {
                run.webAt = run.ticks;
            }

            run.webTry = 0;
            return false;
        }

        run.attackId = -1;
        return true;
    }

    /** What one blow of it is worth when blows are a second apart: all of its harm, if the arm is ready again by then. */
    private static double blowWorth(ItemStack stack) {
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

        damage += (double)net.minecraft.enchantment.EnchantmentHelper.getAttackDamage(stack, net.minecraft.entity.EntityGroup.DEFAULT);
        return damage * Math.min(1.0, Math.max(0.25, speed) / 0.87);
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
        private int unseen;
        private Vec3d lastSeen;
        private int after;
        private int idle;
        private int chase;
        private float heading;
        private Vec3d roam;
        /** Players who struck her while she was fighting a man she was sent for, and when each last did. They are fought too. */
        private final Map<UUID, Integer> others = new HashMap<>();
        private final Map<UUID, Float> arms = new HashMap<>();
        private boolean men;
        private int dwell;

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

        /** Who else is in it: any player whose blow has just landed on her, or on her shield, who is not the one she is on. */
        private void joined(Run run, ServerPlayerEntity host) {
            if (host.getAttacker() instanceof PlayerEntity other && other != host && other.isAlive() && !other.isSpectator() && !other.isCreative()
                && host.age - host.getLastAttackedTime() < 4 && PhaseFx.samePhase(other, host) && (this.others.containsKey(other.getUuid()) || other != this.foe && !this.prey.test(other))) {
                this.others.put(other.getUuid(), run.ticks);
            }

            if (this.foe != null && this.others.containsKey(this.foe.getUuid())) {
                // The one she is fighting this moment is in it for as long as she is fighting him.
                this.others.put(this.foe.getUuid(), run.ticks);
            }

            // Out of it again when he is gone, or has not struck at her for a quarter of a minute.
            this.others.entrySet().removeIf(entry -> {
                PlayerEntity who = host.getWorld().getPlayerByUuid(entry.getKey());
                return who == null || !who.isAlive() || who.isSpectator() || run.ticks - entry.getValue() > 300;
            });
        }

        private PlayerEntity nearestOther(ServerPlayerEntity host) {
            PlayerEntity best = null;
            double bestDistance = 24.0 * 24.0;

            for (UUID id : this.others.keySet()) {
                PlayerEntity who = host.getWorld().getPlayerByUuid(id);
                if (who != null && who.isAlive() && who.squaredDistanceTo(host) < bestDistance) {
                    bestDistance = who.squaredDistanceTo(host);
                    best = who;
                }
            }

            return best;
        }

        /**
         * Two or more players on her. Which of them she fights this tick, and which is the other one to mind. She
         * stays on one for most of a second at least, since every change costs her what she had learnt of the last.
         * The nearest as a rule, with something in hand for the one she is on already and for one who is two blows
         * from the end; and whoever has just swung at her and missed or hit her shield, while her own arm is ready,
         * is the one the answer goes to.
         */
        private LivingEntity among(Run run, ServerPlayerEntity host) {
            List<PlayerEntity> near = new ArrayList<>();

            for (PlayerEntity who : host.getWorld().getPlayers()) {
                if (who != host && who.isAlive() && !who.isSpectator() && !who.isCreative() && who.squaredDistanceTo(host) < 64.0 && PhaseFx.samePhase(who, host)
                    && (who == this.foe || this.others.containsKey(who.getUuid()) || this.prey.test(who))) {
                    near.add(who);
                }
            }

            if (near.size() < 2 && (near.isEmpty() || near.get(0) == this.foe)) {
                this.arms.clear();
                return this.foe;
            }

            LivingEntity pick = this.foe;
            PlayerEntity struck = null;

            for (PlayerEntity who : near) {
                float cool = who.getAttackCooldownProgress(0.0F);
                Float was = this.arms.put(who.getUuid(), cool);
                if (was != null && cool < was - 0.3F && who.distanceTo(host) < 4.2F && (struck == null || who.squaredDistanceTo(host) < struck.squaredDistanceTo(host))) {
                    struck = who;
                }
            }

            this.dwell--;
            // A blow her shield took shakes her as one that landed does: what tells them apart is whether it cost her anything.
            if (struck != null && struck != this.foe && host.getHealth() + host.getAbsorptionAmount() >= run.hostHealth - 0.01F && host.getAttackCooldownProgress(0.0F) >= 0.8F && shielded(host)) {
                pick = struck;
                this.dwell = 10;
                run.counter = true;
                run.counterFor = struck.getId();
                run.foeSwungAt = run.ticks;
                run.foeCool = struck.getAttackCooldownProgress(0.0F);
            } else if (this.dwell <= 0 || !(this.foe instanceof PlayerEntity) || !near.contains(this.foe)) {
                double best = Double.MAX_VALUE;

                for (PlayerEntity who : near) {
                    if (!host.canSee(who) && who != this.foe) {
                        continue;
                    }

                    double d = (double)who.distanceTo(host);
                    double score = d - (who == this.foe ? 1.2 : 0.0) - (who.getHealth() <= 6.0F && d < 4.5 ? 2.0 : 0.0);
                    if (score < best) {
                        best = score;
                        pick = who;
                    }
                }

                if (pick != this.foe) {
                    this.dwell = 15;
                }
            }

            PlayerEntity second = null;

            for (PlayerEntity who : near) {
                if (who != pick && (second == null || who.squaredDistanceTo(host) < second.squaredDistanceTo(host))) {
                    second = who;
                }
            }

            if (second != null) {
                double far = (double)second.distanceTo(host);
                run.second = second;
                run.pair = far < 5.5;
                int arm = Math.max(0, (int)Math.ceil((double)((0.95F - second.getAttackCooldownProgress(0.0F)) * second.getAttackCooldownProgressPerTick())));
                run.secondIn = Math.max(arm, far <= 3.4 ? 0 : (int)((far - 3.4) / 0.28));
            }

            return pick;
        }

        @Override
        public boolean tick(Run run, ServerPlayerEntity host) {
            if (this.foe != null && (!this.foe.isAlive() || this.foe.isRemoved())) {
                // One who joined in uninvited is not one of those she was sent for, and is not counted as one.
                if (this.foe.isDead() && this.others.remove(this.foe.getUuid()) == null) {
                    this.kills++;
                }

                this.foe = null;
                this.chase = 0;
            }

            if (this.foe instanceof PlayerEntity) {
                this.men = true;
            }

            if (this.men) {
                this.joined(run, host);
                if (this.foe == null && !this.others.isEmpty()) {
                    // The one she was sent for is down or gone, and the other is still on her: him, then.
                    this.foe = this.nearestOther(host);
                }
            }

            if (this.foe instanceof PlayerEntity && this.foe.squaredDistanceTo(host) < 64.0 && host.canSee(this.foe)) {
                // A creature she has chased three quarters of a minute and not caught is given up for another. A man
                // she is fighting is not a chase: giving him up for a tick is a tick with her guard down.
                this.chase = 0;
            }

            if (this.foe != null && (this.foe.getWorld() != host.getWorld() || this.foe.squaredDistanceTo(host) > 48.0 * 48.0 || ++this.chase > 900)) {
                this.foe = null;
                this.chase = 0;
            }

            if (this.want > 0 && this.kills >= this.want && !(this.foe != null && this.others.containsKey(this.foe.getUuid()))) {
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

            if (run.ticks % 10 == 3 && this.foe != null && !(this.foe instanceof PlayerEntity)) {
                // Of those within the arm's reach, the one nearest its end: one fewer on her the sooner.
                LivingEntity weak = null;

                for (MobEntity mob : host.getWorld().getEntitiesByClass(MobEntity.class, host.getBoundingBox().expand(3.6, 2.0, 3.6), x -> x.isAlive() && x.getTarget() == host && host.canSee(x) && !(x instanceof CreeperEntity))) {
                    if (weak == null || mob.getHealth() < weak.getHealth()) {
                        weak = mob;
                    }
                }

                if (weak != null && weak != this.foe && (this.foe.squaredDistanceTo(host) > 16.0 || weak.getHealth() < this.foe.getHealth() - 2.0F)) {
                    this.foe = weak;
                }
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
                if (host.canSee(this.foe)) {
                    this.unseen = 0;
                    this.lastSeen = this.foe.getPos().add(run.foeSpeed.multiply(20.0).multiply(1.0, 0.0, 1.0));
                } else if (++this.unseen > 30 && this.lastSeen != null && this.foe.squaredDistanceTo(host) > 36.0) {
                    // Out of sight: to where it was going when she last saw it, and a look round from there.
                    if (this.unseen > 260 || walk(run, host, this.lastSeen, 2.0, NATURAL, true)) {
                        this.foe = null;
                        this.unseen = 0;
                    }

                    run.flags |= SPRINT;
                    return true;
                }

                if (this.men) {
                    this.foe = this.among(run, host);
                }

                fight(run, host, this.foe);
                amid(run, host, this.foe);
                return true;
            }

            // Between fights: what they dropped.
            ItemEntity loot = lying(host, 7.0, null);
            if (loot != null && hasRoom(host)) {
                fetch(run, host, loot, NONE);
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
        // Under water, with more of it over its head: held down by something, or still on its way up. What floats
        // on the top of a lake, however deep, is not sunk: she swims, and it is hers to swim to.
        return world.getFluidState(at.up()).isIn(FluidTags.WATER);
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

    /**
     * Where a thing on the ground is to be walked to. One that has only just dropped is still in the air, a little
     * above where it will lie; going "up" to it is a jump, and a jump in a field comes down on a bed and treads it
     * back into earth. Until it has landed it is taken to be at the level of her feet.
     */
    static Vec3d lies(ServerPlayerEntity host, ItemEntity item) {
        Vec3d at = item.getPos();
        return !item.isOnGround() && at.y > host.getY() && at.y - host.getY() < 1.5 ? new Vec3d(at.x, host.getY(), at.z) : at;
    }

    /** What grows and can be eaten as it is, or baked: a ripe field of wheat, carrots, potatoes or beetroot, a melon, a bush with berries on it. */
    private static boolean meal(BlockState state) {
        if (state.isOf(Blocks.WHEAT) || state.isOf(Blocks.CARROTS) || state.isOf(Blocks.POTATOES) || state.isOf(Blocks.BEETROOTS)) {
            return ((net.minecraft.block.CropBlock)state.getBlock()).isMature(state);
        }

        return state.isOf(Blocks.MELON) || state.isOf(Blocks.SWEET_BERRY_BUSH) && state.get(net.minecraft.block.SweetBerryBushBlock.AGE) >= 2;
    }

    /** How many things to eat the body carries. */
    private static int meals(ServerPlayerEntity host) {
        int sum = 0;

        for (ItemStack stack : host.getInventory().main) {
            if (Eat.foodWorth(stack) > 0.0 || stack.isOf(Items.WHEAT)) {
                sum += stack.getCount();
            }
        }

        return sum;
    }

    /**
     * "Find food". Over the country for whatever can be eaten: the beasts that are meat, killed and picked up; a
     * field that is ripe, cut and sown again; a melon, a bush of berries. A beast in sight comes before a field,
     * since the field stays where it is. Done when she has so many things to eat more than she set out with.
     */
    static final class Forage implements Task {
        private final Hunt hunt;
        private final Set<BlockPos> skip = new HashSet<>();
        private final int want;
        private final int had;
        private Mine reap;
        private int ticks;
        private final Set<Integer> given = new HashSet<>();
        private int after;
        private int afterId = -1;

        Forage(ServerPlayerEntity host, int want) {
            this.hunt = new Hunt(host, meat(), 0, false, 1000000);
            this.want = Math.min(want, 256);
            this.had = meals(host);
        }

        @Override
        public int label() {
            return this.reap != null ? L_MINE : L_HUNT;
        }

        @Override
        public boolean tick(Run run, ServerPlayerEntity host) {
            int got = meals(host) - this.had;
            // What has been knocked out of a beast or a bed and still lies there is hers before she moves on, or stops.
            ItemEntity lies = hasRoom(host) ? lying(host, 8.0, stack -> Eat.foodWorth(stack) > 0.0 || stack.isOf(Items.WHEAT)) : null;
            if (lies != null && this.given.contains(lies.getId())) {
                lies = null;
            }

            if (got >= this.want && lies == null || ++this.ticks > 6000 || this.ticks % 40 == 0 && !hasRoom(host)) {
                if (got > 0) {
                    run.end("pilot_foraged", got);
                } else {
                    run.end("pilot_none_far", 0);
                }

                return false;
            }

            if (this.reap != null) {
                if (this.reap.tick(run, host)) {
                    return true;
                }

                // That field is done; what she says of it is kept for the end.
                this.reap = null;
                run.ending = null;
                run.endingArgs = new Object[0];
            }

            if (lies != null && this.hunt.foe == null) {
                if (lies.getId() != this.afterId) {
                    this.afterId = lies.getId();
                    this.after = 0;
                }

                if (++this.after > 160) {
                    // Eight seconds and still not in her hand: it lies where she cannot get.
                    this.given.add(lies.getId());
                }

                fetch(run, host, lies, NONE);
                return true;
            }

            if (this.hunt.foe == null && run.ticks % 20 == 5 && this.hunt.pick(host) == null) {
                BlockPos ripe = seen(host, Pilot::meal, 14, this.skip);
                if (ripe != null) {
                    this.skip.add(ripe);
                    this.reap = new Mine(host, List.of(Pilot::meal), 64, false, L_MINE);
                    return true;
                }
            }

            if (!this.hunt.tick(run, host)) {
                run.ending = null;
                run.endingArgs = new Object[0];
            }

            return true;
        }
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

            fetch(run, host, this.item, NONE);
            return true;
        }
    }

    /**
     * Gets so many blocks of a kind: the ones in sight first, then the vein behind them. With {@code search}
     * she goes looking when there are none: down to the depth the ore lies at and along a tunnel there, or over
     * the surface for what lies on it.
     */
    static final class Mine implements Task {
        /** Crops she knows of and has not cut as the one she was after: where each stands and what it is sown with. */
        private final Map<BlockPos, net.minecraft.item.Item> crops = new HashMap<>();
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
        /** The block of the kind asked for that the Host was looking at when he asked, however far off: that one first. */
        private BlockPos eyed;
        private int eyedFor;

        Mine(ServerPlayerEntity host, List<Predicate<BlockState>> tiers, int want, boolean search, int label) {
            this.tiers = tiers;
            this.want = want;
            this.search = search;
            this.label = label;
            this.heading = host.getYaw();
            this.shaft = host.getHorizontalFacing();
            this.lastCell = host.getBlockPos();
            Vec3d eye = host.getEyePos();
            BlockHitResult at = host.getWorld().raycast(new RaycastContext(eye, eye.add(host.getRotationVec(1.0F).multiply(64.0)), RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, host));
            if (at.getType() == HitResult.Type.BLOCK) {
                for (BlockPos pos : new BlockPos[]{at.getBlockPos(), at.getBlockPos().up()}) {
                    BlockState there = host.getWorld().getBlockState(pos);
                    if (this.eyed == null && tiers.stream().anyMatch(tier -> tier.test(there))) {
                        this.eyed = pos.toImmutable();
                    }
                }
            }

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
        /** Notes the crop here and those within two steps of it, ripe or not: whichever of them goes down, its bed is sown again. */
        private void crop(World world, BlockPos cell) {
            for (BlockPos near : BlockPos.iterate(cell.add(-2, 0, -2), cell.add(2, 0, 2))) {
                net.minecraft.block.Block block = world.getBlockState(near).getBlock();
                if (block instanceof net.minecraft.block.CropBlock || block == Blocks.NETHER_WART) {
                    this.crops.putIfAbsent(near.toImmutable(), block.asItem());
                }
            }
        }

        private ItemEntity drop(ServerPlayerEntity host) {
            if (!hasRoom(host)) {
                return null;
            }

            // Five seconds for one that lies on the ground; three times that for one she has to knock out of a tree.
            if (this.loot != null && this.loot.isAlive() && this.lootTicks++ < (this.loot.getY() - host.getY() > 1.3 ? 300 : 100)) {
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
                ItemEntity drop = this.wrap++ < 700 ? this.drop(host) : null;
                if (drop != null) {
                    this.settle = 0;
                    fetch(run, host, drop, ANYTHING);
                    return true;
                }

                if (this.settle++ < 30 && this.wrap < 700) {
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
                        this.crops.remove(this.target);
                    }

                    for (BlockPos near : BlockPos.iterate(this.target.add(-1, -1, -1), this.target.add(1, 1, 1))) {
                        if (!this.skip.contains(near) && !this.vein.contains(near) && this.fits(world.getBlockState(near)) && world.getBlockEntity(near) == null && !foot(world, near)) {
                            this.vein.add(near.toImmutable());
                            this.crop(world, near);
                        }
                    }

                    this.target = null;
                } else if ((run.ticks - run.workAt > 5 ? ++this.spent : this.spent) > 400) {
                    // Twenty seconds without getting to it, not counting what goes on a bridge or a stair to it.
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
                    this.crop(world, best);
                }

                return true;
            }

            ItemEntity drop = this.got > 0 && this.pause++ < 400 ? this.drop(host) : null;
            if (drop != null) {
                if (LOG) {
                    com.windykindy49.windyorigins.WindyOrigins.LOGGER.info("PILOT harvest: after a drop {} at {} pause {} she {}", drop.getStack(), String.format("%.2f %.2f %.2f", drop.getX(), drop.getY(), drop.getZ()), this.pause, String.format("%.2f %.2f %.2f", host.getX(), host.getY(), host.getZ()));
                }

                fetch(run, host, drop, ANYTHING);
                return true;
            }

            // A crop that stood in the way of the one she was after went down without ever being the one she was
            // after: its bed is empty all the same.
            for (java.util.Iterator<Map.Entry<BlockPos, net.minecraft.item.Item>> known = this.crops.entrySet().iterator(); known.hasNext(); ) {
                Map.Entry<BlockPos, net.minecraft.item.Item> entry = known.next();
                if (world.getBlockState(entry.getKey()).isAir()) {
                    if (!this.beds.contains(entry.getKey())) {
                        this.beds.add(entry.getKey());
                        this.seeds.put(entry.getKey(), entry.getValue());
                    }

                    known.remove();
                }
            }

            if (!this.beds.isEmpty()) {
                // Sown again with what the crop itself gave.
                BlockPos bed = this.beds.get(0);
                net.minecraft.item.Item seed = this.seeds.get(bed);
                if (LOG) {
                    com.windykindy49.windyorigins.WindyOrigins.LOGGER.info("PILOT harvest: bed {} sowing {} air {} seed {} carried {} grows {} tillable {} hand {}", bed.toShortString(), this.sowing, world.getBlockState(bed).isAir(), seed, carries(host, stack -> stack.isOf(seed)), net.minecraft.block.Block.getBlockFromItem(seed).getDefaultState().canPlaceAt(world, bed), tillable(world, bed.down()), host.getMainHandStack().getItem());
                }

                if (++this.sowing < 80 && world.getBlockState(bed).isAir() && carries(host, stack -> stack.isOf(seed))) {
                    if (net.minecraft.block.Block.getBlockFromItem(seed).getDefaultState().canPlaceAt(world, bed)) {
                        if (put(run, host, bed, seed) == 0) {
                            return true;
                        }
                    } else if (tillable(world, bed.down()) && till(run, host, bed.down()) == 0) {
                        // The bed has gone back to plain earth, dried out or trodden down: nothing is sown in that.
                        // With a hoe in the pockets it is tilled again first; without one the bed is left.
                        return true;
                    }
                }

                this.beds.remove(0);
                this.sowing = 0;
                return true;
            }

            if (this.eyed != null) {
                // He was looking at one when he asked: that is the one he means, and she goes to it, sixty blocks
                // off or six, before she looks about for any other.
                BlockState there = world.getBlockState(this.eyed);
                Predicate<BlockState> fits = null;
                for (Predicate<BlockState> tier : this.tiers) {
                    if (fits == null && tier.test(there)) {
                        fits = tier;
                    }
                }

                if (fits == null || this.skip.contains(this.eyed) || ++this.eyedFor > 900) {
                    this.eyed = null;
                } else if (host.getBlockPos().getSquaredDistance(this.eyed) > 100.0 && run.stuck < 6 && !walk(run, host, Vec3d.ofBottomCenter(this.eyed), 9.0, NATURAL, true)) {
                    return true;
                } else {
                    BlockPos found = this.eyed;
                    this.eyed = null;
                    this.crop(world, found);
                    this.cutting = there.getBlock();
                    this.kind = fits;
                    this.target = found;
                    this.spent = 0;
                    this.idle = 0;
                    this.pause = 0;
                    this.searching = false;
                    if (this.name == null) {
                        this.name = there.getBlock().getName();
                    }

                    return true;
                }
            }

            if (run.ticks % 10 == 0) {
                for (Predicate<BlockState> tier : this.kind != null ? List.of(this.kind) : this.tiers) {
                    BlockPos found = seen(host, tier, 14, this.skip);
                    if (found != null && foot(world, found)) {
                        this.skip.add(found);
                        found = null;
                    }

                    if (found != null) {
                        this.crop(world, found);
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

            Vec3d to = away(host, away, 14.0);
            if (to == null) {
                run.end("pilot_lost");
                return false;
            }

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
            ServerWorld world = host.getServerWorld();
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
        float[] bearing = all.get(0).kind().equals("resume") ? BEARING.get(host.getUuid()) : null;
        if (!all.get(0).kind().equals("take")) {
            BEARING.remove(host.getUuid());
        }

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
        Run resumed = bearing == null ? null : RUNS.get(host.getUuid());
        if (resumed != null && resumed.task instanceof Explore search) {
            // And the leg already laid out by the way the body happens to face is thrown away with it.
            search.heading = bearing[0];
            search.leg = null;
        } else if (resumed != null && resumed.task instanceof Mine search) {
            search.heading = bearing[0];
            search.roam = null;
            if (bearing[1] >= 0.0F) {
                search.shaft = Direction.fromHorizontal((int)bearing[1]);
            }
        }

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
        ServerWorld world = host.getServerWorld();
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
            case "sow" -> {
                String thing = VoltaChat.thing(subject);
                int beds = order.number() > 0 ? order.number() : 256;
                if (thing.isBlank()) {
                    go(host, new Sow(Pilot::seed, beds), held);
                } else {
                    // "Wheat" is sown with wheat seeds: what was named is the seed, or what grows from it.
                    HostManager.askNames(host, false, thing, (exact, partial) -> {
                        Predicate<ItemStack> narrow = stack -> seed(stack) && seedOf(stack.getItem(), exact);
                        Predicate<ItemStack> wide = stack -> seed(stack) && (seedOf(stack.getItem(), exact) || seedOf(stack.getItem(), partial));
                        go(host, new Sow(carries(host, narrow) ? narrow : wide, beds), held);
                    });
                }
            }
            case "shelter" -> go(host, new Shelter(host), held);
            case "sleep", "spawn" -> {
                // "Sleep" at night is a night slept through; by day a bed only takes the respawn point, and that is
                // what she does with it. "Set the respawn point" is the bed touched at any hour and left again at once.
                BlockPos bed = bedNear(host);
                if (!world.getDimension().bedWorks()) {
                    HostManager.say(host, "pilot_bed_unsafe", 2);
                } else if (bed == null) {
                    HostManager.say(host, "pilot_no_bed", 2);
                } else {
                    go(host, new Sleep(bed, order.kind().equals("sleep")), held);
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
                    go(host, new Forage(host, order.number() > 0 ? order.number() : 8), held);
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
                        Predicate<ItemStack> wanted = stack -> ids.contains(Registries.ITEM.getId(stack.getItem())) || ids.isEmpty() && everyday != null && Registries.ITEM.getId(stack.getItem()).getPath().contains(everyday);
                        if (lying(host, 20.0, wanted) != null) {
                            go(host, new Collect(wanted), held);
                            return;
                        }

                        // "Collect the cane", said standing in it: none of it lies on the ground, it is growing
                        // all round. What is asked for is the cutting of it, so that is what she does.
                        String grown = VoltaChat.thing(subject);
                        if (grown.isBlank() && alias != null) {
                            mineNamed(host, order, subject, List.of(), List.of(), held, true);
                        } else if (!grown.isBlank()) {
                            HostManager.askNames(host, 1, grown, (blocks, blocksInPart) -> mineNamed(host, order, grown, blocks, blocksInPart, held, true));
                        } else {
                            go(host, new Collect(wanted), held);
                        }
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

            for (ServerPlayerEntity other : host.getServerWorld().getPlayers()) {
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
            // Named, or pointed at: she knows who. Where, only if they can be seen or heard from here; otherwise she
            // goes looking, as she would for anything else, and falls on them when they come into view.
            boolean known = host.canSee(target) || target.squaredDistanceTo(host) < 16.0 * 16.0;
            Hunt chase = new Hunt(host, e -> e == target, 1, false, known ? 60 : 900);
            if (known) {
                chase.foe = target;
            }

            go(host, chase, held);
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
        ServerWorld world = host.getServerWorld();
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

        if (order.what().equals("food")) {
            // "Find food": whatever can be eaten, wherever it is. Not only the beasts.
            go(host, new Forage(host, order.number() > 0 ? order.number() : 8), held);
            return;
        }

        if (order.what().equals("animals")) {
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

    /**
     * To bed: she walks the body there and lies it down. A bed takes the respawn point whenever it is used,
     * day or night, asleep or not, so that is what is left of the job when there is no sleeping to be done:
     * by day, with monsters about, or when only the respawn point was asked for.
     */
    static final class Sleep implements Task {
        private final BlockPos bed;
        /** To stay in it for the night, not just to touch it. */
        private final boolean stay;
        private int ticks;
        private int used;

        Sleep(BlockPos bed, boolean stay) {
            this.bed = bed;
            this.stay = stay;
        }

        /** The respawn point is at this bed: at the block that was used or at its other half. */
        private boolean home(ServerPlayerEntity host) {
            BlockPos spawn = host.getSpawnPointPosition();
            return spawn != null
                && host.getSpawnPointDimension() == host.getWorld().getRegistryKey()
                && spawn.getManhattanDistance(this.bed) <= 1
                && host.getWorld().getBlockState(spawn).getBlock() instanceof net.minecraft.block.BedBlock;
        }

        @Override
        public int label() {
            return L_SLEEP;
        }

        @Override
        public boolean tick(Run run, ServerPlayerEntity host) {
            World world = host.getWorld();
            if (host.isSleeping()) {
                if (this.stay) {
                    run.end("pilot_sleep");
                    return false;
                }

                // Only the respawn point was wanted: up again before the night is slept away.
                host.wakeUp(true, true);
                run.end("pilot_spawn_set");
                return false;
            }

            if (this.used > 0 && ++this.used > 12 && this.home(host) && (!this.stay || world.isDay() || this.used > 100)) {
                // Used, and not asleep: by day that is all a bed does, and at night it is all it does with monsters near.
                run.end("pilot_spawn_set");
                return false;
            }

            if (++this.ticks > 1200 || !(world.getBlockState(this.bed).getBlock() instanceof net.minecraft.block.BedBlock)) {
                run.end("pilot_lost");
                return false;
            }

            if (host.getEyePos().squaredDistanceTo(Vec3d.ofCenter(this.bed)) > 9.0) {
                walk(run, host, Vec3d.ofBottomCenter(this.bed), 1.5, NATURAL, false);
            } else if (this.ticks % 20 == 0) {
                run.usePos = this.bed;
                run.look = Vec3d.ofCenter(this.bed);
                this.used = Math.max(this.used, 1);
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
            BlockPos feet = standing(host);
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

    /** What is put in the ground to grow: seeds and the like for a tilled bed, wart for soul sand. */
    static boolean seed(ItemStack stack) {
        return stack.getItem() instanceof net.minecraft.item.BlockItem item
            && (item.getBlock() instanceof net.minecraft.block.CropBlock || item.getBlock() instanceof net.minecraft.block.StemBlock || item.getBlock() == Blocks.NETHER_WART);
    }

    /** Whether one of these names is this seed, or the plant that comes of it: "wheat" for wheat seeds, "beetroot" for the bed of beetroots. */
    static boolean seedOf(net.minecraft.item.Item seed, List<net.minecraft.util.Identifier> names) {
        net.minecraft.util.Identifier own = net.minecraft.registry.Registries.ITEM.getId(seed);
        net.minecraft.util.Identifier plant = net.minecraft.registry.Registries.BLOCK.getId(net.minecraft.block.Block.getBlockFromItem(seed));

        for (net.minecraft.util.Identifier name : names) {
            if (name.equals(own) || name.equals(plant)) {
                return true;
            }

            if (name.getNamespace().equals(plant.getNamespace()) && (plant.getPath().startsWith(name.getPath()) || name.getPath().startsWith(plant.getPath()) || own.getPath().startsWith(name.getPath()))) {
                return true;
            }
        }

        return false;
    }

    /**
     * Sows the bare beds: tilled ground with nothing on it, the nearest first, with the seeds in the pockets.
     * She does not make a field; where there is no bed she sows nothing.
     */
    static final class Sow implements Task {
        private final Predicate<ItemStack> seeds;
        private final int limit;
        private final Set<BlockPos> skip = new HashSet<>();
        private BlockPos bed;
        private net.minecraft.item.Item with;
        private int spent;
        private int sown;

        Sow(Predicate<ItemStack> seeds, int limit) {
            this.seeds = seeds;
            this.limit = limit;
        }

        @Override
        public int label() {
            return L_SOW;
        }

        @Override
        public boolean tick(Run run, ServerPlayerEntity host) {
            World world = host.getWorld();
            if (this.bed != null && !world.getBlockState(this.bed).isAir()) {
                this.sown++;
                this.bed = null;
            }

            if (this.bed != null && ++this.spent > 100) {
                this.skip.add(this.bed);
                this.bed = null;
            }

            if (this.sown >= this.limit) {
                run.end("pilot_sown", this.sown);
                return false;
            }

            if (this.bed == null) {
                this.with = null;

                for (ItemStack stack : host.getInventory().main) {
                    if (!stack.isEmpty() && this.seeds.test(stack)) {
                        this.with = stack.getItem();
                        break;
                    }
                }

                if (this.with == null) {
                    run.end(this.sown > 0 ? "pilot_sown" : "pilot_no_seeds", this.sown);
                    return false;
                }

                BlockState plant = net.minecraft.block.Block.getBlockFromItem(this.with).getDefaultState();
                // A bed trodden down inside a field is tilled again first, when there is a hoe to do it with.
                boolean hoe = !plant.isOf(Blocks.NETHER_WART) && carries(host, stack -> stack.getItem() instanceof net.minecraft.item.HoeItem);
                BlockPos feet = host.getBlockPos();
                BlockPos best = null;

                for (BlockPos cell : BlockPos.iterate(feet.add(-24, -4, -24), feet.add(24, 4, 24))) {
                    if (world.getBlockState(cell).isAir()
                        && !world.getBlockState(cell.down()).isAir()
                        && !this.skip.contains(cell)
                        && (best == null || cell.getSquaredDistance(feet) < best.getSquaredDistance(feet))
                        && (plant.canPlaceAt(world, cell) || hoe && trodden(world, cell.down()))) {
                        best = cell.toImmutable();
                    }
                }

                if (best == null) {
                    run.end(this.sown > 0 ? "pilot_sown" : "pilot_no_beds", this.sown);
                    return false;
                }

                this.bed = best;
                this.spent = 0;
            }

            if (!net.minecraft.block.Block.getBlockFromItem(this.with).getDefaultState().canPlaceAt(world, this.bed)) {
                if (!trodden(world, this.bed.down()) || till(run, host, this.bed.down()) != 0) {
                    this.skip.add(this.bed);
                    this.bed = null;
                }
            } else if (put(run, host, this.bed, this.with) != 0 && world.getBlockState(this.bed).isAir()) {
                this.skip.add(this.bed);
                this.bed = null;
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

    static boolean storage(net.minecraft.block.entity.BlockEntity entity) {
        return entity instanceof net.minecraft.block.entity.ChestBlockEntity || entity instanceof net.minecraft.block.entity.BarrelBlockEntity || entity instanceof net.minecraft.block.entity.ShulkerBoxBlockEntity;
    }

    /** The nearest chest, barrel or shulker box about the place that she has not looked into yet. */
    private static BlockPos storageNear(ServerPlayerEntity host, Set<BlockPos> skip) {
        return storageNear(host, skip, 28);
    }

    private static BlockPos storageNear(ServerPlayerEntity host, Set<BlockPos> skip, int far) {
        ServerWorld world = host.getServerWorld();
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
                walk(run, host, lies(host, dropped), 0.3, NONE, false);
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

        /** Everything there is, as long as it fits: nothing is thrown on the floor for want of room. */
        private boolean all;
        private boolean full;

        Withdraw(List<BlockPos> chests, Predicate<ItemStack> what, int count) {
            this.chests = chests;
            this.what = what;
            this.left = count;
        }

        Withdraw(List<BlockPos> chests, Predicate<ItemStack> what, int count, boolean all) {
            this(chests, what, count);
            this.all = all;
        }

        @Override
        public int label() {
            return L_STORE;
        }

        @Override
        public boolean tick(Run run, ServerPlayerEntity host) {
            if (this.all && (this.at >= Math.min(this.chests.size(), 24) || this.full)) {
                shut(run, host);
                if (this.got > 0) {
                    run.end("fetched_all", this.got);
                } else {
                    run.end(this.full ? "pockets_full" : "chests_empty");
                }

                return false;
            }

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

                    if (this.all) {
                        ItemStack rest = slot.getStack().copy();
                        int had = rest.getCount();
                        host.getInventory().insertStack(rest);
                        if (rest.getCount() < had) {
                            this.got += had - rest.getCount();
                            slot.setStack(rest.isEmpty() ? ItemStack.EMPTY : rest);
                            slot.markDirty();
                        }

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

                this.full = this.all && !hasRoom(host);
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
        if (VoltaChat.onlyBoxes(thing) && (VoltaChat.saysAll(order.subject()) || !thing.isBlank())) {
            // "Take everything out of the chests": round all of them, the nearest first, until the pockets are full.
            if (chests.isEmpty()) {
                HostManager.say(host, "pilot_no_storage", 2);
            } else {
                go(host, new Withdraw(chests, stack -> true, Integer.MAX_VALUE, true), held);
            }

            return;
        }

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
