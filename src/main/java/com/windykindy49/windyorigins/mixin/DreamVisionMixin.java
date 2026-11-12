package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.client.DreamVision;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({GameRenderer.class})
public abstract class DreamVisionMixin {
    @Inject(
        method = {"render"},
        at = {@At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/hud/InGameHud;render(Lnet/minecraft/client/util/math/MatrixStack;F)V"
        )}
    )
    private void windyorigins$doubleVision(float var1, long var2, boolean var4, CallbackInfo var5) {
        DreamVision.drawDouble(var1);
    }
}
