package com.windykindy49.windyorigins.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

public final class VoltaRig {
    private static final VoltaRig.Expression[] FACES = new VoltaRig.Expression[8];
    private static int face = 0;
    private static int fromFace = 0;
    private static float morph = 1.0F;
    private static float prevMorph = 1.0F;
    private static int nextBlink = 60;
    private static int blinkLeft;
    private static float hair;
    private static float hairVel;
    private static float prevHair;
    private static float head;
    private static float prevHead;
    private static int age;
    private static int mouthFrame;
    private static boolean talking;

    private VoltaRig() {
    }

    private static Identifier tex(String var0) {
        return new Identifier("windyorigins", "textures/gui/volta_rig/" + var0 + ".png");
    }

    static void tick(int var0, boolean var1) {
        age++;
        talking = var1;
        if (var0 != face) {
            fromFace = face;
            face = var0;
            morph = 0.0F;
        }

        prevMorph = morph;
        morph = Math.min(1.0F, morph + 0.25F);
        if (blinkLeft > 0) {
            blinkLeft--;
        } else if (--nextBlink <= 0) {
            blinkLeft = 3;
            nextBlink = age % 5 == 0 ? 6 : 50 + (int)((Math.sin((double)age * 12.9898) * 43758.5453 % 1.0 + 1.0) % 1.0 * 60.0);
        }

        if (talking && age % 3 == 0) {
            mouthFrame = (mouthFrame + 1 + age / 7 % 2) % 3;
        } else if (!talking) {
            mouthFrame = 0;
        }

        prevHead = head;
        float var2 = MathHelper.sin((float)age * 0.045F) * 3.0F + (talking ? MathHelper.sin((float)age * 0.6F) * 1.2F : 0.0F);
        head = head + (var2 - head) * 0.3F;
        prevHair = hair;
        hairVel = hairVel + (head - hair) * 0.22F;
        hairVel *= 0.72F;
        hair = hair + hairVel;
    }

    /** When zero or more, this expression is drawn whatever the rig is currently doing. */
    public static int forced = -1;

    private static float noise(int var0, int var1) {
        long var2 = (long)var0 * 0x9E3779B97F4A7C15L + (long)var1 * 0xC2B2AE3D27D4EB4FL;
        var2 ^= var2 >>> 29;
        var2 *= 0xBF58476D1CE4E5B9L;
        var2 ^= var2 >>> 32;
        return (float)(var2 & 0xFFFFL) / 65535.0F;
    }

    /**
     * Volta is pixel art animated the way pixel art is: each pose is held for three ticks, parts
     * shift by whole pixels instead of rotating, an expression changes in one frame, and now and
     * then the picture slips.
     */
    public static void draw(DrawContext var0, float var1, float var2, float var3, float var4, float var5, float var6) {
        MatrixStack var7 = var0.getMatrices();
        int var8 = age / 3;
        // One art pixel is four units of the 256-unit canvas.
        float var9 = 4.0F;
        int var10 = Math.round(MathHelper.sin((float)var8 * 0.42F) * 1.4F);
        int var11 = Math.round(MathHelper.sin((float)(var8 - 1) * 0.42F) * 1.4F) - var10;
        int var12 = var8 / 3 % 2;
        int var13 = talking ? var8 % 2 : 0;
        float var14 = (float)Math.round(var4 / 6.0F) * 6.0F;
        VoltaRig.Expression var15 = FACES[forced >= 0 && forced < FACES.length ? forced : face];
        // A dropped frame: for one pose in a dozen the head slips sideways.
        int var16 = noise(var8, 3) < 0.08F ? (noise(var8, 5) < 0.5F ? -2 : 2) : 0;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        var7.push();
        var7.translate(var1, var2, 0.0F);
        var7.scale(var3 / 256.0F, var3 / 256.0F, 1.0F);
        if (var14 != 0.0F) {
            rotateAbout(var7, 128.0F, 190.0F, var14);
        }

        var7.push();
        var7.translate((float)(var10 + var11) * var9, 0.0F, 0.0F);
        layer(var0, "hair_back", var5);
        var7.pop();
        var7.push();
        var7.translate(0.0F, (float)var12 * var9, 0.0F);
        layer(var0, "body", var5);
        layer(var0, "neck", var5);
        var7.pop();
        var7.push();
        var7.translate((float)(var10 + var16) * var9, (float)(var12 - var13) * var9, 0.0F);
        layer(var0, "head", var5);
        expression(var0, var15, var5, false);
        var7.push();
        var7.translate((float)var11 * var9, 0.0F, 0.0F);
        layer(var0, "hair_front", var5);
        var7.pop();
        brows(var0, var15, var5);
        layer(var0, "phones", var5);
        var7.pop();
        var7.pop();
        var0.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void expression(DrawContext var0, VoltaRig.Expression var1, float var2, boolean var3) {
        if (!(var2 <= 0.01F)) {
            String var4 = var1.eyes;
            boolean var5 = var4.equals("open") || var4.equals("half") || var4.equals("wide") || var4.equals("angry");
            if (blinkLeft > 0 && var5 && !var3) {
                var4 = "closed";
            }

            layer(var0, "eyes_" + var4, var2);
            if (var1.blush > 0.0F) {
                layer(var0, "blush", var2 * var1.blush);
            }

            String var6 = var1.mouth;
            if (talking && !var3 && !var6.equals("tongue")) {
                var6 = switch (mouthFrame) {
                    case 1 -> "talk1";
                    case 2 -> "talk2";
                    default -> !var1.mouth.equals("frown") && !var1.mouth.equals("wavy") ? "smile" : var1.mouth;
                };
            }

            layer(var0, "mouth_" + var6, var2);
        }
    }

    private static void brows(DrawContext var0, VoltaRig.Expression var1, float var2) {
        if (var2 > 0.01F) {
            layer(var0, "brows_" + var1.brows, var2);
        }
    }

    private static void layer(DrawContext var0, String var1, float var2) {
        var0.setShaderColor(1.0F, 1.0F, 1.0F, MathHelper.clamp(var2, 0.0F, 1.0F));
        var0.drawTexture(tex(var1), 0, 0, 0.0F, 0.0F, 256, 256, 256, 256);
    }

    private static void rotateAbout(MatrixStack var0, float var1, float var2, float var3) {
        var0.translate(var1, var2, 0.0F);
        var0.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(var3));
        var0.translate(-var1, -var2, 0.0F);
    }

    static {
        FACES[0] = new VoltaRig.Expression("open", "neutral", "smile", 0.6F);
        FACES[1] = new VoltaRig.Expression("half", "sly", "cat", 0.0F);
        FACES[2] = new VoltaRig.Expression("half", "neutral", "flat", 0.0F);
        FACES[3] = new VoltaRig.Expression("wide", "up", "wavy", 0.8F);
        FACES[4] = new VoltaRig.Expression("angry", "angry", "frown", 0.0F);
        FACES[5] = new VoltaRig.Expression("happy", "neutral", "tongue", 1.0F);
        FACES[6] = new VoltaRig.Expression("wink", "neutral", "grin", 0.8F);
        FACES[7] = new VoltaRig.Expression("glitch", "neutral", "talk1", 0.0F);
    }

    private static record Expression(String eyes, String brows, String mouth, float blush) {
    }
}
