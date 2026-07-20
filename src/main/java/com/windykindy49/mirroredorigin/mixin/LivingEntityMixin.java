package com.windykindy49.mirroredorigin.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import com.windykindy49.mirroredorigin.util.MirrorEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={LivingEntity.class})
public class LivingEntityMixin {
    @Inject(method={"damage"}, at={@At(value="HEAD")}, cancellable=true)
    private void onDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        Entity attacker = source.getAttacker();
        if (attacker != null && ((MirrorEntity)(Object)this).mirroredorigin$isInMirrorWorld() != ((MirrorEntity)attacker).mirroredorigin$isInMirrorWorld()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method={"tickStatusEffects"}, at={@At(value="HEAD")}, cancellable=true)
    private void onTickStatusEffects(CallbackInfo ci) {
        Entity camera;
        LivingEntity thisEntity = (LivingEntity)(Object)this;
        if (thisEntity.getWorld().isClient() && (camera = MinecraftClient.getInstance().cameraEntity) != null && ((MirrorEntity)(Object)this).mirroredorigin$isInMirrorWorld() != ((MirrorEntity)camera).mirroredorigin$isInMirrorWorld()) {
            ci.cancel();
        }
    }
}

