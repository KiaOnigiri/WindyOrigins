package com.windykindy49.windyorigins.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.windykindy49.windyorigins.host.HostNetworking;
import com.windykindy49.windyorigins.host.NameMatch;
import io.netty.buffer.Unpooled;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

/**
 * Answers Volta's "what is that called?" for the server. The server knows every item only by its id and its
 * English name; the game's translations live on the client. So when the Host names a thing in chat, the
 * server asks, and this side looks the words up in the translations of all ten languages of the mod (and
 * whatever language the game is set to), for vanilla and for every other mod alike.
 */
public final class VoltaNames {
    private static final String[] LANGUAGES = new String[]{"en_us", "ru_ru", "uk_ua", "de_de", "es_es", "fr_fr", "pl_pl", "pt_br", "ja_jp", "zh_cn"};
    private static final int LIMIT = 48;
    private static volatile List<NameMatch.Name> items;
    private static volatile List<NameMatch.Name> blocks;

    private VoltaNames() {
    }

    public static void init() {
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public Identifier getFabricId() {
                return new Identifier("windyorigins", "volta_names");
            }

            @Override
            public void reload(ResourceManager manager) {
                // Another resource pack, another set of names. They are read again at once, in the background,
                // so that the first question does not have to wait for ten language files.
                items = null;
                blocks = null;
                String language = MinecraftClient.getInstance().options.language;
                Util.getMainWorkerExecutor().execute(() -> {
                    try {
                        build(manager, language);
                    } catch (RuntimeException e) {
                        // Then they are read when first asked for.
                    }
                });
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(HostNetworking.ASK, (client, handler, buf, sender) -> {
            int id = buf.readVarInt();
            boolean block = buf.readBoolean();
            String said = buf.readString(256);
            // Off the render thread: the first question reads ten language files.
            Util.getMainWorkerExecutor().execute(() -> {
                List<String> exact = new ArrayList<>();
                List<String> partial = new ArrayList<>();

                try {
                    NameMatch.match(said, table(client, block), exact, partial, LIMIT);
                } catch (RuntimeException e) {
                    exact.clear();
                    partial.clear();
                }

                client.execute(() -> {
                    if (client.getNetworkHandler() == null) {
                        return;
                    }

                    PacketByteBuf out = new PacketByteBuf(Unpooled.buffer());
                    out.writeVarInt(id);
                    out.writeVarInt(exact.size());
                    exact.forEach(out::writeString);
                    out.writeVarInt(partial.size());
                    partial.forEach(out::writeString);
                    ClientPlayNetworking.send(HostNetworking.NAMES, out);
                });
            });
        });
    }

    private static List<NameMatch.Name> table(MinecraftClient client, boolean block) {
        List<NameMatch.Name> known = block ? blocks : items;
        if (known == null) {
            build(client.getResourceManager(), client.options.language);
            known = block ? blocks : items;
        }

        return known;
    }

    private static synchronized void build(ResourceManager manager, String current) {
        if (items != null && blocks != null) {
            return;
        }

        Set<String> codes = new LinkedHashSet<>(List.of(LANGUAGES));
        if (current != null) {
            codes.add(current.toLowerCase(Locale.ROOT));
        }

        List<Map<String, String>> languages = new ArrayList<>();

        for (String code : codes) {
            languages.add(read(manager, code));
        }

        List<NameMatch.Name> itemNames = new ArrayList<>();

        for (Item item : Registries.ITEM) {
            add(itemNames, Registries.ITEM.getId(item).toString(), item.getTranslationKey(), languages);
        }

        List<NameMatch.Name> blockNames = new ArrayList<>();

        for (Block one : Registries.BLOCK) {
            add(blockNames, Registries.BLOCK.getId(one).toString(), one.getTranslationKey(), languages);
        }

        items = itemNames;
        blocks = blockNames;
    }

    private static void add(List<NameMatch.Name> into, String id, String key, List<Map<String, String>> languages) {
        Set<String> seen = new LinkedHashSet<>();

        for (Map<String, String> language : languages) {
            String shown = language.get(key);
            if (shown != null && seen.add(shown)) {
                into.add(NameMatch.name(id, shown));
            }
        }
    }

    /** Every pack's lang/<code>.json of every namespace, the later pack winning as it does in the game. */
    private static Map<String, String> read(ResourceManager manager, String code) {
        Map<String, String> out = new HashMap<>();

        for (String namespace : manager.getAllNamespaces()) {
            Identifier file;

            try {
                file = new Identifier(namespace, "lang/" + code + ".json");
            } catch (RuntimeException e) {
                continue;
            }

            for (Resource resource : manager.getAllResources(file)) {
                try (Reader reader = resource.getReader()) {
                    JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();

                    for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                        if (entry.getValue().isJsonPrimitive()) {
                            out.put(entry.getKey(), entry.getValue().getAsString());
                        }
                    }
                } catch (Exception e) {
                    // A broken language file of some pack is that pack's trouble, not a reason to answer nothing.
                }
            }
        }

        return out;
    }
}
