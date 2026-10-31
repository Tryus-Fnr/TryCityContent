package de.leon.trycity.mixin;

import de.leon.trycity.morph.MobBehavior;
import de.leon.trycity.morph.MorphClient;
import de.leon.trycity.morph.ShadowLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hitbox und Augenhöhe des eigenen Spielers mit Morph ({@link MorphClient#dimensions}).
 * Der Client rechnet Bewegung und Kollision selbst – mit diesen Maßen läuft man wie
 * das Mob, ohne je enger zu werden als der Server erlaubt.
 *
 * <p>Mob-Verhalten: Schaden über die Schatten-Welt trifft nie – auch nicht von Mobs, deren
 * Angriff {@code Mob.doHurtTarget} überspringt (Golem, Hoglin …), siehe {@code MobMixin}.</p>
 */
@Mixin(LivingEntity.class)
abstract class LivingEntityMixin {

    @Inject(method = "getDimensions(Lnet/minecraft/world/entity/Pose;)Lnet/minecraft/world/entity/EntityDimensions;",
            at = @At("RETURN"), cancellable = true)
    private void trycity$mobMasse(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        if ((Object) this instanceof LocalPlayer && MorphClient.active()) {
            cir.setReturnValue(MorphClient.dimensions(cir.getReturnValue(), pose));
        }
    }

    @Inject(require = 0, method = "hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z",
            at = @At("HEAD"), cancellable = true)
    private void trycity$keinSchaden(ServerLevel level, DamageSource source, float damage, CallbackInfoReturnable<Boolean> cir) {
        if (level instanceof ShadowLevel) {
            MobBehavior.attack(source.getEntity());
            cir.setReturnValue(false);
        }
    }
}
