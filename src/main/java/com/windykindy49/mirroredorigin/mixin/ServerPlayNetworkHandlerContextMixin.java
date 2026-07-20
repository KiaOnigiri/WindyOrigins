package com.windykindy49.mirroredorigin.mixin;

import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import com.windykindy49.mirroredorigin.util.MirrorEntity;
import com.windykindy49.mirroredorigin.util.PhaseScope;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Publishes the acting player's phase for the duration of a packet handler, so anything the
 * handler spawns or plays is attributed to the right phase.
 *
 * This used to close the scope at TAIL, which only covers the LAST return of a method. These
 * handlers are full of early returns - player already dead, teleport in flight, movement
 * rejected as invalid, wrong hand, block out of reach - and every one of them left the phase
 * hanging for the rest of the server thread's work. Entities spawned later in the same tick
 * from completely unrelated sources then inherited that stale phase. RETURN covers every
 * exit instead.
 *
 * PhaseScope rather than MirrorContext.set/clear for the same reason as everywhere else:
 * clearing discards an enclosing scope instead of restoring it.
 *
 * handleCommandExecution is in the list so that a command carries the phase of the player who
 * typed it: /summon puts the mob in that player's phase, and a command that detonates
 * something confines the blast to it. Commands from the console, a command block or a
 * datapack function pass through none of this, have no phase attached, and therefore land in
 * the overworld phase.
 */
@Mixin(value={ServerPlayNetworkHandler.class})
public class ServerPlayNetworkHandlerContextMixin {
    @Shadow
    public ServerPlayerEntity player;

    @Inject(method={"onPlayerInteractEntity", "onPlayerAction", "onPlayerInteractBlock", "onPlayerInteractItem", "onHandSwing", "onPlayerMove", "onPlayerInput", "onClientStatus", "handleCommandExecution"}, at={@At(value="HEAD")})
    private void onActionHead(CallbackInfo ci) {
        PhaseScope.enter(((MirrorEntity)(Object)this.player).mirroredorigin$isInMirrorWorld());
    }

    @Inject(method={"onPlayerInteractEntity", "onPlayerAction", "onPlayerInteractBlock", "onPlayerInteractItem", "onHandSwing", "onPlayerMove", "onPlayerInput", "onClientStatus", "handleCommandExecution"}, at={@At(value="RETURN")})
    private void onActionTail(CallbackInfo ci) {
        PhaseScope.exit();
    }
}
