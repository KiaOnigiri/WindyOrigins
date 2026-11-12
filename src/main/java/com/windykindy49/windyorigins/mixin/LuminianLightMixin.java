package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.client.LuminianLight;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({WorldRenderer.class})
public abstract class LuminianLightMixin {
    @Inject(
        method = {"getLightmapCoordinates(Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;)I"},
        at = {@At("RETURN")},
        cancellable = true
    )
    private static void windyorigins$glow(BlockRenderView var0, BlockState var1, BlockPos var2, CallbackInfoReturnable<Integer> var3) {
        int var4 = LuminianLight.lightAt(var2);
        if (var4 > 0) {
            int var5 = (Integer)var3.getReturnValue();
            int var6 = LightmapTextureManager.getBlockLightCoordinates(var5);
            if (var4 > var6) {
                var3.setReturnValue(LightmapTextureManager.pack(var4, LightmapTextureManager.getSkyLightCoordinates(var5)));
            }
        }
    }
}
