package com.windykindy49.windyorigins.mixin;

import com.windykindy49.windyorigins.common.StillTracker;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({Entity.class})
public abstract class StillnessMixin implements StillTracker {
    @Unique
    private Vec3d windyorigins$lastPos;
    @Unique
    private int windyorigins$stillTicks;

    @Inject(
        method = {"tick"},
        at = {@At("TAIL")}
    )
    private void windyorigins$countStill(CallbackInfo var1) {
        Vec3d var2 = ((Entity)(Object)this).getPos();
        if (this.windyorigins$lastPos != null && this.windyorigins$lastPos.squaredDistanceTo(var2) < 1.0E-4) {
            this.windyorigins$stillTicks++;
        } else {
            this.windyorigins$stillTicks = 0;
        }

        this.windyorigins$lastPos = var2;
    }

    @Override
    public int windyorigins$getStillTicks() {
        return this.windyorigins$stillTicks;
    }
}
