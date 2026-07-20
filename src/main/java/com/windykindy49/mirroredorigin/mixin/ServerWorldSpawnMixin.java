package com.windykindy49.mirroredorigin.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.entity.LightningEntity;
import com.windykindy49.mirroredorigin.util.CrossPhaseEntity;
import com.windykindy49.mirroredorigin.util.NaturalLightning;
import com.windykindy49.mirroredorigin.util.MirrorContext;
import com.windykindy49.mirroredorigin.util.MirrorEntity;
import com.windykindy49.mirroredorigin.util.SpawnPhaseOverride;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={ServerWorld.class})
public class ServerWorldSpawnMixin {
    @Inject(method={"spawnEntity"}, at={@At(value="HEAD")})
    private void onSpawnEntity(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        // Entities created by game logic that must inherit a parent's phase (bred babies,
        // hatching turtles). Checked first because it also applies to mobs, which the
        // generic path below deliberately skips.
        Boolean forced = SpawnPhaseOverride.get();
        if (forced != null) {
            ((MirrorEntity)entity).mirroredorigin$setInMirrorWorld(forced);
            return;
        }

        // Mobs used to be excluded here, because back then the context leaked far beyond
        // the action that set it and natural spawns would have been dragged into the mirror
        // phase. That is no longer true: the context is opened and closed around a single
        // entity tick (ServerWorldTickMixin) or a single packet handler, and reset at the
        // head of every world tick. Natural spawning runs in the world tick through
        // SpawnHelper, outside any of those, so it still sees no context and stays in the
        // overworld phase - and the same goes for spawners, which tick as block entities.
        //
        // Dropping the exclusion is what finally gives a phase to mobs that vanilla creates
        // by hand in the middle of someone else's tick: slimes and magma cubes splitting,
        // zombies calling reinforcements, a thrown egg hatching a chicken, a tadpole turning
        // into a frog, a sheared mooshroom becoming a cow, and golems or a wither built by a
        // player (that one arrives through the block-placement handler).
        // Weather lightning belongs to nobody, so it belongs to both worlds.
        if (entity instanceof LightningEntity && NaturalLightning.isWeatherStrike()) {
            ((CrossPhaseEntity) entity).mirroredorigin$setCrossPhase(true);
            return;
        }
        Boolean phase = MirrorContext.get();
        if (phase != null) {
            ((MirrorEntity)entity).mirroredorigin$setInMirrorWorld(phase);
        }
    }
}
