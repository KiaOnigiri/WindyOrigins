package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.util.MirrorEntity;
import com.windykindy49.windyorigins.util.PhaseScope;
import net.minecraft.block.entity.SculkShriekerBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({SculkShriekerBlockEntity.class})
public class SculkShriekerBlockEntityMixin {
    @Inject(
        method = {"shriek(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/Entity;)V"},
        at = {@At("HEAD")}
    )
    private void onShriekHead(ServerWorld var1, @Nullable Entity var2, CallbackInfo var3) {
        PhaseScope.enterOrKeep(var2 != null ? ((MirrorEntity)var2).windyorigins$isInMirrorWorld() : null);
    }

    @Inject(
        method = {"shriek(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/Entity;)V"},
        at = {@At("RETURN")}
    )
    private void onShriekTail(ServerWorld var1, @Nullable Entity var2, CallbackInfo var3) {
        PhaseScope.exit();
    }
}
