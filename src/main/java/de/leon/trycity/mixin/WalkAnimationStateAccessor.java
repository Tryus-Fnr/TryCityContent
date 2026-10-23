package de.leon.trycity.mixin;

import net.minecraft.world.entity.WalkAnimationState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Laufanimation 1:1 vom Spieler auf die Morph-Figur übertragen. */
@Mixin(WalkAnimationState.class)
public interface WalkAnimationStateAccessor {

    @Accessor("speedOld")
    float trycity$speedOld();

    @Accessor("speedOld")
    void trycity$setSpeedOld(float value);

    @Accessor("speed")
    float trycity$speed();

    @Accessor("speed")
    void trycity$setSpeed(float value);

    @Accessor("position")
    float trycity$position();

    @Accessor("position")
    void trycity$setPosition(float value);

    @Accessor("positionScale")
    float trycity$positionScale();

    @Accessor("positionScale")
    void trycity$setPositionScale(float value);
}
