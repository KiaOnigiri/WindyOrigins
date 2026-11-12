package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.util.MirrorEntity;
import com.windykindy49.windyorigins.util.PhaseScope;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({Entity.class})
public class EntityContextMixin {
    @Inject(
        method = {"tick"},
        at = {@At("HEAD")}
    )
    private void onTickHead(CallbackInfo var1) {
        PhaseScope.enter(((MirrorEntity)(Object)this).windyorigins$isInMirrorWorld());
    }

    @Inject(
        method = {"tick"},
        at = {@At("RETURN")}
    )
    private void onTickTail(CallbackInfo var1) {
        PhaseScope.exit();
    }
}
