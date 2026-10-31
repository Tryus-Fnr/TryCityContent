package de.leon.trycity.mixin;

import de.leon.trycity.morph.MobBehavior;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mob-Verhalten: der Blick des Mobs ändert sich einmal je Tick – jedes Bild dazwischen
 * gleitet die Kamera hin, statt 20-mal in der Sekunde zu springen. Kommt nach der eigenen
 * Maus; hat die sich bewegt, steuert man schon selbst.
 */
// Nach Lithium & Co. (1000): in Methoden, die andere Mods überschreiben, darf nur einhängen, wer danach kommt
@Mixin(value = MouseHandler.class, priority = 1500)
abstract class MouseHandlerMixin {

    @Inject(require = 0, method = "handleAccumulatedMovement()V", at = @At("TAIL"))
    private void trycity$mobBlick(CallbackInfo ci) {
        MobBehavior.frame();
    }
}
