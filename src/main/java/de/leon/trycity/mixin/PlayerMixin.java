package de.leon.trycity.mixin;

import de.leon.trycity.morph.MorphClient;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Der eigene Schritt klingt wie das Mob ({@link MorphClient#stepSound}); im Wasser bleibt das Schwimmen. */
@Mixin(Player.class)
abstract class PlayerMixin {

    @Inject(method = "playStepSound(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V",
            at = @At("HEAD"), cancellable = true)
    private void trycity$schrittWieMob(BlockPos pos, BlockState state, CallbackInfo ci) {
        if ((Object) this instanceof LocalPlayer player && !player.isInWater()
                && MorphClient.stepSound(player, pos, state)) {
            ci.cancel();
        }
    }
}
