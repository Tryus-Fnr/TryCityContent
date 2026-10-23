package de.leon.trycity.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Schritte der Morph-Figur: Geräusch, ob sie beim Laufen überhaupt klingt, und Takt. */
@Mixin(Entity.class)
public interface EntityInvoker {

    @Invoker("playStepSound")
    void trycity$playStepSound(BlockPos pos, BlockState state);

    @Invoker("getMovementEmission")
    Entity.MovementEmission trycity$movementEmission();

    @Invoker("nextStep")
    float trycity$nextStep();
}
