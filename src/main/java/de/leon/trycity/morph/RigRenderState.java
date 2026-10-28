package de.leon.trycity.morph;

import org.jetbrains.annotations.Nullable;

/** Zusatzfeld am Zeichenstand des Rüstungsständers ({@code ArmorStandRenderStateMixin}): die Teile der Figur. */
public interface RigRenderState {

    @Nullable RigParts trycity$rig();

    void trycity$setRig(@Nullable RigParts parts);
}
