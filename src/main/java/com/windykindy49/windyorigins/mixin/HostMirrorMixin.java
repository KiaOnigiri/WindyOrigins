package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.client.HostClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({GameRenderer.class})
public abstract class HostMirrorMixin {
    @Unique
    private boolean windyorigins$flipped;

    @Inject(
        method = {"renderWorld"},
        at = {@At("HEAD")}
    )
    private void windyorigins$mirrorStart(float var1, long var2, MatrixStack var4, CallbackInfo var5) {
        this.windyorigins$flipped = HostClient.mirrored();
        if (this.windyorigins$flipped) {
            GL11.glFrontFace(2304);
        }
    }

    @Inject(
        method = {"renderWorld"},
        at = {@At("RETURN")}
    )
    private void windyorigins$mirrorEnd(float var1, long var2, MatrixStack var4, CallbackInfo var5) {
        if (this.windyorigins$flipped) {
            GL11.glFrontFace(2305);
            this.windyorigins$flipped = false;
        }
    }

    @Inject(
        method = {"getBasicProjectionMatrix"},
        at = {@At("RETURN")}
    )
    private void windyorigins$mirror(double var1, CallbackInfoReturnable<Matrix4f> var3) {
        if (this.windyorigins$flipped) {
            ((Matrix4f)var3.getReturnValue()).scale(-1.0F, 1.0F, 1.0F);
        }
    }
}
