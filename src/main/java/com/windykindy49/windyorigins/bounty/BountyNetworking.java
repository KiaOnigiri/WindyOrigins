package com.windykindy49.windyorigins.bounty;

import io.netty.buffer.Unpooled;
import java.util.Collection;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public final class BountyNetworking {
    public static final Identifier HUNTER_SYNC = new Identifier("windyorigins", "bounty_hunter_sync");
    public static final Identifier MARKED_SYNC = new Identifier("windyorigins", "bounty_marked_sync");
    public static final Identifier TARGET_DETAIL = new Identifier("windyorigins", "bounty_target_detail");
    public static final int HOTBAR_SIZE = 9;

    private BountyNetworking() {
    }

    public static void syncHunter(ServerPlayerEntity var0, int var1, int var2, int var3) {
        PacketByteBuf var4 = new PacketByteBuf(Unpooled.buffer());
        var4.writeVarInt(var1);
        var4.writeVarInt(var2);
        var4.writeVarInt(var3);
        ServerPlayNetworking.send(var0, HUNTER_SYNC, var4);
    }

    public static void syncMarked(ServerPlayerEntity var0, boolean var1) {
        PacketByteBuf var2 = new PacketByteBuf(Unpooled.buffer());
        var2.writeBoolean(var1);
        ServerPlayNetworking.send(var0, MARKED_SYNC, var2);
    }

    public static void sendDetail(ServerPlayerEntity var0, ServerPlayerEntity var1) {
        PacketByteBuf var2 = new PacketByteBuf(Unpooled.buffer());
        var2.writeVarInt(var1.getId());
        var2.writeVarInt(var1.getHungerManager().getFoodLevel());
        var2.writeVarInt(var1.getArmor());
        var2.writeVarInt(var1.getInventory().selectedSlot);

        for (int var3 = 0; var3 < 9; var3++) {
            var2.writeItemStack(var1.getInventory().getStack(var3));
        }

        var2.writeItemStack(var1.getOffHandStack());
        Collection var6 = var1.getStatusEffects();
        var2.writeVarInt(var6.size());

        for (StatusEffectInstance var5 : (Iterable<StatusEffectInstance>)(Object)(var6)) {
            var2.writeVarInt(Registries.STATUS_EFFECT.getRawId(var5.getEffectType()));
            var2.writeVarInt(var5.getAmplifier());
            var2.writeVarInt(var5.getDuration());
        }

        ServerPlayNetworking.send(var0, TARGET_DETAIL, var2);
    }
}
