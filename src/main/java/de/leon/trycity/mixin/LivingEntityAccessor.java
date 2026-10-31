package de.leon.trycity.mixin;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * „Benutzt gerade einen Gegenstand“ vom Spieler auf die Morph-Figur: Bogen spannen,
 * Armbrust laden, essen, Schild hoch. Bei echten Mobs kommt das über die Metadaten –
 * die Figur bekommt es von Hand.
 */
@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {

    @Accessor("useItem")
    ItemStack trycity$useItem();

    @Accessor("useItem")
    void trycity$setUseItem(ItemStack item);

    @Accessor("useItemRemaining")
    void trycity$setUseItemRemaining(int ticks);

    /** 1 = benutzt, 2 = mit der Nebenhand */
    @Invoker("setLivingEntityFlag")
    void trycity$setLivingEntityFlag(int flag, boolean value);

    // Mob-Verhalten: will das Mob springen (JumpControl), und wann traf einen zuletzt etwas
    @Accessor("jumping")
    boolean trycity$jumping();

    @Accessor("lastDamageStamp")
    long trycity$lastDamageStamp();

    @Accessor("lastDamageStamp")
    void trycity$setLastDamageStamp(long gameTime);

    @Accessor("lastDamageSource")
    void trycity$setLastDamageSource(DamageSource source);

    // Mob-Verhalten mit Lithium: Client-Wesen bekommen dort alle dasselbe leere Brain
    @Invoker("makeBrain")
    Brain<?> trycity$makeBrain(Brain.Packed packed);

    @Accessor("brain")
    void trycity$setBrain(Brain<?> brain);

    // Ab 26.3: Schlag-Animation als eigenes Objekt ({@link SwingStateAccessor})
    //? if >=26.3 {
    /*@Accessor("swingState")
    LivingEntity.SwingState trycity$swingState();
    *///?}
}
