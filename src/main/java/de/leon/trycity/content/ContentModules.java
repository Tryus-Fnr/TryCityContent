package de.leon.trycity.content;

import de.leon.trycity.morph.MorphModule;

import java.util.List;

/**
 * Alle Extras der Mod. Neues Extra = neue Zeile hier.
 */
public final class ContentModules {

    public static final List<ContentModule> ALL = List.of(
            new MorphModule()
    );

    private ContentModules() {}
}
