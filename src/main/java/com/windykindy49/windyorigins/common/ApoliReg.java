package com.windykindy49.windyorigins.common;

import io.github.apace100.apoli.power.factory.action.ActionFactory;
import io.github.apace100.apoli.power.factory.condition.ConditionFactory;
import io.github.apace100.apoli.registry.ApoliRegistries;
import io.github.apace100.calio.data.SerializableData;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.entity.Entity;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ApoliReg {
    private ApoliReg() {
    }

    public static void action(String var0, Consumer<Entity> var1) {
        Identifier var2 = Powers.id(var0);
        Registry.register(ApoliRegistries.ENTITY_ACTION, var2, new ActionFactory<>(var2, new SerializableData(), (var1x, var2x) -> var1.accept(var2x)));
    }

    public static void condition(String var0, Predicate<Entity> var1) {
        Identifier var2 = Powers.id(var0);
        Registry.register(ApoliRegistries.ENTITY_CONDITION, var2, new ConditionFactory<>(var2, new SerializableData(), (var1x, var2x) -> var1.test(var2x)));
    }
}
