package de.leon.trycity.mixin;

import de.leon.trycity.morph.MorphClient;
import net.minecraft.client.player.LocalPlayer;
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
}
