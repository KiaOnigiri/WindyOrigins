package com.windykindy49.windyorigins.common;

import com.windykindy49.windyorigins.dream.DreamEntities;
import com.windykindy49.windyorigins.dream.DreamManager;
import com.windykindy49.windyorigins.folded.FoldedManager;
import com.windykindy49.windyorigins.frost.FrostManager;
import com.windykindy49.windyorigins.host.HostCommand;
import com.windykindy49.windyorigins.host.HostManager;
import com.windykindy49.windyorigins.host.HostNetworking;
import com.windykindy49.windyorigins.host.HostRenames;
import com.windykindy49.windyorigins.luminian.LuminianManager;
import com.windykindy49.windyorigins.revenant.RevenantManager;
import com.windykindy49.windyorigins.sanguine.SanguineManager;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.util.ActionResult;
import net.minecraft.util.TypedActionResult;

public final class RaceEvents {
    private RaceEvents() {
    }

    public static void register() {
        WindySounds.init();
        WindyEffects.init();
        LuminianManager.init();
        FoldedManager.init();
        HostManager.init();
        DreamEntities.init();
        ServerTickEvents.END_SERVER_TICK.register(var0 -> {
            TempBlocks.tick(var0);
            FrostManager.tick(var0);
            LuminianManager.tick(var0);
            FoldedManager.tick(var0);
            SanguineManager.tick(var0);
            RevenantManager.tick(var0);
            HostManager.tick(var0);
            DreamManager.tick(var0);
            com.windykindy49.windyorigins.mirror.MirrorManager.tick(var0);
            if (var0.getTicks() % 20 == 0) {
                HostRenames.tick(var0);
            }
        });
        ServerEntityEvents.ENTITY_LOAD.register(HostRenames::onLoad);
        ServerLivingEntityEvents.AFTER_DEATH.register((var0, var1) -> RevenantManager.onDeath(var0));
        ServerLivingEntityEvents.ALLOW_DEATH.register((var0, var1, var2) -> DreamManager.allowDeath(var0, var1));
        ServerLivingEntityEvents.ALLOW_DEATH.register((var0, var1, var2) -> RevenantManager.allowDeath(var0, var1));
        ServerPlayConnectionEvents.JOIN.register((var0, var1, var2) -> {
            RevenantManager.onJoin(var0.player);
            DreamManager.onJoin(var0.player);
        });
        PlayerBlockBreakEvents.BEFORE.register((var0, var1, var2, var3, var4) -> !var1.hasStatusEffect(WindyEffects.VENGEANCE));
        AttackBlockCallback.EVENT
            .register((var0, var1, var2, var3, var4) -> var0.hasStatusEffect(WindyEffects.VENGEANCE) ? ActionResult.FAIL : ActionResult.PASS);
        UseBlockCallback.EVENT.register((var0, var1, var2, var3) -> var0.hasStatusEffect(WindyEffects.VENGEANCE) ? ActionResult.FAIL : ActionResult.PASS);
        UseBlockCallback.EVENT.register((var0, var1, var2, var3) -> {
            com.windykindy49.windyorigins.mirror.MirrorManager.onUseBlock(var0, var1, var2, var3);
            return ActionResult.PASS;
        });
        UseItemCallback.EVENT
            .register(
                (var0, var1, var2) -> var0.hasStatusEffect(WindyEffects.VENGEANCE)
                        ? TypedActionResult.fail(var0.getStackInHand(var2))
                        : TypedActionResult.pass(var0.getStackInHand(var2))
            );
        ServerPlayNetworking.registerGlobalReceiver(HostNetworking.COMMAND, (var0, var1, var2, var3, var4) -> {
            int var5 = var3.readVarInt();
            var0.execute(() -> {
                HostCommand var2x = HostCommand.byId(var5);
                if (var2x != null) {
                    HostManager.command(var1, var2x, null, -1);
                }
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(
            com.windykindy49.windyorigins.revenant.RevenantNetworking.RELEASE, (var0, var1, var2, var3, var4) -> var0.execute(() -> RevenantManager.release(var1))
        );
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((var0, var1, var2) -> !HostManager.onChat(var1, var0.getContent().getString()));
        ServerPlayConnectionEvents.DISCONNECT.register((var0, var1) -> {
            FrostManager.forget(var0.player);
            LuminianManager.remove(var0.player);
            FoldedManager.forget(var0.player);
            HostManager.forget(var0.player);
            RevenantManager.onLeave(var0.player);
            com.windykindy49.windyorigins.revenant.WispManager.forget(var0.player);
            DreamManager.onLeave(var0.player);
        });
        // While the worlds are still up: wake the dreamers, release the possessed, take back what was only lent.
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING.register(var0 -> {
            DreamManager.stopping(var0);
            RevenantManager.stopping(var0);
            HostManager.stopping();
            FrostManager.reset();
            SanguineManager.reset();
            LuminianManager.reset();
        });
        // In single player the game outlives the world: nothing remembered about one may reach the next.
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(var0 -> {
            DreamManager.reset();
            RevenantManager.reset();
            FrostManager.reset();
            FoldedManager.reset();
            HostManager.reset();
            SanguineManager.reset();
            LuminianManager.reset();
            com.windykindy49.windyorigins.bounty.BountyManager.reset();
        });
    }
}
