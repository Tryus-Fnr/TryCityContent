package de.leon.trycity.mixin;

import net.minecraft.world.entity.monster.Blaze;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lohe „geladen“ (brennt beim Angriff) – der Setter ist privat. */
@Mixin(Blaze.class)
public interface BlazeInvoker {

    @Invoker("setCharged")
    void trycity$setCharged(boolean charged);
}
