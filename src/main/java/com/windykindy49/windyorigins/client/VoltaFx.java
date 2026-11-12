package com.windykindy49.windyorigins.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.windykindy49.windyorigins.host.HostNetworking;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Volta's energy, drawn the way a cartoon would draw it: flat colour, an ink outline, a streak of
 * highlight, moving on twos. It is painted on the Host's screen only, but it is anchored to the
 * world: a living mass that swells at the edge of the view, reaches out to a block or a creature,
 * grips it and works on it, then lets go.
 */
public final class VoltaFx {
    private static final int INK = 0x0B1030;
    private static final List<Fx> ACTIVE = new ArrayList<>();
    private static final List<Drop> DROPS = new ArrayList<>();
    private static final Matrix4f PROJECTION = new Matrix4f();
    private static final Matrix4f VIEW = new Matrix4f();
    private static Vec3d cameraPos = Vec3d.ZERO;
    private static boolean captured;
    private static int screenW = 480;
    private static int screenH = 270;
    private static float kick;
    private static float prevKick;
    private static float flash;
    private static int flashColor;
    // Immediate-mode batch for the current frame.
    private static BufferBuilder batch;
    private static Matrix4f batchMatrix;

    private VoltaFx() {
    }

    private static final class Fx {
        final int kind;
        final int slot;
        final int casterId;
        int entityId;
        Vec3d pos;
        Vec3d from;
        final Vec3d source;
        final int color;
        int life;
        int age;
        int total;
        int punch = 99;
        final long seed;
        boolean landed;

        Fx(int kind, int slot, int casterId, int entityId, Vec3d pos, Vec3d source, int color, int ticks) {
            this.kind = kind;
            this.slot = slot;
            this.casterId = casterId;
            this.entityId = entityId;
            this.pos = pos;
            this.from = pos;
            this.source = source;
            this.color = color;
            this.total = ticks;
            this.life = kind == HostNetworking.FX_BOLT ? ticks + 10 : ticks;
            this.seed = (long)(Math.random() * 100000.0);
        }

        int launch() {
            return Math.max(0, this.total - 3);
        }
    }

    /** A fleck of the mass thrown off by an impact; lives in screen space. */
    private static final class Drop {
        float x;
        float y;
        float vx;
        float vy;
        float radius;
        int life;
        final int color;

        Drop(float x, float y, float vx, float vy, float radius, int life, int color) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.radius = radius;
            this.life = life;
            this.color = color;
        }
    }

    public static void clear() {
        ACTIVE.clear();
        DROPS.clear();
        kick = 0.0F;
        flash = 0.0F;
    }

    public static void add(int kind, int slot, int casterId, int entityId, Vec3d pos, Vec3d source, int color, int ticks) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!own(client, casterId)) {
            return;
        }

        if (kind == HostNetworking.FX_STOP) {
            if (slot == 2) {
                ACTIVE.clear();
            } else {
                int gone = slot == 1 ? HostNetworking.FX_SELF : HostNetworking.FX_TENDRIL;
                // Let go rather than vanish: the arm is given a few ticks to pull back.
                for (Fx fx : ACTIVE) {
                    if (fx.kind == gone) {
                        fx.life = Math.min(fx.life, 4);
                    }
                }
            }

            return;
        }

        if (kind == HostNetworking.FX_TENDRIL) {
            for (Fx fx : ACTIVE) {
                if (fx.kind == kind && fx.slot == slot) {
                    // Same arm, new place to grip: it swings across and lands a blow there.
                    fx.from = current(fx, client, 1.0F);
                    fx.pos = pos;
                    fx.entityId = entityId;
                    fx.life = ticks;
                    fx.total = ticks;
                    fx.age = Math.min(fx.age, 4) + 100;
                    fx.punch = -3;
                    return;
                }
            }
        }

        if (kind == HostNetworking.FX_DECOY || kind == HostNetworking.FX_NODE) {
            ACTIVE.removeIf(fx -> fx.kind == kind);
        }

        Fx fx = new Fx(kind, slot, casterId, entityId, pos, source, color, ticks);
        if (kind == HostNetworking.FX_TENDRIL) {
            fx.punch = -4;
        }

        ACTIVE.add(fx);
        if (kind == HostNetworking.FX_SELF || kind == HostNetworking.FX_PULSE) {
            flash = Math.max(flash, 0.3F);
            flashColor = color;
        }
    }

    private static boolean own(MinecraftClient client, int casterId) {
        return client.player != null && client.player.getId() == casterId;
    }

    /** Whether a full cast (face first, then the shot) is playing for the local Host. */
    public static boolean castingFace() {
        for (Fx fx : ACTIVE) {
            if (fx.kind == HostNetworking.FX_BOLT && fx.slot == 0 && fx.age < fx.total + 4) {
                return true;
            }
        }

        return false;
    }

    public static float kick(float tickDelta) {
        return MathHelper.lerp(tickDelta, prevKick, kick);
    }

    private static void play(MinecraftClient client, SoundEvent sound, float volume, float pitch) {
        client.getSoundManager().play(PositionedSoundInstance.master(sound, pitch, volume));
    }

    private static void splash(float x, float y, int count, int color, float force) {
        float sc = scale();

        for (int i = 0; i < count; i++) {
            double angle = Math.random() * Math.PI * 2.0;
            float speed = (float)(2.0 + Math.random() * 5.0) * force * sc;
            DROPS.add(
                new Drop(x, y, (float)Math.cos(angle) * speed, (float)Math.sin(angle) * speed - 2.0F * sc, (float)(2.0 + Math.random() * 3.5) * sc, 8 + (int)(Math.random() * 8.0), color)
            );
        }
    }

    public static void tick(MinecraftClient client) {
        prevKick = kick;
        kick *= 0.72F;
        flash *= 0.7F;
        if (client.world == null) {
            ACTIVE.clear();
            DROPS.clear();
            return;
        }

        Iterator<Drop> drops = DROPS.iterator();

        while (drops.hasNext()) {
            Drop drop = drops.next();
            drop.x += drop.vx;
            drop.y += drop.vy;
            drop.vy += 0.9F * scale();
            drop.vx *= 0.92F;
            if (--drop.life <= 0) {
                drops.remove();
            }
        }

        Iterator<Fx> it = ACTIVE.iterator();

        while (it.hasNext()) {
            Fx fx = it.next();
            fx.age++;
            fx.life--;
            fx.punch++;
            if (fx.kind == HostNetworking.FX_BOLT) {
                if (fx.age == fx.launch() + 1) {
                    kick = fx.slot == 0 ? 1.0F : 0.35F;
                    if (fx.slot == 0) {
                        flash = 0.5F;
                        flashColor = 0xFFFFFF;
                        play(client, SoundEvents.ITEM_TRIDENT_RIPTIDE_1, 0.5F, 1.9F);
                        play(client, SoundEvents.ENTITY_SLIME_ATTACK, 0.7F, 0.7F);
                    }
                }

                if (!fx.landed && fx.age >= fx.total) {
                    fx.landed = true;
                    float[] at = project(current(fx, client, 1.0F));
                    splash(at[0], at[1], 12, fx.color, 1.3F);
                    play(client, SoundEvents.ENTITY_SLIME_SQUISH, 0.6F, 1.5F);
                }
            } else if (fx.kind == HostNetworking.FX_TENDRIL) {
                kick = Math.max(kick, 0.1F);
                if (fx.punch == 0) {
                    // The grip lands.
                    float[] at = project(current(fx, client, 1.0F));
                    splash(at[0], at[1], 6, fx.color, 0.9F);
                    kick = Math.max(kick, 0.3F);
                    play(client, SoundEvents.ENTITY_SLIME_SQUISH_SMALL, 0.5F, 1.2F + (float)Math.random() * 0.5F);
                }
            } else if (fx.kind == HostNetworking.FX_ARC && fx.age == 1) {
                float[] at = project(fx.pos);
                splash(at[0], at[1], 4, fx.color, 0.8F);
            }

            if (fx.life <= 0) {
                it.remove();
            }
        }
    }

    /** Where the effect currently points: it follows its target entity while that still exists. */
    private static Vec3d current(Fx fx, MinecraftClient client, float tickDelta) {
        Vec3d target = fx.pos;
        if (fx.entityId >= 0 && client.world != null) {
            Entity entity = client.world.getEntityById(fx.entityId);
            if (entity != null && entity.isAlive()) {
                target = entity.getLerpedPos(tickDelta).add(0.0, (double)entity.getHeight() * 0.6, 0.0);
                fx.pos = target;
            }
        }

        // A retargeted arm swings across over four ticks instead of jumping.
        if (fx.kind == HostNetworking.FX_TENDRIL && fx.age >= 100 && fx.age < 104) {
            return fx.from.lerp(target, (double)MathHelper.clamp(((float)(fx.age - 100) + tickDelta) / 4.0F, 0.0F, 1.0F));
        }

        return target;
    }

    private static double hash(long seed, int a, int b) {
        long h = seed * 0x9E3779B97F4A7C15L + (long)a * 0xC2B2AE3D27D4EB4FL + (long)b * 0x165667B19E3779F9L;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return (double)(h & 0xFFFFFFL) / (double)0xFFFFFF - 0.5;
    }
    private static float scale() {
        return Math.max(0.75F, (float)screenH / 360.0F);
    }

    // ------------------------------------------------------------------ world

    /** Remembers the camera for this frame, so the screen pass can pin its drawings to the world. */
    public static void renderWorld(WorldRenderContext context) {
        PROJECTION.set(context.projectionMatrix());
        VIEW.set(context.matrixStack().peek().getPositionMatrix());
        cameraPos = context.camera().getPos();
        captured = true;
        if (context.consumers() == null) {
            return;
        }

        for (Fx fx : ACTIVE) {
            if (fx.kind == HostNetworking.FX_DECOY) {
                hologram(context.matrixStack(), context.consumers(), context.camera(), fx, (float)fx.age + context.tickDelta());
            }
        }
    }

    /** World point to screen point. The third value is 0 when the point is behind the camera. */
    private static float[] project(Vec3d world) {
        Vector4f v = new Vector4f((float)(world.x - cameraPos.x), (float)(world.y - cameraPos.y), (float)(world.z - cameraPos.z), 1.0F);
        v.mul(VIEW);
        v.mul(PROJECTION);
        if (v.w <= 0.05F) {
            // Behind the view: aim far off the edge of the screen on the side it lies.
            float len = Math.max(0.001F, (float)Math.sqrt((double)(v.x * v.x + v.y * v.y)));
            return new float[]{(float)screenW * 0.5F + v.x / len * (float)screenW * 1.5F, (float)screenH * 0.5F - v.y / len * (float)screenH * 1.5F, 0.0F};
        }

        return new float[]{(v.x / v.w * 0.5F + 0.5F) * (float)screenW, (1.0F - (v.y / v.w * 0.5F + 0.5F)) * (float)screenH, 1.0F};
    }

    private static void hologram(MatrixStack matrices, VertexConsumerProvider consumers, Camera camera, Fx fx, float age) {
        Vec3d cam = camera.getPos();
        float appear = MathHelper.clamp(age / 6.0F, 0.0F, 1.0F);
        float vanish = MathHelper.clamp((float)fx.life / 8.0F, 0.0F, 1.0F);
        float flicker = hash(fx.seed, (int)(age / 2.0F), 0) > 0.38 ? 0.45F : 1.0F;
        int alpha = (int)(230.0F * appear * vanish * flicker);
        float width = 0.95F;
        float height = 1.42F * (0.3F + 0.7F * appear);
        matrices.push();
        matrices.translate(fx.pos.x - cam.x, fx.pos.y + (double)height / 2.0 + 0.05 + Math.sin((double)age * 0.1) * 0.05 - cam.y, fx.pos.z - cam.z);
        matrices.multiply(new org.joml.Quaternionf().rotationY((float)Math.toRadians((double)(180.0F - camera.getYaw()))));
        int frame = 2 + (int)(age / 8.0F) % 2;
        float u0 = (float)(frame * 32) / 256.0F;
        float u1 = (float)((frame + 1) * 32) / 256.0F;
        VertexConsumer buffer = consumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(HostClient.SHEET));
        HostClient.quad(
            buffer, matrices.peek().getPositionMatrix(), matrices.peek().getNormalMatrix(), -width / 2.0F, -height / 2.0F, width / 2.0F, height / 2.0F, u0, 0.0F, u1, 0.1875F, alpha, 190, 255, 250
        );
        matrices.pop();
    }

    // ------------------------------------------------------------------ ink and paint

    private static void begin(DrawContext context) {
        context.draw();
        batchMatrix = context.getMatrices().peek().getPositionMatrix();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        batch = Tessellator.getInstance().getBuffer();
        batch.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
    }

    private static void end() {
        Tessellator.getInstance().draw();
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        batch = null;
    }

    private static int argb(int rgb, float alpha) {
        return rgb & 0xFFFFFF | (int)(MathHelper.clamp(alpha, 0.0F, 1.0F) * 255.0F) << 24;
    }

    private static int mix(int a, int b, float t) {
        int r = (int)MathHelper.lerp(t, (float)(a >> 16 & 0xFF), (float)(b >> 16 & 0xFF));
        int g = (int)MathHelper.lerp(t, (float)(a >> 8 & 0xFF), (float)(b >> 8 & 0xFF));
        int bl = (int)MathHelper.lerp(t, (float)(a & 0xFF), (float)(b & 0xFF));
        return r << 16 | g << 8 | bl;
    }

    private static void tri(float x1, float y1, float x2, float y2, float x3, float y3, int color) {
        int a = color >>> 24;
        int r = color >> 16 & 0xFF;
        int g = color >> 8 & 0xFF;
        int b = color & 0xFF;
        batch.vertex(batchMatrix, x1, y1, 0.0F).color(r, g, b, a).next();
        batch.vertex(batchMatrix, x2, y2, 0.0F).color(r, g, b, a).next();
        batch.vertex(batchMatrix, x3, y3, 0.0F).color(r, g, b, a).next();
    }

    private static void disc(float x, float y, float radius, int color) {
        ellipse(x, y, radius, radius, color);
    }

    private static void ellipse(float x, float y, float rx, float ry, int color) {
        if (rx <= 0.2F || ry <= 0.2F) {
            return;
        }

        int steps = MathHelper.clamp(8 + (int)(Math.max(rx, ry) * 0.6F), 8, 28);
        float px = x + rx;
        float py = y;

        for (int i = 1; i <= steps; i++) {
            double a = (double)i / (double)steps * Math.PI * 2.0;
            float nx = x + (float)Math.cos(a) * rx;
            float ny = y + (float)Math.sin(a) * ry;
            tri(x, y, px, py, nx, ny, color);
            px = nx;
            py = ny;
        }
    }

    /** The straight-sided body between two discs of a stroke. */
    private static void limb(float x1, float y1, float r1, float x2, float y2, float r2, int color) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float len = (float)Math.sqrt((double)(dx * dx + dy * dy));
        if (len < 0.01F) {
            return;
        }

        float nx = -dy / len;
        float ny = dx / len;
        tri(x1 + nx * r1, y1 + ny * r1, x1 - nx * r1, y1 - ny * r1, x2 + nx * r2, y2 + ny * r2, color);
        tri(x1 - nx * r1, y1 - ny * r1, x2 - nx * r2, y2 - ny * r2, x2 + nx * r2, y2 + ny * r2, color);
    }

    /**
     * A body of the mass: a run of joined blobs (the first {@code chain} of them form one stroke,
     * the rest are loose lumps stuck to it), inked, filled, shaded and given its streak of light.
     */
    private static void mass(float[] xs, float[] ys, float[] rs, int count, int chain, int rgb, float alpha) {
        float sc = scale();
        float line = 2.2F * sc;
        int ink = argb(INK, alpha);
        int fill = argb(rgb, alpha);
        int shade = argb(mix(rgb, INK, 0.3F), alpha);
        int light = argb(mix(rgb, 0xFFFFFF, 0.72F), alpha);

        for (int pass = 0; pass < 4; pass++) {
            for (int i = 0; i < count; i++) {
                float r = rs[i];
                if (r <= 0.3F) {
                    continue;
                }

                float ox = 0.0F;
                float oy = 0.0F;
                float pr = r;
                int color = fill;
                if (pass == 0) {
                    pr = r + line;
                    color = ink;
                } else if (pass == 2) {
                    // Shadow hugging the lower right.
                    ox = r * 0.2F;
                    oy = r * 0.24F;
                    pr = r * 0.74F;
                    color = shade;
                } else if (pass == 3) {
                    // A streak of light along the upper left.
                    if (r < 3.5F * sc) {
                        continue;
                    }

                    ox = -r * 0.36F;
                    oy = -r * 0.4F;
                    pr = r * 0.24F;
                    color = light;
                }

                if (pass == 2) {
                    // Paint the shadow, then lay the body colour back over most of it.
                    disc(xs[i] + ox, ys[i] + oy, pr, color);
                    disc(xs[i] - r * 0.08F, ys[i] - r * 0.1F, r * 0.8F, fill);
                } else {
                    disc(xs[i] + ox, ys[i] + oy, pr, color);
                }

                if (i + 1 < chain && rs[i + 1] > 0.3F) {
                    float r2 = rs[i + 1];
                    if (pass == 0) {
                        limb(xs[i], ys[i], r + line, xs[i + 1], ys[i + 1], r2 + line, color);
                    } else if (pass == 1) {
                        limb(xs[i], ys[i], r, xs[i + 1], ys[i + 1], r2, color);
                    } else if (pass == 3 && r2 >= 3.5F * sc) {
                        limb(xs[i] + ox, ys[i] + oy, pr, xs[i + 1] - r2 * 0.36F, ys[i + 1] - r2 * 0.4F, r2 * 0.24F, color);
                    }
                }
            }
        }
    }

    /** A cartoon impact: a spiked star, inked. */
    private static void burst(float x, float y, float outer, float inner, int points, float turn, int rgb, float alpha) {
        float line = 2.2F * scale();

        for (int pass = 0; pass < 2; pass++) {
            float ro = outer + (pass == 0 ? line * 1.6F : 0.0F);
            float ri = inner + (pass == 0 ? line : 0.0F);
            int color = pass == 0 ? argb(INK, alpha) : argb(rgb, alpha);
            float px = 0.0F;
            float py = 0.0F;

            for (int i = 0; i <= points * 2; i++) {
                double a = (double)turn + (double)i / (double)(points * 2) * Math.PI * 2.0;
                float r = i % 2 == 0 ? ro : ri;
                float nx = x + (float)Math.cos(a) * r;
                float ny = y + (float)Math.sin(a) * r;
                if (i > 0) {
                    tri(x, y, px, py, nx, ny, color);
                }

                px = nx;
                py = ny;
            }
        }
    }

    /** An inked ring, squashed to lie like a wave on the ground. */
    private static void wave(float x, float y, float radius, float squash, float thickness, int rgb, float alpha) {
        float line = 2.0F * scale();
        int steps = 44;

        for (int pass = 0; pass < 3; pass++) {
            float outer = radius + (pass == 0 ? line : (pass == 2 ? -thickness * 0.55F : 0.0F));
            float inner = Math.max(0.0F, radius - thickness - (pass == 0 ? line : (pass == 2 ? -thickness * 0.2F : 0.0F)));
            if (pass == 2) {
                inner = Math.max(0.0F, radius - thickness * 0.8F);
            }

            int color = pass == 0 ? argb(INK, alpha) : (pass == 1 ? argb(rgb, alpha) : argb(mix(rgb, 0xFFFFFF, 0.7F), alpha));

            for (int i = 0; i < steps; i++) {
                double a0 = (double)i / (double)steps * Math.PI * 2.0;
                double a1 = (double)(i + 1) / (double)steps * Math.PI * 2.0;
                float c0 = (float)Math.cos(a0);
                float s0 = (float)Math.sin(a0) * squash;
                float c1 = (float)Math.cos(a1);
                float s1 = (float)Math.sin(a1) * squash;
                tri(x + c0 * outer, y + s0 * outer, x + c0 * inner, y + s0 * inner, x + c1 * outer, y + s1 * outer, color);
                tri(x + c0 * inner, y + s0 * inner, x + c1 * inner, y + s1 * inner, x + c1 * outer, y + s1 * outer, color);
            }
        }
    }

    // ------------------------------------------------------------------ screen

    public static void renderHud(DrawContext context, float tickDelta, int width, int height) {
        MinecraftClient client = MinecraftClient.getInstance();
        screenW = width;
        screenH = height;
        if (client.world == null || ACTIVE.isEmpty() && DROPS.isEmpty() && flash <= 0.02F) {
            return;
        }

        for (Fx fx : ACTIVE) {
            if (fx.kind == HostNetworking.FX_BOLT && fx.slot == 0) {
                face(context, fx, (float)fx.age + tickDelta, width, height, tickDelta);
            }
        }

        if (flash > 0.02F) {
            context.fill(0, 0, width, height, argb(flashColor, flash * 0.45F));
        }

        if (!captured) {
            return;
        }

        float time = (float)(client.world.getTime() % 100000L) + tickDelta;
        // Drawn animation holds each pose for two ticks.
        int beat = (int)(client.world.getTime() % 100000L / 2L);
        begin(context);

        for (Fx fx : ACTIVE) {
            float age = (float)fx.age + tickDelta;
            switch (fx.kind) {
                case HostNetworking.FX_SELF -> frame(fx, age, tickDelta, beat, width, height);
                case HostNetworking.FX_PULSE -> pulse(fx, age, width, height);
                case HostNetworking.FX_NODE -> node(fx, age, time, beat);
                case HostNetworking.FX_ARC -> arc(fx, age, beat);
                case HostNetworking.FX_TENDRIL -> arm(client, fx, age, time, beat, tickDelta, width, height);
                case HostNetworking.FX_BOLT -> shot(client, fx, age, beat, tickDelta, width, height);
                default -> {
                }
            }
        }

        for (Drop drop : DROPS) {
            float a = MathHelper.clamp((float)drop.life / 5.0F, 0.0F, 1.0F);
            float x = drop.x + drop.vx * tickDelta;
            float y = drop.y + drop.vy * tickDelta;
            disc(x, y, drop.radius + 1.6F * scale(), argb(INK, a));
            disc(x, y, drop.radius, argb(drop.color, a));
            disc(x - drop.radius * 0.3F, y - drop.radius * 0.3F, drop.radius * 0.3F, argb(0xFFFFFF, a));
        }

        end();
    }

    /** The arm of mass that reaches from the edge of the screen and holds on to its work. */
    private static void arm(MinecraftClient client, Fx fx, float age, float time, int beat, float tickDelta, int width, int height) {
        float sc = scale();
        boolean drain = fx.slot == HostNetworking.SLOT_DRAIN;
        float ox = (float)width + 14.0F * sc;
        float oy = (float)height * (drain ? 0.72F : 0.5F + 0.11F * (float)(fx.slot % 3));
        float[] tip = project(current(fx, client, tickDelta));
        float dx = tip[0] - ox;
        float dy = tip[1] - oy;
        float len = Math.max(1.0F, (float)Math.sqrt((double)(dx * dx + dy * dy)));
        float nx = -dy / len;
        float ny = dx / len;
        // The arm sags like something with weight, and sways.
        float bend = len * (0.16F + 0.05F * (float)Math.sin((double)(time * 0.11F) + (double)fx.seed));
        float cx = ox + dx * 0.5F + nx * bend;
        float cy = oy + dy * 0.5F + ny * bend + 18.0F * sc;
        float grow = fx.age >= 100 ? 1.0F : MathHelper.clamp(age / 4.0F, 0.0F, 1.0F);
        float reach = Math.min(grow, MathHelper.clamp(((float)fx.life - tickDelta) / 4.0F, 0.0F, 1.0F));
        if (reach <= 0.02F) {
            return;
        }

        int joints = 26;
        int extra = 4;
        float[] xs = new float[joints + extra];
        float[] ys = new float[joints + extra];
        float[] rs = new float[joints + extra];
        float flow = drain ? 1.0F : -1.0F;

        for (int i = 0; i < joints; i++) {
            float s = (float)i / (float)(joints - 1) * reach;
            float u = 1.0F - s;
            float bx = u * u * ox + 2.0F * u * s * cx + s * s * tip[0];
            float by = u * u * oy + 2.0F * u * s * cy + s * s * tip[1];
            float sway = (float)Math.sin((double)(s * 11.0F) + (double)beat * 1.3 + (double)fx.seed) * 5.0F * sc * (float)Math.sin(Math.PI * (double)s);
            xs[i] = bx + nx * sway;
            ys[i] = by + ny * sway;
            float base = MathHelper.lerp((float)Math.pow((double)s, 0.7), 27.0F, 9.0F) * sc;
            // Lumps travel along the arm: the mass is being pumped out to the work, or drunk back.
            float lump = (float)Math.pow(Math.max(0.0, Math.sin((double)(s * 13.0F) + (double)(flow * time * 0.45F))), 3.0) * 7.0F * sc;
            rs[i] = base + lump;
        }

        // The grip: a fist that swells as it lands a blow, with three stubby fingers working at the block.
        int last = joints - 1;
        float hit = fx.punch < 0 ? 0.0F : (float)Math.exp((double)(-(float)fx.punch - tickDelta) / 2.5);
        float fist = 15.0F * sc * (1.0F + 0.2F * (float)Math.sin((double)beat * 1.9)) * (1.0F + 0.8F * hit) * Math.min(1.0F, reach * 1.2F);
        rs[last] = Math.max(rs[last], fist);
        float ax = xs[last] - xs[last - 1];
        float ay = ys[last] - ys[last - 1];
        float heading = (float)Math.atan2((double)ay, (double)ax);

        for (int f = 0; f < 3; f++) {
            float spread = ((float)f - 1.0F) * 0.75F + (float)hash(fx.seed, f, beat) * 0.5F;
            float out = fist * (0.95F + 0.35F * (float)Math.abs(hash(fx.seed + 5L, f, beat)));
            xs[joints + f] = xs[last] + (float)Math.cos((double)(heading + spread)) * out;
            ys[joints + f] = ys[last] + (float)Math.sin((double)(heading + spread)) * out;
            rs[joints + f] = fist * 0.44F;
        }

        // A drip hanging off the belly of the arm.
        int mid = joints / 2;
        xs[joints + 3] = xs[mid];
        ys[joints + 3] = ys[mid] + rs[mid] * (0.9F + 0.5F * (float)Math.abs(Math.sin((double)beat * 0.7)));
        rs[joints + 3] = rs[mid] * 0.4F;
        if (hit > 0.15F && reach >= 1.0F) {
            burst(xs[last], ys[last], fist * (1.5F + 1.4F * (1.0F - hit)), fist * 0.8F, 8, (float)beat * 0.6F, 0xFFFFFF, hit);
        }

        mass(xs, ys, rs, joints + extra, joints, fx.color, 0.96F);
    }

    /** The shot: the mass gathers at the bottom of the screen, then throws itself at the target. */
    private static void shot(MinecraftClient client, Fx fx, float age, int beat, float tickDelta, int width, int height) {
        float sc = scale();
        float launch = (float)fx.launch();
        boolean lash = fx.slot != 0;
        float ox = lash ? (float)width + 14.0F * sc : (float)width * 0.5F;
        float oy = lash ? (float)height * 0.55F : (float)height + 18.0F * sc;
        float[] tip = project(current(fx, client, tickDelta));
        if (age < launch) {
            // Gathering: a swelling knot with smaller lumps crowding into it.
            float charge = age / Math.max(1.0F, launch);
            float[] xs = new float[5];
            float[] ys = new float[5];
            float[] rs = new float[5];
            xs[0] = ox;
            ys[0] = oy - 26.0F * sc * charge;
            rs[0] = (14.0F + 30.0F * charge) * sc * (1.0F + 0.08F * (float)hash(fx.seed, 1, beat));

            for (int i = 1; i < 5; i++) {
                double a = Math.PI + (double)i / 5.0 * Math.PI + (double)beat * 0.5;
                float away = (60.0F - 46.0F * charge) * sc;
                xs[i] = xs[0] + (float)Math.cos(a) * away;
                ys[i] = ys[0] + (float)Math.abs(Math.sin(a)) * away * 0.5F + 6.0F * sc;
                rs[i] = (5.0F + 7.0F * charge) * sc;
            }

            mass(xs, ys, rs, 5, 1, fx.color, 0.96F);
            return;
        }

        float span = Math.max(1.0F, (float)fx.total - launch);
        float travel = MathHelper.clamp((age - launch) / span, 0.0F, 1.0F);
        float after = MathHelper.clamp((age - (float)fx.total) / 9.0F, 0.0F, 1.0F);
        // Once it has hit, the tail lets go of the screen and is swallowed by the impact.
        float tail = after * after;
        float head = (float)Math.pow((double)travel, 0.75);
        if (tail >= 0.98F) {
            return;
        }

        int joints = 16;
        float[] xs = new float[joints + 1];
        float[] ys = new float[joints + 1];
        float[] rs = new float[joints + 1];
        float dx = tip[0] - ox;
        float dy = tip[1] - oy;
        float len = Math.max(1.0F, (float)Math.sqrt((double)(dx * dx + dy * dy)));
        float nx = -dy / len;
        float ny = dx / len;

        for (int i = 0; i < joints; i++) {
            float s = MathHelper.lerp((float)i / (float)(joints - 1), tail, head);
            float wobble = (float)Math.sin((double)(s * 9.0F) + (double)beat * 2.1 + (double)fx.seed) * 7.0F * sc * (float)Math.sin(Math.PI * (double)s);
            xs[i] = ox + dx * s + nx * wobble;
            ys[i] = oy + dy * s + ny * wobble;
            // Fat at the screen, thin in the middle where it stretches, heavy again at the head.
            float body = (lash ? 9.0F : 15.0F) * sc * (1.0F - 0.55F * (float)Math.sin(Math.PI * (double)((float)i / (float)(joints - 1))));
            rs[i] = body * (1.0F - after * 0.7F);
        }

        float fist = (lash ? 13.0F : 24.0F) * sc * (1.0F - after * 0.6F);
        xs[joints] = xs[joints - 1];
        ys[joints] = ys[joints - 1];
        rs[joints] = fist;
        if (after > 0.0F) {
            burst(tip[0], tip[1], (34.0F + 80.0F * after) * sc, (16.0F + 30.0F * after) * sc, 9, (float)beat * 0.7F, 0xFFFFFF, 1.0F - after);
            burst(tip[0], tip[1], (20.0F + 46.0F * after) * sc, (9.0F + 18.0F * after) * sc, 7, -(float)beat * 0.5F, fx.color, 1.0F - after);
        }

        mass(xs, ys, rs, joints + 1, joints, fx.color, 1.0F - after * 0.4F);
    }

    /** A jump of the mass between two things in the world. */
    private static void arc(Fx fx, float age, int beat) {
        float[] a = project(fx.source);
        float[] b = project(fx.pos);
        if (a[2] == 0.0F && b[2] == 0.0F) {
            return;
        }

        float sc = scale();
        float fade = MathHelper.clamp((float)fx.life / 4.0F, 0.0F, 1.0F);
        float reach = MathHelper.clamp(age / 2.0F, 0.0F, 1.0F);
        int joints = 8;
        float[] xs = new float[joints];
        float[] ys = new float[joints];
        float[] rs = new float[joints];
        float dx = b[0] - a[0];
        float dy = b[1] - a[1];
        float len = Math.max(1.0F, (float)Math.sqrt((double)(dx * dx + dy * dy)));

        for (int i = 0; i < joints; i++) {
            float s = (float)i / (float)(joints - 1) * reach;
            float jag = i == 0 || i == joints - 1 ? 0.0F : (float)hash(fx.seed, i, beat) * 26.0F * sc;
            xs[i] = a[0] + dx * s - dy / len * jag;
            ys[i] = a[1] + dy * s + dx / len * jag;
            rs[i] = (i == joints - 1 ? 9.0F : 5.5F) * sc * fade;
        }

        if (reach >= 1.0F) {
            burst(b[0], b[1], 22.0F * sc, 9.0F * sc, 6, (float)beat * 0.8F, 0xFFFFFF, fade);
        }

        mass(xs, ys, rs, joints, joints, fx.color, fade);
    }

    /** A shockwave rolling out across the ground. */
    private static void pulse(Fx fx, float age, int width, int height) {
        float sc = scale();
        float t = MathHelper.clamp(age / (float)Math.max(1, fx.total), 0.0F, 1.0F);
        float ease = 1.0F - (1.0F - t) * (1.0F - t);
        float alpha = 1.0F - t * t;
        if (fx.slot == 0) {
            float x = (float)width * 0.5F;
            float y = (float)height * 1.02F;
            wave(x, y, ease * (float)width * 0.72F, 0.42F, (8.0F + 26.0F * (1.0F - t)) * sc, fx.color, alpha);
            wave(x, y, ease * (float)width * 0.5F, 0.42F, (5.0F + 12.0F * (1.0F - t)) * sc, mix(fx.color, 0xFFFFFF, 0.5F), alpha * 0.9F);
        } else {
            float[] at = project(fx.pos.add(0.0, 0.1, 0.0));
            if (at[2] != 0.0F) {
                wave(at[0], at[1], ease * 70.0F * sc, 0.45F, (4.0F + 12.0F * (1.0F - t)) * sc, fx.color, alpha);
            }
        }
    }

    /** A lasting state (barrier, slowed time, a mended chip): the mass creeps in around the frame. */
    private static void frame(Fx fx, float age, float tickDelta, int beat, int width, int height) {
        float in = MathHelper.clamp(age / 5.0F, 0.0F, 1.0F);
        float out = MathHelper.clamp(((float)fx.life - tickDelta) / 6.0F, 0.0F, 1.0F);
        float show = Math.min(in, out);
        if (show <= 0.02F) {
            return;
        }

        float unit = (float)height * 0.085F;
        int bottom = 11;
        int side = 5;
        int count = bottom + side * 2;
        float[] xs = new float[count];
        float[] ys = new float[count];
        float[] rs = new float[count];

        for (int i = 0; i < bottom; i++) {
            float r = unit * (0.75F + 0.5F * (float)Math.abs(hash(fx.seed, i, 0)));
            xs[i] = (float)width * ((float)i + 0.5F) / (float)bottom;
            ys[i] = (float)height + r * (1.0F - 0.75F * show) - (float)Math.sin((double)beat * 0.9 + (double)i * 1.7) * r * 0.22F;
            rs[i] = r;
        }

        for (int i = 0; i < side; i++) {
            float r = unit * (0.6F + 0.4F * (float)Math.abs(hash(fx.seed, i, 7))) * (1.0F - (float)i / (float)side * 0.55F);
            float y = (float)height - unit * 0.6F - (float)i * unit * 1.25F;
            float bob = (float)Math.sin((double)beat * 0.8 + (double)i * 2.1) * r * 0.2F;
            xs[bottom + i] = -r * (1.0F - 0.8F * show) + bob;
            ys[bottom + i] = y;
            rs[bottom + i] = r;
            xs[bottom + side + i] = (float)width + r * (1.0F - 0.8F * show) - bob;
            ys[bottom + side + i] = y;
            rs[bottom + side + i] = r;
        }

        mass(xs, ys, rs, count, bottom, fx.color, 0.9F * show);
    }

    /** The turret node: a knot of the mass hanging in the air. */
    private static void node(Fx fx, float age, float time, int beat) {
        float[] at = project(fx.pos);
        if (at[2] == 0.0F) {
            return;
        }

        float sc = scale();
        double distance = Math.sqrt(fx.pos.squaredDistanceTo(cameraPos));
        float r = MathHelper.clamp((float)(150.0 / Math.max(2.0, distance)), 5.0F, 22.0F) * sc * MathHelper.clamp(age / 5.0F, 0.0F, 1.0F) * MathHelper.clamp((float)fx.life / 6.0F, 0.0F, 1.0F);
        burst(at[0], at[1], r * 2.1F, r * 0.9F, 4, time * 0.12F, mix(fx.color, 0xFFFFFF, 0.5F), 0.9F);
        float[] xs = new float[4];
        float[] ys = new float[4];
        float[] rs = new float[4];
        xs[0] = at[0];
        ys[0] = at[1];
        rs[0] = r * (1.0F + 0.1F * (float)Math.sin((double)beat * 1.4));

        for (int i = 1; i < 4; i++) {
            double a = (double)time * 0.2 + (double)i * Math.PI * 2.0 / 3.0;
            xs[i] = at[0] + (float)Math.cos(a) * r * 1.9F;
            ys[i] = at[1] + (float)Math.sin(a) * r * 1.1F;
            rs[i] = r * 0.32F;
        }

        mass(xs, ys, rs, 4, 1, fx.color, 0.96F);
    }

    /** Volta's sly face cuts in over the view for the moment before the shot. */
    private static void face(DrawContext context, Fx fx, float age, int width, int height, float tickDelta) {
        float end = (float)fx.total + 5.0F;
        if (age >= end) {
            return;
        }

        float in = MathHelper.clamp(age / 3.0F, 0.0F, 1.0F);
        float out = MathHelper.clamp((end - age) / 4.0F, 0.0F, 1.0F);
        float show = Math.min(in, out);
        float size = (float)Math.min(width, height) * 0.74F * (0.9F + 0.1F * in);
        float x = (float)width * 0.5F - size * 0.5F + (1.0F - in) * (float)width * 0.12F;
        float y = (float)height * 0.52F - size * 0.5F;
        int rgb = fx.color & 0xFFFFFF;
        int band = (int)(size * 0.36F);
        int cy = (int)((float)height * 0.5F);
        // A letterbox bar slams across the middle of the screen and she is in it.
        int open = (int)((float)band * show);
        context.fill(0, cy - open, width, cy + open, 0x101820 | (int)(150.0F * show) << 24);
        context.fill(0, cy - open - 2, width, cy - open, rgb | (int)(255.0F * show) << 24);
        context.fill(0, cy + open, width, cy + open + 2, rgb | (int)(255.0F * show) << 24);
        context.enableScissor(0, cy - open, width, cy + open);
        // Ghost copies either side sell the speed of the cut.
        VoltaRig.draw(context, x - 14.0F * (1.0F - out + 0.3F), y, size, -4.0F, 0.25F * show, tickDelta);
        VoltaRig.draw(context, x + 14.0F * (1.0F - out + 0.3F), y, size, -4.0F, 0.25F * show, tickDelta);
        VoltaRig.draw(context, x, y, size, -4.0F + 3.0F * (1.0F - in), show, tickDelta);

        for (int line = cy - open; line < cy + open; line += 3) {
            context.fill(0, line, width, line + 1, rgb | (int)(26.0F * show) << 24);
        }

        // Speed lines racing towards the muzzle as the charge peaks.
        for (int i = 0; i < 9; i++) {
            float lane = (float)(hash(fx.seed, i, 3) + 0.5);
            float phase = (age * (0.11F + 0.05F * (float)(i % 3)) + lane) % 1.0F;
            int ly = cy - open + (int)(lane * (float)open * 2.0F);
            int lx = (int)((1.0F - phase) * (float)width * 1.3F) - width / 6;
            context.fill(lx, ly, lx + width / 7, ly + 1, 0xFFFFFF | (int)(140.0F * show) << 24);
        }

        context.disableScissor();
        if (age > (float)fx.launch() - 2.0F) {
            float charge = MathHelper.clamp((age - (float)fx.launch() + 2.0F) / 2.0F, 0.0F, 1.0F) * out;
            context.fill(0, cy - open, width, cy + open, 0xFFFFFF | (int)(110.0F * charge) << 24);
        }
    }
}
