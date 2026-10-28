package de.leon.trycity.morph;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Welche Figur zu welchem Morph gehört.
 *
 * Normale Mobs: aus dem Entity-Typ, als Baby wenn gemeldet ({@link #STANDARD}).
 * Eigene Aussehen des Servers ({@code custom}): über {@link #register}.
 */
public final class MorphLooks {

    /** Ein normales Mob – aus dem Typ gebaut. */
    public static final MorphLook STANDARD = (level, state) -> {
        // Wie das frühere EntityType.byString (ab 26.2 weg)
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.tryParse(state.entity())).orElse(null);
        if (type == null) return null;
        Entity entity = type.create(level, EntitySpawnReason.LOAD);
        if (entity instanceof Mob mob && state.baby()) mob.setBaby(true);
        return entity;
    };

    private static final Map<String, MorphLook> EIGENE = new HashMap<>();

    private MorphLooks() {}

    /** Ein eigenes Aussehen des Servers eintragen, z. B. {@code "tryus:geist"}. */
    public static void register(String custom, MorphLook look) {
        EIGENE.put(custom, look);
    }

    /** Das Aussehen für diesen Zustand – unbekannte eigene Aussehen fallen auf das Mob zurück. */
    static MorphLook lookFor(MorphState state) {
        MorphLook eigenes = state.custom() == null ? null : EIGENE.get(state.custom());
        return eigenes != null ? eigenes : STANDARD;
    }

    static @Nullable Entity create(MorphState state, ClientLevel level) {
        return lookFor(state).create(level, state);
    }
}
