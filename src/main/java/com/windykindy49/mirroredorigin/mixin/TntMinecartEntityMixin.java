package com.windykindy49.mirroredorigin.mixin;

import net.minecraft.entity.vehicle.TntMinecartEntity;
import com.windykindy49.mirroredorigin.util.MirrorEntity;
import com.windykindy49.mirroredorigin.util.PhaseScope;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Priming a TNT minecart sends an entity status packet that drives the client-side
 * flashing animation. The context is pinned to the minecart's own phase so the packet
 * reaches players in that phase (the filter also resolves this packet from the entity
 * itself, see ServerPlayNetworkHandlerFilterMixin).
 *
 * The previous context is restored rather than cleared: clearing it left the remainder of
 * the surrounding tick with no phase context, which let later sounds leak into both phases.
 */
@Mixin(value={TntMinecartEntity.class})
public class TntMinecartEntityMixin {
    @Inject(method={"prime"}, at={@At(value="HEAD")})
    private void onPrimeHead(CallbackInfo ci) {
        PhaseScope.enter(((MirrorEntity)(Object)this).mirroredorigin$isInMirrorWorld());
    }

    @Inject(method={"prime"}, at={@At(value="RETURN")})
    private void onPrimeTail(CallbackInfo ci) {
        PhaseScope.exit();
    }
}
