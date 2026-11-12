package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.util.MirrorEntity;
import com.windykindy49.windyorigins.util.PhaseScope;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ItemEntity.class})
public abstract class ItemEntityMixin {
    @Unique
    private boolean windyorigins$scoped;

    @Inject(
        method = {"onPlayerCollision"},
        at = {@At("HEAD")},
        cancellable = true
    )
    private void windyorigins$beginCollision(PlayerEntity var1, CallbackInfo var2) {
        boolean var3 = ((MirrorEntity)(Object)this).windyorigins$isInMirrorWorld();
        boolean var4 = ((MirrorEntity)var1).windyorigins$isInMirrorWorld();
        if (var3 != var4) {
            var2.cancel();
        } else {
            PhaseScope.enter(var4);
            this.windyorigins$scoped = true;
        }
    }

    @Inject(
        method = {"onPlayerCollision"},
        at = {@At("RETURN")}
    )
    private void windyorigins$endCollision(PlayerEntity var1, CallbackInfo var2) {
        if (this.windyorigins$scoped) {
            this.windyorigins$scoped = false;
            PhaseScope.exit();
        }
    }
}
