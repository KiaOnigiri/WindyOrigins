package com.windykindy49.windyorigins.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.windykindy49.windyorigins.dream.DreamManager;
import net.fabricmc.fabric.api.client.rendering.v1.DimensionRenderingRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.DimensionEffects;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.DimensionEffects.SkyType;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.joml.Matrix4f;

public final class DreamSky {
    public static final Identifier EFFECTS = new Identifier("windyorigins", "dreams");
    private static final int STARS = 220;
    private static final float[][] STAR_DATA = new float[220][6];
    static final float[] FOG;
    private static final float[][] STOPS;

    private DreamSky() {
    }

    public static void init() {
        DimensionRenderingRegistry.registerDimensionEffects(EFFECTS, new DreamSky.Effects());
        DimensionRenderingRegistry.registerSkyRenderer(DreamManager.DREAMS, DreamSky::render);
        DimensionRenderingRegistry.registerCloudRenderer(DreamManager.DREAMS, var0 -> {
        });
        DimensionRenderingRegistry.registerWeatherRenderer(DreamManager.DREAMS, var0 -> {
        });
    }

    private static float[] colorAt(float var0, float var1) {
        for (int var2 = 1; var2 < STOPS.length; var2++) {
            if (var0 <= STOPS[var2][0]) {
                float var3 = (var0 - STOPS[var2 - 1][0]) / (STOPS[var2][0] - STOPS[var2 - 1][0]);
                float var4 = MathHelper.lerp(var3, STOPS[var2 - 1][1], STOPS[var2][1]);
                float var5 = MathHelper.lerp(var3, STOPS[var2 - 1][2], STOPS[var2][2]);
                float var6 = MathHelper.lerp(var3, STOPS[var2 - 1][3], STOPS[var2][3]);
                float var7 = 1.0F - Math.min(1.0F, Math.abs(var0) * 3.0F);
                var4 = Math.min(1.0F, var4 + 0.04F * var1 * var7);
                return new float[]{var4, var5, var6};
            }
        }

        return new float[]{STOPS[STOPS.length - 1][1], STOPS[STOPS.length - 1][2], STOPS[STOPS.length - 1][3]};
    }

    private static void render(WorldRenderContext var0) {
        float var1 = (float)(var0.world().getTime() % 24000L) + var0.tickDelta();
        float var2 = MathHelper.sin(var1 * 0.01F);
        float var3 = DreamVision.fogAmount(var0.tickDelta());
        Matrix4f var4 = var0.matrixStack().peek().getPositionMatrix();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        Tessellator var5 = Tessellator.getInstance();
        BufferBuilder var6 = var5.getBuffer();
        float var7 = 100.0F;
        byte var8 = 24;
        byte var9 = 32;
        var6.begin(DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        for (int var10 = 0; var10 < var8; var10++) {
            float var11 = (float) (-Math.PI / 2) + (float) Math.PI * (float)var10 / (float)var8;
            float var12 = (float) (-Math.PI / 2) + (float) Math.PI * (float)(var10 + 1) / (float)var8;
            float[] var13 = swallow(colorAt(MathHelper.sin(var11), var2), var3);
            float[] var14 = swallow(colorAt(MathHelper.sin(var12), var2), var3);

            for (int var15 = 0; var15 < var9; var15++) {
                float var16 = (float) (Math.PI * 2) * (float)var15 / (float)var9;
                float var17 = (float) (Math.PI * 2) * (float)(var15 + 1) / (float)var9;
                vertex(var6, var4, var7, var11, var16, var13);
                vertex(var6, var4, var7, var11, var17, var13);
                vertex(var6, var4, var7, var12, var17, var14);
                vertex(var6, var4, var7, var12, var16, var14);
            }
        }

        var5.draw();
        var6.begin(DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        float var28 = 95.0F;

        for (float[] var32 : STAR_DATA) {
            float var33 = 0.45F + 0.55F * (0.5F + 0.5F * MathHelper.sin(var1 * 0.05F + var32[4]));
            float var34 = var32[3] * (0.8F + 0.4F * var33);
            float var35 = var32[0] * var28;
            float var18 = var32[1] * var28;
            float var19 = var32[2] * var28;
            Vec3d var20 = new Vec3d((double)var32[0], (double)var32[1], (double)var32[2]);
            Vec3d var21 = Math.abs(var32[1]) > 0.9F ? new Vec3d(1.0, 0.0, 0.0) : new Vec3d(0.0, 1.0, 0.0);
            Vec3d var22 = var20.crossProduct(var21).normalize().multiply((double)var34);
            Vec3d var23 = var20.crossProduct(var22).normalize().multiply((double)var34);
            float var24 = var32[5] > 0.5F ? 1.0F : 1.0F;
            float var25 = var32[5] > 0.5F ? 0.8F : 0.97F;
            float var26 = var32[5] > 0.5F ? 0.94F : 1.0F;
            float var27 = 0.9F * var33 * (1.0F - var3);
            var6.vertex(
                    var4, (float)((double)var35 - var22.x - var23.x), (float)((double)var18 - var22.y - var23.y), (float)((double)var19 - var22.z - var23.z)
                )
                .color(var24, var25, var26, var27)
                .next();
            var6.vertex(
                    var4, (float)((double)var35 + var22.x - var23.x), (float)((double)var18 + var22.y - var23.y), (float)((double)var19 + var22.z - var23.z)
                )
                .color(var24, var25, var26, var27)
                .next();
            var6.vertex(
                    var4, (float)((double)var35 + var22.x + var23.x), (float)((double)var18 + var22.y + var23.y), (float)((double)var19 + var22.z + var23.z)
                )
                .color(var24, var25, var26, var27)
                .next();
            var6.vertex(
                    var4, (float)((double)var35 - var22.x + var23.x), (float)((double)var18 - var22.y + var23.y), (float)((double)var19 - var22.z + var23.z)
                )
                .color(var24, var25, var26, var27)
                .next();
        }

        var5.draw();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
    }

    private static float[] swallow(float[] var0, float var1) {
        if (var1 <= 0.0F) {
            return var0;
        } else {
            float[] var2 = DreamVision.tint(FOG[0], FOG[1], FOG[2], 1.0F);
            return var2 == null
                ? var0
                : new float[]{MathHelper.lerp(var1, var0[0], var2[0]), MathHelper.lerp(var1, var0[1], var2[1]), MathHelper.lerp(var1, var0[2], var2[2])};
        }
    }

    private static void vertex(BufferBuilder var0, Matrix4f var1, float var2, float var3, float var4, float[] var5) {
        float var6 = MathHelper.cos(var3);
        var0.vertex(var1, var2 * var6 * MathHelper.cos(var4), var2 * MathHelper.sin(var3), var2 * var6 * MathHelper.sin(var4))
            .color(var5[0], var5[1], var5[2], 1.0F)
            .next();
    }

    static {
        Random var0 = Random.create(20261104L);

        for (int var1 = 0; var1 < 220; var1++) {
            double var2 = var0.nextDouble() * 1.7 - 0.7;
            double var4 = var0.nextDouble() * Math.PI * 2.0;
            double var6 = Math.sqrt(Math.max(0.0, 1.0 - var2 * var2));
            STAR_DATA[var1][0] = (float)(Math.cos(var4) * var6);
            STAR_DATA[var1][1] = (float)var2;
            STAR_DATA[var1][2] = (float)(Math.sin(var4) * var6);
            STAR_DATA[var1][3] = 0.25F + var0.nextFloat() * 0.45F;
            STAR_DATA[var1][4] = var0.nextFloat() * 6.28F;
            STAR_DATA[var1][5] = var0.nextFloat();
        }

        FOG = new float[]{1.0F, 0.8F, 0.76F};
        STOPS = new float[][]{
            {-1.0F, 0.7F, 0.62F, 1.0F},
            {-0.35F, 0.92F, 0.72F, 0.94F},
            {0.0F, 1.0F, 0.8F, 0.76F},
            {0.18F, 0.86F, 0.84F, 1.0F},
            {0.55F, 0.66F, 0.82F, 1.0F},
            {1.0F, 0.5F, 0.72F, 1.0F}
        };
    }

    static final class Effects extends DimensionEffects {
        Effects() {
            super(Float.NaN, false, SkyType.NONE, false, false);
        }

        @Override
        public Vec3d adjustFogColor(Vec3d var1, float var2) {
            return new Vec3d((double)DreamSky.FOG[0], (double)DreamSky.FOG[1], (double)DreamSky.FOG[2]);
        }

        @Override
        public boolean useThickFog(int var1, int var2) {
            return true;
        }

        @Override
        public float[] getFogColorOverride(float var1, float var2) {
            return null;
        }
    }
}
