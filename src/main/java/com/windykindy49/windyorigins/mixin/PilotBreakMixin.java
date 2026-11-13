package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.client.PilotClient;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * While Volta has the body and is breaking a block, the attack button counts as held: the game itself then
 * breaks what is under the crosshair, at the speed of the tool in the hand, exactly as for a finger on the mouse.
 */
@Mixin(MinecraftClient.class)
public abstract class PilotBreakMixin {
    @ModifyVariable(method = "handleBlockBreaking", at = @At("HEAD"), argsOnly = true)
    private boolean windyorigins$pilotHolds(boolean breaking) {
        return breaking || PilotClient.digging();
    }
}
