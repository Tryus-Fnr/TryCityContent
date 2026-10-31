package de.leon.trycity.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Schritte der Morph-Figur: Geräusch, ob sie beim Laufen überhaupt klingt, und Takt.
 * Fürs Mob-Verhalten: steht das Mob im Wasser oder in Lava?
 */
@Mixin(Entity.class)
public interface EntityInvoker {

    @Invoker("updateFluidInteraction")
    boolean trycity$updateFluidInteraction();

    /** Goals des Gehirns in der Schatten-Welt anlegen – manche merken sich die Welt. */
    @Invoker("setLevel")
    void trycity$setLevel(Level level);

    @Invoker("playStepSound")
    void trycity$playStepSound(BlockPos pos, BlockState state);

    @Invoker("getMovementEmission")
    Entity.MovementEmission trycity$movementEmission();

    @Invoker("nextStep")
    float trycity$nextStep();
}
