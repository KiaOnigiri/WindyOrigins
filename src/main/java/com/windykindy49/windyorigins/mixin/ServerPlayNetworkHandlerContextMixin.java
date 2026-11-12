package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.util.MirrorEntity;
import com.windykindy49.windyorigins.util.PhaseScope;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ServerPlayNetworkHandler.class})
public class ServerPlayNetworkHandlerContextMixin {
    @Shadow
    public ServerPlayerEntity player;

    @Inject(
        method = {"onPlayerInteractEntity", "onPlayerAction", "onPlayerInteractBlock", "onPlayerInteractItem", "onHandSwing", "onPlayerMove", "onPlayerInput", "onClientStatus", "handleCommandExecution"},
        at = {@At("HEAD")}
    )
    private void onActionHead(CallbackInfo var1) {
        PhaseScope.enter(((MirrorEntity)(Object)this.player).windyorigins$isInMirrorWorld());
    }

    @Inject(
        method = {"onPlayerInteractEntity", "onPlayerAction", "onPlayerInteractBlock", "onPlayerInteractItem", "onHandSwing", "onPlayerMove", "onPlayerInput", "onClientStatus", "handleCommandExecution"},
        at = {@At("RETURN")}
    )
    private void onActionTail(CallbackInfo var1) {
        PhaseScope.exit();
    }
}
