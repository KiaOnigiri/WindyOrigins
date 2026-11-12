package com.windykindy49.windyorigins.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.datafixers.util.Pair;
import com.windykindy49.windyorigins.client.HostClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A Host's head slot is taken by the chip, so the empty slot shows a circuit instead of the outline of a
 * helmet. Only the picture of the empty slot is swapped, where every inventory screen asks for it.
 */
@Mixin(HandledScreen.class)
public abstract class ChipSlotIconMixin {
    @WrapOperation(
        method = "drawSlot",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/screen/slot/Slot;getBackgroundSprite()Lcom/mojang/datafixers/util/Pair;")
    )
    private Pair<Identifier, Identifier> windyorigins$chipSlot(Slot slot, Operation<Pair<Identifier, Identifier>> original) {
        return HostClient.slotIcon(slot, original.call(slot));
    }
}
