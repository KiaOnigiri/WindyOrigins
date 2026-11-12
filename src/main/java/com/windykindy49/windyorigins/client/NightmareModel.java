package com.windykindy49.windyorigins.client;

import com.windykindy49.windyorigins.dream.NightmareEntity;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.util.math.MathHelper;

public class NightmareModel extends BipedEntityModel<NightmareEntity> {
    private static final int TENTACLES = 4;
    private final ModelPart[] roots = new ModelPart[4];
    private final ModelPart[] tips = new ModelPart[4];

    public NightmareModel(ModelPart var1) {
        super(var1);
        ModelPart var2 = var1.getChild("body");

        for (int var3 = 0; var3 < 4; var3++) {
            this.roots[var3] = var2.getChild("tentacle_" + var3);
            this.tips[var3] = this.roots[var3].getChild("tip");
        }
    }

    public static TexturedModelData getTexturedModelData() {
        ModelData var0 = BipedEntityModel.getModelData(Dilation.NONE, 0.0F);
        ModelPartData var1 = var0.getRoot().getChild("body");
        float[][] var2 = new float[][]{{-3.0F, 3.0F, 2.0F}, {3.0F, 4.0F, 2.0F}, {-2.0F, 8.0F, 2.0F}, {2.5F, 9.0F, 2.0F}};

        for (int var3 = 0; var3 < 4; var3++) {
            ModelPartData var4 = var1.addChild(
                "tentacle_" + var3,
                ModelPartBuilder.create().uv(0, 32).cuboid(-1.0F, 0.0F, -1.0F, 2.0F, 7.0F, 2.0F),
                ModelTransform.pivot(var2[var3][0], var2[var3][1], var2[var3][2])
            );
            var4.addChild("tip", ModelPartBuilder.create().uv(8, 32).cuboid(-0.5F, 0.0F, -0.5F, 1.0F, 7.0F, 1.0F), ModelTransform.pivot(0.0F, 6.5F, 0.0F));
        }

        return TexturedModelData.of(var0, 64, 64);
    }

    public void setAngles(NightmareEntity var1, float var2, float var3, float var4, float var5, float var6) {
        super.setAngles(var1, var2, var3, var4, var5, var6);
        float var7 = var4;
        int var8 = var1.getId() * 7;
        this.head.xScale = this.head.zScale = 1.0F + 0.25F * MathHelper.sin(var4 * 0.05F + (float)var8);
        this.head.yScale = 1.0F - 0.2F * MathHelper.sin(var4 * 0.07F + (float)(var8 * 2));
        float var9 = var1.getKind() == 1 ? 1.6F : 1.0F;
        this.rightArm.yScale = var9 + 0.5F * Math.max(0.0F, MathHelper.sin(var4 * 0.04F + (float)(var8 * 3)));
        this.leftArm.yScale = var9 + 0.4F * Math.max(0.0F, MathHelper.sin(var4 * 0.055F + (float)(var8 * 5)));
        this.rightLeg.yScale = 1.0F + 0.3F * Math.max(0.0F, MathHelper.sin(var4 * 0.03F + (float)(var8 * 11)));
        this.leftLeg.yScale = 1.0F + 0.2F * Math.max(0.0F, MathHelper.sin(var4 * 0.045F + (float)(var8 * 13)));
        if (var1.getKind() == 2 && var1.getTell() > 0) {
            this.rightArm.pitch = -2.6F;
            this.leftArm.pitch = -2.6F;
        }

        if (var1.getKind() == 1 && var1.getTell() > 0) {
            this.rightArm.pitch = -1.5F;
        }

        for (int var10 = 0; var10 < 4; var10++) {
            float var11 = var7 * 0.03F + (float)var10 * 1.7F + (float)var8;
            float var12 = MathHelper.clamp(MathHelper.sin(var11) * 1.4F, 0.0F, 1.0F);
            this.roots[var10].visible = var12 > 0.05F;
            this.roots[var10].yScale = var12;
            this.roots[var10].pitch = 1.2F + 0.6F * MathHelper.sin(var7 * 0.21F + (float)var10);
            this.roots[var10].roll = (float)(var10 % 2 == 0 ? -1 : 1) * (0.6F + 0.5F * MathHelper.sin(var7 * 0.17F + (float)(var10 * 2)));
            this.tips[var10].pitch = 0.7F * MathHelper.sin(var7 * 0.33F + (float)(var10 * 3));
            this.tips[var10].roll = 0.5F * MathHelper.cos(var7 * 0.29F + (float)var10);
        }
    }
}
