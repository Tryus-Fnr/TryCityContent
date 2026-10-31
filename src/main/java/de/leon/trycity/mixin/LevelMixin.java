package de.leon.trycity.mixin;

import de.leon.trycity.morph.MobBehavior;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Mob-Verhalten: auch Abbauen (mit Partikeln und Geräusch) bleibt in der Client-Welt aus. */
// Nach Lithium & Co. (1000): in Methoden, die andere Mods überschreiben, darf nur einhängen, wer danach kommt
@Mixin(value = Level.class, priority = 1500)
abstract class LevelMixin {

    @Inject(require = 0, method = "destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;I)Z",
            at = @At("HEAD"), cancellable = true)
    private void trycity$nichtAbbauen(BlockPos pos, boolean dropResources, @Nullable Entity breaker, int recursionLeft,
                                      CallbackInfoReturnable<Boolean> cir) {
        if (MobBehavior.thinking() && ((Level) (Object) this).isClientSide()) cir.setReturnValue(false);
    }
}
