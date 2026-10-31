package de.leon.trycity.mixin;

import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Mob-Verhalten: Laufen in Stößen darf nie als Doppel-Vorwärts (Sprinten) zählen – sprinten entscheidet das Mob. */
@Mixin(LocalPlayer.class)
public interface LocalPlayerAccessor {

    @Accessor("sprintTriggerTime")
    void trycity$setSprintTriggerTime(int ticks);
}
