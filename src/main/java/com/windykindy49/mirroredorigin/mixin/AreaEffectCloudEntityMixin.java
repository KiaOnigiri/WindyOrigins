package com.windykindy49.mirroredorigin.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.AreaEffectCloudEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import com.windykindy49.mirroredorigin.util.MirrorEntity;
import java.util.ArrayList;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={AreaEffectCloudEntity.class})
public class AreaEffectCloudEntityMixin {
    @Inject(method={"tick"}, at={@At(value="HEAD")}, cancellable=true)
    private void onTick(CallbackInfo ci) {
        if (((AreaEffectCloudEntity)(Object)this).getWorld().isClient()) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.cameraEntity != null && ((MirrorEntity)(Object)this).mirroredorigin$isInMirrorWorld() != ((MirrorEntity)client.cameraEntity).mirroredorigin$isInMirrorWorld()) {
                ci.cancel();
            }
        }
    }

    /**
     * Gives a freshly created cloud a phase to start from. spawnEntity stamps it again a
     * moment later from the surrounding context, which is why lingering potions land in the
     * right phase either way, but a cloud that never passes through a phase context still
     * needs an answer here.
     *
     * setWaitTime(0) used to sit in this method and had nothing to do with phases: it made
     * clouds skip the delay before their first application. Lingering potions never noticed,
     * because vanilla calls setWaitTime(10) right after setOwner and overwrote it - but
     * dragon breath sets no wait time at all, so those clouds were silently dealing their
     * damage instantly instead of after the usual 20 ticks. Removed; vanilla timing is back.
     */
    @Inject(method={"setOwner"}, at={@At(value="RETURN")})
    private void onSetOwner(LivingEntity owner, CallbackInfo ci) {
        if (owner != null) {
            ((MirrorEntity)(Object)this).mirroredorigin$setInMirrorWorld(((MirrorEntity)owner).mirroredorigin$isInMirrorWorld());
        }
    }

    @Redirect(method={"tick"}, at=@At(value="INVOKE", target="Lnet/minecraft/world/World;getNonSpectatingEntities(Ljava/lang/Class;Lnet/minecraft/util/math/Box;)Ljava/util/List;"))
    private List<LivingEntity> filterCloudTargets(World world, Class<? extends LivingEntity> entityClass, Box box) {
        List original = world.getNonSpectatingEntities(entityClass, box);
        ArrayList<LivingEntity> filtered = new ArrayList<LivingEntity>(original);
        boolean phase = ((MirrorEntity)(Object)this).mirroredorigin$isInMirrorWorld();
        filtered.removeIf(e -> ((MirrorEntity)e).mirroredorigin$isInMirrorWorld() != phase);
        return filtered;
    }
}

