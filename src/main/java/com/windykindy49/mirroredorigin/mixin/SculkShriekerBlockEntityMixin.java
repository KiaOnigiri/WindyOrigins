package com.windykindy49.mirroredorigin.mixin;

import net.minecraft.block.entity.SculkShriekerBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import com.windykindy49.mirroredorigin.util.MirrorEntity;
import com.windykindy49.mirroredorigin.util.PhaseScope;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={SculkShriekerBlockEntity.class})
public class SculkShriekerBlockEntityMixin {
    @Inject(method={"shriek(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/Entity;)V"}, at={@At(value="HEAD")})
    private void onShriekHead(ServerWorld world, @Nullable Entity entity, CallbackInfo ci) {
        PhaseScope.enterOrKeep(entity != null
            ? Boolean.valueOf(((MirrorEntity)entity).mirroredorigin$isInMirrorWorld())
            : null);
    }

    @Inject(method={"shriek(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/Entity;)V"}, at={@At(value="RETURN")})
    private void onShriekTail(ServerWorld world, @Nullable Entity entity, CallbackInfo ci) {
        PhaseScope.exit();
    }
}

