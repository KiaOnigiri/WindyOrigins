package com.windykindy49.mirroredorigin.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.world.explosion.Explosion;
import com.windykindy49.mirroredorigin.util.MirrorContext;
import com.windykindy49.mirroredorigin.util.MirrorEntity;
import java.util.ArrayList;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value={Explosion.class})
public abstract class ExplosionMixin {
    @Shadow
    public abstract Entity getEntity();

    @ModifyVariable(method={"collectBlocksAndDamageEntities"}, at=@At(value="STORE"), ordinal=0)
    private List<Entity> filterExplosion(List<Entity> original) {
        // Not every explosion has a source entity: a bed in the Nether, a respawn anchor in
        // the Overworld and command-triggered blasts all pass null. Those used to skip the
        // filter entirely, so a bed lit by a mirror-phase player still hurt and threw around
        // everyone standing there in the overworld phase, and the other way round. The
        // surrounding context answers that case - during such a detonation it holds the phase
        // of the player whose interaction caused it.
        Entity source = this.getEntity();
        Boolean phase = source != null
            ? Boolean.valueOf(((MirrorEntity)source).mirroredorigin$isInMirrorWorld())
            : MirrorContext.get();
        ArrayList<Entity> safeList = new ArrayList<Entity>(original);
        // No explosion ever belongs to both phases. When there is neither a source entity nor
        // a context to attribute it to - a blast from the console, a command block, a
        // datapack function - it belongs to the overworld phase, the same rule natural
        // spawning follows. Returning the list unfiltered would have been the one case where
        // a single explosion hit both worlds at once.
        boolean explosionPhase = phase != null && phase.booleanValue();
        safeList.removeIf(e -> ((MirrorEntity)e).mirroredorigin$isInMirrorWorld() != explosionPhase);
        return safeList;
    }
}

