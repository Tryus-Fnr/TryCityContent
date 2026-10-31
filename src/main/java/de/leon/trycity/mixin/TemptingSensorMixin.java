package de.leon.trycity.mixin;

import de.leon.trycity.morph.ShadowLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.TemptingSensor;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Brain-Mobs folgen dem Spieler mit ihrem Futter (Ziege Weizen, Axolotl Tropenfisch-Eimer,
 * Frosch Schleimball …) – im Mob-Verhalten mit den Spielern der Client-Welt. Vanilla
 * filtert sie als {@code ServerPlayer} und würde an Client-Spielern scheitern; hier
 * dieselbe Rechnung mit {@code Player}: nächster Spieler, kein Zuschauer, in Reichweite,
 * Futter in einer Hand, nicht aufgesessen.
 */
// Nach Lithium & Co. (1000): in Methoden, die andere Mods überschreiben, darf nur einhängen, wer danach kommt
@Mixin(value = TemptingSensor.class, priority = 1500)
abstract class TemptingSensorMixin {

    @Shadow @Final private static TargetingConditions TEMPT_TARGETING;

    @Shadow
    private boolean playerHoldingTemptation(PathfinderMob mob, Player player) {
        throw new AssertionError();
    }

    @Inject(require = 0, method = "doTick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/PathfinderMob;)V",
            at = @At("HEAD"), cancellable = true)
    private void trycity$imClient(ServerLevel level, PathfinderMob body, CallbackInfo ci) {
        if (!(level instanceof ShadowLevel)) return;
        ci.cancel();
        Brain<?> brain = body.getBrain();
        TargetingConditions targeting = TEMPT_TARGETING.copy().range((float) body.getAttributeValue(Attributes.TEMPT_RANGE));
        @Nullable Player naechster = null;
        double abstand = Double.MAX_VALUE;
        for (Object eintrag : level.players()) {
            Player player = (Player) eintrag;
            if (!EntitySelector.NO_SPECTATORS.test(player) || !targeting.test(level, body, player)
                    || !playerHoldingTemptation(body, player) || body.hasPassenger(player)) {
                continue;
            }
            double d = body.distanceToSqr(player);
            if (d < abstand) {
                abstand = d;
                naechster = player;
            }
        }
        if (naechster != null) {
            brain.setMemory(MemoryModuleType.TEMPTING_PLAYER, naechster);
        } else {
            brain.eraseMemory(MemoryModuleType.TEMPTING_PLAYER);
        }
    }
}
