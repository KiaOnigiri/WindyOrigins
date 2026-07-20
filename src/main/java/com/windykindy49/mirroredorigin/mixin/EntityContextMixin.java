package com.windykindy49.mirroredorigin.mixin;

import net.minecraft.entity.Entity;
import com.windykindy49.mirroredorigin.util.MirrorEntity;
import com.windykindy49.mirroredorigin.util.PhaseScope;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Client-side counterpart of ServerWorldTickMixin (ServerWorld#tickEntity does not exist on
 * the client). Uses PhaseScope rather than set/clear: Entity#tick is reached through
 * super.tick() from LivingEntity/PlayerEntity, so clearing on the way out used to wipe the
 * phase for the rest of the enclosing tick.
 */
@Mixin(value = {Entity.class})
public class EntityContextMixin {
    @Inject(method = {"tick"}, at = {@At(value = "HEAD")})
    private void onTickHead(CallbackInfo ci) {
        PhaseScope.enter(((MirrorEntity) (Object) this).mirroredorigin$isInMirrorWorld());
    }

    @Inject(method = {"tick"}, at = {@At(value = "RETURN")})
    private void onTickTail(CallbackInfo ci) {
        PhaseScope.exit();
    }
}
