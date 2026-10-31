package de.leon.trycity.mixin;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Mob-Verhalten: zweimal kurz Springen schaltet das Fliegen um – drückt das Mob die
 * Sprungtaste in kurzen Stößen (Biene steigt), darf das nie als Doppelsprung zählen.
 */
@Mixin(Player.class)
public interface PlayerAccessor {

    @Accessor("jumpTriggerTime")
    void trycity$setJumpTriggerTime(int ticks);
}
