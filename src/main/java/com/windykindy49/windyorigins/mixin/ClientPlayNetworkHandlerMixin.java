package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.util.CrossPhaseEntity;
import com.windykindy49.windyorigins.util.MirrorEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.s2c.play.PlaySoundFromEntityS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ClientPlayNetworkHandler.class})
public class ClientPlayNetworkHandlerMixin {
    @Inject(
        method = {"onPlaySoundFromEntity"},
        at = {@At("HEAD")},
        cancellable = true
    )
    private void onEntitySoundPlay(PlaySoundFromEntityS2CPacket var1, CallbackInfo var2) {
        MinecraftClient var6 = MinecraftClient.getInstance();
        if (var6.world != null && var6.cameraEntity != null) {
            Entity var5 = var6.world.getEntityById(var1.getEntityId());
            if (var5 != null) {
                if (!(var5 instanceof CrossPhaseEntity) || !((CrossPhaseEntity)var5).windyorigins$isCrossPhase()) {
                    if (((MirrorEntity)var6.cameraEntity).windyorigins$isInMirrorWorld() != ((MirrorEntity)var5).windyorigins$isInMirrorWorld()) {
                        var2.cancel();
                    }
                }
            }
        }
    }
}
