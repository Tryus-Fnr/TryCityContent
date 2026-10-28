package de.leon.trycity.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
//? if >=26.3
/*import org.spongepowered.asm.mixin.gen.Accessor;*/

/**
 * Schlag-Animation auf die Morph-Figur übertragen. Ab 26.3 steckt sie in
 * {@code LivingEntity.SwingState} (vorher waren {@code attackAnim}, {@code swinging} …
 * öffentliche Felder am LivingEntity). Vor 26.3 gibt es die Klasse nicht – dann hängt
 * das leere Interface einfach am LivingEntity, damit die Mixin-Liste überall gleich ist.
 */
//? if >=26.3 {
/*@Mixin(LivingEntity.SwingState.class)
public interface SwingStateAccessor {

    @Accessor("currentSwing")
    LivingEntity.SwingDescription trycity$currentSwing();

    @Accessor("currentSwing")
    void trycity$setCurrentSwing(LivingEntity.SwingDescription swing);

    @Accessor("ticks")
    int trycity$ticks();

    @Accessor("ticks")
    void trycity$setTicks(int ticks);

    @Accessor("oldAnimation")
    float trycity$oldAnimation();

    @Accessor("oldAnimation")
    void trycity$setOldAnimation(float value);

    @Accessor("animation")
    float trycity$animation();

    @Accessor("animation")
    void trycity$setAnimation(float value);
}
*///?} else {
@Mixin(LivingEntity.class)
public interface SwingStateAccessor {}
//?}
