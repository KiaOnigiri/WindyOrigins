package com.windykindy49.windyorigins.common;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public final class WindySounds {
    public static final SoundEvent FROST_COFFIN = reg("frost.coffin");
    public static final SoundEvent FROST_COFFIN_BREAK = reg("frost.coffin_break");
    public static final SoundEvent FROST_WALL = reg("frost.wall");
    public static final SoundEvent FROST_FREEZE = reg("frost.freeze");
    public static final SoundEvent LUMI_REVIVE = reg("luminian.revive");
    public static final SoundEvent LUMI_FADE = reg("luminian.fade");
    public static final SoundEvent FOLD_SHOT = reg("folded.shot");
    public static final SoundEvent FOLD_CHARGE = reg("folded.charge");
    public static final SoundEvent FOLD_RIFLE = reg("folded.rifle");
    public static final SoundEvent FOLD_STEP = reg("folded.step");
    public static final SoundEvent FOLD_POINT = reg("folded.point");
    public static final SoundEvent FOLD_HOVER = reg("folded.hover");
    public static final SoundEvent BLOOD_BITE = reg("sanguine.bite");
    public static final SoundEvent BLOOD_BATS = reg("sanguine.bats");
    public static final SoundEvent BLOOD_SIZZLE = reg("sanguine.sizzle");
    public static final SoundEvent BLOOD_REST = reg("sanguine.rest");
    public static final SoundEvent REV_ECHO = reg("revenant.echo");
    public static final SoundEvent REV_POSSESS = reg("revenant.possess");
    public static final SoundEvent REV_RELEASE = reg("revenant.release");
    public static final SoundEvent REV_WHISPER = reg("revenant.whisper");
    public static final SoundEvent REV_WISPS = reg("revenant.wisps");
    public static final SoundEvent REV_VENGEANCE = reg("revenant.vengeance");
    public static final SoundEvent REV_FADE = reg("revenant.fade");
    public static final SoundEvent DREAM_LULL = reg("dream.lull");
    public static final SoundEvent DREAM_ENTER = reg("dream.enter");
    public static final SoundEvent DREAM_WAKE = reg("dream.wake");
    public static final SoundEvent DENY = reg("common.deny");

    private WindySounds() {
    }

    private static SoundEvent reg(String var0) {
        Identifier var1 = new Identifier("windyorigins", var0);
        return Registry.register(Registries.SOUND_EVENT, var1, SoundEvent.of(var1));
    }

    public static void init() {
    }
}
