package de.leon.trycity.morph;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * Wie ein eigenes Aussehen des Servers ({@code custom}, z. B. {@code tryus:geist})
 * im eigenen Client aussieht. Für normale Mobs braucht es keins – die baut
 * {@link MorphLooks} aus dem Entity-Typ.
 *
 * <p>Neues eigenes Aussehen: Klasse schreiben, die das hier umsetzt, und in
 * {@link MorphModule#init} mit {@link MorphLooks#register} eintragen.</p>
 */
public interface MorphLook {

    /** Die Figur bauen – nur für den eigenen Client, sie kommt nie in die Welt. */
    @Nullable Entity create(ClientLevel level, MorphState state);

    /**
     * Nach jedem Abgleich mit dem Spieler (Position, Drehung, Laufen, Hände) – für
     * eigene Bewegungen, z. B. Arme beim Schlagen.
     */
    default void sync(LocalPlayer player, Entity figure) {}

    /**
     * Trägt die Figur die Rüstung des Spielers? Mobs, die Rüstung zeigen können (Zombie,
     * Skelett, Piglin …), zeigen sie dann; die übrigen ignorieren sie einfach. Aus, wenn
     * ein Rüstungs-Slot schon etwas anderes trägt (Geist: Modell im Helm).
     */
    default boolean showsArmor() {
        return true;
    }

    /** Bleibt die Figur immer unsichtbar (z. B. ein Ständer, der nur ein Modell trägt)? */
    default boolean alwaysInvisible() {
        return false;
    }

    /**
     * Hand in der Ego-Sicht: true = Arm (oder Vorderbein) des Mobs, false = gar keine
     * Hand – nur das gehaltene Item.
     */
    default boolean mobArm() {
        return true;
    }

    /** Einmal je Client-Tick, auch in der Ego-Sicht – für eigene Animationen ({@link RigLook}). */
    default void tick(LocalPlayer player) {}

    /** Der Spieler schlägt zu (Angriffstaste, auch ins Leere und beim Abbauen). */
    default void attack() {}

    /** Spezial vom Server (Schleichen + F), {@code millis} lang. */
    default void special(long millis) {}

    /**
     * Bleiben Hitbox und Augen die des Spielers? Für Aussehen, die der Server nicht
     * verkleinert und deren Modell auf Spieler-Augenhöhe gebaut ist ({@link RigLook}) –
     * sonst nähme {@link MorphClient#dimensions} die Maße der Figur.
     */
    default boolean keepsPlayerSize() {
        return false;
    }
}
