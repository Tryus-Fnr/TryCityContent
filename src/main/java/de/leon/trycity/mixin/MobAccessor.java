package de.leon.trycity.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Das Denken eines Mobs von außen anstoßen ({@code MobBehavior}) – Schritt für Schritt wie
 * {@code Mob.serverAiStep}, das selbst nur in einer Server-Welt läuft.
 */
@Mixin(Mob.class)
public interface MobAccessor {

    @Accessor("goalSelector")
    GoalSelector trycity$goalSelector();

    @Accessor("targetSelector")
    GoalSelector trycity$targetSelector();

    /** Der Konstruktor trägt die Goals nur in einer Server-Welt ein. */
    @Invoker("registerGoals")
    void trycity$registerGoals();

    /** Brain-Mobs (Axolotl, Ziege, Frosch …) ticken hier ihr Brain. */
    @Invoker("customServerAiStep")
    void trycity$customServerAiStep(ServerLevel level);

    /** Körper dreht sich nach (BodyRotationControl), wie in {@code LivingEntity.tick}. */
    @Invoker("tickHeadTurn")
    void trycity$tickHeadTurn(float yBodyRotT);
}
