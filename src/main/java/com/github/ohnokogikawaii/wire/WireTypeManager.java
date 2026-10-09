package com.github.ohnokogikawaii.wire;

import com.github.ohnokogikawaii.PowerNetwork;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads and stores the wire type definitions shipped with Power Network.
 *
 * Wire types are currently loaded from the mod JAR resources.
 * They are intentionally separate from the Minecraft Item registry.
 */
public final class WireTypeManager {

    private static final Map<ResourceLocation, WireType> TYPES =
            new ConcurrentHashMap<>();

    private WireTypeManager() {
    }

    public static WireType load(ResourceLocation id) {
        WireType existing = TYPES.get(id);

        if (existing != null) {
            return existing;
        }

        String path = "data/"
                + id.getNamespace()
                + "/wire_types/"
                + id.getPath()
                + ".json";

        try (InputStream stream =
                     PowerNetwork.class
                             .getClassLoader()
                             .getResourceAsStream(path)) {

            if (stream == null) {
                throw new IllegalStateException(
                        "Wire type definition not found: " + path
                );
            }

            try (Reader reader = new InputStreamReader(
                    stream,
                    StandardCharsets.UTF_8
            )) {

                StringBuilder json = new StringBuilder();

                char[] buffer = new char[1024];
                int read;

                while ((read = reader.read(buffer)) != -1) {
                    json.append(buffer, 0, read);
                }

                WireType type = WireType.fromJson(
                        id,
                        json.toString()
                );

                TYPES.put(id, type);

                PowerNetwork.LOGGER.debug(
                        "Loaded wire type {}",
                        id
                );

                return type;
            }

        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to load wire type: " + id,
                    e
            );
        }
    }

    public static WireType get(ResourceLocation id) {
        WireType type = TYPES.get(id);

        if (type != null) {
            return type;
        }

        return load(id);
    }

    public static WireType get(String id) {
        return get(ResourceLocation.parse(id));
    }

    public static void clear() {
        TYPES.clear();
    }
}