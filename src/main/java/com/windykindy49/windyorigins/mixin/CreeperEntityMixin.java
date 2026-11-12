package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.util.MirrorEntity;
import com.windykindy49.windyorigins.util.PhaseScope;
import net.minecraft.entity.AreaEffectCloudEntity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({CreeperEntity.class})
public class CreeperEntityMixin {
    @Inject(
        method = {"tick"},
        at = {@At("HEAD")}
    )
    private void onTickHead(CallbackInfo var1) {
        PhaseScope.enter(((MirrorEntity)(Object)this).windyorigins$isInMirrorWorld());
    }

    @Inject(
        method = {"tick"},
        at = {@At("RETURN")}
    )
    private void onTickTail(CallbackInfo var1) {
        PhaseScope.exit();
    }

    @Inject(
        method = {"interactMob"},
        at = {@At("HEAD")}
    )
    private void onInteract(PlayerEntity var1, Hand var2, CallbackInfoReturnable<ActionResult> var3) {
        ItemStack var4 = var1.getStackInHand(var2);
        if (var4.isOf(Items.FLINT_AND_STEEL) || var4.isOf(Items.FIRE_CHARGE)) {
            ((MirrorEntity)(Object)this).windyorigins$setInMirrorWorld(((MirrorEntity)var1).windyorigins$isInMirrorWorld());
        }
    }

    @ModifyVariable(
        method = {"spawnEffectsCloud"},
        at = @At("STORE"),
        ordinal = 0
    )
    private AreaEffectCloudEntity setCloudPhase(AreaEffectCloudEntity var1) {
        boolean var2 = ((MirrorEntity)(Object)this).windyorigins$isInMirrorWorld();
        ((MirrorEntity)var1).windyorigins$setInMirrorWorld(var2);
        return var1;
    }
}
