package com.windykindy49.windyorigins.bounty;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public final class BountySounds {
    public static final SoundEvent MARK = register("bounty.mark");
    public static final SoundEvent HUNTED = register("bounty.hunted");
    public static final SoundEvent COMPLETE = register("bounty.complete");
    public static final SoundEvent BROKEN = register("bounty.broken");
    public static final SoundEvent READY = register("bounty.ready");
    public static final SoundEvent DODGE = register("bounty.dodge");
    public static final SoundEvent DENY = register("bounty.deny");

    private BountySounds() {
    }

    private static SoundEvent register(String var0) {
        Identifier var1 = new Identifier("windyorigins", var0);
        return Registry.register(Registries.SOUND_EVENT, var1, SoundEvent.of(var1));
    }

    public static void init() {
    }

    public static void playPrivate(ServerPlayerEntity var0, SoundEvent var1, float var2, float var3) {
        var0.playSound(var1, SoundCategory.PLAYERS, var2, var3);
    }
}
