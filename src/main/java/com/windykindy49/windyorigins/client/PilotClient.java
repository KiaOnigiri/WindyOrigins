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
    private static int mountWait;
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
            if (dx * dx + dz * dz < (PATH.size() > 1 ? 0.42 : 0.1) && Math.abs(step.y - player.getY()) < 1.7) {
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

        Vec3d aim = null;
        if (target != null) {
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

        boolean run = (flags & SPRINT) != 0 && forward > 0.0F && !player.isUsingItem() && player.getHungerManager().getFoodLevel() > 6;
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

        if (placeWait > 0) {
            placeWait--;
        } else if (placeAgainst != null && target == null && facing && client.interactionManager != null) {
            // A click of the use button on the face she was shown, with what she put in the hand.
            Vec3d on = Vec3d.ofCenter(placeAgainst).add((double)placeFace.getOffsetX() * 0.5, (double)placeFace.getOffsetY() * 0.5, (double)placeFace.getOffsetZ() * 0.5);
            if (player.getEyePos().squaredDistanceTo(on) < 4.5 * 4.5 && player.getMainHandStack().getItem() instanceof net.minecraft.item.BlockItem) {
                client.interactionManager.interactBlock(player, Hand.MAIN_HAND, new BlockHitResult(on, placeFace, placeAgainst, false));
                player.swingHand(Hand.MAIN_HAND);
                placeWait = 4;
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

    /**
     * Swings when the weapon is ready and the crosshair is on the creature, a few ticks late and a little off, like
     * anybody. One against one she jumps into the blow, to land it on the way down, where it counts for more; with
     * several on her she stays on the ground, where a sword sweeps.
     */
    private static void fight(MinecraftClient client, ClientPlayerEntity player, Entity target) {
        HitResult hit = client.crosshairTarget;
        boolean on = hit instanceof EntityHitResult entity && entity.getEntity() == target;
        float ready = player.getAttackCooldownProgress(0.0F);
        boolean sweep = (flags & SWEEP) != 0;
        if (!sweep && on && ready > 0.8F && ready < 1.0F && player.isOnGround() && !player.isTouchingWater() && (double)player.distanceTo(target) < 3.4) {
            critJump = true;
        }

        if (ready < 1.0F || client.interactionManager == null || player.isUsingItem()) {
            return;
        }

        boolean falling = !player.isOnGround() && player.getVelocity().y < -0.05;
        if (swingWait > 0) {
            swingWait--;
        } else if (on && (sweep || falling || ++critWait > 5)) {
            client.interactionManager.attackEntity(player, target);
            player.swingHand(Hand.MAIN_HAND);
            critWait = 0;
            hitFlash = 4;
            VoltaFx.pilotHit(target.getPos().add(0.0, (double)target.getHeight() * 0.6, 0.0), falling);
            swingWait = 2 + player.getRandom().nextInt(4);
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
