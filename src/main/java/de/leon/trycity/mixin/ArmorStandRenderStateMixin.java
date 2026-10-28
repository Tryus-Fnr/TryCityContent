package de.leon.trycity.mixin;

import de.leon.trycity.morph.RigParts;
import de.leon.trycity.morph.RigRenderState;
import net.minecraft.client.renderer.entity.state.ArmorStandRenderState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Platz für die Teile einer Verwandlung aus Teilen am Zeichenstand des Rüstungsständers. */
@Mixin(ArmorStandRenderState.class)
abstract class ArmorStandRenderStateMixin implements RigRenderState {

    @Unique
    private @Nullable RigParts trycity$rig;

    @Override
    public @Nullable RigParts trycity$rig() {
        return trycity$rig;
    }

    @Override
    public void trycity$setRig(@Nullable RigParts parts) {
        trycity$rig = parts;
    }
}
