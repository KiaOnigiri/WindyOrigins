package com.windykindy49.windyorigins.bounty;

import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;

public final class BountyKillScope {
    private static final ThreadLocal<BountyKillScope.Context> CURRENT = new ThreadLocal<>();

    private BountyKillScope() {
    }

    public static void open(BountyKillScope.Kind var0, ServerPlayerEntity var1, LivingEntity var2) {
        CURRENT.set(new BountyKillScope.Context(var0, var1, var2, CURRENT.get()));
    }

    public static void close() {
        BountyKillScope.Context var0 = CURRENT.get();
        if (var0 != null) {
            if (var0.outer == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(var0.outer);
            }
        }
    }

    public static BountyKillScope.Context get() {
        return CURRENT.get();
    }

    public static BountyKillScope.Context contractFor(Object var0) {
        BountyKillScope.Context var1 = CURRENT.get();
        return var1 != null && var1.kind == BountyKillScope.Kind.CONTRACT && var1.victim == var0 ? var1 : null;
    }

    public static BountyKillScope.Context unmarkedFor(Object var0) {
        BountyKillScope.Context var1 = CURRENT.get();
        return var1 != null && var1.kind == BountyKillScope.Kind.UNMARKED && var1.victim == var0 ? var1 : null;
    }

    public static final class Context {
        public final BountyKillScope.Kind kind;
        public final ServerPlayerEntity hunter;
        public final LivingEntity victim;
        public final BountyKillScope.Context outer;

        Context(BountyKillScope.Kind var1, ServerPlayerEntity var2, LivingEntity var3, BountyKillScope.Context var4) {
            this.kind = var1;
            this.hunter = var2;
            this.victim = var3;
            this.outer = var4;
        }
    }

    public static enum Kind {
        NONE,
        CONTRACT,
        UNMARKED;
    }
}
