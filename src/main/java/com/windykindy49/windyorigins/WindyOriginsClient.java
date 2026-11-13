package com.windykindy49.windyorigins;

import com.windykindy49.windyorigins.bounty.client.BountyClient;
import com.windykindy49.windyorigins.client.DreamClient;
import com.windykindy49.windyorigins.client.DreamSky;
import com.windykindy49.windyorigins.client.DreamVision;
import com.windykindy49.windyorigins.client.FoldedClient;
import com.windykindy49.windyorigins.client.HostClient;
import com.windykindy49.windyorigins.client.LuminianLight;
import com.windykindy49.windyorigins.client.RaceHud;
import com.windykindy49.windyorigins.client.RevenantClient;
import com.windykindy49.windyorigins.util.SidedBridge;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;

public class WindyOriginsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        SidedBridge.setCamera(() -> MinecraftClient.getInstance().cameraEntity);
        BountyClient.init();
        FoldedClient.init();
        RevenantClient.init();
        RaceHud.init();
        HostClient.init();
        com.windykindy49.windyorigins.client.PilotClient.init();
        LuminianLight.init();
        DreamSky.init();
        DreamVision.init();
        DreamClient.init();
    }
}
