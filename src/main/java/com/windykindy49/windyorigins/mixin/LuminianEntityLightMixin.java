package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.client.LuminianLight;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({EntityRenderer.class})
public abstract class LuminianEntityLightMixin {
    @Inject(
        method = {"getBlockLight"},
        at = {@At("RETURN")},
        cancellable = true
    )
    private void windyorigins$glow(Entity var1, BlockPos var2, CallbackInfoReturnable<Integer> var3) {
        int var4 = LuminianLight.lightAt(var2);
        if (var4 > (Integer)var3.getReturnValue()) {
            var3.setReturnValue(var4);
        }
    }
}
