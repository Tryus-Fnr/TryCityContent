package de.leon.trycity.content;

/**
 * Ein Extra der Mod (z. B. „morph“). Wird einmal beim Start des Clients
 * initialisiert und registriert dort selbst, was es braucht: Kanäle
 * ({@link de.leon.trycity.net.TryCityChannel#listen}), Ereignisse, Renderer …
 */
public interface ContentModule {

    /** Kurzer Name für das Log – gleich dem Kanal-Namen, wenn es einen gibt. */
    String id();

    /** Einmal beim Start des Clients. */
    void init();
}
