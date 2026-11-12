package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.util.MirrorEntity;
import net.minecraft.entity.decoration.AbstractDecorationEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.DecorationItem;
import net.minecraft.item.ItemUsageContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin({DecorationItem.class})
public abstract class DecorationItemPhaseMixin {
    @Redirect(
        method = {"useOnBlock"},
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/entity/decoration/AbstractDecorationEntity;canStayAttached()Z"
        )
    )
    private boolean windyorigins$stampPhaseBeforeCheck(AbstractDecorationEntity var1, ItemUsageContext var2) {
        PlayerEntity var3 = var2.getPlayer();
        if (var3 != null) {
            ((MirrorEntity)var1).windyorigins$setInMirrorWorld(((MirrorEntity)var3).windyorigins$isInMirrorWorld());
        }

        return var1.canStayAttached();
    }
}
