package de.leon.trycity.mixin;

import de.leon.trycity.morph.MobBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Goals holen sich die Server-Welt über {@code getServerLevel} (ein harter Cast). Während das
 * Mob-Verhalten denkt, bekommen sie die Schatten-Welt ({@code ShadowLevel}) – echte
 * Server-Welten (Einzelspieler) bleiben unberührt.
 */
// Nach Lithium & Co. (1000): in Methoden, die andere Mods überschreiben, darf nur einhängen, wer danach kommt
@Mixin(value = Goal.class, priority = 1500)
abstract class GoalMixin {

    @Inject(require = 0, method = "getServerLevel(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/server/level/ServerLevel;",
            at = @At("HEAD"), cancellable = true)
    private static void trycity$schattenFuerWesen(Entity entity, CallbackInfoReturnable<ServerLevel> cir) {
        if (MobBehavior.thinking() && !(entity.level() instanceof ServerLevel)) cir.setReturnValue(MobBehavior.shadow());
    }

    @Inject(require = 0, method = "getServerLevel(Lnet/minecraft/world/level/Level;)Lnet/minecraft/server/level/ServerLevel;",
            at = @At("HEAD"), cancellable = true)
    private static void trycity$schattenFuerWelt(Level level, CallbackInfoReturnable<ServerLevel> cir) {
        if (MobBehavior.thinking() && !(level instanceof ServerLevel)) cir.setReturnValue(MobBehavior.shadow());
    }
}
