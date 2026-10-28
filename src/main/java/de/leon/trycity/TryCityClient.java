package de.leon.trycity;

import de.leon.trycity.content.ContentModule;
import de.leon.trycity.content.ContentModules;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.MixinEnvironment;

/**
 * TryCity Content – Extras, die der Server dem Client per Paket ankündigt.
 *
 * Jedes Extra ist ein {@link ContentModule} und steht in {@link ContentModules#ALL};
 * hier werden sie nur der Reihe nach gestartet. Neues Extra: Modul schreiben, dort
 * eintragen, fertig (siehe README.md).
 */
public final class TryCityClient implements ClientModInitializer {

    public static final String MOD_ID = "trycity";
    public static final Logger LOG = LoggerFactory.getLogger("TryCity");

    @Override
    public void onInitializeClient() {
        for (ContentModule module : ContentModules.ALL) {
            module.init();
            LOG.info("[TryCity] {} bereit", module.id());
        }
        // Nur im Dev-Client/Selbsttest: alle Mixin-Ziele sofort laden, damit ein falsches Ziel
        // in einer der Minecraft-Versionen beim Start auffällt und nicht erst beim Spielen
        if (Boolean.getBoolean("trycity.audit")) {
            MixinEnvironment.getCurrentEnvironment().audit();
            LOG.info("[TryCity] Mixin-Audit fertig");
        }
    }
}
