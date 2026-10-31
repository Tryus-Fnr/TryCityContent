package de.leon.trycity.mixin;

import de.leon.trycity.morph.MobBehavior;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mob-Verhalten: Goals, die direkt an ihrer eigenen Welt bauen (Türen öffnen, Eier
 * zertreten), dürfen die Client-Welt nicht ändern – sonst stünden Geister-Blöcke da.
 */
// Nach Lithium & Co. (1000): in Methoden, die andere Mods überschreiben, darf nur einhängen, wer danach kommt
@Mixin(value = ClientLevel.class, priority = 1500)
abstract class ClientLevelMixin {

    @Inject(require = 0, method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("HEAD"), cancellable = true)
    private void trycity$nichtBauen(BlockPos pos, BlockState state, int flags, int recursionLeft,
                                    CallbackInfoReturnable<Boolean> cir) {
        if (MobBehavior.thinking()) cir.setReturnValue(false);
    }
}
