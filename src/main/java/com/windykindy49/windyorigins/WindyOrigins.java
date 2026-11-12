package com.windykindy49.windyorigins;

import com.windykindy49.windyorigins.bounty.BountyEvents;
import com.windykindy49.windyorigins.common.RaceEvents;
import com.windykindy49.windyorigins.registry.BountyPowers;
import com.windykindy49.windyorigins.registry.MirrorPowers;
import com.windykindy49.windyorigins.registry.RacePowers;
import com.windykindy49.windyorigins.util.MirrorEntity;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.util.ActionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WindyOrigins implements ModInitializer {
    public static final String MOD_ID = "windyorigins";
    public static final Logger LOGGER = LoggerFactory.getLogger("windyorigins");

    @Override
    public void onInitialize() {
        LOGGER.info("Windy Origins loaded.");
        UseEntityCallback.EVENT
            .register(
                (var0, var1, var2, var3, var4) -> var0 != null
                            && var3 != null
                            && ((MirrorEntity)var0).windyorigins$isInMirrorWorld() != ((MirrorEntity)var3).windyorigins$isInMirrorWorld()
                        ? ActionResult.FAIL
                        : ActionResult.PASS
            );
        MirrorPowers.register();
        BountyPowers.register();
        BountyEvents.register();
        RaceEvents.register();
        RacePowers.register();
    }
}
