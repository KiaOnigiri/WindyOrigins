package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.bounty.client.ClientBountyState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({MinecraftClient.class})
public class MarkGlowMixin {
    @Inject(
        method = {"hasOutline"},
        at = {@At("HEAD")},
        cancellable = true
    )
    private void windyorigins$outlineOwnMark(Entity var1, CallbackInfoReturnable<Boolean> var2) {
        if (ClientBountyState.hasContract() && var1.getId() == ClientBountyState.getTargetId()) {
            var2.setReturnValue(true);
        }
    }
}
