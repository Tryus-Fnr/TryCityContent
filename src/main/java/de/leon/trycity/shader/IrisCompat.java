package de.leon.trycity.shader;

import de.leon.trycity.TryCityClient;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Method;

/**
 * Läuft gerade ein Iris-Shaderpack? Über die öffentliche Iris-API, aber per
 * Reflexion – die Mod hängt nicht von Iris ab und läuft ohne genauso.
 *
 * <p>Warum das zählt: Iris schreibt am Ende jedes Bildes sein eigenes Bild über
 * das Hauptbild. Was die Outline-Kette des Resourcepacks vorher hineingemalt
 * hat (Claim-Grenzen, Duell-Zone), ist dann weg – siehe
 * {@code mixin.PackPostAfterIrisMixin}.</p>
 */
public final class IrisCompat {

    private static boolean looked;
    private static Object api;
    private static Method inUse;

    private IrisCompat() {}

    public static boolean shaderPackActive() {
        if (!looked) {
            looked = true;
            if (FabricLoader.getInstance().isModLoaded("iris")) {
                try {
                    Class<?> type = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                    api = type.getMethod("getInstance").invoke(null);
                    inUse = type.getMethod("isShaderPackInUse");
                    TryCityClient.LOG.info("[TryCity] Iris gefunden – Pack-Shader laufen mit Shaderpack nach Iris");
                } catch (ReflectiveOperationException | LinkageError e) {
                    TryCityClient.LOG.warn("[TryCity] Iris-API nicht erreichbar, Pack-Shader bleiben wie sie sind", e);
                    api = null;
                }
            }
        }
        if (api == null) return false;
        try {
            return (Boolean) inUse.invoke(api);
        } catch (ReflectiveOperationException | RuntimeException e) {
            // Iris hat sich geändert: lieber nichts tun als jedes Bild abstürzen.
            TryCityClient.LOG.warn("[TryCity] Iris-API fehlgeschlagen, Pack-Shader bleiben wie sie sind", e);
            api = null;
            return false;
        }
    }
}
