package com.windykindy49.windyorigins.common;

import io.github.ladysnake.pal.AbilitySource;
import io.github.ladysnake.pal.Pal;
import io.github.ladysnake.pal.VanillaAbilities;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public final class Flight {
    public static final AbilitySource BAT = Pal.getAbilitySource(new Identifier("windyorigins", "bat_form"));
    public static final AbilitySource GHOST = Pal.getAbilitySource(new Identifier("windyorigins", "vengeance"));

    private Flight() {
    }

    public static void grant(ServerPlayerEntity var0, AbilitySource var1, boolean var2) {
        var1.grantTo(var0, VanillaAbilities.ALLOW_FLYING);
        if (var2) {
            var1.grantTo(var0, VanillaAbilities.FLYING);
        }
    }

    public static void revoke(ServerPlayerEntity var0, AbilitySource var1) {
        var1.revokeFrom(var0, VanillaAbilities.FLYING);
        var1.revokeFrom(var0, VanillaAbilities.ALLOW_FLYING);
    }

    public static boolean grants(ServerPlayerEntity var0, AbilitySource var1) {
        return var1.grants(var0, VanillaAbilities.ALLOW_FLYING);
    }
}
