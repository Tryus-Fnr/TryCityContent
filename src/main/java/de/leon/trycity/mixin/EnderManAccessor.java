package de.leon.trycity.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.monster.EnderMan;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Enderman reißt das Maul auf – es gibt keinen öffentlichen Setter. */
@Mixin(EnderMan.class)
public interface EnderManAccessor {

    @Accessor("DATA_CREEPY")
    static EntityDataAccessor<Boolean> trycity$dataCreepy() {
        throw new AssertionError();
    }
}
