package com.windykindy49.windyorigins.revenant;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

public final class RevenantNetworking {
    public static final Identifier ECHO_ADD = new Identifier("windyorigins", "echo_add");
    public static final Identifier ECHO_REMOVE = new Identifier("windyorigins", "echo_remove");
    public static final Identifier HAUNTED = new Identifier("windyorigins", "haunted");
    public static final Identifier RELEASE = new Identifier("windyorigins", "revenant_release");
    public static final Identifier EJECT = new Identifier("windyorigins", "revenant_eject");
    public static final Identifier MARKS = new Identifier("windyorigins", "revenant_marks");

    private RevenantNetworking() {
    }

    public static void echoAdd(ServerPlayerEntity var0, int var1, Vec3d var2) {
        PacketByteBuf var3 = new PacketByteBuf(Unpooled.buffer());
        var3.writeVarInt(var1);
        var3.writeDouble(var2.x);
        var3.writeDouble(var2.y);
        var3.writeDouble(var2.z);
        ServerPlayNetworking.send(var0, ECHO_ADD, var3);
    }

    public static void echoRemove(ServerPlayerEntity var0, int var1) {
        PacketByteBuf var2 = new PacketByteBuf(Unpooled.buffer());
        var2.writeVarInt(var1);
        ServerPlayNetworking.send(var0, ECHO_REMOVE, var2);
    }

    public static void haunted(ServerPlayerEntity var0, boolean var1) {
        PacketByteBuf var2 = new PacketByteBuf(Unpooled.buffer());
        var2.writeBoolean(var1);
        ServerPlayNetworking.send(var0, HAUNTED, var2);
    }

    public static void eject(ServerPlayerEntity var0, float var1, float var2) {
        PacketByteBuf var3 = new PacketByteBuf(Unpooled.buffer());
        var3.writeFloat(var1);
        var3.writeFloat(var2);
        ServerPlayNetworking.send(var0, EJECT, var3);
    }

    public static void marks(ServerPlayerEntity var0, int[] var1) {
        PacketByteBuf var2 = new PacketByteBuf(Unpooled.buffer());
        var2.writeIntArray(var1);
        ServerPlayNetworking.send(var0, MARKS, var2);
    }
}
