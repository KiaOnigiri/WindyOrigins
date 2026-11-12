package com.windykindy49.windyorigins.client;

import com.windykindy49.windyorigins.common.Powers;
import com.windykindy49.windyorigins.folded.FoldRifleItem;
import com.windykindy49.windyorigins.folded.FoldedManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import com.windykindy49.windyorigins.client.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;

public final class FoldedClient {
    private static final double RISE_LIMIT = 8.0;
    private static final double VERTICAL = 0.2;
    /** Blocks per tick: a little under walking pace (0.216), well under a sprint (0.28). */
    private static final double DRIFT = 0.185;
    /** Fast enough to get down in a hurry, slow enough to land unhurt. */
    private static final double GLIDE_FALL = -0.55;
    private static int mode;
    private static final org.joml.Matrix4f PROJECTION = new org.joml.Matrix4f();
    private static final org.joml.Matrix4f VIEW = new org.joml.Matrix4f();
    private static Vec3d cameraPos = Vec3d.ZERO;
    private static boolean captured;
    private static int aimId = -1;
    private static Vec3d aimPos = Vec3d.ZERO;
    private static int aimLeft;
    private static int aimFire;
    private static int aimAge;

    private FoldedClient() {
    }

    public static boolean isHovering() {
        return mode == FoldedManager.MODE_HOVER;
    }

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(FoldedManager.HOVER_SYNC, (var0, var1, var2, var3) -> {
            int var4 = var2.readByte();
            var0.execute(() -> mode = var4);
        });
        ClientPlayNetworking.registerGlobalReceiver(FoldedManager.AIM, (client, handler, buf, sender) -> {
            int id = buf.readInt();
            Vec3d pos = new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble());
            int fire = buf.readByte();
            client.execute(() -> {
                if (aimLeft <= 0 || aimId != id) {
                    aimAge = 0;
                }

                aimId = id;
                aimPos = pos;
                aimFire = fire;
                aimLeft = 5;
            });
        });
        ClientPlayConnectionEvents.DISCONNECT.register((var0, var1) -> {
            mode = 0;
            aimLeft = 0;
        });
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            PROJECTION.set(context.projectionMatrix());
            VIEW.set(context.matrixStack().peek().getPositionMatrix());
            cameraPos = context.camera().getPos();
            captured = true;
        });
        HudRenderCallback.EVENT.register((matrices, delta) -> FoldedClient.renderGlint(new com.windykindy49.windyorigins.client.DrawContext(matrices), delta));
        ClientTickEvents.END_CLIENT_TICK.register(FoldedClient::tick);
        HudRenderCallback.EVENT.register((matrices, delta) -> FoldedClient.renderScope(new com.windykindy49.windyorigins.client.DrawContext(matrices), delta));
        FoldedManager.clientShot = FoldedClient::reportShot;
    }

    /** Tells the server exactly what was under the crosshair on the frame the shot was fired. */
    private static void reportShot(net.minecraft.entity.LivingEntity var0) {
        MinecraftClient var1 = MinecraftClient.getInstance();
        if (var0 != var1.player || var1.world == null) {
            return;
        }

        float var2 = var1.getTickDelta();
        Vec3d var3 = var0.getCameraPosVec(var2);
        Vec3d var4 = var0.getRotationVec(var2);
        Vec3d var5 = var3.add(var4.multiply(FoldedManager.SHOT_RANGE));
        BlockHitResult var6 = var1.world.raycast(new RaycastContext(var3, var5, ShapeType.COLLIDER, FluidHandling.NONE, var0));
        if (var6.getType() != Type.MISS) {
            var5 = var6.getPos();
        }

        net.minecraft.util.hit.EntityHitResult var7 = net.minecraft.entity.projectile.ProjectileUtil.raycast(
            var0,
            var3,
            var5,
            new net.minecraft.util.math.Box(var3, var5).expand(1.0),
            var1x -> var1x != var0 && var1x instanceof net.minecraft.entity.LivingEntity && !var1x.isSpectator() && var1x.canHit() && var1x.isAlive(),
            var3.squaredDistanceTo(var5)
        );
        net.minecraft.network.PacketByteBuf var8 = new net.minecraft.network.PacketByteBuf(io.netty.buffer.Unpooled.buffer());
        var8.writeInt(var7 == null ? -1 : var7.getEntity().getId());
        var8.writeDouble(var3.x);
        var8.writeDouble(var3.y);
        var8.writeDouble(var3.z);
        var8.writeFloat(var0.getYaw(var2));
        var8.writeFloat(var0.getPitch(var2));
        ClientPlayNetworking.send(FoldedManager.SHOT, var8);
    }

    /**
     * The scope glint: a hard little star pinned to wherever the rifle is, seen by whoever it is
     * pointed at. It swells while the charge finishes and strobes once the shot can come at any moment.
     */
    private static void renderGlint(DrawContext context, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (aimLeft <= 0 || !captured || client.world == null || client.player == null || client.options.hudHidden) {
            return;
        }

        net.minecraft.entity.Entity shooter = client.world.getEntityById(aimId);
        Vec3d at = shooter != null ? shooter.getCameraPosVec(tickDelta) : aimPos;
        int w = context.getScaledWindowWidth();
        int h = context.getScaledWindowHeight();
        org.joml.Vector4f v = new org.joml.Vector4f((float)(at.x - cameraPos.x), (float)(at.y - cameraPos.y), (float)(at.z - cameraPos.z), 1.0F);
        v.mul(VIEW);
        v.mul(PROJECTION);
        float sx;
        float sy;
        boolean front = v.w > 0.05F;
        if (front) {
            sx = (v.x / v.w * 0.5F + 0.5F) * (float)w;
            sy = (1.0F - (v.y / v.w * 0.5F + 0.5F)) * (float)h;
        } else {
            float len = Math.max(0.001F, (float)Math.sqrt((double)(v.x * v.x + v.y * v.y)));
            sx = (float)w * 0.5F + v.x / len * (float)w;
            sy = (float)h * 0.5F - v.y / len * (float)h;
        }

        int margin = 14;
        boolean onScreen = front && sx >= (float)margin && sx <= (float)(w - margin) && sy >= (float)margin && sy <= (float)(h - margin);
        int x = (int)Math.max((float)margin, Math.min((float)(w - margin), sx));
        int y = (int)Math.max((float)margin, Math.min((float)(h - margin), sy));
        boolean ready = aimFire <= 0;
        int beat = aimAge / 2;
        float progress = 1.0F - Math.min(1.0F, (float)aimFire / (float)FoldedManager.WARN_TICKS);
        int size = 5 + (int)(7.0F * progress) + (ready && beat % 2 == 0 ? 3 : 0);
        int ray = ready && beat % 2 == 0 ? 0xFFFFFFFF : (ready ? 0xFFFF6A9A : 0xFFD2B4FF);
        int halo = ready ? 0x66FF6A9A : 0x559A72FF;
        if (!onScreen) {
            // Out of view: a block on the edge of the screen, on the side the rifle is.
            if (beat % 2 == 0 || !ready) {
                context.fill(x - 5, y - 5, x + 6, y + 6, halo);
                context.fill(x - 3, y - 3, x + 4, y + 4, ray);
                context.fill(x - 1, y - 1, x + 2, y + 2, 0xFF2A1450);
            }

            return;
        }

        context.fill(x - 3, y - 3, x + 4, y + 4, halo);
        context.fill(x - size, y, x + size + 1, y + 1, ray);
        context.fill(x, y - size, x + 1, y + size + 1, ray);
        int diagonal = size / 2;

        for (int i = 1; i <= diagonal; i++) {
            context.fill(x + i, y + i, x + i + 1, y + i + 1, ray);
            context.fill(x - i, y + i, x - i + 1, y + i + 1, ray);
            context.fill(x + i, y - i, x + i + 1, y - i + 1, ray);
            context.fill(x - i, y - i, x - i + 1, y - i + 1, ray);
        }

        context.fill(x - 1, y - 1, x + 2, y + 2, 0xFFFFFFFF);
    }

    private static void tick(MinecraftClient var0) {
        if (aimLeft > 0 && !var0.isPaused()) {
            aimLeft--;
            aimAge++;
        }

        ClientPlayerEntity var1 = var0.player;
        if (var1 == null || mode == 0 || var0.isPaused()) {
            return;
        }

        Vec3d var2 = var1.getVelocity();
        if (mode == FoldedManager.MODE_GLIDE) {
            if (var1.isOnGround() || var1.isTouchingWater() || var1.getAbilities().flying) {
                mode = 0;
            } else if (var2.y < GLIDE_FALL) {
                var1.setVelocity(var2.x, GLIDE_FALL, var2.z);
            }

            var1.fallDistance = 0.0F;
        } else {
            double var3;
            if (var1.input.jumping) {
                var3 = groundWithin(var1, RISE_LIMIT) ? VERTICAL : 0.0;
            } else if (var1.input.sneaking) {
                var3 = -VERTICAL;
            } else {
                var3 = 0.0;
            }

            // Steer straight from the keys so the drift has a steady, predictable pace.
            float var5 = var1.input.movementForward;
            float var6 = var1.input.movementSideways;
            double var7 = Math.sqrt((double)(var5 * var5 + var6 * var6));
            double var9 = 0.0;
            double var11 = 0.0;
            if (var7 > 0.01) {
                double var13 = Math.min(1.0, var7) / var7 * DRIFT;
                double var15 = Math.toRadians((double)var1.getYaw());
                double var17 = Math.sin(var15);
                double var19 = Math.cos(var15);
                var9 = ((double)var6 * var19 - (double)var5 * var17) * var13;
                var11 = ((double)var5 * var19 + (double)var6 * var17) * var13;
            }

            var1.setVelocity(var2.x * 0.55 + var9 * 0.45, var3, var2.z * 0.55 + var11 * 0.45);
            var1.fallDistance = 0.0F;
        }
    }

    private static boolean groundWithin(ClientPlayerEntity var0, double var1) {
        Vec3d var3 = var0.getPos();
        BlockHitResult var4 = var0.getWorld().raycast(new RaycastContext(var3, var3.subtract(0.0, var1, 0.0), ShapeType.COLLIDER, FluidHandling.ANY, var0));
        return var4.getType() != Type.MISS;
    }

    private static void renderScope(DrawContext var0, float var1) {
        MinecraftClient var2 = MinecraftClient.getInstance();
        if (var2.player != null && !var2.options.hudHidden && var2.options.getPerspective().isFirstPerson()) {
            float var3 = FoldRifleItem.charge(var2.player);
            if (!(var3 <= 0.0F)) {
                int var4 = var0.getScaledWindowWidth();
                int var5 = var0.getScaledWindowHeight();
                int var6 = var4 / 2;
                int var7 = var5 / 2;
                int var8 = (int)((float)Math.min(var4, var5) * (0.48F - 0.18F * Math.min(1.0F, var3 * 1.8F)));
                int var9 = -1073413360;
                var0.fill(0, 0, var4, var7 - var8, var9);
                var0.fill(0, var7 + var8, var4, var5, var9);
                var0.fill(0, var7 - var8, var6 - var8, var7 + var8, var9);
                var0.fill(var6 + var8, var7 - var8, var4, var7 + var8, var9);
                int var10 = var3 >= 1.0F ? -1647361 : -6655745;
                var0.fill(var6 - var8, var7, var6 - 4, var7 + 1, var10);
                var0.fill(var6 + 4, var7, var6 + var8, var7 + 1, var10);
                var0.fill(var6, var7 - var8, var6 + 1, var7 - 4, var10);
                var0.fill(var6, var7 + 4, var6 + 1, var7 + var8, var10);
                int var11 = (int)(40.0F * var3);
                var0.fill(var6 - 20, var7 + 10, var6 - 20 + var11, var7 + 12, var10);
            }
        }
    }
}
