package com.windykindy49.windyorigins.client;

import com.windykindy49.windyorigins.common.Powers;
import com.windykindy49.windyorigins.common.WindyEffects;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import com.windykindy49.windyorigins.client.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

public final class RaceHud {
    private static final Identifier SANGUINE = new Identifier("windyorigins", "textures/gui/sanguine.png");

    private RaceHud() {
    }

    public static void init() {
        HudRenderCallback.EVENT.register((matrices, delta) -> RaceHud.render(new com.windykindy49.windyorigins.client.DrawContext(matrices), delta));
    }

    private static void render(DrawContext var0, float var1) {
        MinecraftClient var2 = MinecraftClient.getInstance();
        ClientPlayerEntity var3 = var2.player;
        if (var3 != null && !var2.options.hudHidden) {
            int var4 = var0.getScaledWindowWidth();
            int var5 = var0.getScaledWindowHeight();
            float var6 = ((float)var3.age + var1) / 20.0F;
            int var7 = Powers.resource(var3, "chill", -1);
            if (var7 >= 60) {
                float var8 = (float)(var7 - 60) / 40.0F;
                edges(var0, var4, var5, (int)(64.0F * var8 + 16.0F), 14546175);
            }

            if (RevenantClient.isHaunted()) {
                int var13 = (int)(56.0F + 32.0F * (0.5F + 0.5F * MathHelper.sin(var6 * 1.3F)));
                edges(var0, var4, var5, var13, 4171960);
            }

            if (var3.hasStatusEffect(WindyEffects.POSSESSED)) {
                edges(var0, var4, var5, 48, 7332080);
            }

            StatusEffectInstance var14 = var3.getStatusEffect(WindyEffects.TASTE);
            if (var14 != null && Powers.has(var3, "blood_thirst") && !var3.isCreative() && !var3.isSpectator()) {
                int var9 = var4 / 2 + 91 + 3;
                int var10 = var5 - 39;
                var0.drawTexture(SANGUINE, var9, var10, 73, 18, 8, 8);
                String[] var11 = new String[]{"", "❂", "≋", "♨", "☁", "⚔"};
                int var12 = MathHelper.clamp(var14.getAmplifier(), 0, var11.length - 1);
                if (!var11[var12].isEmpty()) {
                    var0.drawTextWithShadow(var2.textRenderer, var11[var12], var9 + 10, var10, 14696528);
                }
            }
        }
    }

    private static void edges(DrawContext var0, int var1, int var2, int var3, int var4) {
        int var5 = Math.max(12, Math.min(var1, var2) / 7);
        int var6 = MathHelper.clamp(var3, 0, 255) << 24 | var4;
        var0.fillGradient(0, 0, var1, var5, var6, var4);
        var0.fillGradient(0, var2 - var5, var1, var2, var4, var6);
        byte var8 = 8;

        for (int var9 = 0; var9 < var8; var9++) {
            int var10 = (int)((float)MathHelper.clamp(var3, 0, 255) * (1.0F - (float)var9 / (float)var8));
            int var11 = var10 << 24 | var4;
            int var12 = var5 * var9 / var8;
            int var13 = var5 * (var9 + 1) / var8;
            var0.fill(var12, var5, var13, var2 - var5, var11);
            var0.fill(var1 - var13, var5, var1 - var12, var2 - var5, var11);
        }
    }
}
