package com.windykindy49.mirroredorigin.mixin;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.AbstractDecorationEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import com.windykindy49.mirroredorigin.util.MirrorContext;
import com.windykindy49.mirroredorigin.util.MirrorEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Item frames and paintings occupy the same wall in both phases, so two players in
 * opposite phases must each be able to hang their own on the same spot.
 *
 * canStayAttached() ends with a getOtherEntities() test that counts every decoration
 * regardless of phase, which made the second one impossible to place. The phase is taken
 * from MirrorContext because the decoration being placed has not been spawned yet and so
 * still carries the default phase; during placement the context holds the acting player's
 * phase (published by ServerPlayNetworkHandlerContextMixin).
 *
 * ItemFrameEntity overrides canStayAttached() with its own copy of that test, so patching
 * only the base class fixed paintings while leaving frames broken - both are targeted now.
 *
 * require = 0: this call is a plain library call, and a missing match must degrade to
 * vanilla behaviour instead of taking the game down.
 */
@Mixin(value = {AbstractDecorationEntity.class, ItemFrameEntity.class})
public abstract class DecorationPlacementMixin {
    @Redirect(
        method = "canStayAttached",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;getOtherEntities(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/Box;Ljava/util/function/Predicate;)Ljava/util/List;"),
        require = 0
    )
    private List<Entity> mirroredorigin$onlySamePhase(World world, Entity except, Box box, Predicate<? super Entity> predicate) {
        List<Entity> list = world.getOtherEntities(except, box, predicate);
        Boolean context = MirrorContext.get();
        boolean phase = context != null
            ? context.booleanValue()
            : ((MirrorEntity) (Object) this).mirroredorigin$isInMirrorWorld();
        list.removeIf(entity -> ((MirrorEntity) entity).mirroredorigin$isInMirrorWorld() != phase);
        return list;
    }
}
