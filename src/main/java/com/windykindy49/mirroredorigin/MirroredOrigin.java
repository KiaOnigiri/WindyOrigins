package com.windykindy49.mirroredorigin;

import net.minecraft.util.ActionResult;
import com.windykindy49.mirroredorigin.registry.MirroredApoli;
import com.windykindy49.mirroredorigin.util.MirrorEntity;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MirroredOrigin
implements ModInitializer {
    public static final String MOD_ID = "mirroredorigin";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public void onInitialize() {
        LOGGER.info("Origin The Mirrored has been successfully uploaded!");
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (player != null && entity != null && ((MirrorEntity)player).mirroredorigin$isInMirrorWorld() != ((MirrorEntity)entity).mirroredorigin$isInMirrorWorld()) {
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });
        MirroredApoli.register();
    }
}

