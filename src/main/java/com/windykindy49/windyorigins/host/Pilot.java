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
    private static final int SPRINT = 1;
    private static final int SNEAK = 2;
    private static final int JUMP = 4;
    private static final int USE = 8;
    /** A point of heat this often while she works, and this often while she only holds the body and waits. */
    static final int HEAT_EVERY = 100;
    static final int HEAT_EVERY_IDLE = 200;
    /** Taking the body is a request like any other. */
    static final int HEAT_TAKE = 12;
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

    private Pilot() {
    }

    /** One job. */
    interface Task {
        int label();

        /** One tick of the job. False once it is over. */
        boolean tick(Run run, ServerPlayerEntity host);
    }

    static final class Run {
        Task task;
        /** Taken "until told otherwise": when a job ends she keeps the body and waits for the next. */
        boolean held;
        int ticks;
        /** Ticks spent with nothing to do and no leave to wait. */
        int idle;
        ZombieEntity scout;
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
        int attackId = -1;
        Vec3d look;
        int flags;
        int slot = -1;
        int label;
        int sentHash;
        long sentAt;
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
        if (run == null) {
            if (HostManager.isBroken(host) || host.hasStatusEffect(WindyEffects.CYBERPSYCHOSIS) || host.isSpectator() || !host.isAlive()) {
                return false;
            }

            if (host.isTouchingWater()) {
                HostManager.say(host, "water", 3);
                HostManager.addHeat(host, 8);
                return false;
            }

            if (HostManager.heat(host) >= 90) {
                HostManager.say(host, "pilot_hot", 3);
                return false;
            }

            run = new Run();
            run.lastSpot = host.getPos();
            RUNS.put(host.getUuid(), run);
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

    private static final Set<String> SAYS_IT_ALL = Set.of("pilot_off", "pilot_taken", "pilot_hurt", "pilot_sleep");

    /** The body goes back to the Host. With a line when there is something to say about it. */
    public static void release(ServerPlayerEntity host, String line, Object... args) {
        NEXT.remove(host.getUuid());
        if (RUNS.remove(host.getUuid()) == null) {
            return;
        }

        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeBoolean(false);
        ServerPlayNetworking.send(host, HostNetworking.PILOT, buf);
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
    }

    /** The Host put their hands back on the keys. */
    public static void taken(ServerPlayerEntity host) {
        release(host, "pilot_taken");
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

        if (!HostManager.isHost(host) || !host.isAlive() || host.isSpectator() || HostManager.isBroken(host) || host.hasStatusEffect(WindyEffects.CYBERPSYCHOSIS)) {
            release(host, null);
            return;
        }

        run.ticks++;
        if (run.ticks % (run.task == null ? HEAT_EVERY_IDLE : HEAT_EVERY) == 0) {
            HostManager.addHeat(host, 1);
            if (host.hasStatusEffect(WindyEffects.CYBERPSYCHOSIS)) {
                release(host, null);
                return;
            }
        }

        run.clear();
        run.label = run.task == null ? L_HOLD : run.task.label();
        LivingEntity threat = threat(host);
        if (run.task != null && !(run.task instanceof Flee) && host.getHealth() <= Math.max(6.0F, host.getMaxHealth() * 0.3F) && (threat != null || host.isOnFire())) {
            // She is losing this one: better the Host's own hands than a dead Host.
            release(host, "pilot_hurt");
            return;
        }

        if (threat != null && !(run.task instanceof Hunt) && !(run.task instanceof Flee)) {
            // Whatever she was doing, something is chewing on the body.
            run.label = L_HUNT;
            fight(run, host, threat);
        } else if (run.task != null && !run.task.tick(run, host)) {
            String line = run.ending == null ? "pilot_done" : run.ending;
            Object[] args = run.ending == null ? new Object[0] : run.endingArgs;
            run.task = null;
            run.ending = null;
            run.clear();
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
        } else if (run.task != null && threat == null && !(run.task instanceof Eat) && (host.getHungerManager().getFoodLevel() <= 8 || host.getHungerManager().getFoodLevel() <= 17 && host.getHealth() <= host.getMaxHealth() - 4.0F) && host.getVelocity().horizontalLengthSquared() < 0.02) {
            // Starving on the job: a bite from the pockets, then on with it.
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

        if (host.getAir() < 100 || host.isInLava()) {
            run.flags |= JUMP;
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
                "PILOT {} at {} way {} {} break {} attack {} path {} stuck {} burrow {} flags {}",
                run.task == null ? "-" : run.task.getClass().getSimpleName(),
                String.format("%.1f %.1f %.1f", host.getX(), host.getY(), host.getZ()),
                run.way.size(),
                run.way.isEmpty() ? "" : String.format("%.1f %.1f %.1f", run.way.get(0).x, run.way.get(0).y, run.way.get(0).z),
                run.breakPos == null ? "" : run.breakPos.toShortString(),
                run.attackId,
                run.path == null ? "none" : run.path.getLength() + (run.path.reachesTarget() ? " reaches" : " partial"),
                run.stuck,
                run.burrowFor,
                run.flags
            );
        }

        send(host, run);
    }

    private static void send(ServerPlayerEntity host, Run run) {
        int hash = run.way.hashCode() * 31 + java.util.Objects.hash(run.breakPos, run.usePos, run.attackId, run.look, run.flags, run.slot, run.label);
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

        ServerPlayNetworking.send(host, HostNetworking.PILOT, buf);
    }

    // ------------------------------------------------------------------ what she can make the body do

    /** Something hostile that has the body for its target and is close enough to hurt it. */
    private static LivingEntity threat(ServerPlayerEntity host) {
        LivingEntity best = null;
        double bestDistance = 36.0;

        for (MobEntity mob : host.getWorld().getEntitiesByClass(MobEntity.class, host.getBoundingBox().expand(6.0), x -> x.isAlive() && x.getTarget() == host && PhaseFx.samePhase(x, host))) {
            double d = mob.squaredDistanceTo(host);
            if (d < bestDistance) {
                bestDistance = d;
                best = mob;
            }
        }

        return best;
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

        if (dig > NONE && run.burrowFor > 0) {
            run.burrowFor--;
            burrow(run, host, to, null, dig == ANYTHING);
            return false;
        }

        BlockPos goal = BlockPos.ofFloored(to);
        long now = host.getWorld().getTime();
        if (run.path == null || run.pathGoal == null || !goal.isWithinDistance(run.pathGoal, 1.5) || now - run.pathAt > 30L) {
            plan(run, host, goal);
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
                // A hole or lava ahead: not that way.
                run.turn = run.turn % 3 + 1;
                run.turnFor = 40;
                return;
            }
        }

        for (BlockPos pos : open) {
            if (passable(world, pos)) {
                continue;
            }

            if (!breakable(world, pos) || !anything && !natural(world.getBlockState(pos))) {
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

        walk(run, host, Vec3d.ofBottomCenter(pos), 0.4, mayBurrow ? ANYTHING : NONE, false);
        return false;
    }

    /** The body is told to fight this creature with the best weapon it carries. */
    static void fight(Run run, ServerPlayerEntity host, LivingEntity foe) {
        run.slot = equip(host, Pilot::weaponWorth);
        run.attackId = foe.getId();
        double away = (double)host.distanceTo(foe);
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

        double sum = 0.0;

        for (EntityAttributeModifier modifier : stack.getAttributeModifiers(EquipmentSlot.MAINHAND).get(EntityAttributes.GENERIC_ATTACK_DAMAGE)) {
            sum += modifier.getValue();
        }

        return sum;
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

        if (best < 9) {
            return best;
        }

        int hand = inventory.selectedSlot;
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
                run.end("pilot_hunted", this.kills);
                return false;
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
    static ItemEntity lying(ServerPlayerEntity host, double within, Predicate<ItemStack> wanted) {
        ItemEntity best = null;
        double bestDistance = Double.MAX_VALUE;

        for (ItemEntity item : host.getWorld().getEntitiesByClass(ItemEntity.class, host.getBoundingBox().expand(within), x -> x.isAlive() && !x.getStack().isEmpty() && PhaseFx.samePhase(x, host))) {
            double d = item.squaredDistanceTo(host);
            if (d < bestDistance && (wanted == null || wanted.test(item.getStack())) && (d < 25.0 || host.canSee(item))) {
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

            for (ItemEntity item : host.getWorld().getEntitiesByClass(ItemEntity.class, host.getBoundingBox().expand(8.0), x -> x.isAlive() && !x.getStack().isEmpty() && PhaseFx.samePhase(x, host) && !this.lost.contains(x.getId()))) {
                double d = item.squaredDistanceTo(host);
                if (d < best) {
                    best = d;
                    this.loot = item;
                }
            }

            return this.loot;
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

                    for (BlockPos near : BlockPos.iterate(this.target.add(-1, -1, -1), this.target.add(1, 1, 1))) {
                        if (!this.skip.contains(near) && !this.vein.contains(near) && this.fits(world.getBlockState(near)) && world.getBlockEntity(near) == null) {
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
                    this.spent = 0;
                }

                return true;
            }

            ItemEntity drop = this.got > 0 && this.pause++ < 160 ? this.drop(host) : null;
            if (drop != null) {
                walk(run, host, drop.getPos(), 0.3, ANYTHING, false);
                return true;
            }

            if (run.ticks % 10 == 0) {
                for (Predicate<BlockState> tier : this.kind != null ? List.of(this.kind) : this.tiers) {
                    BlockPos found = seen(host, tier, 14, this.skip);
                    if (found != null) {
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
            ServerWorld world = host.getServerWorld();
            if (this.found != null) {
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
        return e -> e instanceof Monster || e instanceof MobEntity mob && mob.getTarget() == host;
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
        if (HostManager.isBroken(host)) {
            HostManager.deadChip(host);
            return;
        }

        boolean held = active(host) && run(host).held;
        ServerWorld world = host.getServerWorld();
        String subject = order.subject();
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
        int linger = farm ? 900 : 100;
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
            go(host, new Hunt(host, e -> e == target, 1, false, 60), held);
        } else if (order.anyPlayer()) {
            HostManager.say(host, "pilot_none", 2);
        } else if (order.what().equals("food") || order.what().equals("animals")) {
            go(host, new Hunt(host, meat(), want > 0 ? want : (farm ? 0 : 3), false, 900), held);
        } else if (alias != null && MOBS.contains(alias)) {
            go(host, new Hunt(host, ofKind(alias), want, false, linger), held);
        } else if (order.what().equals("hostile") || order.subject().isBlank()) {
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
        Map.entry("potato", "#potatoes"), Map.entry("beetroot", "#beetroots"), Map.entry("sugar_cane", "#sugar_cane"), Map.entry("cactus", "#cactus")
    );

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
            Predicate<BlockState> tier = switch (block) {
                case "iron", "gold", "copper", "lapis", "log" -> state -> HostManager.isStuff(state, block);
                case "leaves" -> state -> state.isIn(net.minecraft.registry.tag.BlockTags.LEAVES);
                case "grass" -> state -> Registries.BLOCK.getId(state.getBlock()).getPath().endsWith("grass") && !state.isOf(Blocks.GRASS_BLOCK);
                case "snow" -> state -> state.isOf(Blocks.SNOW) || state.isOf(Blocks.SNOW_BLOCK);
                case "wheat", "carrots", "potatoes", "beetroots" -> state -> Registries.BLOCK.getId(state.getBlock()).getPath().equals(block) && ripe(state);
                default -> state -> Registries.BLOCK.getId(state.getBlock()).getPath().equals(block);
            };
            boolean ore = block.equals("iron") || block.equals("gold") || block.equals("copper") || block.equals("lapis") || block.endsWith("_ore");
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

        String subject = order.subject();
        String[] structure = place(subject, STRUCTURES);
        String[] biome = structure == null ? place(subject, BIOMES) : null;
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
            || state.isOf(Blocks.MELON)
            || state.isOf(Blocks.PUMPKIN);
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
}
