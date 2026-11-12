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
    /** Server to client: "what do these words name?" Client to server: the ids they can mean. */
    public static final Identifier ASK = new Identifier("windyorigins", "volta_ask");
    public static final Identifier NAMES = new Identifier("windyorigins", "volta_names");
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
    /** An arm that reaches a container, grips, and hauls an item back; the entity field carries the item's raw id. */
    public static final int FX_GRAB = 8;
    /** Two arms from either side seize a creature and trade its place with the Host's; the slot is the tick of the trade. */
    public static final int FX_SWAP = 9;
    /** Taut strings let down from the top of the screen onto a creature; the slot is how many. */
    public static final int FX_STRING = 10;
    /** A burst of speed lines: slot 0 for a leap forward, 1 for being wound back. */
    public static final int FX_RUSH = 11;
    /** A drawn sound effect; the slot picks the word. */
    public static final int FX_WORD = 12;
    /** Tendril slot whose lumps run back towards the Host: something is being drunk. */
    public static final int SLOT_DRAIN = 7;
    /** A thin arm that only points: "it is there". */
    public static final int SLOT_POINT = 8;
    /** A heavy arm from below that seizes a creature and hurls it away, or hauls it in. */
    public static final int SLOT_SHOVE = 9;
    public static final int SLOT_YANK = 10;
    /** A quick jab at a door or a lever. */
    public static final int SLOT_POKE = 11;
    /** A thin arm with a needle on it: goes in, and on the sixth tick pushes the plunger. */
    public static final int SLOT_INJECT = 12;
    /** An arm from the other side of the screen that claps something over the target's eyes. */
    public static final int SLOT_SLAP = 13;
    /** An arm that sets something down in the world and lets go. */
    public static final int SLOT_PLANT = 14;
    /** A cable left trailing from the screen to something she is feeding; each re-send is a pulse down it. */
    public static final int SLOT_CABLE = 15;
    /** Eight hair-thin tracers (20..27) that snap out to whatever a scan has found. */
    public static final int SLOT_TRACE = 20;
    /** A mark pinned over a creature for as long as it lasts; the slot picks the mark. */
    public static final int FX_TAG = 13;
    public static final int TAG_BLINDFOLD = 0;
    public static final int TAG_VIRUS = 1;
    /** A piece drawn on the Host's screen itself; the slot picks which. */
    public static final int FX_STAMP = 14;
    public static final int STAMP_PALM = 0;
    public static final int STAMP_CLOCK = 1;
    public static final int STAMP_MEND = 2;
    public static final int STAMP_EYES = 3;
    public static final int STAMP_CRACK = 4;
    public static final int STAMP_HANDS = 5;
    public static final int WORD_BLOCK = 0;
    public static final int WORD_SLOW = 1;
    public static final int WORD_PING = 2;
    public static final int WORD_PATCHED = 3;
    public static final int WORD_MINE = 4;
    public static final int WORD_SLAM = 5;
    public static final int WORD_PSST = 6;
    public static final int WORD_ONLINE = 7;
    public static final int WORD_LIGHTS_OUT = 8;
    public static final int WORD_SLURP = 9;
    public static final int WORD_INFECTED = 10;
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

    public static void ask(ServerPlayerEntity host, int id, boolean block, String said) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeVarInt(id);
        buf.writeBoolean(block);
        buf.writeString(said.length() > 200 ? said.substring(0, 200) : said, 256);
        ServerPlayNetworking.send(host, ASK, buf);
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

    /** A drawn word over a place in the world, or in the middle of the screen when there is no place. */
    public static void word(ServerPlayerEntity host, int word, Vec3d at, int color) {
        send(host, FX_WORD, word, at == null ? -2 : -1, at == null ? Vec3d.ZERO : at, Vec3d.ZERO, color, 9);
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
