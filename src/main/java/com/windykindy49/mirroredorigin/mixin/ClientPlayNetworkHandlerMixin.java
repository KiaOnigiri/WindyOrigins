package com.windykindy49.mirroredorigin.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.s2c.play.PlaySoundFromEntityS2CPacket;
import com.windykindy49.mirroredorigin.util.CrossPhaseEntity;
import com.windykindy49.mirroredorigin.util.MirrorEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={ClientPlayNetworkHandler.class})
public class ClientPlayNetworkHandlerMixin {
    @Inject(method={"onPlaySoundFromEntity"}, at={@At(value="HEAD")}, cancellable=true)
    private void onEntitySoundPlay(PlaySoundFromEntityS2CPacket packet, CallbackInfo ci) {
        boolean isSourceInMirror;
        boolean isCameraInMirror;
        Entity soundSource;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.cameraEntity == null) {
            return;
        }
        soundSource = client.world.getEntityById(packet.getEntityId());
        if (soundSource == null) {
            return;
        }
        // A phase-neutral source - weather lightning - is heard by both worlds. The server
        // already lets its packets through by publishing no phase during its tick, so
        // without this the client would silence what the server deliberately sent.
        if (soundSource instanceof CrossPhaseEntity && ((CrossPhaseEntity)soundSource).mirroredorigin$isCrossPhase()) {
            return;
        }
        if ((isCameraInMirror = ((MirrorEntity)client.cameraEntity).mirroredorigin$isInMirrorWorld()) != (isSourceInMirror = ((MirrorEntity)soundSource).mirroredorigin$isInMirrorWorld())) {
            ci.cancel();
        }
    }
}

