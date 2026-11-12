package com.windykindy49.windyorigins.bounty.client;

import com.windykindy49.windyorigins.bounty.BountyManager;
import com.windykindy49.windyorigins.bounty.BountyNetworking;
import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.Power;
import io.github.apace100.apoli.power.PowerTypeRegistry;
import io.github.apace100.apoli.power.VariableIntPower;
import java.util.ArrayList;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import com.windykindy49.windyorigins.client.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.texture.Sprite;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.registry.Registries;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Vector3f;

public final class BountyClient {
    private static final Identifier SHEET = new Identifier("windyorigins", "textures/gui/bounty_hunter.png");
    private static final Identifier ICONS = new Identifier("textures/gui/icons.png");
    private static final Identifier CONTRACT_TIMER = new Identifier("windyorigins", "contract_timer");
    private static final Identifier MARK_RECHARGE = new Identifier("windyorigins", "mark_recharge");
    private static final int PANEL_BG = -1340863990;
    private static final int PANEL_EDGE = -5039842;
    private static final int SLOT_BG = 1342177280;
    private static final int VIGNETTE_RGB = 11534336;
    private static final DustParticleEffect CROWN = new DustParticleEffect(new Vector3f(0.78F, 0.05F, 0.07F), 0.7F);

    private BountyClient() {
    }

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(BountyNetworking.HUNTER_SYNC, (var0, var1, var2, var3) -> {
            int var4 = var2.readVarInt();
            int var5 = var2.readVarInt();
            int var6 = var2.readVarInt();
            var0.execute(() -> ClientBountyState.setContract(var4, var5, var6));
        });
        ClientPlayNetworking.registerGlobalReceiver(BountyNetworking.MARKED_SYNC, (var0, var1, var2, var3) -> {
            boolean var4 = var2.readBoolean();
            var0.execute(() -> ClientBountyState.setMarked(var4));
        });
        ClientPlayNetworking.registerGlobalReceiver(BountyNetworking.TARGET_DETAIL, (var0, var1, var2, var3) -> {
            int var4 = var2.readVarInt();
            int var5 = var2.readVarInt();
            int var6 = var2.readVarInt();
            int var7 = var2.readVarInt();
            ItemStack[] var8 = new ItemStack[9];

            for (int var9 = 0; var9 < var8.length; var9++) {
                var8[var9] = var2.readItemStack();
            }

            ItemStack var16 = var2.readItemStack();
            int var10 = var2.readVarInt();
            ArrayList var11 = new ArrayList();

            for (int var12 = 0; var12 < var10; var12++) {
                StatusEffect var13 = Registries.STATUS_EFFECT.get(var2.readVarInt());
                int var14 = var2.readVarInt();
                int var15 = var2.readVarInt();
                if (var13 != null) {
                    var11.add(new StatusEffectInstance(var13, var15, var14));
                }
            }

            var0.execute(() -> ClientBountyState.setDetail(var4, var5, var6, var7, var8, var16, var11));
        });
        ClientPlayConnectionEvents.DISCONNECT.register((var0, var1) -> ClientBountyState.reset());
        ClientTickEvents.END_CLIENT_TICK.register(BountyClient::tick);
        HudRenderCallback.EVENT.register((matrices, delta) -> BountyClient.render(new com.windykindy49.windyorigins.client.DrawContext(matrices), delta));
    }

    private static void tick(MinecraftClient var0) {
        ClientPlayerEntity var1 = var0.player;
        if (var1 != null && var0.world != null) {
            ClientBountyState.setLocalPlayer(var1.getUuid());
            if (!var0.isPaused()) {
                ClientBountyState.clientTick();
                feedBars(var1);
                if (ClientBountyState.hasContract()) {
                    Entity var2 = var0.world.getEntityById(ClientBountyState.getTargetId());
                    if (var2 != null && var2 != var0.cameraEntity && var1.age % 2 == 0) {
                        spawnCrown(var0, var2, var1.age);
                    }
                }
            }
        }
    }

    private static void feedBars(ClientPlayerEntity var0) {
        setResource(var0, CONTRACT_TIMER, ClientBountyState.hasContract() ? ClientBountyState.getMarkTicks() : 0);
        setResource(
            var0, MARK_RECHARGE, !ClientBountyState.hasContract() && ClientBountyState.getCooldownTicks() > 0 ? 2400 - ClientBountyState.getCooldownTicks() : 0
        );
    }

    private static void setResource(PlayerEntity var0, Identifier var1, int var2) {
        if (PowerTypeRegistry.contains(var1)) {
            PowerHolderComponent.KEY.maybeGet(var0).ifPresent(var2x -> {
                Power var3 = var2x.getPower(PowerTypeRegistry.get(var1));
                if (var3 instanceof VariableIntPower) {
                    ((VariableIntPower)var3).setValue(var2);
                }
            });
        }
    }

    private static void spawnCrown(MinecraftClient var0, Entity var1, int var2) {
        double var3 = (double)var2 * 0.32;
        double var5 = Math.max(0.35, (double)var1.getWidth() * 0.55);
        double var7 = var1.getY() + (double)var1.getHeight() + 0.35;

        for (int var9 = 0; var9 < 2; var9++) {
            double var10 = var3 + (double)var9 * Math.PI;
            var0.world.addParticle(CROWN, var1.getX() + Math.cos(var10) * var5, var7, var1.getZ() + Math.sin(var10) * var5, 0.0, 0.0, 0.0);
        }
    }

    private static void render(DrawContext var0, float var1) {
        MinecraftClient var2 = MinecraftClient.getInstance();
        if (var2.player != null && !var2.options.hudHidden && var2.world != null) {
            if (ClientBountyState.isMarked()) {
                drawVignette(var0, var2, var1);
            }

            if (ClientBountyState.hasContract()) {
                Entity var3 = var2.world.getEntityById(ClientBountyState.getTargetId());
                if (var3 != null) {
                    drawPanel(var0, var2, var3);
                }
            }
        }
    }

    private static void drawVignette(DrawContext var0, MinecraftClient var1, float var2) {
        int var3 = var0.getScaledWindowWidth();
        int var4 = var0.getScaledWindowHeight();
        float var5 = ((float)var1.player.age + var2) / 20.0F;
        int var6 = (int)(48.0F + 24.0F * (0.5F + 0.5F * MathHelper.sin(var5 * 2.2F)));
        int var7 = var6 << 24 | 11534336;
        int var8 = 11534336;
        int var9 = Math.max(14, Math.min(var3, var4) / 7);
        var0.fillGradient(0, 0, var3, var9, var7, var8);
        var0.fillGradient(0, var4 - var9, var3, var4, var8, var7);
        byte var10 = 8;

        for (int var11 = 0; var11 < var10; var11++) {
            int var12 = (int)((float)var6 * (1.0F - (float)var11 / (float)var10));
            int var13 = var12 << 24 | 11534336;
            int var14 = var9 * var11 / var10;
            int var15 = var9 * (var11 + 1) / var10;
            var0.fill(var14, var9, var15, var4 - var9, var13);
            var0.fill(var3 - var15, var9, var3 - var14, var4 - var9, var13);
        }
    }

    private static void drawPanel(DrawContext var0, MinecraftClient var1, Entity var2) {
        TextRenderer var3 = var1.textRenderer;
        boolean var4 = BountyManager.hasPower(var1.player, "dossier");
        boolean var5 = var4 && ClientBountyState.hasDetail();
        LivingEntity var6 = var2 instanceof LivingEntity ? (LivingEntity)var2 : null;
        int var7 = Math.round(var1.player.distanceTo(var2));
        int var8 = (ClientBountyState.getMarkTicks() + 19) / 20;
        MutableText var9 = var2.getDisplayName().copy().formatted(Formatting.RED);
        MutableText var10 = Text.literal(var7 + "m")
            .formatted(Formatting.GRAY)
            .append(Text.literal("  ·  ").formatted(Formatting.DARK_GRAY))
            .append(Text.literal(var8 + "s").formatted(var8 <= 10 ? Formatting.RED : Formatting.GOLD));
        ItemStack var11 = var6 == null ? ItemStack.EMPTY : var6.getMainHandStack();
        ItemStack var12 = var6 == null ? ItemStack.EMPTY : var6.getOffHandStack();
        boolean var13 = var4 && !var5 && (!var11.isEmpty() || !var12.isEmpty());
        int var14 = Math.max(120, 18 + var3.getWidth(var9) + 10 + var3.getWidth(var10) + 6);
        if (var5) {
            var14 = Math.max(var14, 198);
        }

        byte var15 = 16;
        if (var4 && var6 != null) {
            var15 += 12;
        }

        if (var5) {
            var15 += 20;
            if (!ClientBountyState.getDetailEffects().isEmpty()) {
                var15 += 12;
            }
        } else if (var13) {
            var15 += 20;
        }

        byte var16 = 4;
        byte var17 = 4;
        var0.fill(var16, var17, var16 + var14, var17 + var15, -1340863990);
        var0.fill(var16, var17, var16 + var14, var17 + 1, -5039842);
        var0.fill(var16, var17 + var15 - 1, var16 + var14, var17 + var15, -2135746274);
        var0.fill(var16, var17 + 1, var16 + 1, var17 + var15 - 1, -5039842);
        var0.drawTexture(SHEET, var16 + 5, var17 + 4, 73, 8, 8, 8);
        var0.drawTextWithShadow(var3, var9, var16 + 17, var17 + 4, 16777215);
        var0.drawTextWithShadow(var3, var10, var16 + var14 - 5 - var3.getWidth(var10), var17 + 4, 16777215);
        int var18 = var17 + 16;
        if (var4 && var6 != null) {
            int var19 = var16 + 5;
            var0.drawTexture(ICONS, var19, var18, 16, 0, 9, 9);
            var0.drawTexture(ICONS, var19, var18, 52, 0, 9, 9);
            String var20 = formatHealth(var6.getHealth() + var6.getAbsorptionAmount()) + "/" + formatHealth(var6.getMaxHealth());
            var0.drawTextWithShadow(var3, var20, var19 + 11, var18 + 1, 16733525);
            var19 += 11 + var3.getWidth(var20) + 8;
            int var21 = var5 ? ClientBountyState.getDetailArmor() : var6.getArmor();
            var0.drawTexture(ICONS, var19, var18, 34, 9, 9, 9);
            var0.drawTextWithShadow(var3, String.valueOf(var21), var19 + 11, var18 + 1, 13619151);
            var19 += 11 + var3.getWidth(String.valueOf(var21)) + 8;
            if (var5) {
                var0.drawTexture(ICONS, var19, var18, 16, 27, 9, 9);
                var0.drawTexture(ICONS, var19, var18, 52, 27, 9, 9);
                var0.drawTextWithShadow(var3, String.valueOf(ClientBountyState.getDetailFood()), var19 + 11, var18 + 1, 14721088);
            }

            var18 += 12;
        }

        if (var5) {
            int var27 = var16 + 5;

            for (int var28 = 0; var28 < 9; var28++) {
                boolean var30 = var28 == ClientBountyState.getDetailSelected();
                drawSlot(var0, var3, ClientBountyState.getDetailHotbar(var28), var27 + var28 * 18, var18, var30);
            }

            drawSlot(var0, var3, ClientBountyState.getDetailOffhand(), var27 + 162 + 6, var18, false);
            var18 += 20;
            int var29 = var16 + 5;

            for (StatusEffectInstance var22 : ClientBountyState.getDetailEffects()) {
                if (var29 + 10 > var16 + var14 - 4) {
                    break;
                }

                Sprite var23 = var1.getStatusEffectSpriteManager().getSprite(var22.getEffectType());
                com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(0, var23.getAtlasId());
                var0.drawSprite(var29, var18, 0, 9, 9, var23);
                var29 += 11;
            }
        } else if (var13) {
            drawSlot(var0, var3, var11, var16 + 5, var18, false);
            drawSlot(var0, var3, var12, var16 + 5 + 18, var18, false);
        }
    }

    private static void drawSlot(DrawContext var0, TextRenderer var1, ItemStack var2, int var3, int var4, boolean var5) {
        var0.fill(var3, var4, var3 + 17, var4 + 17, 1342177280);
        if (var5) {
            var0.fill(var3, var4, var3 + 17, var4 + 1, -5039842);
            var0.fill(var3, var4 + 16, var3 + 17, var4 + 17, -5039842);
            var0.fill(var3, var4, var3 + 1, var4 + 17, -5039842);
            var0.fill(var3 + 16, var4, var3 + 17, var4 + 17, -5039842);
        }

        if (!var2.isEmpty()) {
            var0.drawItem(var2, var3 + 1, var4 + 1);
            var0.drawItemInSlot(var1, var2, var3 + 1, var4 + 1);
        }
    }

    private static String formatHealth(float var0) {
        float var1 = (float)Math.round(var0 * 2.0F) / 2.0F;
        return var1 == (float)((int)var1) ? String.valueOf((int)var1) : String.valueOf(var1);
    }
}
