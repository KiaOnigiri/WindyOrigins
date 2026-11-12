package com.windykindy49.windyorigins.client;

import com.windykindy49.windyorigins.dream.NightmareEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory.Context;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

public class NightmareRenderer extends MobEntityRenderer<NightmareEntity, NightmareModel> {
    public static final EntityModelLayer LAYER = new EntityModelLayer(new Identifier("windyorigins", "nightmare"), "main");
    private static final Identifier PLAIN = new Identifier("windyorigins", "textures/entity/nightmare.png");
    private static final Identifier FACE = new Identifier("windyorigins", "textures/entity/nightmare_face.png");

    public NightmareRenderer(Context var1) {
        super(var1, new NightmareModel(var1.getPart(LAYER)), 0.0F);
    }

    public Identifier getTexture(NightmareEntity var1) {
        boolean var2 = var1.getKind() == 2 && var1.getTell() > 0;
        boolean var3 = (var1.age + var1.getId() * 37) % 140 < 5;
        return !var2 && !var3 ? PLAIN : FACE;
    }

    protected void scale(NightmareEntity var1, MatrixStack var2, float var3) {
        float var4 = var1.getKind() == 2 ? 1.5F : 1.0F;
        var2.scale(var4, var4, var4);
    }

    public void render(NightmareEntity var1, float var2, float var3, MatrixStack var4, VertexConsumerProvider var5, int var6) {
        int var7 = var1.age + var1.getId();
        var4.push();
        if (var7 / 3 % 13 == 0 || var1.getTell() > 0 && var1.getKind() == 0 && var7 % 2 == 0) {
            var4.translate((double)(var7 * 31 % 7 - 3) * 0.04, 0.0, (double)(var7 * 17 % 7 - 3) * 0.04);
        }

        super.render(var1, var2, var3, var4, var5, var6);
        var4.pop();
        if (var7 / 5 % 17 == 0) {
            var4.push();
            var4.translate(0.35, 0.0, -0.2);
            super.render(var1, var2 + 25.0F, var3, var4, var5, var6);
            var4.pop();
        }
    }
}
