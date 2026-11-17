package com.windykindy49.windyorigins.host;

import com.windykindy49.windyorigins.WindyOrigins;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.world.GameRules;

/**
 * The record of a fight between Volta and a player, tick by tick, for whoever wants to see afterwards how it was
 * won or lost: where each stood, what each held up, whose arm was ready, what she had decided to do. Nothing is
 * written unless the game rule windyDuelLog is on. One file a fight, in logs/windyorigins of the server, the
 * newest twenty kept.
 */
public final class DuelLog {
    private static final boolean FORCED = Boolean.getBoolean("windy.duellog");
    private static final int KEPT = Integer.getInteger("windy.duellogs", 20);
    private static final Map<UUID, Session> OPEN = new HashMap<>();
    private static GameRules.Key<GameRules.BooleanRule> rule;

    private DuelLog() {
    }

    private static final class Session {
        final StringBuilder text = new StringBuilder(1 << 16);
        UUID foe;
        final String hostName;
        final String foeName;
        final String stamp;
        long last;
        int ticks;
        float hostHealth;
        float foeHealth;
        float hostArm;
        float foeArm;
        float taken;
        float dealt;
        final java.util.Set<UUID> met = new java.util.HashSet<>();

        Session(ServerPlayerEntity host, PlayerEntity foe) {
            this.foe = foe.getUuid();
            this.met.add(this.foe);
            this.hostName = host.getGameProfile().getName();
            this.foeName = foe.getGameProfile().getName();
            this.stamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            this.hostHealth = host.getHealth() + host.getAbsorptionAmount();
            this.foeHealth = foe.getHealth() + foe.getAbsorptionAmount();
            this.hostArm = 1.0F;
            this.foeArm = 1.0F;
        }
    }

    /** The game rule, where the loader has rules for a mod to add; without them only the start-up switch turns the record on. */
    public static void init() {
        try {
            rule = net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry
                .register("windyDuelLog", GameRules.Category.PLAYER, net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory.createBooleanRule(false));
        } catch (Throwable problem) {
            rule = null;
            WindyOrigins.LOGGER.warn("Windy Origins: no game rule for the record of fights here ({}); -Dwindy.duellog=true turns it on instead.", problem.toString());
        }
    }

    private static boolean on(MinecraftServer server) {
        return FORCED || rule != null && server.getGameRules().getBoolean(rule);
    }

    /** One tick of a fight, with what she has just decided. */
    static void tick(ServerPlayerEntity host, Pilot.Run run) {
        MinecraftServer server = host.getServer();
        if (server == null || !on(server)) {
            return;
        }

        Entity other = host.getWorld().getEntityById(run.duelFoe);
        if (!(other instanceof PlayerEntity foe)) {
            return;
        }

        Session s = OPEN.get(host.getUuid());
        if (s != null && !s.foe.equals(foe.getUuid())) {
            // With two on her she goes from one to the other and back: it is one fight, and one file.
            s.foe = foe.getUuid();
            s.foeHealth = foe.getHealth() + foe.getAbsorptionAmount();
            s.foeArm = foe.getAttackCooldownProgress(0.0F);
            if (s.met.add(foe.getUuid())) {
                s.text.append("# another in it, ").append(foe.getGameProfile().getName()).append(": ").append(kit(foe)).append('\n');
            }

            s.text.append("# from here the columns under \"his\" are ").append(foe.getGameProfile().getName()).append("'s\n");
        }

        if (s == null) {
            s = new Session(host, foe);
            OPEN.put(host.getUuid(), s);
            s.text.append("# Windy Origins fight record. Volta has ").append(s.hostName).append("'s body; the opponent is ").append(s.foeName).append(".\n");
            s.text.append("# mod ").append(FabricLoader.getInstance().getModContainer("windyorigins").map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("?"))
                .append(", started ").append(s.stamp).append(", heat ").append(HostManager.heat(host)).append('\n');
            s.text.append("# her: ").append(kit(host)).append('\n');
            s.text.append("# him: ").append(kit(foe)).append('\n');
            s.text.append("# flags: 1 sprint, 2 sneak, 4 jump, 8 use (shield, food), 64 blow from the ground, 512 back, 8192 press in, 16384 back off, 65536 jump into the blow, 131072 hold, 262144 feint, 1048576 shield to the middle of two\n");
            s.text.append("t;her x;y;z;yaw;pitch;hp;arm;use;block;ground;sprint;hand;off;his x;y;z;yaw;pitch;hp;arm;use;block;ground;sprint;hand;off;apart;his blow in;flags;what happened\n");
        }

        float hostHealth = host.getHealth() + host.getAbsorptionAmount();
        float foeHealth = foe.getHealth() + foe.getAbsorptionAmount();
        float hostArm = host.getAttackCooldownProgress(0.0F);
        float foeArm = foe.getAttackCooldownProgress(0.0F);
        StringBuilder what = new StringBuilder();
        if (hostHealth < s.hostHealth - 0.01F) {
            what.append("she is hit for ").append(num(s.hostHealth - hostHealth)).append(' ');
            s.taken += s.hostHealth - hostHealth;
        }

        if (foeHealth < s.foeHealth - 0.01F) {
            what.append("he is hit for ").append(num(s.foeHealth - foeHealth)).append(' ');
            s.dealt += s.foeHealth - foeHealth;
        }

        if (hostArm < s.hostArm - 0.3F) {
            what.append("she swings ");
        }

        if (foeArm < s.foeArm - 0.3F) {
            what.append("he swings ");
        }

        if (run.second != null) {
            // The other one on her, when there is one: who, how far, his arm, his shield, and how she stands to him.
            double to = Math.toDegrees(Math.atan2(run.second.getZ() - host.getZ(), run.second.getX() - host.getX())) - 90.0;
            what.append("other ").append(run.second.getName().getString()).append(" at ").append(num(host.distanceTo(run.second))).append(" off her eyes by ")
                .append(Math.round(Math.abs(net.minecraft.util.math.MathHelper.wrapDegrees(to - (double)host.getYaw())))).append(" arm ")
                .append(run.second instanceof PlayerEntity two ? num(two.getAttackCooldownProgress(0.0F)) : "-").append(run.second.isBlocking() ? " blocking " : " ");
        }

        s.hostHealth = hostHealth;
        s.foeHealth = foeHealth;
        s.hostArm = hostArm;
        s.foeArm = foeArm;
        s.last = host.getWorld().getTime();
        s.ticks++;
        s.text.append(run.ticks).append(';');
        body(s.text, host, hostHealth, hostArm);
        body(s.text, foe, foeHealth, foeArm);
        s.text.append(num(host.distanceTo(foe))).append(';').append(run.strikeIn).append(';').append(run.flags).append(';').append(what.toString().trim()).append('\n');
        if (s.text.length() > 4_000_000) {
            close(server, host.getUuid(), "too long to go on writing");
        }
    }

    private static void body(StringBuilder out, PlayerEntity who, float health, float arm) {
        out.append(num(who.getX())).append(';').append(num(who.getY())).append(';').append(num(who.getZ())).append(';')
            .append(Math.round(net.minecraft.util.math.MathHelper.wrapDegrees(who.getYaw()))).append(';').append(Math.round(who.getPitch())).append(';')
            .append(num(health)).append(';').append(num(arm)).append(';')
            .append(who.isUsingItem() ? 1 : 0).append(';').append(who.isBlocking() ? 1 : 0).append(';').append(who.isOnGround() ? 1 : 0).append(';').append(who.isSprinting() ? 1 : 0).append(';')
            .append(name(who.getMainHandStack())).append(';').append(name(who.getOffHandStack())).append(';');
    }

    private static String num(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String name(ItemStack stack) {
        return stack.isEmpty() ? "-" : Registries.ITEM.getId(stack.getItem()).getPath();
    }

    /** What one of them went into the fight with: health, armour, and what is on the belt. */
    private static String kit(PlayerEntity who) {
        StringBuilder out = new StringBuilder();
        out.append("health ").append(num(who.getHealth())).append(" of ").append(num(who.getMaxHealth())).append(", armour ").append(who.getArmor()).append(", wearing");
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            out.append(' ').append(name(who.getEquippedStack(slot)));
        }

        out.append(", belt");
        for (int i = 0; i < 9; i++) {
            ItemStack stack = who.getInventory().getStack(i);
            out.append(' ').append(name(stack));
            if (stack.getCount() > 1) {
                out.append('x').append(stack.getCount());
            }
        }

        out.append(", off hand ").append(name(who.getOffHandStack()));
        return out.toString();
    }

    /** A fight nobody has struck a blow in for five seconds is over; so is one whose Host has gone. */
    public static void sweep(MinecraftServer server) {
        if (OPEN.isEmpty()) {
            return;
        }

        for (UUID id : new ArrayList<>(OPEN.keySet())) {
            Session s = OPEN.get(id);
            ServerPlayerEntity host = server.getPlayerManager().getPlayer(id);
            if (host == null) {
                close(server, id, "she is gone from the server");
            } else if (Math.abs(host.getWorld().getTime() - s.last) > 100L) {
                close(server, id, !host.isAlive() ? "she is dead" : "the fight is over");
            }
        }
    }

    /** She has let the body go, for whatever reason. */
    static void end(ServerPlayerEntity host, String why) {
        if (host.getServer() != null && OPEN.containsKey(host.getUuid())) {
            close(host.getServer(), host.getUuid(), why == null ? "she let the body go" : "she let the body go: " + why);
        }
    }

    public static void stopping(MinecraftServer server) {
        for (UUID id : new ArrayList<>(OPEN.keySet())) {
            close(server, id, "the server is stopping");
        }
    }

    private static void close(MinecraftServer server, UUID id, String why) {
        Session s = OPEN.remove(id);
        if (s == null || s.ticks < 20) {
            return;
        }

        ServerPlayerEntity host = server.getPlayerManager().getPlayer(id);
        ServerPlayerEntity foe = server.getPlayerManager().getPlayer(s.foe);
        s.text.append("# ended after ").append(s.ticks).append(" ticks: ").append(why).append(". She dealt ").append(num(s.dealt)).append(" and took ").append(num(s.taken))
            .append("; at the end she had ").append(host == null ? "?" : num(host.getHealth())).append(" and he had ").append(foe == null ? "?" : num(foe.getHealth())).append(".\n");
        try {
            Path dir = FabricLoader.getInstance().getGameDir().resolve("logs").resolve("windyorigins");
            Files.createDirectories(dir);
            String file = "duel-" + s.stamp + "-" + safe(s.hostName) + "-vs-" + safe(s.foeName) + ".log";
            Files.write(dir.resolve(file), s.text.toString().getBytes(StandardCharsets.UTF_8));
            List<Path> old = new ArrayList<>();
            try (java.util.stream.Stream<Path> all = Files.list(dir)) {
                all.filter(path -> path.getFileName().toString().startsWith("duel-")).forEach(old::add);
            }

            old.sort(java.util.Comparator.comparing(path -> path.getFileName().toString()));
            for (int i = 0; i < old.size() - KEPT; i++) {
                Files.deleteIfExists(old.get(i));
            }

            WindyOrigins.LOGGER.info("Windy Origins: fight recorded in logs/windyorigins/{}", file);
            if (host != null) {
                host.sendMessage(Text.translatable("message.windyorigins.duel_log", "logs/windyorigins/" + file), false);
            }
        } catch (Exception problem) {
            WindyOrigins.LOGGER.warn("Windy Origins: the record of a fight could not be written", problem);
        }
    }

    private static String safe(String name) {
        return name.replaceAll("[^A-Za-z0-9_-]", "_");
    }
}
