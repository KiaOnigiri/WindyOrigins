package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.bounty.client.ClientBountyState;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({Entity.class})
public abstract class MarkGlowColorMixin {
    private static final int MARK_RED = 13112350;

    @Inject(
        method = {"getTeamColorValue"},
        at = {@At("HEAD")},
        cancellable = true
    )
    private void windyorigins$markColor(CallbackInfoReturnable<Integer> var1) {
        Entity var2 = (Entity)(Object)this;
        if (ClientBountyState.hasContract() && var2.getWorld().isClient() && var2.getId() == ClientBountyState.getTargetId()) {
            var1.setReturnValue(13112350);
        }
    }
}
