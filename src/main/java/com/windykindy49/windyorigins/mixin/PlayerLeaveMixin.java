package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.common.RaceEvents;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ServerPlayNetworkHandler.class})
public class PlayerLeaveMixin {
    @Shadow
    public ServerPlayerEntity player;

    @Inject(
        method = {"onDisconnected"},
        at = {@At("HEAD")}
    )
    private void windyorigins$leaving(Text var1, CallbackInfo var2) {
        RaceEvents.onLeave(this.player);
    }
}
