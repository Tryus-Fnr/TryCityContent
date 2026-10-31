package de.leon.trycity.mixin;

import de.leon.trycity.morph.MobBehavior;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mob-Verhalten: man selbst ist das Mob – also nie sein Ziel, nie der Spieler, den es
 * anschaut, vor dem es flieht oder dem es folgt. Goals, die direkt in der Client-Welt
 * suchen, finden einen sonst an der eigenen Stelle.
 */
// Nach Lithium & Co. (1000): in Methoden, die andere Mods überschreiben, darf nur einhängen, wer danach kommt
@Mixin(value = TargetingConditions.class, priority = 1500)
abstract class TargetingConditionsMixin {

    @Inject(require = 0, method = "test(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/LivingEntity;)Z",
            at = @At("HEAD"), cancellable = true)
    private void trycity$nichtSichSelbst(ServerLevel level, @Nullable LivingEntity targeter, LivingEntity target,
                                         CallbackInfoReturnable<Boolean> cir) {
        if (MobBehavior.thinking() && target == Minecraft.getInstance().player) cir.setReturnValue(false);
    }
}
