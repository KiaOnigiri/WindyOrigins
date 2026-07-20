package com.windykindy49.mirroredorigin.mixin;

import net.minecraft.entity.decoration.AbstractDecorationEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.DecorationItem;
import net.minecraft.item.ItemUsageContext;
import com.windykindy49.mirroredorigin.util.MirrorEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Gives an item frame or painting its phase BEFORE vanilla starts asking it questions.
 *
 * useOnBlock builds the decoration, then calls canStayAttached(), then onPlace(), and only
 * afterwards spawnEntity(). Since ServerWorldSpawnMixin stamps the phase during spawnEntity,
 * everything before that point was working with an entity that still carried the default
 * overworld phase. Two separate symptoms came out of that one gap:
 *
 *   - No placement sound in the mirror phase. onPlace() plays it through Entity#playSound,
 *     so EntitySoundMixin correctly recorded the frame as the source - a frame that, at that
 *     instant, still claimed to be in the overworld phase. The packet filter then dropped
 *     the sound for exactly the player who had just placed it.
 *
 *   - No hand swing when a frame already occupied that spot in the other phase. The client
 *     predicts placement by running useOnBlock itself, and there MirrorContext is empty -
 *     item use does not happen inside an entity tick - so DecorationPlacementMixin fell back
 *     to the entity's own phase and counted the other phase's frame as a real obstacle.
 *     canStayAttached() returned false, the client predicted failure and skipped the swing,
 *     while the server (which does have the context, from the packet handler) went ahead and
 *     placed it. Client and server simply disagreed.
 *
 * Stamping the phase from the placing player fixes both at once and on both sides, because
 * from here on the entity answers for itself instead of relying on ambient context.
 */
@Mixin(value = {DecorationItem.class})
public abstract class DecorationItemPhaseMixin {
    @Redirect(
        method = "useOnBlock",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/decoration/AbstractDecorationEntity;canStayAttached()Z")
    )
    private boolean mirroredorigin$stampPhaseBeforeCheck(AbstractDecorationEntity decoration, ItemUsageContext context) {
        PlayerEntity player = context.getPlayer();
        if (player != null) {
            ((MirrorEntity) decoration).mirroredorigin$setInMirrorWorld(
                ((MirrorEntity) player).mirroredorigin$isInMirrorWorld());
        }
        return decoration.canStayAttached();
    }
}
