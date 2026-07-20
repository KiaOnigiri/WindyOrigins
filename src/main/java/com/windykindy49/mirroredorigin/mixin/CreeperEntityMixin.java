package com.windykindy49.mirroredorigin.mixin;

import net.minecraft.entity.AreaEffectCloudEntity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import com.windykindy49.mirroredorigin.util.MirrorEntity;
import com.windykindy49.mirroredorigin.util.PhaseScope;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={CreeperEntity.class})
public class CreeperEntityMixin {
    @Inject(method={"tick"}, at={@At(value="HEAD")})
    private void onTickHead(CallbackInfo ci) {
        PhaseScope.enter(((MirrorEntity)(Object)this).mirroredorigin$isInMirrorWorld());
    }

    @Inject(method={"tick"}, at={@At(value="RETURN")})
    private void onTickTail(CallbackInfo ci) {
        PhaseScope.exit();
    }

    @Inject(method={"interactMob"}, at={@At(value="HEAD")})
    private void onInteract(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        ItemStack itemStack = player.getStackInHand(hand);
        if (itemStack.isOf(Items.FLINT_AND_STEEL) || itemStack.isOf(Items.FIRE_CHARGE)) {
            ((MirrorEntity)(Object)this).mirroredorigin$setInMirrorWorld(((MirrorEntity)player).mirroredorigin$isInMirrorWorld());
        }
    }

    @ModifyVariable(method={"spawnEffectsCloud"}, at=@At(value="STORE"), ordinal=0)
    private AreaEffectCloudEntity setCloudPhase(AreaEffectCloudEntity cloud) {
        boolean creeperPhase = ((MirrorEntity)(Object)this).mirroredorigin$isInMirrorWorld();
        ((MirrorEntity)cloud).mirroredorigin$setInMirrorWorld(creeperPhase);
        return cloud;
    }
}

