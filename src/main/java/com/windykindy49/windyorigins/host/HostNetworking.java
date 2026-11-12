package com.windykindy49.windyorigins.host;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public final class HostNetworking {
    public static final Identifier COMMAND = new Identifier("windyorigins", "volta_command");
    public static final Identifier SAY = new Identifier("windyorigins", "volta_say");
    public static final Identifier PRANK = new Identifier("windyorigins", "volta_prank");
    public static final Identifier MARKS = new Identifier("windyorigins", "volta_marks");
    public static final Identifier FOUND = new Identifier("windyorigins", "volta_found");
    public static final Identifier SCANNED = new Identifier("windyorigins", "volta_scanned");
    public static final Identifier CAST = new Identifier("windyorigins", "volta_cast");
    public static final int SMILE = 0;
    public static final int SLY = 1;
    public static final int BORED = 2;
    public static final int SCARED = 3;
    public static final int ANGRY = 4;
    public static final int TONGUE = 5;
    public static final int WINK = 6;
    public static final int GLITCH = 7;

    public static final Identifier FX = new Identifier("windyorigins", "volta_fx");
    public static final int FX_BOLT = 0;
    public static final int FX_TENDRIL = 1;
    public static final int FX_PULSE = 2;
    public static final int FX_SELF = 3;
    public static final int FX_DECOY = 4;
    public static final int FX_STOP = 5;
    public static final int FX_ARC = 6;
    public static final int FX_NODE = 7;
    /** Tendril slot whose lumps run back towards the Host: something is being drunk. */
    public static final int SLOT_DRAIN = 7;
    public static final int CYAN = 0x45F0E6;
    public static final int PINK = 0xF03CC8;
    public static final int GREEN = 0x5CFF8A;
    public static final int GOLD = 0xFFD24A;
    public static final int BLUE = 0x5AA8FF;

    private HostNetworking() {
    }

    public static void say(ServerPlayerEntity var0, Text var1, int var2) {
        PacketByteBuf var3 = new PacketByteBuf(Unpooled.buffer());
        var3.writeText(var1);
        var3.writeVarInt(var2);
        ServerPlayNetworking.send(var0, SAY, var3);
    }

    public static void prank(ServerPlayerEntity var0, HostPrank var1, Text var2, BlockPos var3) {
        PacketByteBuf var4 = new PacketByteBuf(Unpooled.buffer());
        var4.writeVarInt(var1.ordinal());
        var4.writeText(var2);
        var4.writeBoolean(var3 != null);
        if (var3 != null) {
            var4.writeBlockPos(var3);
        }

        ServerPlayNetworking.send(var0, PRANK, var4);
    }

    public static void marks(ServerPlayerEntity var0, int[] var1, int var2) {
        PacketByteBuf var3 = new PacketByteBuf(Unpooled.buffer());
        var3.writeIntArray(var1);
        var3.writeVarInt(var2);
        ServerPlayNetworking.send(var0, MARKS, var3);
    }

    public static void found(ServerPlayerEntity var0, Vec3d var1, int var2, Text var3) {
        PacketByteBuf var4 = new PacketByteBuf(Unpooled.buffer());
        var4.writeDouble(var1.x);
        var4.writeDouble(var1.y);
        var4.writeDouble(var1.z);
        var4.writeVarInt(var2);
        var4.writeText(var3);
        ServerPlayNetworking.send(var0, FOUND, var4);
    }

    public static void scanned(ServerPlayerEntity var0) {
        ServerPlayNetworking.send(var0, SCANNED, new PacketByteBuf(Unpooled.buffer()));
    }

    /**
     * One of Volta's drawn effects. They exist only on the Host's own screen: a mass of energy
     * that leaves it like a shot, reaches out and works on a block, pulses, glows.
     */
    public static void fx(ServerPlayerEntity var0, int var1, int var2, int var3, Vec3d var4, int var5, int var6) {
        send(var0, var1, var2, var3, var4, var4, var5, var6);
    }

    /** A jump of energy between two points in the world. */
    public static void arc(ServerPlayerEntity var0, Vec3d var1, Vec3d var2, int var3, int var4) {
        send(var0, FX_ARC, 0, -1, var2, var1, var3, var4);
    }

    private static void send(ServerPlayerEntity var0, int var1, int var2, int var3, Vec3d var4, Vec3d var5, int var6, int var7) {
        PacketByteBuf var8 = new PacketByteBuf(Unpooled.buffer());
        var8.writeByte(var1);
        var8.writeByte(var2);
        var8.writeInt(var0.getId());
        var8.writeInt(var3);
        var8.writeDouble(var4.x);
        var8.writeDouble(var4.y);
        var8.writeDouble(var4.z);
        var8.writeDouble(var5.x);
        var8.writeDouble(var5.y);
        var8.writeDouble(var5.z);
        var8.writeInt(var6);
        var8.writeVarInt(var7);
        ServerPlayNetworking.send(var0, FX, var8);
    }

    public static void cast(ServerPlayerEntity var0, int var1, int var2) {
        PacketByteBuf var3 = new PacketByteBuf(Unpooled.buffer());
        var3.writeVarInt(var1);
        var3.writeVarInt(var2);
        ServerPlayNetworking.send(var0, CAST, var3);
    }
}
