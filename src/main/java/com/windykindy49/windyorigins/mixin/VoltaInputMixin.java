package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.client.HostClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** One of Volta's pranks: for a few seconds forward is back and left is right. */
@Mixin({KeyboardInput.class})
public abstract class VoltaInputMixin {
    @Inject(
        method = {"tick"},
        at = {@At("TAIL")}
    )
    private void windyorigins$invert(boolean var1, float var2, CallbackInfo var3) {
        com.windykindy49.windyorigins.client.PilotClient.steer((Input)(Object)this);
        if (HostClient.inverted() && !com.windykindy49.windyorigins.client.PilotClient.active()) {
            Input var4 = (Input)(Object)this;
            var4.movementForward = -var4.movementForward;
            var4.movementSideways = -var4.movementSideways;
        }
    }
}
