package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.client.ClientOutlines;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({MinecraftClient.class})
public abstract class WindyOutlineMixin {
    @Inject(
        method = {"hasOutline"},
        at = {@At("HEAD")},
        cancellable = true
    )
    private void windyorigins$outline(Entity var1, CallbackInfoReturnable<Boolean> var2) {
        if (ClientOutlines.should(var1)) {
            var2.setReturnValue(true);
        }
    }
}
