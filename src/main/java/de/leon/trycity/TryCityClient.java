package de.leon.trycity;

import de.leon.trycity.content.ContentModule;
import de.leon.trycity.content.ContentModules;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    }
}
