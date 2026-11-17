package com.windykindy49.windyorigins.client;

import com.windykindy49.windyorigins.host.HostNetworking;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Volta at the controls of the Host's own body. The server decides what she is after and sends the next few
 * steps; this side carries them out the way a player would, with the same keys: it turns the head, holds
 * "forward", jumps, holds the attack button on a block, clicks on a creature. Nothing here moves the player
 * in any way the keyboard and mouse could not.
 *
 * Any movement key of the Host's own takes the body back at once.
 */
public final class PilotClient {
    public static final int SPRINT = 1;
    public static final int SNEAK = 2;
    public static final int JUMP = 4;
    public static final int USE = 8;
    public static final int BAG = 16;
    public static final int CLICK = 32;
    public static final int SWEEP = 64;
    public static final int MOUNT = 128;
    /** A long straight run: she jumps as she sprints, the way a player in a hurry does. */
    public static final int HOP = 256;
    public static final int BACK = 512;
    public static final int LEAP = 1024;
    public static final int FLY = 2048;
    public static final int DUEL = 4096;
    public static final int OFFHAND = 32768;
    public static final int CRIT = 65536;
    public static final int HOLD = 131072;
    /** Whether there is ground to stand on a step that way: forward and sideways as the keys have them, turned by where she faces. */
    private static boolean hot(ClientPlayerEntity player, double x, double z) {
        net.minecraft.util.math.BlockPos at = net.minecraft.util.math.BlockPos.ofFloored(x, player.getY() + 0.2, z);
        for (net.minecraft.util.math.BlockPos cell : new net.minecraft.util.math.BlockPos[]{at, at.down()}) {
            net.minecraft.block.BlockState state = player.getWorld().getBlockState(cell);
            if (state.getBlock() instanceof net.minecraft.block.AbstractFireBlock || state.isOf(net.minecraft.block.Blocks.MAGMA_BLOCK) || player.getWorld().getFluidState(cell).isIn(net.minecraft.registry.tag.FluidTags.LAVA)) {
                return true;
            }
        }

        return false;
    }

    /** Fire or lava in the next step or the one after it, that way, anywhere the width of the body would pass: at the height of the feet or in the floor. */
    private static boolean burning(ClientPlayerEntity player, float forward, float sideways) {
        double yaw = Math.toRadians((double)player.getYaw());
        double dx = (double)sideways * Math.cos(yaw) - (double)forward * Math.sin(yaw);
        double dz = (double)forward * Math.cos(yaw) + (double)sideways * Math.sin(yaw);
        double len = Math.max(1.0E-4, Math.sqrt(dx * dx + dz * dz));
        dx /= len;
        dz /= len;

        for (double far : new double[]{0.5, 1.0, 1.5}) {
            for (double aside : new double[]{-0.35, 0.0, 0.35}) {
                if (hot(player, player.getX() + dx * far - dz * aside, player.getZ() + dz * far + dx * aside)) {
                    return true;
                }
            }
        }

        return false;
    }

    /** Standing in it: any corner of the body over fire or lava. */
    private static boolean inFire(ClientPlayerEntity player) {
        for (double ox : new double[]{-0.3, 0.3}) {
            for (double oz : new double[]{-0.3, 0.3}) {
                if (hot(player, player.getX() + ox, player.getZ() + oz)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * A drop of four blocks or more, or lava, along the way she faces: behind her within two and a half blocks when
     * the reach given is negative, ahead of her within that reach when it is not. A wall in between is no edge.
     */
    private static boolean edge(ClientPlayerEntity player, float reach) {
        double yaw = Math.toRadians((double)player.getYaw());
        double sign = reach < 0.0F ? -1.0 : 1.0;
        double dx = -Math.sin(yaw) * sign;
        double dz = Math.cos(yaw) * sign;
        net.minecraft.world.World world = player.getWorld();
        double[] fars = reach < 0.0F ? new double[]{1.0, 1.75, 2.5} : new double[]{(double)reach, (double)reach + 0.5};

        for (double far : fars) {
            net.minecraft.util.math.BlockPos cell = net.minecraft.util.math.BlockPos.ofFloored(player.getX() + dx * far, player.getY() + 0.2, player.getZ() + dz * far);
            if (!world.getBlockState(cell).getCollisionShape(world, cell).isEmpty() || !world.getBlockState(cell.up()).getCollisionShape(world, cell.up()).isEmpty()) {
                return false;
            }

            if (world.getFluidState(cell).isIn(net.minecraft.registry.tag.FluidTags.LAVA)) {
                return true;
            }

            int down = 0;
            while (down < 4) {
                net.minecraft.util.math.BlockPos under = cell.down(down + 1);
                if (world.getFluidState(under).isIn(net.minecraft.registry.tag.FluidTags.LAVA)) {
                    return true;
                }

                if (!world.getBlockState(under).getCollisionShape(world, under).isEmpty() || !world.getFluidState(under).isEmpty()) {
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

    private static boolean footing(ClientPlayerEntity player, float forward, float sideways) {
        double yaw = Math.toRadians((double)player.getYaw());
        double dx = (double)sideways * Math.cos(yaw) - (double)forward * Math.sin(yaw);
        double dz = (double)forward * Math.cos(yaw) + (double)sideways * Math.sin(yaw);

        for (double far : new double[]{0.8, 1.6}) {
            net.minecraft.util.math.BlockPos at = net.minecraft.util.math.BlockPos.ofFloored(player.getX() + dx * far, player.getY() + 0.2, player.getZ() + dz * far);
            boolean ground = false;
            // Fire stands on the floor, at the height of the feet, not under them.
            if (player.getWorld().getBlockState(at).getBlock() instanceof net.minecraft.block.AbstractFireBlock || player.getWorld().getFluidState(at).isIn(net.minecraft.registry.tag.FluidTags.LAVA)) {
                return false;
            }

            for (int down = 0; down <= 2 && !ground; down++) {
                net.minecraft.util.math.BlockPos under = at.down(down + 1);
                net.minecraft.block.BlockState state = player.getWorld().getBlockState(under);
                if (!player.getWorld().getFluidState(under.up()).isEmpty() && player.getWorld().getFluidState(under.up()).isIn(net.minecraft.registry.tag.FluidTags.LAVA)
                    || state.isOf(net.minecraft.block.Blocks.MAGMA_BLOCK) || state.isOf(net.minecraft.block.Blocks.CACTUS) || state.getBlock() instanceof net.minecraft.block.AbstractFireBlock) {
                    return false;
                }

                ground = !state.getCollisionShape(player.getWorld(), under).isEmpty();
            }

            if (!ground) {
                return false;
            }
        }

        return true;
    }

    public static final int FEINT = 262144;
    public static final int DROP = 524288;
    /** Between two: she looks where she is told to, and stands, or backs away from there, with her shield to it. */
    public static final int MID = 1048576;
    private static int dropWait;
    /** Which way round a raised shield, and for how long yet. */
    private static int holdSide = 1;
    private static int holdFor;
    /** Up for a blow on the way down: the run is let go near the top of the jump, since a blow at a run is never the heavy one. */
    private static boolean critAir;
    /** Swimming: no try at it for this many ticks more; how long she has been trying to get her head under; up for air. */
    private static int swimOff;
    private static int swimDip;
    private static boolean breathe;
    /** In a duel: straight at him, no circling. */
    public static final int PRESS = 8192;
    /** In a duel: backwards out of his reach, eyes still on him. */
    public static final int KITE = 16384;
    private static int wtap;
    private static int strafe = 1;
    private static int strafeFor;
    private static int backWait;
    private static int mountWait;
    private static boolean flown;
    private static int grounded;
    private static int falling;
    /** The label that says this body is on another Host's strings, not at its own Volta's hands. */
    public static final int PUPPET = 18;
    private static boolean puppet;
    private static int struggles;
    private static boolean lastJump;
    private static boolean critJump;
    private static int critWait;
    private static int hitFlash;
    private static int useFlash;
    private static BlockPos lastBreak;
    private static boolean wasEating;
    private static int screenRest;
    private static boolean active;
    private static int label;
    private static int flags;
    private static int slot = -1;
    private static final List<Vec3d> PATH = new ArrayList<>();
    private static BlockPos breakPos;
    private static BlockPos usePos;
    private static int attackId = -1;
    private static Vec3d look;
    private static BlockPos placeAgainst;
    private static Direction placeFace;
    private static int placeWait;
    private static int clickWait;
    /** The inventory screen is up because she is rummaging in it. */
    private static boolean bagOpen;
    /** The Host's own keys have all been up since she took over: from now on pressing one takes the body back. */
    private static boolean armed;
    private static boolean digging;
    private static boolean using;
    private static int swingWait;
    private static int useWait;
    private static int since;
    private static float forward;
    private static float sideways;
    private static boolean jump;
    private static boolean sneak;
    private static double offX;
    private static double offY;

    private PilotClient() {
    }

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(HostNetworking.PILOT, (client, handler, buf, sender) -> {
            boolean on = buf.readBoolean();
            if (!on) {
                client.execute(() -> stop(client));
                return;
            }

            int task = buf.readVarInt();
            int bits = buf.readVarInt();
            int hand = buf.readVarInt();
            int count = Math.min(buf.readVarInt(), 32);
            List<Vec3d> way = new ArrayList<>();

            for (int i = 0; i < count; i++) {
                way.add(new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble()));
            }

            BlockPos toBreak = buf.readBoolean() ? buf.readBlockPos() : null;
            BlockPos toUse = buf.readBoolean() ? buf.readBlockPos() : null;
            int toAttack = buf.readInt();
            Vec3d toLook = buf.readBoolean() ? new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble()) : null;
            BlockPos against = buf.readBoolean() ? buf.readBlockPos() : null;
            Direction face = against != null ? Direction.byId(buf.readByte()) : null;
            client.execute(() -> {
                if (!active) {
                    active = true;
                    armed = false;
                    since = 0;
                    swingWait = 4;
                    puppet = task == PUPPET;
                    struggles = 0;
                    VoltaFx.pilotOn(puppet);
                }

                label = task;
                flags = bits;
                slot = hand;
                PATH.clear();
                PATH.addAll(way);
                breakPos = toBreak;
                if (toUse != null) {
                    usePos = toUse;
                }

                attackId = toAttack;
                look = toLook;
                placeAgainst = against;
                placeFace = face;
            });
        });
        ClientTickEvents.START_CLIENT_TICK.register(PilotClient::tick);
    }

    public static boolean active() {
        return active;
    }

    /** Should the attack button count as held this tick? Asked where the game decides whether a block is being broken. */
    public static boolean digging() {
        // Not on the tick a screen shuts: the game holds the attack button off for as long as it has seen it held
        // since a screen was up, and has to see it let go once.
        return active && digging && screenRest == 0;
    }

    public static int age() {
        return since;
    }

    /** On another Host's strings. */
    public static boolean puppet() {
        return active && puppet;
    }

    public static boolean running() {
        return active && (flags & SPRINT) != 0 && forward > 0.0F;
    }

    public static int flags() {
        return flags;
    }

    /** What her hands are on: the block being broken or built against, or the creature being fought. Null when they are free. */
    public static Vec3d mark(MinecraftClient client) {
        if (attackId >= 0 && client.world != null) {
            Entity target = client.world.getEntityById(attackId);
            if (target != null && target.isAlive()) {
                return target.getPos().add(0.0, (double)target.getHeight() * 0.5, 0.0);
            }
        }

        if (breakPos != null) {
            return Vec3d.ofCenter(breakPos);
        }

        if (placeAgainst != null && placeFace != null) {
            return Vec3d.ofCenter(placeAgainst.offset(placeFace));
        }

        return null;
    }

    public static boolean fighting() {
        return attackId >= 0;
    }

    public static List<Vec3d> path() {
        return PATH;
    }

    private static void stop(MinecraftClient client) {
        if (using) {
            client.options.useKey.setPressed(false);
        }

        if (bagOpen && client.currentScreen instanceof net.minecraft.client.gui.screen.ingame.InventoryScreen) {
            client.setScreen(null);
        }

        bagOpen = false;
        if (active) {
            VoltaFx.pilotOff();
        }

        puppet = false;
        struggles = 0;
        lastBreak = null;

        active = false;
        digging = false;
        using = false;
        PATH.clear();
        breakPos = null;
        usePos = null;
        attackId = -1;
        look = null;
        placeAgainst = null;
        forward = 0.0F;
        sideways = 0.0F;
        jump = false;
        sneak = false;
    }

    /** The Host wants the body back. */
    private static void takeBack(MinecraftClient client) {
        stop(client);
        if (client.getNetworkHandler() != null) {
            ClientPlayNetworking.send(HostNetworking.PILOT_STOP, new PacketByteBuf(Unpooled.buffer()));
        }
    }

    /**
     * Called where the keyboard has just been read. With the Host's hands on the keys the body is theirs again;
     * otherwise the keys are "pressed" for them.
     */
    public static void steer(Input input) {
        if (!active) {
            return;
        }

        ClientPlayerEntity rider = MinecraftClient.getInstance().player;
        if (!puppet && rider != null && rider.getVehicle() instanceof net.minecraft.entity.vehicle.BoatEntity boat) {
            // In a boat the keys mean something else: left and right swing the bow, forward rows.
            boolean ownKeys = input.pressingForward || input.pressingBack || input.pressingLeft || input.pressingRight || input.jumping || input.sneaking;
            if (armed && ownKeys) {
                takeBack(MinecraftClient.getInstance());
                return;
            }

            armed |= !ownKeys;
            Vec3d go = PATH.isEmpty() ? null : PATH.get(0);
            input.pressingForward = false;
            input.pressingBack = false;
            input.pressingLeft = false;
            input.pressingRight = false;
            input.jumping = false;
            input.sneaking = sneak;
            if (go == null && !sneak) {
                input.pressingBack = boat.getVelocity().horizontalLength() > 0.06;
            }

            if (go != null && !sneak) {
                float want = (float)(MathHelper.atan2(go.z - boat.getZ(), go.x - boat.getX()) * 180.0 / Math.PI) - 90.0F;
                float off = MathHelper.wrapDegrees(want - boat.getYaw());
                input.pressingLeft = off < -6.0F;
                input.pressingRight = off > 6.0F;
                input.pressingForward = Math.abs(off) < 75.0F;
            }

            input.movementForward = input.pressingForward ? 1.0F : 0.0F;
            input.movementSideways = input.pressingLeft ? 1.0F : (input.pressingRight ? -1.0F : 0.0F);
            return;
        }

        if (!puppet && rider != null && rider.getVehicle() instanceof net.minecraft.entity.vehicle.AbstractMinecartEntity) {
            // In a cart there is one key: forward, along the rails, the way she is looking.
            boolean ownKeys = input.pressingForward || input.pressingBack || input.pressingLeft || input.pressingRight || input.jumping || input.sneaking;
            if (armed && ownKeys) {
                takeBack(MinecraftClient.getInstance());
                return;
            }

            armed |= !ownKeys;
            input.pressingForward = !sneak && !PATH.isEmpty();
            input.pressingBack = false;
            input.pressingLeft = false;
            input.pressingRight = false;
            input.jumping = false;
            input.sneaking = sneak;
            input.movementForward = input.pressingForward ? 1.0F : 0.0F;
            input.movementSideways = 0.0F;
            return;
        }

        if (puppet) {
            // Not theirs to take back with a step: the strings come off by hammering the jump key.
            if (input.jumping && !lastJump && ++struggles >= 5) {
                lastJump = false;
                takeBack(MinecraftClient.getInstance());
                return;
            }

            lastJump = input.jumping;
            input.pressingForward = forward > 0.0F;
            input.pressingBack = forward < 0.0F;
            input.pressingLeft = sideways > 0.0F;
            input.pressingRight = sideways < 0.0F;
            input.movementForward = forward;
            input.movementSideways = sideways;
            input.jumping = jump;
            input.sneaking = sneak;
            if (sneak) {
                input.movementForward *= 0.3F;
                input.movementSideways *= 0.3F;
            }

            return;
        }

        boolean own = input.pressingForward || input.pressingBack || input.pressingLeft || input.pressingRight || input.jumping || input.sneaking;
        if (!armed) {
            armed = !own;
        } else if (own) {
            takeBack(MinecraftClient.getInstance());
            return;
        }

        if (own) {
            // Still holding what was held when she took over: nothing is pressed twice.
            return;
        }

        input.pressingForward = forward > 0.0F;
        input.pressingBack = forward < 0.0F;
        input.pressingLeft = sideways > 0.0F;
        input.pressingRight = sideways < 0.0F;
        input.movementForward = forward;
        input.movementSideways = sideways;
        input.jumping = jump;
        input.sneaking = sneak;
        if (sneak) {
            input.movementForward *= 0.3F;
            input.movementSideways *= 0.3F;
        }
    }

    private static boolean wet(MinecraftClient client, BlockPos pos) {
        return client.world.getFluidState(pos).isIn(net.minecraft.registry.tag.FluidTags.WATER);
    }

    private static float yawTo(ClientPlayerEntity player, Vec3d to) {
        return (float)(MathHelper.atan2(to.z - player.getZ(), to.x - player.getX()) * 180.0 / Math.PI) - 90.0F;
    }

    /** Turns the head towards a point, no faster than a hand on a mouse does. */
    private static boolean turn(ClientPlayerEntity player, Vec3d aim, float most) {
        Vec3d eye = player.getEyePos();
        double dx = aim.x - eye.x;
        double dy = aim.y - eye.y;
        double dz = aim.z - eye.z;
        double flat = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float)(MathHelper.atan2(dz, dx) * 180.0 / Math.PI) - 90.0F;
        float pitch = (float)(-(MathHelper.atan2(dy, flat) * 180.0 / Math.PI));
        float byYaw = MathHelper.wrapDegrees(yaw - player.getYaw());
        float byPitch = pitch - player.getPitch();
        player.setYaw(player.getYaw() + MathHelper.clamp(byYaw, -most, most));
        player.setPitch(MathHelper.clamp(player.getPitch() + MathHelper.clamp(byPitch, -most * 0.7F, most * 0.7F), -90.0F, 90.0F));
        return Math.abs(byYaw) <= most && Math.abs(byPitch) <= most * 0.7F;
    }

    private static void tick(MinecraftClient client) {
        if (!active) {
            return;
        }

        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null || !player.isAlive() || !puppet && !HostClient.isHost(player)) {
            stop(client);
            return;
        }

        if (client.isPaused()) {
            return;
        }

        since++;
        if (client.currentScreen != null) {
            screenRest = 3;
        } else if (screenRest > 0) {
            screenRest--;
        }

        if (hitFlash > 0) {
            hitFlash--;
        }

        if (useFlash > 0) {
            useFlash--;
        }

        if (!puppet && armed && (client.options.attackKey.isPressed() || !using && client.options.useKey.isPressed())) {
            takeBack(client);
            return;
        }

        if (slot >= 0 && slot < 9 && player.getInventory().selectedSlot != slot) {
            player.getInventory().selectedSlot = slot;
        }

        Entity target = attackId >= 0 ? client.world.getEntityById(attackId) : null;
        if (target != null && !target.isAlive()) {
            target = null;
        }

        // Steps already made are behind her.
        while (!PATH.isEmpty()) {
            Vec3d step = PATH.get(0);
            double dx = step.x - player.getX();
            double dz = step.z - player.getZ();
            // Made once she is over it, and, with her feet on the ground, not still on the lip of the block above it.
            double above = player.getY() - step.y;
            if (dx * dx + dz * dz < (PATH.size() > 1 ? 0.42 : 0.1) && above > -1.7 && (above < 0.6 || !player.isOnGround() && above < 1.7)) {
                PATH.remove(0);
            } else {
                break;
            }
        }

        Vec3d go = PATH.isEmpty() ? null : PATH.get(0);
        Float heading = null;
        if (go != null) {
            heading = yawTo(player, go);
        } else if (target != null) {
            double dx = target.getX() - player.getX();
            double dz = target.getZ() - player.getZ();
            double away = Math.sqrt(dx * dx + dz * dz) - (double)target.getWidth() * 0.5;
            // The length of the arm and no nearer: forward to reach, back out of the other's reach.
            if (away > 2.6) {
                heading = yawTo(player, target.getPos());
            } else if (away < 2.0 && (flags & MOUNT) == 0) {
                heading = yawTo(player, target.getPos()) + 180.0F;
            }
        }

        // Deep water with a way to go across it is swum the way anyone swims a lake: head under, flat out in the water
        // and at a run, which is two and a half times as fast as treading along the top. Up for air when it runs low.
        Float swim = null;
        boolean swimJump = false;
        boolean swimDive = false;
        if (swimOff > 0) {
            swimOff--;
        }

        if (go != null && target == null && breakPos == null && look == null && player.isTouchingWater() && !player.hasVehicle() && swimOff <= 0
            && (flags & (SNEAK | USE | BAG | CLICK | DROP | JUMP)) == 0 && player.getHungerManager().getFoodLevel() > 6) {
            BlockPos at = player.getBlockPos();
            Vec3d end = PATH.get(PATH.size() - 1);
            double ex = end.x - player.getX();
            double ez = end.z - player.getZ();
            boolean deep = wet(client, at) && (wet(client, at.down()) || wet(client, at.up()) || player.isSwimming());
            boolean on = wet(client, BlockPos.ofFloored(go.x, player.getY(), go.z)) || wet(client, BlockPos.ofFloored(go.x, go.y, go.z)) || wet(client, BlockPos.ofFloored(go.x, go.y - 1.0, go.z));
            if (deep && on && ex * ex + ez * ez > 9.0) {
                if (player.getAir() < 100) {
                    breathe = true;
                } else if (player.getAir() > 250) {
                    breathe = false;
                }

                BlockPos.Mutable top = at.mutableCopy();
                int over = 0;

                while (over < 5 && wet(client, top)) {
                    top.move(net.minecraft.util.math.Direction.UP);
                    over++;
                }

                if (breathe || player.horizontalCollision) {
                    swim = -25.0F;
                    swimJump = true;
                    swimDip = 0;
                } else if (!player.isSwimming() && !player.isSubmergedInWater()) {
                    // Still head out: she stops treading and ducks under, which is the crouch key in water.
                    swim = 25.0F;
                    swimDive = true;
                    if (++swimDip > 40) {
                        swimOff = 120;
                        swimDip = 0;
                        swim = null;
                        swimDive = false;
                    }
                } else {
                    // Just under the top, level.
                    swimDip = 0;
                    swim = (float)MathHelper.clamp((player.getY() - ((double)top.getY() - 0.95)) * 45.0, -30.0, 30.0);
                }
            }
        }

        if (swim == null) {
            swimDip = 0;
            breathe = false;
        }

        Vec3d aim = null;
        if (swim != null) {
            double dx = go.x - player.getX();
            double dz = go.z - player.getZ();
            double flat = Math.max(0.6, Math.sqrt(dx * dx + dz * dz));
            aim = new Vec3d(player.getX() + dx / flat * 4.0, player.getEyeY() - Math.tan(Math.toRadians((double)swim)) * 4.0, player.getZ() + dz / flat * 4.0);
        } else if (target != null && (flags & MID) != 0 && look != null) {
            aim = look;
        } else if (target != null) {
            aim = target.getPos().add(offX, (double)target.getHeight() * (0.55 + offY), offX);
        } else if (breakPos != null) {
            aim = look != null ? look : Vec3d.ofCenter(breakPos);
        } else if (look != null) {
            aim = look;
        } else if (go != null) {
            Vec3d ahead = PATH.get(Math.min(1, PATH.size() - 1));
            double dx = ahead.x - player.getX();
            double dz = ahead.z - player.getZ();
            if (dx * dx + dz * dz > 0.3) {
                aim = new Vec3d(ahead.x, player.getEyeY() + MathHelper.clamp((ahead.y - player.getY()) * 0.5, -1.2, 1.2), ahead.z);
            }
        }

        boolean facing = aim == null || turn(player, aim, target != null ? 30.0F : 24.0F);
        forward = 0.0F;
        sideways = 0.0F;
        jump = (flags & JUMP) != 0;
        sneak = (flags & SNEAK) != 0;
        if (heading != null) {
            float off = (float)Math.toRadians((double)MathHelper.wrapDegrees(heading - player.getYaw()));
            float ahead = MathHelper.cos(off);
            float aside = -MathHelper.sin(off);
            forward = ahead > 0.38F ? 1.0F : (ahead < -0.38F ? -1.0F : 0.0F);
            sideways = aside > 0.38F ? 1.0F : (aside < -0.38F ? -1.0F : 0.0F);
            if (go != null) {
                double dx = go.x - player.getX();
                double dz = go.z - player.getZ();
                boolean up = go.y - player.getY() > 0.55;
                if (player.isOnGround() && (up && dx * dx + dz * dz < 3.2 || player.horizontalCollision)) {
                    jump = true;
                }

                if (player.isTouchingWater() || player.isInLava()) {
                    jump = (flags & JUMP) != 0 || go.y >= player.getY() - 0.4 || player.horizontalCollision;
                }
            } else if (player.isTouchingWater()) {
                jump = true;
            }
        } else if (player.isSubmergedInWater()) {
            jump = true;
        }

        if (swim != null) {
            forward = 1.0F;
            sideways = 0.0F;
            jump = swimJump;
            sneak = swimDive;
        }

        // Struck, with her feet still on the ground and his blow already throwing her back: a jump at a run in that
        // same instant, towards him. The push a running jump gives goes against the throw of the blow, so she comes
        // down a step nearer than he meant her to, and what she lands on the way down is the heavy blow. Players call
        // it a jump reset; it is three keys pressed at the right moment and nothing else.
        boolean reset = (flags & DUEL) != 0 && (flags & (SNEAK | HOLD | KITE | USE)) == 0 && target != null && go == null && player.hurtTime >= 9 && player.isOnGround()
            && player.getVelocity().y > 0.2 && !player.isTouchingWater() && !player.isUsingItem() && player.distanceTo(target) < 5.0F
            && player.getHungerManager().getFoodLevel() > 6 && footing(player, 1.0F, 0.0F);
        if (reset) {
            jump = true;
            forward = 1.0F;
            sideways = 0.0F;
        }

        if ((flags & LEAP) != 0 && go != null && player.isOnGround() && forward > 0.0F) {
            // Off the last of the floor: the foot leaves it and she is in the air.
            double gx = go.x - player.getX();
            double gz = go.z - player.getZ();
            double len = Math.sqrt(gx * gx + gz * gz);
            if (len > 0.8) {
                BlockPos under = BlockPos.ofFloored(player.getX() + gx / len * 0.42, player.getY() - 0.5, player.getZ() + gz / len * 0.42);
                if (client.world.getBlockState(under).getCollisionShape(client.world, under).isEmpty()) {
                    jump = true;
                }
            }
        }

        if ((flags & DUEL) != 0 && target != null && go == null) {
            // A duel: round the other in a circle, changing hands now and then, and never standing where the last blow came from.
            double near = (double)player.distanceTo(target);
            if (--strafeFor <= 0) {
                strafe = player.getRandom().nextBoolean() ? 1 : -1;
                strafeFor = 10 + player.getRandom().nextInt(18);
            }

            if (near < 4.5 && (flags & PRESS) == 0) {
                sideways = (float)strafe;
            }

            if ((flags & HOLD) != 0) {
                // Round a raised shield, a step beyond his reach, one way for a good while: it covers only what is in front of it.
                if (--holdFor <= 0) {
                    holdSide = -holdSide;
                    holdFor = 50 + player.getRandom().nextInt(40);
                }

                if ((flags & KITE) != 0 || (flags & FEINT) != 0) {
                    sideways = 0.0F;
                    forward = (flags & KITE) != 0 ? -1.0F : 0.0F;
                } else if ((flags & USE) != 0) {
                    // Behind her own shield she stands where his blow will have to come: no going round, she is too slow for it.
                    // And no giving ground when he walks up to it: a man who only has to walk at her to move her walks
                    // her into a corner, or off an edge.
                    sideways = 0.0F;
                    forward = near > 3.8 ? 1.0F : 0.0F;
                } else {
                    // With no shield, out of a lunge's reach, and round him there.
                    sideways = (float)holdSide;
                    forward = near > 5.6 ? 1.0F : (near < 5.0 ? -1.0F : 0.0F);
                }
            } else if (wtap > 0) {
                // The sprint let go for a moment after a blow: the next one throws them back again.
                wtap--;
                forward = 0.0F;
                player.setSprinting(false);
            } else if ((flags & KITE) != 0) {
                forward = -1.0F;
            } else if (near > ((flags & PRESS) != 0 ? 1.3 : 2.3)) {
                // Pressing, she is still moving in when the blow lands: it is the run that throws him back.
                forward = 1.0F;
            }
            if ((flags & MID) != 0) {
                // Her shield to the middle of two of them: no going round either, and backwards out from between them if told to.
                // With one to either side she has her shield to one of them and goes sideways, one way, out of it.
                sideways = (flags & HOLD) != 0 ? (float)holdSide : 0.0F;
                forward = (flags & KITE) != 0 ? -1.0F : 0.0F;
            }

            // An edge close at her back and a man in front of her: one blow of his and she is over it. Whatever
            // stance she was told to keep, she gives no more ground towards it, and moves along it instead.
            if (forward < 0.0F && target instanceof net.minecraft.entity.player.PlayerEntity && near < 6.0 && edge(player, -1.0F)) {
                forward = 0.0F;
                if (sideways == 0.0F) {
                    sideways = (float)strafe;
                }
            }

            // Nor forwards over one after him, while he is still up here with her: she stops at the lip of it.
            if (forward > 0.0F && target.getY() > player.getY() - 2.0 && edge(player, 0.5F)) {
                forward = 0.0F;
            }

            // Never a step to the side or backwards onto nothing, or into fire: the eyes are on him, not on the ground.
            if (sideways != 0.0F && !footing(player, 0.0F, sideways)) {
                if (footing(player, 0.0F, -sideways)) {
                    sideways = -sideways;
                    strafe = -strafe;
                    holdSide = -holdSide;
                } else {
                    sideways = 0.0F;
                }
            }

            if (forward < 0.0F && !footing(player, forward, 0.0F)) {
                forward = 0.0F;
            }

            // Nor forwards into fire, her own or anyone's: she goes round it, or waits for him on this side of it.
            if (forward > 0.0F && burning(player, forward, 0.0F)) {
                forward = 0.0F;
                if (sideways == 0.0F) {
                    sideways = footing(player, 0.0F, (float)strafe) && !burning(player, 0.0F, (float)strafe) ? (float)strafe : (footing(player, 0.0F, (float)(-strafe)) && !burning(player, 0.0F, (float)(-strafe)) ? (float)(-strafe) : 0.0F);
                }
            }

            if (sideways != 0.0F && burning(player, 0.0F, sideways)) {
                sideways = 0.0F;
            }

            // Standing in it is not a stance. Out, by the nearest way that has ground and no fire, whatever else she was doing.
            if (inFire(player)) {
                float[][] ways = {{-1.0F, 0.0F}, {0.0F, 1.0F}, {0.0F, -1.0F}, {1.0F, 0.0F}, {-1.0F, 1.0F}, {-1.0F, -1.0F}, {1.0F, 1.0F}, {1.0F, -1.0F}};
                for (float[] way : ways) {
                    if (footing(player, way[0], way[1]) && !burning(player, way[0], way[1])) {
                        forward = way[0];
                        sideways = way[1];
                        break;
                    }
                }
            }
        } else {
            wtap = 0;
        }

        // Falling, far enough to be hurt, and something to climb within reach on the way down: she steers onto it and
        // it stops the fall, as it would for anyone who thought of it in time.
        if (!player.isOnGround() && !player.isClimbing() && !player.isTouchingWater() && !player.isFallFlying() && !player.hasVehicle()
            && player.getVelocity().y < -0.3 && (flags & (DROP | FLY)) == 0 && deep(client, player)) {
            Vec3d hold = grip(client, player);
            if (Boolean.getBoolean("windy.pilotlog")) {
                com.windykindy49.windyorigins.WindyOrigins.LOGGER.info("PILOT grip at {} vy {} fell {} hold {}", player.getPos(), player.getVelocity().y, player.fallDistance, hold);
            }

            if (hold != null) {
                float off = (float)Math.toRadians((double)MathHelper.wrapDegrees(yawTo(player, hold) - player.getYaw()));
                float ahead = MathHelper.cos(off);
                float aside = -MathHelper.sin(off);
                forward = ahead > 0.38F ? 1.0F : (ahead < -0.38F ? -1.0F : 0.0F);
                sideways = aside > 0.38F ? 1.0F : (aside < -0.38F ? -1.0F : 0.0F);
                jump = false;
                sneak = false;
            }
        }

        if ((flags & FLY) != 0 && player.isFallFlying() && player.isOnGround()) {
            // Wings open with the feet on the ground: this side thought she was up and the server did not. Fold them and go again.
            if (++grounded > 3) {
                player.stopFallFlying();
                grounded = 0;
            }
        } else {
            grounded = 0;
        }

        if ((flags & FLY) != 0 && !player.isFallFlying()) {
            // Up off the ground; then, on the way down, the jump key once more, which is what opens the wings.
            // The press comes at the very top of the jump, with all of the fall still to open them in.
            if (player.isOnGround()) {
                jump = true;
                falling = 0;
            } else if (player.getVelocity().y < 0.0) {
                jump = falling++ % 6 < 3;
            } else {
                jump = false;
                falling = 0;
            }
        } else if (player.isFallFlying()) {
            jump = false;
            forward = 0.0F;
            sideways = 0.0F;
            if ((flags & FLY) != 0 && !flown && player.getMainHandStack().isOf(net.minecraft.item.Items.FIREWORK_ROCKET) && client.interactionManager != null) {
                // The wings are open a hand's breadth over the ground: the rocket goes now or not at all.
                flown = true;
                client.interactionManager.interactItem(player, Hand.MAIN_HAND);
                player.swingHand(Hand.MAIN_HAND);
                clickWait = 10;
                useFlash = 4;
            }
        }

        if (!player.isFallFlying()) {
            flown = false;
        }

        if ((flags & DROP) != 0 && client.interactionManager != null) {
            // Falling on purpose. The last blocks of a long fall pass in a tick or two, too quick to be told from the
            // other side: the eyes go to the ground, and the moment it is in reach the bucket is emptied on it or the
            // block set on it. Landed in what the bucket held, it is taken up again.
            net.minecraft.item.ItemStack inHand = player.getMainHandStack();
            boolean falling = !player.isOnGround() && !player.isTouchingWater() && player.getVelocity().y < -0.4;
            boolean soaked = player.isTouchingWater() || player.inPowderSnow;
            if (falling || soaked && inHand.isOf(net.minecraft.item.Items.BUCKET)) {
                player.setPitch(90.0F);
                if (dropWait > 0) {
                    dropWait--;
                } else if (falling && inHand.isOf(net.minecraft.item.Items.WATER_BUCKET)) {
                    if (player.raycast(5.0, 1.0F, false).getType() == net.minecraft.util.hit.HitResult.Type.BLOCK) {
                        client.interactionManager.interactItem(player, Hand.MAIN_HAND);
                        player.swingHand(Hand.MAIN_HAND);
                        dropWait = 6;
                    }
                } else if (falling && inHand.getItem() instanceof net.minecraft.item.BlockItem) {
                    if (player.raycast(4.5, 1.0F, false) instanceof net.minecraft.util.hit.BlockHitResult ground && ground.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK && ground.getSide() == net.minecraft.util.math.Direction.UP) {
                        client.interactionManager.interactBlock(player, Hand.MAIN_HAND, ground);
                        player.swingHand(Hand.MAIN_HAND);
                        dropWait = 6;
                    }
                } else if (soaked && inHand.isOf(net.minecraft.item.Items.BUCKET)) {
                    client.interactionManager.interactItem(player, Hand.MAIN_HAND);
                    dropWait = 5;
                }
            }
        } else {
            dropWait = 0;
        }

        // In the air against a player, whether she jumped or was thrown up by his blow, the run is let go near the
        // top: what she lands on the way down is then the heavy blow.
        if (player.isOnGround() || (flags & (CRIT | DUEL)) == 0) {
            critAir = false;
        } else if (player.getVelocity().y < 0.12) {
            critAir = true;
        }

        boolean run = (flags & SPRINT) != 0 && forward > 0.0F && !player.isUsingItem() && player.getHungerManager().getFoodLevel() > 6 && !critAir;
        if (reset) {
            run = true;
        }

        if (player.isTouchingWater() && !player.isSwimming()) {
            // On top of water there is no running; under it, running is what swimming is.
            run = swim != null && player.isSubmergedInWater();
        } else if (player.isSwimming()) {
            run = swim != null;
        }

        if (run && (flags & HOP) != 0 && player.isOnGround() && player.isSprinting() && sideways == 0.0F && !player.isTouchingWater() && !player.hasVehicle()) {
            jump = true;
        }

        if (run != player.isSprinting() && (run || player.isSprinting())) {
            player.setSprinting(run);
        }

        boolean dug = digging;
        digging = false;
        critJump = false;
        if (mountWait > 0) {
            mountWait--;
        }

        if (target != null && (flags & MOUNT) != 0) {
            // Not a blow: a hand on the gunwale and in.
            if (mountWait == 0 && facing && player.distanceTo(target) < 3.6F && client.interactionManager != null) {
                client.interactionManager.interactEntity(player, target, Hand.MAIN_HAND);
                player.swingHand(Hand.MAIN_HAND);
                mountWait = 8;
                useFlash = 4;
            }
        } else if (target != null) {
            fight(client, player, target);
            if (critJump) {
                jump = true;
            }
        } else if (breakPos != null && facing) {
            dig(client, player);
        }

        // What the hands just did, for the picture: a block gone, a meal down.
        if (breakPos != null) {
            lastBreak = breakPos;
        }

        if (lastBreak != null && client.world.getBlockState(lastBreak).isAir()) {
            if (dug) {
                VoltaFx.pilotBroke(Vec3d.ofCenter(lastBreak));
            }

            lastBreak = null;
        }

        boolean eating = player.isUsingItem() && player.getActiveItem().isFood();
        if (wasEating && !eating && player.getItemUseTime() == 0) {
            VoltaFx.pilotAte();
        }

        wasEating = eating;
        if (digging) {
            hitFlash = 2;
        }

        if (using) {
            useFlash = 2;
        }

        boolean eat = (flags & USE) != 0;
        if (eat != using) {
            using = eat;
            client.options.useKey.setPressed(eat);
        }

        // The inventory comes up while she digs in it and goes down again when she has what she was after.
        boolean bag = (flags & BAG) != 0;
        if (bag && !bagOpen && client.currentScreen == null) {
            client.setScreen(new net.minecraft.client.gui.screen.ingame.InventoryScreen(player));
            bagOpen = true;
        } else if (!bag && bagOpen) {
            if (client.currentScreen instanceof net.minecraft.client.gui.screen.ingame.InventoryScreen) {
                client.setScreen(null);
            }

            bagOpen = false;
        }

        if (clickWait > 0) {
            clickWait--;
        } else if ((flags & CLICK) != 0 && facing && target == null && client.interactionManager != null && client.currentScreen == null) {
            // One press of the use button with what she put in the hand: a bucket emptied where she looks, or filled.
            client.interactionManager.interactItem(player, Hand.MAIN_HAND);
            player.swingHand(Hand.MAIN_HAND);
            clickWait = 8;
        }

        if ((flags & BACK) == 0) {
            backWait = 0;
        }

        if (placeWait > 0) {
            placeWait--;
        } else if (placeAgainst != null && target == null && facing && client.interactionManager != null) {
            // A click of the use button on the face she was shown, with what she put in the hand.
            Vec3d on = Vec3d.ofCenter(placeAgainst).add((double)placeFace.getOffsetX() * 0.5, (double)placeFace.getOffsetY() * 0.5, (double)placeFace.getOffsetZ() * 0.5);
            boolean seen = client.crosshairTarget instanceof BlockHitResult aimed && aimed.getType() == HitResult.Type.BLOCK && aimed.getBlockPos().equals(placeAgainst) && aimed.getSide() == placeFace;
            if ((flags & BACK) != 0 && !seen && ++backWait < 50) {
                // Out over the edge the face is not in sight yet: no click on what the eye cannot see.
                placeWait = 0;
            } else if ((flags & OFFHAND) != 0) {
                // Out of the off hand, with the weapon still in the other.
                if (player.getEyePos().squaredDistanceTo(on) < 4.5 * 4.5 && player.getOffHandStack().getItem() instanceof net.minecraft.item.BlockItem) {
                    client.interactionManager.interactBlock(player, Hand.OFF_HAND, new BlockHitResult(on, placeFace, placeAgainst, false));
                    player.swingHand(Hand.OFF_HAND);
                    placeWait = 3;
                    useFlash = 4;
                }
            } else if (player.getEyePos().squaredDistanceTo(on) < 4.5 * 4.5 && (player.getMainHandStack().getItem() instanceof net.minecraft.item.BlockItem || player.getMainHandStack().getItem() instanceof net.minecraft.item.HoeItem || player.getMainHandStack().isOf(net.minecraft.item.Items.END_CRYSTAL)
                || player.getMainHandStack().isOf(net.minecraft.item.Items.FLINT_AND_STEEL) || player.getMainHandStack().isOf(net.minecraft.item.Items.FIRE_CHARGE))) {
                backWait = 0;
                client.interactionManager.interactBlock(player, Hand.MAIN_HAND, seen ? (BlockHitResult)client.crosshairTarget : new BlockHitResult(on, placeFace, placeAgainst, false));
                player.swingHand(Hand.MAIN_HAND);
                placeWait = player.getMainHandStack().isOf(net.minecraft.item.Items.END_CRYSTAL) ? 1 : 4;
                useFlash = 4;
            }
        }

        if (useWait > 0) {
            useWait--;
        } else if (usePos != null) {
            Vec3d at = Vec3d.ofCenter(usePos);
            if (player.getEyePos().squaredDistanceTo(at) < 4.2 * 4.2 && client.interactionManager != null) {
                client.interactionManager.interactBlock(player, Hand.MAIN_HAND, new BlockHitResult(at, Direction.UP, usePos, false));
                player.swingHand(Hand.MAIN_HAND);
                useWait = 12;
                usePos = null;
            }
        }
    }

    /** Whether what is under a falling body is far enough down to hurt: more than four clear blocks, and no water to land in. */
    private static boolean deep(MinecraftClient client, ClientPlayerEntity player) {
        BlockPos at = player.getBlockPos();

        for (int down = 1; down <= 4; down++) {
            BlockPos cell = at.down(down);
            if (!client.world.getBlockState(cell).getCollisionShape(client.world, cell).isEmpty() || !client.world.getFluidState(cell).isEmpty()) {
                return false;
            }
        }

        return true;
    }

    /**
     * A ladder, a vine, scaffolding: the nearest thing to climb below a falling body that it can still get onto,
     * given how fast it is falling and how little a body steers in the air. Null when there is none.
     */
    private static Vec3d grip(MinecraftClient client, ClientPlayerEntity player) {
        BlockPos at = player.getBlockPos();
        double fall = Math.max(0.5, -player.getVelocity().y);
        Vec3d best = null;
        double bestFlat = 9.0;

        for (int down = 1; down <= 12 && best == null; down++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos cell = at.add(dx, -down, dz);
                    if (!client.world.getBlockState(cell).isIn(net.minecraft.registry.tag.BlockTags.CLIMBABLE)) {
                        continue;
                    }

                    double hx = (double)cell.getX() + 0.5 - player.getX();
                    double hz = (double)cell.getZ() + 0.5 - player.getZ();
                    double flat = Math.sqrt(hx * hx + hz * hz);
                    if (flat <= 0.35 + 0.1 * (double)down / fall && flat < bestFlat) {
                        best = new Vec3d((double)cell.getX() + 0.5, (double)cell.getY(), (double)cell.getZ() + 0.5);
                        bestFlat = flat;
                    }
                }
            }
        }

        return best;
    }

    /**
     * Is the crosshair on it, this tick? The game works that out once a picture, which on a slow machine is several
     * ticks old; this is the same sum done now: along the eyes, no further than the arm reaches, nothing in the way.
     */
    private static boolean aimed(ClientPlayerEntity player, Entity target) {
        Vec3d eye = player.getCameraPosVec(1.0F);
        Vec3d look = player.getRotationVec(1.0F);
        HitResult wall = player.raycast(4.5, 1.0F, false);
        double limit = wall.getType() != HitResult.Type.MISS ? wall.getPos().squaredDistanceTo(eye) : 4.5 * 4.5;
        Vec3d end = eye.add(look.multiply(4.5));
        EntityHitResult found = net.minecraft.entity.projectile.ProjectileUtil.raycast(player, eye, end, player.getBoundingBox().stretch(look.multiply(4.5)).expand(1.0), other -> !other.isSpectator() && other.canHit(), limit);
        return found != null && found.getEntity() == target && eye.squaredDistanceTo(found.getPos()) <= 9.0;
    }

    /**
     * Swings when the weapon is ready and the crosshair is on the creature, a few ticks late and a little off, like
     * anybody. One against one she jumps into the blow, to land it on the way down, where it counts for more; with
     * several on her she stays on the ground, where a sword sweeps.
     */
    private static void fight(MinecraftClient client, ClientPlayerEntity player, Entity target) {
        HitResult hit = client.crosshairTarget;
        boolean on = hit instanceof EntityHitResult entity && entity.getEntity() == target || aimed(player, target);
        float ready = player.getAttackCooldownProgress(0.0F);
        boolean sweep = (flags & SWEEP) != 0;
        boolean leap = (flags & CRIT) != 0;
        if (leap) {
            // Timed, not waited for: seven ticks from the ground to coming down, so she leaves it seven ticks before
            // the arm is ready, or at once when it is ready already, and near enough to be in reach when she lands.
            float left = (1.0F - ready) * player.getAttackCooldownProgressPerTick();
            if (player.isOnGround() && !player.isTouchingWater() && left <= 7.0F && (double)player.distanceTo(target) < 5.2) {
                critJump = true;
            }
        } else if (!sweep && on && ready > 0.8F && ready < 1.0F && player.isOnGround() && !player.isTouchingWater() && (double)player.distanceTo(target) < 3.4) {
            critJump = true;
        }

        // A crystal goes off at a touch; a player is struck the moment the arm is all but ready, not a moment after.
        boolean duel = (flags & DUEL) != 0;
        boolean crystal = target instanceof net.minecraft.entity.decoration.EndCrystalEntity;
        if ((flags & FEINT) != 0 && !crystal && !player.isUsingItem() && ready >= 1.0F) {
            // A blow at the air, meant to be seen: the arm is as spent after it as after any other.
            player.swingHand(Hand.MAIN_HAND);
            player.resetLastAttackedTicks();
            return;
        }

        if ((flags & HOLD) != 0 && !crystal) {
            return;
        }

        // The full of the arm, and a tick over for the server, which counts it a moment behind: a blow a twentieth
        // short of full does a tenth less harm, and over a fight that is one blow more to land.
        if (!crystal && (duel ? player.getAttackCooldownProgress(-1.0F) < 1.0F : ready < 1.0F) || client.interactionManager == null || player.isUsingItem()) {
            return;
        }

        boolean falling = !player.isOnGround() && player.getVelocity().y < -0.05;
        if (leap) {
            // The heavy blow is one the server agrees she is falling for, and not at a run.
            falling = !player.isOnGround() && player.fallDistance > 0.0F && !player.isSprinting();
        }

        if (swingWait > 0) {
            swingWait--;
        } else if (on && (sweep || falling || ++critWait > (leap ? 11 : 5))) {
            client.interactionManager.attackEntity(player, target);
            player.swingHand(Hand.MAIN_HAND);
            critWait = 0;
            hitFlash = 4;
            if ((flags & DUEL) != 0) {
                wtap = 2;
            }

            if (target instanceof net.minecraft.entity.LivingEntity) {
                VoltaFx.pilotHit(target.getPos().add(0.0, (double)target.getHeight() * 0.6, 0.0), falling);
            } else {
                // A boat, a crystal: a thing broken, not a creature struck.
                VoltaFx.pilotBroke(target.getPos().add(0.0, (double)target.getHeight() * 0.5, 0.0));
            }
            // A creature is struck in an uneven rhythm; a cart or a boat is hammered, or it shakes the blows off between them.
            swingWait = target instanceof net.minecraft.entity.LivingEntity && !duel ? 2 + player.getRandom().nextInt(4) : 0;
            offX = (player.getRandom().nextDouble() - 0.5) * (double)target.getWidth() * 0.5;
            offY = (player.getRandom().nextDouble() - 0.5) * 0.3;
        }
    }

    /** Holds the attack button on the block she was sent to break, or on whatever turned out to stand in front of it. */
    private static void dig(MinecraftClient client, ClientPlayerEntity player) {
        if (!(client.crosshairTarget instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }

        BlockPos at = hit.getBlockPos();
        BlockState state = client.world.getBlockState(at);
        if (state.isAir() || state.getHardness(client.world, at) < 0.0F) {
            return;
        }

        // Never a chest or a furnace that happened to be in the way.
        digging = at.equals(breakPos) || client.world.getBlockEntity(at) == null;
    }

    public static void renderHud(DrawContext context, int width, int height) {
        if (!active) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options.hudHidden) {
            return;
        }

        int heat = puppet ? 0 : com.windykindy49.windyorigins.host.HostManager.heat(client.player);
        boolean hot = heat >= 80;
        int pulse = (int)(150.0 + 100.0 * Math.sin((double)since * (hot ? 0.9 : 0.25)));
        int tint = hot || puppet ? 0xF03CC8 : 0x45F0E6;
        int color = tint | pulse << 24;
        // The view is hers: a band of her colour down from the top and up from the bottom, breathing.
        int bands = 5;
        int deep = height / 9;

        for (int i = 0; i < bands; i++) {
            int alpha = (int)((double)(bands - i) * (hot ? 13.0 : 8.0) * (0.75 + 0.25 * Math.sin((double)since * 0.2)));
            context.fill(0, i * deep / bands, width, (i + 1) * deep / bands, alpha << 24 | tint);
            context.fill(0, height - (i + 1) * deep / bands, width, height - i * deep / bands, alpha << 24 | tint);
        }

        Text title = puppet ? Text.translatable("hud.windyorigins.puppet") : Text.translatable("hud.windyorigins.pilot", Text.translatable("hud.windyorigins.pilot.task." + label));
        Text hint = puppet ? Text.translatable("hud.windyorigins.puppet.hint", Math.max(0, 5 - struggles)) : Text.translatable("hud.windyorigins.pilot.hint");
        int top = 30;
        int half = Math.max(Math.max(client.textRenderer.getWidth(title), client.textRenderer.getWidth(hint)) / 2 + 8, 62);
        // The plate slams down oversized for the first few ticks, then sits.
        int grow = since < 3 ? (3 - since) * 4 : 0;
        context.fill(width / 2 - half - grow, top - 5 - grow, width / 2 + half + grow, top + 29 + grow, 0x8C0B1030);
        context.fill(width / 2 - half - grow, top - 5 - grow, width / 2 + half + grow, top - 3 - grow, color);
        context.fill(width / 2 - half - grow, top + 27 + grow, width / 2 + half + grow, top + 29 + grow, color);
        context.drawCenteredTextWithShadow(client.textRenderer, title, width / 2, top, 0xFF000000 | tint);
        context.drawCenteredTextWithShadow(client.textRenderer, hint, width / 2, top + 11, 0xFF9AB8C0);
        // How long the chip will stand it: a row of cells that fills as it heats. On strings: the pulls still to make.
        int cells = puppet ? 5 : 24;
        int span = half * 2 - 12;
        int lit = puppet ? struggles : Math.min(cells, heat * cells / 95);

        for (int i = 0; i < cells; i++) {
            int x0 = width / 2 - span / 2 + i * span / cells;
            int x1 = width / 2 - span / 2 + (i + 1) * span / cells - 1;
            boolean on = i < lit;
            int cellColor = on ? (puppet ? 0xFFFFFFFF : (i >= cells * 80 / 95 ? 0xFFF03CC8 : (i >= cells / 2 ? 0xFFFFFFFF : 0xFF45F0E6))) : 0x66203040;
            if (on && hot && i == lit - 1 && since % 4 < 2) {
                cellColor = 0xFFFFFFFF;
            }

            context.fill(x0, top + 22, x1, top + 25, cellColor);
        }

        // The keys she has down, as she presses them: the Host sees their own keyboard played.
        int k = 13;
        int kx = width - 9 * k + 2;
        int ky = height - 5 * k;
        key(context, client, kx + k, ky, k, "W", forward > 0.0F, tint);
        key(context, client, kx, ky + k, k, "A", sideways > 0.0F, tint);
        key(context, client, kx + k, ky + k, k, "S", forward < 0.0F, tint);
        key(context, client, kx + 2 * k, ky + k, k, "D", sideways < 0.0F, tint);
        key(context, client, kx, ky + 2 * k, 3 * k, jump ? "JUMP" : (sneak ? "SNEAK" : "·"), jump || sneak, tint);
        key(context, client, kx + 4 * k, ky, 2 * k - 2, "LMB", hitFlash > 0, tint);
        key(context, client, kx + 6 * k, ky, 2 * k - 2, "RMB", useFlash > 0, tint);
        key(context, client, kx + 4 * k, ky + k, 4 * k - 2, player(client).isSprinting() ? "SPRINT" : "·", player(client).isSprinting(), tint);
    }

    private static ClientPlayerEntity player(MinecraftClient client) {
        return client.player;
    }

    private static void key(DrawContext context, MinecraftClient client, int x, int y, int w, String face, boolean down, int tint) {
        int h = 12;
        context.fill(x, y, x + w - 1, y + h - 1, down ? 0xFF000000 | tint : 0x800B1030);
        context.fill(x, y + h - 2, x + w - 1, y + h - 1, down ? 0xFFFFFFFF : 0xA0000000 | tint);
        int tw = client.textRenderer.getWidth(face);
        if (tw <= w + 2) {
            context.drawText(client.textRenderer, face, x + (w - 1 - tw) / 2, y + 2, down ? 0xFF0B1030 : 0xB0000000 | tint, false);
        }
    }
}
