package de.leon.trycity.mixin;

import de.leon.trycity.morph.MobBehavior;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mob-Verhalten: nach dem Einlesen der Tasten die Eingabe des Mobs eintragen – alles danach
 * (Sprinten, Springen, Schwimmen, Fliegen) ist der ganz normale Spieler.
 */
// Nach Lithium & Co. (1000): in Methoden, die andere Mods überschreiben, darf nur einhängen, wer danach kommt
@Mixin(value = KeyboardInput.class, priority = 1500)
abstract class KeyboardInputMixin {

    @Inject(require = 0, method = "tick()V", at = @At("RETURN"))
    private void trycity$mobVerhalten(CallbackInfo ci) {
        MobBehavior.input((ClientInput) (Object) this);
    }
}
