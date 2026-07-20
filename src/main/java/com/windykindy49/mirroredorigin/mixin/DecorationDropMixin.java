package com.windykindy49.mirroredorigin.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.decoration.painting.PaintingEntity;
import com.windykindy49.mirroredorigin.util.MirrorContext;
import com.windykindy49.mirroredorigin.util.MirrorEntity;
import com.windykindy49.mirroredorigin.util.PhaseScope;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Item frames / paintings used to drop in the wrong phase when they were knocked down by
 * a player standing in the other phase (blocks are shared between phases). Pin
 * MirrorContext to the decoration's own phase for the whole onBreak call so both
 * ServerWorldSpawnMixin and EntityDropMixin resolve the drop to the decoration's phase.
 *
 * Targets the concrete subclasses, NOT AbstractDecorationEntity: onBreak(Entity) is
 * declared abstract there, so it carries no instructions and the injector has nothing
 * to attach to (fails with "Cannot read field index because insnNode is null").
 * GlowItemFrameEntity inherits ItemFrameEntity's implementation, so it is covered too.
 *
 * Restore uses RETURN rather than TAIL on purpose: ItemFrameEntity.onBreak returns early
 * in the creative-mode branch, and TAIL would only cover the final return, leaving
 * MirrorContext pinned to the frame's phase for everything ticked afterwards.
 */
@Mixin(value={ItemFrameEntity.class, PaintingEntity.class})
public abstract class DecorationDropMixin {
    @Inject(method={"onBreak"}, at={@At(value="HEAD")})
    private void mirroredorigin$pinContext(Entity breaker, CallbackInfo ci) {
        PhaseScope.enter(((MirrorEntity)(Object)this).mirroredorigin$isInMirrorWorld());
    }

    @Inject(method={"onBreak"}, at={@At(value="RETURN")})
    private void mirroredorigin$restoreContext(Entity breaker, CallbackInfo ci) {
        PhaseScope.exit();
    }
}
