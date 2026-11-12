package com.windykindy49.windyorigins.client;

import com.windykindy49.windyorigins.host.HostCommand;
import com.windykindy49.windyorigins.host.HostNetworking;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.InputUtil.Key;
import net.minecraft.client.util.InputUtil.Type;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

public class VoltaWheelScreen extends Screen {
    private static final int RADIUS = 82;
    private int page;
    private int selected = -1;
    private final long opened = System.currentTimeMillis();

    public VoltaWheelScreen() {
        super(Text.translatable("screen.windyorigins.volta_wheel"));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void tick() {
        long var1 = this.client.getWindow().getHandle();
        Key var3 = InputUtil.fromTranslationKey(HostClient.WHEEL.getBoundKeyTranslationKey());
        boolean var4 = var3.getCategory() == Type.KEYSYM ? InputUtil.isKeyPressed(var1, var3.getCode()) : HostClient.WHEEL.isPressed();
        if (!var4) {
            this.send();
        }
    }

    private void send() {
        if (this.selected >= 0) {
            HostCommand var1 = HostCommand.byId(this.page * 8 + this.selected);
            if (var1 != null) {
                PacketByteBuf var2 = new PacketByteBuf(Unpooled.buffer());
                var2.writeVarInt(var1.ordinal());
                ClientPlayNetworking.send(HostNetworking.COMMAND, var2);
            }
        }

        this.close();
    }

    @Override
    public boolean mouseScrolled(double var1, double var3, double var5) {
        int var7 = (HostCommand.WHEEL + 8 - 1) / 8;
        this.page = Math.floorMod(this.page + (var5 > 0.0 ? -1 : 1), var7);
        return true;
    }

    @Override
    public boolean mouseClicked(double var1, double var3, int var5) {
        double var6 = var1 - (double)this.width / 2.0;
        double var8 = var3 - (double)this.height / 2.0;
        if (var6 * var6 + var8 * var8 < 676.0) {
            this.mouseScrolled(var1, var3, -1.0);
            return true;
        } else if (this.selected >= 0) {
            this.send();
            return true;
        } else {
            return super.mouseClicked(var1, var3, var5);
        }
    }

    @Override
    public void render(DrawContext var1, int var2, int var3, float var4) {
        TextRenderer var5 = this.textRenderer;
        int var6 = this.width / 2;
        int var7 = this.height / 2;
        var1.fillGradient(0, 0, this.width, this.height, -1878389734, -1341848048);
        double var8 = (double)(var2 - var6);
        double var10 = (double)(var3 - var7);
        this.selected = -1;
        if (var8 * var8 + var10 * var10 > 900.0) {
            double var12 = Math.toDegrees(Math.atan2(var10, var8)) + 90.0 + 22.5;
            this.selected = Math.floorMod((int)Math.floor(var12 / 45.0), 8);
        }

        long var32 = System.currentTimeMillis() - this.opened;
        float var14 = MathHelper.clamp((float)var32 / 120.0F, 0.0F, 1.0F);
        int var15 = (int)(82.0F * (0.6F + 0.4F * var14));

        for (int var16 = 0; var16 < 8; var16++) {
            HostCommand var17 = HostCommand.byId(this.page * 8 + var16);
            if (var17 != null) {
                double var18 = Math.toRadians((double)var16 * 45.0 - 90.0);
                int var20 = var6 + (int)(Math.cos(var18) * (double)var15);
                int var21 = var7 + (int)(Math.sin(var18) * (double)var15);
                MutableText var22 = Text.translatable("volta.windyorigins.cmd." + var17.key() + ".name");
                int var23 = Math.max(54, var5.getWidth(var22) + 12);
                boolean var24 = var16 == this.selected;
                int var25 = var24 && var32 / 90L % 7L == 0L ? 2 : 0;
                int var26 = var24 ? HostClient.pink() : -13987943;
                HostClient.panel(var1, var20 - var23 / 2 + var25, var21 - 9, var20 + var23 / 2 + var25, var21 + 9, var26);
                var1.drawTextWithShadow(var5, var22, var20 - var5.getWidth(var22) / 2 + var25, var21 - 4, var24 ? -1 : -4655374);
                double var27 = Math.cos(var18);
                double var29 = Math.sin(var18);

                for (byte var31 = 30; var31 < var15 - 12; var31 += 3) {
                    var1.fill(
                        var6 + (int)(var27 * (double)var31),
                        var7 + (int)(var29 * (double)var31),
                        var6 + (int)(var27 * (double)var31) + 1,
                        var7 + (int)(var29 * (double)var31) + 1,
                        var24 ? HostClient.pink() : 1346826470
                    );
                }
            }
        }

        HostClient.panel(var1, var6 - 26, var7 - 14, var6 + 26, var7 + 14, HostClient.neon());
        String var33 = this.page + 1 + "/" + (HostCommand.WHEEL + 7) / 8;
        Text var34 = HostClient.hud(var33);
        var1.drawTextWithShadow(var5, var34, var6 - var5.getWidth(var34) / 2, var7 - 4, HostClient.neon());
        int var35 = var7 + var15 + 18;
        if (this.selected >= 0) {
            HostCommand var19 = HostCommand.byId(this.page * 8 + this.selected);
            if (var19 != null) {
                MutableText var37 = Text.translatable("volta.windyorigins.cmd." + var19.key() + ".desc");
                List var38 = var5.wrapLines(var37, 260);
                short var39 = 270;
                HostClient.panel(var1, var6 - var39 / 2, var35, var6 + var39 / 2, var35 + 18 + var38.size() * 10, HostClient.pink());
                MutableText var40 = Text.translatable("hud.windyorigins.volta.cost", var19.heat, var19.cooldown / 20);
                var1.drawTextWithShadow(var5, var40, var6 - var5.getWidth(var40) / 2, var35 + 4, -1033016);
                int var41 = var35 + 15;

                for (OrderedText var43 : (Iterable<OrderedText>)(Object)(var38)) {
                    var1.drawTextWithShadow(var5, var43, var6 - var5.getWidth(var43) / 2, var41, -1);
                    var41 += 10;
                }
            }
        }

        MutableText var36 = Text.translatable("hud.windyorigins.volta.hint");
        var1.drawTextWithShadow(var5, var36, var6 - var5.getWidth(var36) / 2, var7 - var15 - 28, -13987943);
    }
}
