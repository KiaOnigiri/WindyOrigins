package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.bounty.BountyKillScope;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({EnchantmentHelper.class})
public abstract class BountyLootingMixin {
    @Inject(
        method = {"getLooting"},
        at = {@At("RETURN")},
        cancellable = true
    )
    private static void windyorigins$contractLooting(LivingEntity var0, CallbackInfoReturnable<Integer> var1) {
        BountyKillScope.Context var2 = BountyKillScope.get();
        if (var2 != null && var2.kind == BountyKillScope.Kind.CONTRACT && var2.hunter == var0 && var1.getReturnValueI() < 3) {
            var1.setReturnValue(3);
        }
    }
}
