package de.leon.trycity.morph;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

/**
 * Als was der eigene Spieler gerade verwandelt ist – so, wie der Server es meldet
 * (Kanal {@code trycity:morph}):
 * <pre>
 * {"v":1, "active":true, "entity":"minecraft:zombie", "baby":false, "custom":null, "name":true}
 * {"v":1, "active":true, "entity":"minecraft:armor_stand", "custom":"tryus:geist"}
 * {"v":1, "active":false}
 * </pre>
 *
 * @param entity Entity-Typ (Registry-Name)
 * @param baby   als Baby
 * @param custom eigenes Aussehen des Servers (z. B. {@code tryus:geist}), sonst null –
 *               dafür gibt es ein {@link MorphLook} in {@link MorphLooks}
 * @param name   {@code /morph name an}: andere sehen den Spielernamen über dem Mob –
 *               dann zeigt ihn auch die eigene Figur in F5
 */
public record MorphState(String entity, boolean baby, @Nullable String custom, boolean name) {

    /** null = keine Verwandlung (oder unlesbar). */
    static @Nullable MorphState fromJson(JsonObject json) {
        if (!json.has("active") || !json.get("active").getAsBoolean()) return null;
        String entity = text(json, "entity");
        if (entity == null) return null;
        boolean baby = json.has("baby") && json.get("baby").getAsBoolean();
        boolean name = json.has("name") && json.get("name").getAsBoolean();
        return new MorphState(entity, baby, text(json, "custom"), name);
    }

    private static @Nullable String text(JsonObject json, String key) {
        JsonElement value = json.get(key);
        return value == null || value.isJsonNull() ? null : value.getAsString();
    }
}
