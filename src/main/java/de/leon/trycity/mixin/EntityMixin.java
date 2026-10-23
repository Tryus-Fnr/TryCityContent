package de.leon.trycity.mixin;

import de.leon.trycity.morph.MorphClient;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Schritte als Mob ({@link MorphClient#stepSound}):
 * <ul>
 *   <li>Geräusche der Figur beim Schritt abspielen – Mobs spielen sie mit „alle außer
 *       niemand“, und das hört der Client nie selbst (er wartet auf den Server).</li>
 *   <li>Schritt-Takt des Mobs für den eigenen Spieler (Schildkröte trippelt, Wärter stapft).</li>
 * </ul>
 */
@Mixin(Entity.class)
abstract class EntityMixin {

    @Inject(method = "playSound(Lnet/minecraft/sounds/SoundEvent;FF)V", at = @At("HEAD"), cancellable = true)
    private void trycity$figurKlingt(SoundEvent sound, float volume, float pitch, CallbackInfo ci) {
        if (MorphClient.playFigureSound((Entity) (Object) this, sound, volume, pitch)) ci.cancel();
    }

    @Inject(method = "nextStep()F", at = @At("RETURN"), cancellable = true)
    private void trycity$taktWieMob(CallbackInfoReturnable<Float> cir) {
        float vanilla = cir.getReturnValueF();
        float mob = MorphClient.nextStep((Entity) (Object) this, vanilla);
        if (mob != vanilla) cir.setReturnValue(mob);
    }
}
