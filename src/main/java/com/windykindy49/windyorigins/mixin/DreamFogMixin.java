package com.windykindy49.windyorigins.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.windykindy49.windyorigins.client.DreamVision;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.BackgroundRenderer.FogType;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({BackgroundRenderer.class})
public abstract class DreamFogMixin {
    @Shadow
    private static float red;
    @Shadow
    private static float green;
    @Shadow
    private static float blue;

    @Inject(
        method = {"render"},
        at = {@At("TAIL")}
    )
    private static void windyorigins$tint(Camera var0, float var1, ClientWorld var2, int var3, float var4, CallbackInfo var5) {
        float[] var6 = DreamVision.tint(red, green, blue, var1);
        if (var6 != null) {
            red = var6[0];
            green = var6[1];
            blue = var6[2];
            RenderSystem.clearColor(red, green, blue, 0.0F);
        }
    }

    @Inject(
        method = {"applyFog"},
        at = {@At("TAIL")}
    )
    private static void windyorigins$close(Camera var0, FogType var1, float var2, boolean var3, float var4, CallbackInfo var5) {
        DreamVision.applyFog(var0, var1, var4);
    }
}
