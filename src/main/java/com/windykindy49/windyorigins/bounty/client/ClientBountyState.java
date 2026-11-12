package com.windykindy49.windyorigins.bounty.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;

public final class ClientBountyState {
    private static int targetId = -1;
    private static int markTicks;
    private static int cooldownTicks;
    private static boolean marked;
    private static UUID localPlayer;
    private static int detailEntityId = -1;
    private static int detailFood;
    private static int detailArmor;
    private static int detailSelected;
    private static final ItemStack[] DETAIL_HOTBAR = new ItemStack[9];
    private static ItemStack detailOffhand = ItemStack.EMPTY;
    private static final List<StatusEffectInstance> DETAIL_EFFECTS = new ArrayList<>();

    private ClientBountyState() {
    }

    public static void setContract(int var0, int var1, int var2) {
        if (var0 != targetId) {
            detailEntityId = -1;
        }

        targetId = var0;
        markTicks = var1;
        cooldownTicks = var2;
    }

    public static void setMarked(boolean var0) {
        marked = var0;
    }

    public static void setLocalPlayer(UUID var0) {
        localPlayer = var0;
    }

    public static void clientTick() {
        if (targetId < 0 && cooldownTicks > 0) {
            cooldownTicks--;
        }
    }

    public static void setDetail(int var0, int var1, int var2, int var3, ItemStack[] var4, ItemStack var5, List<StatusEffectInstance> var6) {
        detailEntityId = var0;
        detailFood = var1;
        detailArmor = var2;
        detailSelected = var3;

        for (int var7 = 0; var7 < DETAIL_HOTBAR.length; var7++) {
            DETAIL_HOTBAR[var7] = var7 < var4.length ? var4[var7] : ItemStack.EMPTY;
        }

        detailOffhand = var5;
        DETAIL_EFFECTS.clear();
        DETAIL_EFFECTS.addAll(var6);
    }

    public static void reset() {
        targetId = -1;
        markTicks = 0;
        cooldownTicks = 0;
        marked = false;
        detailEntityId = -1;
        DETAIL_EFFECTS.clear();
    }

    public static boolean isLocal(Entity var0) {
        return var0 != null && var0.getWorld().isClient() && localPlayer != null && localPlayer.equals(var0.getUuid());
    }

    public static int getTargetId() {
        return targetId;
    }

    public static boolean hasContract() {
        return targetId >= 0;
    }

    public static int getMarkTicks() {
        return markTicks;
    }

    public static int getCooldownTicks() {
        return cooldownTicks;
    }

    public static boolean isMarked() {
        return marked;
    }

    public static boolean hasDetail() {
        return targetId >= 0 && detailEntityId == targetId;
    }

    public static int getDetailFood() {
        return detailFood;
    }

    public static int getDetailArmor() {
        return detailArmor;
    }

    public static int getDetailSelected() {
        return detailSelected;
    }

    public static ItemStack getDetailHotbar(int var0) {
        return DETAIL_HOTBAR[var0];
    }

    public static ItemStack getDetailOffhand() {
        return detailOffhand;
    }

    public static List<StatusEffectInstance> getDetailEffects() {
        return DETAIL_EFFECTS;
    }

    static {
        Arrays.fill(DETAIL_HOTBAR, ItemStack.EMPTY);
    }
}
