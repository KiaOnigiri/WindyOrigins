package com.windykindy49.windyorigins.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.windykindy49.windyorigins.common.WindyEffects;
import com.windykindy49.windyorigins.dream.DreamManager;
import com.windykindy49.windyorigins.mixin.MinecraftClientInvoker;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.CameraSubmersionType;
import net.minecraft.client.render.FogShape;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.BackgroundRenderer.FogType;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.util.Window;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.util.math.MathHelper;

public final class DreamVision {
    private static float fog;
    private static float nightmare;
    private static float lulled;
    private static float prevLulled;
    private static final int DELAY = 7;
    private static int attackIn = -1;
    private static int useIn = -1;
    private static boolean replaying;
    private static float prevFog;
    private static float prevNightmare;
    private static int age;
    private static Framebuffer copy;
    private static final float[] DREAM_FOG_COLOR = new float[]{0.56F, 0.31F, 0.36F};
    private static final float[] NIGHTMARE_COLOR = new float[]{0.4F, 0.28F, 0.31F};

    private DreamVision() {
    }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(var0 -> {
            age++;
            prevFog = fog;
            prevNightmare = nightmare;
            ClientPlayerEntity var1 = var0.player;
            fog = approach(fog, strength(var1, WindyEffects.DREAM_FOG), 0.08F);
            nightmare = approach(nightmare, strength(var1, WindyEffects.NIGHTMARE), 0.04F);
            prevLulled = lulled;
            lulled = approach(lulled, strength(var1, WindyEffects.LULLED), 0.06F);
            if (var1 != null) {
                sway(var1);
                replay(var0);
            }
        });
    }

    private static void sway(ClientPlayerEntity var0) {
        if (!(lulled <= 0.01F) && !var0.getAbilities().flying && !var0.isSpectator() && !var0.hasVehicle()) {
            float var1 = var0.getYaw() * (float) (Math.PI / 180.0);
            double var2 = (double)MathHelper.cos(var1);
            double var4 = (double)MathHelper.sin(var1);
            double var6 = ((double)MathHelper.sin((float)age * 0.11F) * 0.022 + (double)MathHelper.sin((float)age * 0.37F) * 0.008) * (double)lulled;
            if (age % 37 == 0 && var0.isOnGround()) {
                var6 += (double)(var0.getRandom().nextBoolean() ? 1 : -1) * 0.06 * (double)lulled;
            }

            var0.addVelocity(var2 * var6, 0.0, var4 * var6);
        }
    }

    public static boolean delayAttack() {
        if (!replaying && !(lulled <= 0.2F)) {
            if (attackIn < 0) {
                attackIn = 7;
            }

            return true;
        } else {
            return false;
        }
    }

    public static boolean delayUse() {
        if (!replaying && !(lulled <= 0.2F)) {
            if (useIn < 0) {
                useIn = 7;
            }

            return true;
        } else {
            return false;
        }
    }

    private static void replay(MinecraftClient var0) {
        if (attackIn > 0 && --attackIn == 0) {
            attackIn = -1;
            if (var0.currentScreen == null) {
                replaying = true;

                try {
                    ((MinecraftClientInvoker)var0).windyorigins$doAttack();
                } finally {
                    replaying = false;
                }
            }
        }

        if (useIn > 0 && --useIn == 0) {
            useIn = -1;
            if (var0.currentScreen == null) {
                replaying = true;

                try {
                    ((MinecraftClientInvoker)var0).windyorigins$doItemUse();
                } finally {
                    replaying = false;
                }
            }
        }
    }

    private static float strength(ClientPlayerEntity var0, StatusEffect var1) {
        if (var0 == null) {
            return 0.0F;
        } else {
            StatusEffectInstance var2 = var0.getStatusEffect(var1);
            if (var2 == null) {
                return 0.0F;
            } else {
                return var2.isInfinite() ? 1.0F : MathHelper.clamp((float)var2.getDuration() / 40.0F, 0.0F, 1.0F);
            }
        }
    }

    private static float approach(float var0, float var1, float var2) {
        return var0 < var1 ? Math.min(var1, var0 + var2) : Math.max(var1, var0 - var2);
    }

    public static float fogAmount(float var0) {
        return MathHelper.lerp(var0, prevFog, fog);
    }

    public static float nightmareAmount(float var0) {
        return MathHelper.lerp(var0, prevNightmare, nightmare);
    }

    public static float[] tint(float var0, float var1, float var2, float var3) {
        float var4 = fogAmount(var3);
        float var5 = nightmareAmount(var3);
        if (var4 <= 0.001F && var5 <= 0.001F) {
            return null;
        } else {
            float[] var6 = new float[]{var0, var1, var2};

            for (int var7 = 0; var7 < 3; var7++) {
                var6[var7] = MathHelper.lerp(var5, var6[var7], NIGHTMARE_COLOR[var7]);
                var6[var7] = MathHelper.lerp(var4, var6[var7], DREAM_FOG_COLOR[var7]);
            }

            return var6;
        }
    }

    public static void applyFog(Camera var0, FogType var1, float var2) {
        if (var0.getSubmersionType() == CameraSubmersionType.NONE) {
            float var3 = fogAmount(var2);
            float var4 = nightmareAmount(var2);
            if (!(var3 <= 0.001F) || !(var4 <= 0.001F)) {
                float var5 = RenderSystem.getShaderFogStart();
                float var6 = RenderSystem.getShaderFogEnd();
                boolean var7 = var1 == FogType.FOG_SKY;
                float var8 = MathHelper.lerp(var4, var6, var7 ? 6.0F : 14.0F);
                float var9 = MathHelper.lerp(var4, var5, var7 ? 0.0F : 1.5F);
                float var10 = MathHelper.lerp(var3, var6, var7 ? 1.0F : 5.0F);
                float var11 = MathHelper.lerp(var3, var5, 0.0F);
                RenderSystem.setShaderFogStart(Math.min(Math.min(var5, var9), var11));
                RenderSystem.setShaderFogEnd(Math.min(Math.min(var6, var8), var10));
                RenderSystem.setShaderFogShape(FogShape.SPHERE);
            }
        }
    }

    public static void drawDouble(float var0) {
        float var1 = nightmareAmount(var0);
        float var2 = MathHelper.lerp(var0, prevLulled, lulled);
        MinecraftClient var3 = MinecraftClient.getInstance();
        if ((!(var1 <= 0.01F) || !(var2 <= 0.01F)) && var3.world != null && var3.player != null) {
            Framebuffer var4 = var3.getFramebuffer();
            int var5 = var4.textureWidth;
            int var6 = var4.textureHeight;
            if (copy == null) {
                copy = new SimpleFramebuffer(var5, var6, false, MinecraftClient.IS_SYSTEM_MAC);
            } else if (copy.textureWidth != var5 || copy.textureHeight != var6) {
                copy.resize(var5, var6, MinecraftClient.IS_SYSTEM_MAC);
            }

            GlStateManager._glBindFramebuffer(36008, var4.fbo);
            GlStateManager._glBindFramebuffer(36009, copy.fbo);
            GlStateManager._glBlitFrameBuffer(0, 0, var5, var6, 0, 0, var5, var6, 16384, 9728);
            var4.beginWrite(false);
            Window var7 = var3.getWindow();
            float var8 = (float)((double)var7.getFramebufferWidth() / var7.getScaleFactor());
            float var9 = (float)((double)var7.getFramebufferHeight() / var7.getScaleFactor());
            float var10 = (float)age + var0;
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
            RenderSystem.setShaderTexture(0, copy.getColorAttachment());
            if (var2 > 0.01F) {
                float var11 = (1.2F + 1.3F * (0.5F + 0.5F * MathHelper.sin(var10 * 0.09F))) * var2;
                byte var12 = 8;

                for (int var13 = 0; var13 < var12; var13++) {
                    float var14 = (float) (Math.PI * 2) * (float)var13 / (float)var12 + var10 * 0.02F;
                    layer(
                        var8,
                        var9,
                        MathHelper.cos(var14) * var11,
                        MathHelper.sin(var14) * var11,
                        1.0F + 0.004F * var2,
                        1.0F,
                        1.0F,
                        1.0F,
                        0.5F * var2 / (1.0F + (float)var13 * 0.35F)
                    );
                }
            }

            if (var1 > 0.01F) {
                float var15 = (MathHelper.sin(var10 * 0.07F) * 7.0F + MathHelper.sin(var10 * 0.23F) * 2.0F) * var1;
                float var16 = MathHelper.cos(var10 * 0.05F) * 3.0F * var1;
                float var17 = 1.0F + 0.02F * var1 * MathHelper.sin(var10 * 0.04F);
                layer(var8, var9, var15, var16, var17, 1.0F, 0.86F, 0.84F, 0.42F * var1);
                layer(var8, var9, -var15 * 0.6F, -var16, 2.0F - var17, 0.86F, 0.9F, 1.0F, 0.24F * var1);
            }

            RenderSystem.disableBlend();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
        }
    }

    private static void layer(float var0, float var1, float var2, float var3, float var4, float var5, float var6, float var7, float var8) {
        float var9 = var0 / 2.0F + var2;
        float var10 = var1 / 2.0F + var3;
        float var11 = var0 / 2.0F * var4;
        float var12 = var1 / 2.0F * var4;
        BufferBuilder var13 = Tessellator.getInstance().getBuffer();
        var13.begin(DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        var13.vertex((double)(var9 - var11), (double)(var10 + var12), 0.0).texture(0.0F, 0.0F).color(var5, var6, var7, var8).next();
        var13.vertex((double)(var9 + var11), (double)(var10 + var12), 0.0).texture(1.0F, 0.0F).color(var5, var6, var7, var8).next();
        var13.vertex((double)(var9 + var11), (double)(var10 - var12), 0.0).texture(1.0F, 1.0F).color(var5, var6, var7, var8).next();
        var13.vertex((double)(var9 - var11), (double)(var10 - var12), 0.0).texture(0.0F, 1.0F).color(var5, var6, var7, var8).next();
        Tessellator.getInstance().draw();
    }

    public static boolean inDreams() {
        MinecraftClient var0 = MinecraftClient.getInstance();
        return var0.world != null && var0.world.getRegistryKey() == DreamManager.DREAMS;
    }
}
