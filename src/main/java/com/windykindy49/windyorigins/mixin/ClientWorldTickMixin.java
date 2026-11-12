package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.util.CrossPhaseEntity;
import com.windykindy49.windyorigins.util.MirrorEntity;
import com.windykindy49.windyorigins.util.PhaseScope;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ClientWorld.class})
public class ClientWorldTickMixin {
    @Inject(
        method = {"tickEntities"},
        at = {@At("HEAD")},
        require = 0
    )
    private void windyorigins$resetLeakedScopes(CallbackInfo var1) {
        PhaseScope.reset();
    }

    @Inject(
        method = {"tickEntity"},
        at = {@At("HEAD")},
        cancellable = true
    )
    private void onTickEntity(Entity var1, CallbackInfo var2) {
        if (!(var1 instanceof CrossPhaseEntity) || !((CrossPhaseEntity)var1).windyorigins$isCrossPhase()) {
            MinecraftClient var5 = MinecraftClient.getInstance();
            if (var5.cameraEntity != null
                && var1 != var5.cameraEntity
                && ((MirrorEntity)var1).windyorigins$isInMirrorWorld() != ((MirrorEntity)var5.cameraEntity).windyorigins$isInMirrorWorld()) {
                var2.cancel();
            }
        }
    }
}
