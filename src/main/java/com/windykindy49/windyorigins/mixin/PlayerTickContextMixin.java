package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.util.MirrorEntity;
import com.windykindy49.windyorigins.util.PhaseScope;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A player's own tick runs in that player's phase. Mobs get this from the world's entity tick; players are
 * ticked by their connection instead, so what they did on their own tick (finishing a meal and burping,
 * for one) used to be heard on both sides of the mirror.
 */
@Mixin({ServerPlayerEntity.class})
public class PlayerTickContextMixin {
    @Inject(
        method = {"playerTick"},
        at = {@At("HEAD")}
    )
    private void windyorigins$tickInOwnPhase(CallbackInfo var1) {
        PhaseScope.enter(((MirrorEntity)(Object)this).windyorigins$isInMirrorWorld());
    }

    @Inject(
        method = {"playerTick"},
        at = {@At("RETURN")}
    )
    private void windyorigins$leaveOwnPhase(CallbackInfo var1) {
        PhaseScope.exit();
    }
}
