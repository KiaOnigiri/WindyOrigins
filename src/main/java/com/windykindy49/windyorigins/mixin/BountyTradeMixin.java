package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.bounty.BountyManager;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.village.TradeOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({VillagerEntity.class})
public abstract class BountyTradeMixin {
    @Inject(
        method = {"prepareOffersFor"},
        at = {@At("RETURN")}
    )
    private void windyorigins$mercenaryMarkup(PlayerEntity var1, CallbackInfo var2) {
        if (BountyManager.hasPower(var1, "despised_mercenary")) {
            for (TradeOffer var4 : ((VillagerEntity)(Object)this).getOffers()) {
                int var5 = var4.getOriginalFirstBuyItem().getCount();
                int var6 = Math.max(1, Math.round((float)var5 * 0.25F));
                var6 = Math.min(var6, 64 - var5 - var4.getSpecialPrice());
                if (var6 > 0) {
                    var4.increaseSpecialPrice(var6);
                }
            }
        }
    }
}
