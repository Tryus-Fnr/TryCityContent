package de.leon.trycity.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.monster.illager.SpellcasterIllager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Zauber der Magier/Illusionisten – der Typ {@code IllagerSpell} ist geschützt, also
 * direkt die ID in den Eintrag (der Client liest nur sie): 0 keiner, 2 Fangzähne,
 * 3 Wololo, 4 Verschwinden, 5 Blindheit.
 */
@Mixin(SpellcasterIllager.class)
public interface SpellcasterIllagerAccessor {

    @Accessor("DATA_SPELL_CASTING_ID")
    static EntityDataAccessor<Byte> trycity$dataSpell() {
        throw new AssertionError();
    }
}
