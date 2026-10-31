package de.leon.trycity.mixin;

import de.leon.trycity.morph.ShadowLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.PlayerSensor;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Spieler in der Nähe für Brain-Mobs (anschauen, folgen, angreifen) – im Mob-Verhalten mit
 * den Spielern der Client-Welt. Dieselbe Rechnung wie Vanilla, nur mit {@code Player} statt
 * {@code ServerPlayer} (siehe {@code TemptingSensorMixin}).
 */
// Nach Lithium & Co. (1000): in Methoden, die andere Mods überschreiben, darf nur einhängen, wer danach kommt
@Mixin(value = PlayerSensor.class, priority = 1500)
abstract class PlayerSensorMixin {

    @Shadow
    protected abstract double getFollowDistance(LivingEntity body);

    @Inject(require = 0, method = "doTick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;)V",
            at = @At("HEAD"), cancellable = true)
    private void trycity$imClient(ServerLevel level, LivingEntity body, CallbackInfo ci) {
        if (!(level instanceof ShadowLevel)) return;
        ci.cancel();
        double weite = getFollowDistance(body);
        List<Player> players = new ArrayList<>();
        for (Object eintrag : level.players()) {
            Player player = (Player) eintrag;
            if (EntitySelector.NO_SPECTATORS.test(player) && body.closerThan(player, weite)) players.add(player);
        }
        players.sort(Comparator.comparingDouble(body::distanceToSqr));
        Brain<?> brain = body.getBrain();
        brain.setMemory(MemoryModuleType.NEAREST_PLAYERS, players);
        List<Player> sichtbar = new ArrayList<>(players.stream().filter(p -> Sensor.isEntityTargetable(level, body, p)).toList());
        brain.setMemory(MemoryModuleType.NEAREST_VISIBLE_PLAYER, sichtbar.isEmpty() ? null : sichtbar.getFirst());
        List<Player> angreifbar = sichtbar.stream().filter(p -> Sensor.isEntityAttackable(level, body, p)).toList();
        brain.setMemory(MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYERS, angreifbar);
        brain.setMemory(MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYER, angreifbar.isEmpty() ? null : angreifbar.getFirst());
    }
}
