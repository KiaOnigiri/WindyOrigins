package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.util.MirrorContext;
import com.windykindy49.windyorigins.util.MirrorEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.world.explosion.Explosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin({Explosion.class})
public abstract class ExplosionMixin {
    @Shadow
    public abstract Entity getEntity();

    @ModifyVariable(
        method = {"collectBlocksAndDamageEntities"},
        at = @At("STORE"),
        ordinal = 0
    )
    private List<Entity> filterExplosion(List<Entity> var1) {
        Entity var2 = this.getEntity();
        Boolean var3 = var2 != null ? ((MirrorEntity)var2).windyorigins$isInMirrorWorld() : MirrorContext.get();
        ArrayList var4 = new ArrayList(var1);
        boolean var5 = var3 != null && var3;
        var4.removeIf(var1x -> ((MirrorEntity)var1x).windyorigins$isInMirrorWorld() != var5);
        return var4;
    }
}
