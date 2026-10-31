package de.leon.trycity.mixin;

import de.leon.trycity.morph.MobBehavior;
import de.leon.trycity.morph.MorphClient;
import de.leon.trycity.morph.ShadowLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Der eigene Schritt klingt wie das Mob ({@link MorphClient#stepSound}); im Wasser bleibt das Schwimmen.
 * Mob-Verhalten: greift das Mob einen anderen Spieler an, bleibt es beim Ausholen – Spieler
 * prüfen vor {@code LivingEntity.hurtServer} schon Dinge, die nur der Server hat.
 */
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

    @Inject(require = 0, method = "hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z",
            at = @At("HEAD"), cancellable = true)
    private void trycity$keinSchaden(ServerLevel level, DamageSource source, float damage, CallbackInfoReturnable<Boolean> cir) {
        if (level instanceof ShadowLevel) {
            MobBehavior.attack(source.getEntity());
            cir.setReturnValue(false);
        }
    }
}
