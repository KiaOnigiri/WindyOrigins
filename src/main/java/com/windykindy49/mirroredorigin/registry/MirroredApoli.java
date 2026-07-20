package com.windykindy49.mirroredorigin.registry;

import net.minecraft.entity.Entity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.registry.Registry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

import com.windykindy49.mirroredorigin.util.MirrorEntity;
import com.windykindy49.mirroredorigin.util.SoundState;

import io.github.apace100.apoli.power.factory.action.ActionFactory;
import io.github.apace100.apoli.power.factory.condition.ConditionFactory;
import io.github.apace100.apoli.registry.ApoliRegistries;
import io.github.apace100.calio.data.SerializableData;
import io.github.apace100.calio.data.SerializableDataTypes;

public class MirroredApoli {
    public static void register() {
        Registry.register(ApoliRegistries.ENTITY_ACTION, new Identifier("mirroredorigin", "set_phase"),
            new ActionFactory<Entity>(new Identifier("mirroredorigin", "set_phase"),
                new SerializableData().add("in_mirror", SerializableDataTypes.BOOLEAN), (data, entity) -> {
            if (entity instanceof MirrorEntity) {
                ((MirrorEntity)entity).mirroredorigin$setInMirrorWorld(data.getBoolean("in_mirror"));
            }
        }));

        Registry.register(ApoliRegistries.ENTITY_CONDITION, new Identifier("mirroredorigin", "in_mirror_world"),
            new ConditionFactory<Entity>(new Identifier("mirroredorigin", "in_mirror_world"), new SerializableData(), (data, entity) -> {
            if (entity instanceof MirrorEntity) {
                return ((MirrorEntity)entity).mirroredorigin$isInMirrorWorld();
            }
            return false;
        }));

        // Plays a sound and/or particles that reach players in BOTH phases.
        // SoundState.CROSS_PHASE tells ServerPlayNetworkHandlerFilterMixin not to cancel
        // the resulting packets for players standing in the opposite phase.
        Registry.register(ApoliRegistries.ENTITY_ACTION, new Identifier("mirroredorigin", "cross_phase_effect"),
            new ActionFactory<Entity>(new Identifier("mirroredorigin", "cross_phase_effect"),
                new SerializableData()
                    .add("sound", SerializableDataTypes.SOUND_EVENT, null)
                    .add("particle", SerializableDataTypes.PARTICLE_EFFECT_OR_TYPE, null)
                    .add("volume", SerializableDataTypes.FLOAT, Float.valueOf(1.0f))
                    .add("pitch", SerializableDataTypes.FLOAT, Float.valueOf(1.0f))
                    .add("count", SerializableDataTypes.INT, Integer.valueOf(20))
                    .add("spread", SerializableDataTypes.FLOAT, Float.valueOf(0.4f))
                    .add("speed", SerializableDataTypes.FLOAT, Float.valueOf(0.05f)),
                (data, entity) -> {
            if (!(entity.getWorld() instanceof ServerWorld)) {
                return;
            }
            ServerWorld world = (ServerWorld)entity.getWorld();
            SoundState.markCrossPhase();
            try {
                if (data.isPresent("sound")) {
                    world.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                        (SoundEvent)data.get("sound"), SoundCategory.PLAYERS,
                        data.getFloat("volume"), data.getFloat("pitch"));
                }
                if (data.isPresent("particle")) {
                    float spread = data.getFloat("spread");
                    double centerY = entity.getY() + (double)entity.getHeight() * 0.5;
                    world.spawnParticles((ParticleEffect)data.get("particle"),
                        entity.getX(), centerY, entity.getZ(),
                        data.getInt("count"), spread, spread, spread, data.getFloat("speed"));
                }
            } finally {
                SoundState.clearCrossPhase();
            }
        }));
    }
}
