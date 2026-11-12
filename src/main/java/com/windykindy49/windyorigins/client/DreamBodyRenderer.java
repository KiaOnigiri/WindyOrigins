package com.windykindy49.windyorigins.client;

import com.windykindy49.windyorigins.dream.DreamBodyEntity;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory.Context;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

public class DreamBodyRenderer extends EntityRenderer<DreamBodyEntity> {
    private final PlayerEntityModel<PlayerEntity> model;

    public DreamBodyRenderer(Context var1) {
        super(var1);
        this.model = new PlayerEntityModel<>(var1.getPart(EntityModelLayers.PLAYER), false);
    }

    public Identifier getTexture(DreamBodyEntity var1) {
        UUID var2 = var1.getOwner();
        if (var2 == null) {
            return DefaultSkinHelper.getTexture();
        } else {
            PlayerListEntry var3 = MinecraftClient.getInstance().getNetworkHandler() == null
                ? null
                : MinecraftClient.getInstance().getNetworkHandler().getPlayerListEntry(var2);
            return var3 != null ? var3.getSkinTexture() : DefaultSkinHelper.getTexture(var2);
        }
    }

    public void render(DreamBodyEntity var1, float var2, float var3, MatrixStack var4, VertexConsumerProvider var5, int var6) {
        var4.push();
        var4.translate(0.0, 0.12, 0.0);
        var4.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F - var1.getYaw()));
        var4.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90.0F));
        var4.scale(-0.9375F, -0.9375F, 0.9375F);
        var4.translate(0.0, -1.5, 0.0);
        this.model.child = false;
        this.model
            .render(var4, var5.getBuffer(RenderLayer.getEntityCutoutNoCull(this.getTexture(var1))), var6, OverlayTexture.DEFAULT_UV, 1.0F, 1.0F, 1.0F, 1.0F);
        var4.pop();
        super.render(var1, var2, var3, var4, var5, var6);
    }
}
