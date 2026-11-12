package com.windykindy49.windyorigins.registry;

import com.windykindy49.windyorigins.util.MirrorEntity;
import com.windykindy49.windyorigins.util.SoundState;
import io.github.apace100.apoli.power.factory.action.ActionFactory;
import io.github.apace100.apoli.power.factory.condition.ConditionFactory;
import io.github.apace100.apoli.registry.ApoliRegistries;
import io.github.apace100.calio.data.SerializableData;
import io.github.apace100.calio.data.SerializableDataTypes;
import net.minecraft.registry.Registry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;

public class MirrorPowers {
    public static void register() {
        Registry.register(
            ApoliRegistries.BIENTITY_ACTION,
            new Identifier("windyorigins", "banish"),
            new ActionFactory<net.minecraft.util.Pair<net.minecraft.entity.Entity, net.minecraft.entity.Entity>>(
                new Identifier("windyorigins", "banish"),
                new SerializableData(),
                (data, pair) -> com.windykindy49.windyorigins.mirror.MirrorManager.banish(pair.getLeft(), pair.getRight())
            )
        );
        Registry.register(
            ApoliRegistries.ENTITY_ACTION,
            new Identifier("windyorigins", "set_phase"),
            new ActionFactory<>(
                new Identifier("windyorigins", "set_phase"), new SerializableData().add("in_mirror", SerializableDataTypes.BOOLEAN), (var0, var1) -> {
                    if (var1 instanceof MirrorEntity) {
                        ((MirrorEntity)var1).windyorigins$setInMirrorWorld(var0.getBoolean("in_mirror"));
                    }
                }
            )
        );
        Registry.register(
            ApoliRegistries.ENTITY_CONDITION,
            new Identifier("windyorigins", "in_mirror_world"),
            new ConditionFactory<>(
                new Identifier("windyorigins", "in_mirror_world"),
                new SerializableData(),
                (var0, var1) -> var1 instanceof MirrorEntity ? ((MirrorEntity)var1).windyorigins$isInMirrorWorld() : false
            )
        );
        Registry.register(
            ApoliRegistries.ENTITY_ACTION,
            new Identifier("windyorigins", "cross_phase_effect"),
            new ActionFactory<>(
                new Identifier("windyorigins", "cross_phase_effect"),
                new SerializableData()
                    .add("sound", SerializableDataTypes.SOUND_EVENT, null)
                    .add("particle", SerializableDataTypes.PARTICLE_EFFECT_OR_TYPE, null)
                    .add("volume", SerializableDataTypes.FLOAT, 1.0F)
                    .add("pitch", SerializableDataTypes.FLOAT, 1.0F)
                    .add("count", SerializableDataTypes.INT, 20)
                    .add("spread", SerializableDataTypes.FLOAT, 0.4F)
                    .add("speed", SerializableDataTypes.FLOAT, 0.05F),
                (var0, var1) -> {
                    if (var1.getWorld() instanceof ServerWorld) {
                        ServerWorld var2 = (ServerWorld)var1.getWorld();
                        SoundState.markCrossPhase();

                        try {
                            if (var0.isPresent("sound")) {
                                var2.playSound(
                                    null,
                                    var1.getX(),
                                    var1.getY(),
                                    var1.getZ(),
                                    var0.get("sound"),
                                    SoundCategory.PLAYERS,
                                    var0.getFloat("volume"),
                                    var0.getFloat("pitch")
                                );
                            }

                            if (var0.isPresent("particle")) {
                                float var3 = var0.getFloat("spread");
                                double var4 = var1.getY() + (double)var1.getHeight() * 0.5;
                                var2.spawnParticles(
                                    var0.get("particle"),
                                    var1.getX(),
                                    var4,
                                    var1.getZ(),
                                    var0.getInt("count"),
                                    (double)var3,
                                    (double)var3,
                                    (double)var3,
                                    (double)var0.getFloat("speed")
                                );
                            }
                        } finally {
                            SoundState.clearCrossPhase();
                        }
                    }
                }
            )
        );
    }
}
