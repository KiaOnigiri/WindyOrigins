package com.windykindy49.windyorigins.client;

import com.windykindy49.windyorigins.host.HostNetworking;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import com.windykindy49.windyorigins.client.DrawContext;
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
    private static boolean active;
    private static int label;
    private static int flags;
    private static int slot = -1;
    private static final List<Vec3d> PATH = new ArrayList<>();
    private static BlockPos breakPos;
    private static BlockPos usePos;
    private static int attackId = -1;
    private static Vec3d look;
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
            client.execute(() -> {
                if (!active) {
                    active = true;
                    armed = false;
                    since = 0;
                    swingWait = 4;
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
            });
        });
        ClientTickEvents.START_CLIENT_TICK.register(PilotClient::tick);
    }

    public static boolean active() {
        return active;
    }

    /** Should the attack button count as held this tick? Asked where the game decides whether a block is being broken. */
    public static boolean digging() {
        return active && digging;
    }

    private static void stop(MinecraftClient client) {
        if (using) {
            client.options.useKey.setPressed(false);
        }

        active = false;
        digging = false;
        using = false;
        PATH.clear();
        breakPos = null;
        usePos = null;
        attackId = -1;
        look = null;
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
        if (player == null || client.world == null || !player.isAlive() || !HostClient.isHost(player)) {
            stop(client);
            return;
        }

        if (client.isPaused()) {
            return;
        }

        since++;
        if (armed && (client.options.attackKey.isPressed() || !using && client.options.useKey.isPressed())) {
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
            } else if (away < 2.0) {
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
                    jump = go.y >= player.getY() - 0.4 || player.horizontalCollision;
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

        digging = false;
        if (target != null) {
            fight(client, player, target);
        } else if (breakPos != null && facing) {
            dig(client, player);
        }

        boolean eat = (flags & USE) != 0;
        if (eat != using) {
            using = eat;
            client.options.useKey.setPressed(eat);
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

    /** Swings when the weapon is ready and the crosshair is on the creature, a few ticks late and a little off, like anybody. */
    private static void fight(MinecraftClient client, ClientPlayerEntity player, Entity target) {
        HitResult hit = client.crosshairTarget;
        boolean on = hit instanceof EntityHitResult entity && entity.getEntity() == target;
        if (player.getAttackCooldownProgress(0.0F) < 1.0F || client.interactionManager == null) {
            return;
        }

        if (swingWait > 0) {
            swingWait--;
        } else if (on) {
            client.interactionManager.attackEntity(player, target);
            player.swingHand(Hand.MAIN_HAND);
            swingWait = 2 + player.getRandom().nextInt(5);
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

        int pulse = (int)(150.0 + 100.0 * Math.sin((double)since * 0.25));
        int color = 0x45F0E6 | pulse << 24;
        Text title = Text.translatable("hud.windyorigins.pilot", Text.translatable("hud.windyorigins.pilot.task." + label));
        Text hint = Text.translatable("hud.windyorigins.pilot.hint");
        int top = 24;
        int half = Math.max(client.textRenderer.getWidth(title), client.textRenderer.getWidth(hint)) / 2 + 8;
        context.fill(width / 2 - half, top - 4, width / 2 + half, top + 22, 0x66000810);
        context.fill(width / 2 - half, top - 4, width / 2 + half, top - 3, color);
        context.drawCenteredTextWithShadow(client.textRenderer, title, width / 2, top, 0xFF45F0E6);
        context.drawCenteredTextWithShadow(client.textRenderer, hint, width / 2, top + 11, 0xFF9AB8C0);
    }
}
