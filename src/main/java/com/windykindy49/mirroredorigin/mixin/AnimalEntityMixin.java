package com.windykindy49.mirroredorigin.mixin;

import net.minecraft.entity.passive.AnimalEntity;
import com.windykindy49.mirroredorigin.util.MirrorEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={AnimalEntity.class})
public class AnimalEntityMixin {
    @Inject(method={"canBreedWith"}, at={@At(value="HEAD")}, cancellable=true)
    private void onCanBreedWith(AnimalEntity other, CallbackInfoReturnable<Boolean> cir) {
        if (other != null && ((MirrorEntity)(Object)this).mirroredorigin$isInMirrorWorld() != ((MirrorEntity)other).mirroredorigin$isInMirrorWorld()) {
            cir.setReturnValue(false);
        }
    }
}

