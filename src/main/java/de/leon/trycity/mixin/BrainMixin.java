package de.leon.trycity.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import de.leon.trycity.morph.MobBehavior;
import de.leon.trycity.morph.ShadowLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.sensing.Sensor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Brain-Mobs im Mob-Verhalten (Schatten-Welt): jeder Sensor und jedes Verhalten einzeln
 * abgesichert. Was im Client nicht geht (Dorf, POI, Raids …), fällt für sich weg – sonst
 * bräche eine Ausnahme den ganzen Brain-Tick ab. Echte Server-Welten laufen unverändert.
 */
// Nach Lithium & Co. (1000): in Methoden, die andere Mods überschreiben, darf nur einhängen, wer danach kommt
@Mixin(value = Brain.class, priority = 1500)
abstract class BrainMixin {

    @WrapOperation(require = 0, method = "tickSensors(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/sensing/Sensor;tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;)V"))
    private void trycity$sensor(Sensor<?> sensor, ServerLevel level, LivingEntity body, Operation<Void> original) {
        if (!(level instanceof ShadowLevel)) {
            original.call(sensor, level, body);
        } else if (MobBehavior.usable(sensor)) {
            try {
                original.call(sensor, level, body);
            } catch (RuntimeException | LinkageError e) {
                MobBehavior.broken(sensor, e);
            }
        }
    }

    @WrapOperation(require = 0, method = "startEachNonRunningBehavior(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/behavior/BehaviorControl;tryStart(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;J)Z"))
    private boolean trycity$start(BehaviorControl<?> behavior, ServerLevel level, LivingEntity body, long time,
                                  Operation<Boolean> original) {
        if (!(level instanceof ShadowLevel)) return original.call(behavior, level, body, time);
        if (!MobBehavior.usable(behavior)) return false;
        try {
            return original.call(behavior, level, body, time);
        } catch (RuntimeException | LinkageError e) {
            MobBehavior.broken(behavior, e);
            return false;
        }
    }

    @WrapOperation(require = 0, method = "tickEachRunningBehavior(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/behavior/BehaviorControl;tickOrStop(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;J)V"))
    private void trycity$weiter(BehaviorControl<?> behavior, ServerLevel level, LivingEntity body, long time,
                                Operation<Void> original) {
        if (!(level instanceof ShadowLevel)) {
            original.call(behavior, level, body, time);
        } else if (MobBehavior.usable(behavior)) {
            try {
                original.call(behavior, level, body, time);
            } catch (RuntimeException | LinkageError e) {
                MobBehavior.broken(behavior, e);
            }
        }
    }
}
