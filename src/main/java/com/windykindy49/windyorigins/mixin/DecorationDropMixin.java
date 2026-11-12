package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.util.MirrorEntity;
import com.windykindy49.windyorigins.util.PhaseScope;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.decoration.painting.PaintingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ItemFrameEntity.class, PaintingEntity.class})
public abstract class DecorationDropMixin {
    @Inject(
        method = {"onBreak"},
        at = {@At("HEAD")}
    )
    private void windyorigins$pinContext(Entity var1, CallbackInfo var2) {
        PhaseScope.enter(((MirrorEntity)(Object)this).windyorigins$isInMirrorWorld());
    }

    @Inject(
        method = {"onBreak"},
        at = {@At("RETURN")}
    )
    private void windyorigins$restoreContext(Entity var1, CallbackInfo var2) {
        PhaseScope.exit();
    }
}
