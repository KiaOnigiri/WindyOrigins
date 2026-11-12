package com.windykindy49.windyorigins.common;

import com.windykindy49.windyorigins.dream.DreamRules;
import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.Power;
import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.PowerTypeRegistry;
import io.github.apace100.apoli.power.VariableIntPower;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;

public final class Powers {
    public static final String NS = "windyorigins";

    private Powers() {
    }

    public static Identifier id(String var0) {
        return new Identifier("windyorigins", var0);
    }

    public static boolean has(Entity var0, String var1) {
        if (!(var0 instanceof LivingEntity)) {
            return false;
        } else {
            Identifier var2 = id(var1);
            if (!PowerTypeRegistry.contains(var2)) {
                return false;
            } else if (DreamRules.silenced(var0, var2)) {
                return false;
            } else {
                PowerType var3 = PowerTypeRegistry.get(var2);
                return PowerHolderComponent.KEY.maybeGet(var0).map(var1x -> var1x.hasPower(var3)).orElse(false);
            }
        }
    }

    public static boolean active(Entity var0, String var1) {
        Power var2 = power(var0, var1);
        return var2 != null && var2.isActive();
    }

    public static Power power(Entity var0, String var1) {
        if (!(var0 instanceof LivingEntity)) {
            return null;
        } else {
            Identifier var2 = id(var1);
            if (!PowerTypeRegistry.contains(var2)) {
                return null;
            } else {
                PowerType var3 = PowerTypeRegistry.get(var2);
                return PowerHolderComponent.KEY.maybeGet(var0).map(var1x -> var1x.getPower(var3)).orElse(null);
            }
        }
    }

    public static int resource(Entity var0, String var1, int var2) {
        Power var3 = power(var0, var1);
        return var3 instanceof VariableIntPower ? ((VariableIntPower)var3).getValue() : var2;
    }

    public static int resourceMax(Entity var0, String var1, int var2) {
        Power var3 = power(var0, var1);
        return var3 instanceof VariableIntPower ? ((VariableIntPower)var3).getMax() : var2;
    }

    public static void setResource(Entity var0, String var1, int var2) {
        Power var3 = power(var0, var1);
        if (var3 instanceof VariableIntPower var4) {
            int var5 = var4.getValue();
            var4.setValue(var2);
            if (var4.getValue() != var5 && !var0.getWorld().isClient()) {
                PowerHolderComponent.syncPower(var0, var3.getType());
            }
        }
    }

    public static void addResource(Entity var0, String var1, int var2) {
        setResource(var0, var1, resource(var0, var1, 0) + var2);
    }
}
