package de.leon.trycity.mixin;

import net.minecraft.client.player.ClientInput;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Laufrichtung des Mob-Verhaltens statt der Tasten ({@code KeyboardInputMixin}). */
@Mixin(ClientInput.class)
public interface ClientInputAccessor {

    @Accessor("moveVector")
    void trycity$setMoveVector(Vec2 moveVector);
}
