package de.leon.trycity.mixin;

import de.leon.trycity.morph.MobBehavior;
import de.leon.trycity.morph.ShadowLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mob-Verhalten: greift das Mob an, holt man nur aus (Schwung) – getroffen wird nie, weder
 * auf dem Server noch die Kopie im Client.
 */
// Nach Lithium & Co. (1000): in Methoden, die andere Mods überschreiben, darf nur einhängen, wer danach kommt
@Mixin(value = Mob.class, priority = 1500)
abstract class MobMixin {

    @Inject(require = 0, method = "doHurtTarget(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("HEAD"), cancellable = true)
    private void trycity$nurAusholen(ServerLevel level, Entity target, CallbackInfoReturnable<Boolean> cir) {
        if (level instanceof ShadowLevel) {
            MobBehavior.attack((Entity) (Object) this);
            cir.setReturnValue(false);
        }
    }
}
