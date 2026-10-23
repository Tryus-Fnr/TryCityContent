package de.leon.trycity.mixin;

import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/** Teile eines Modells nach Namen finden (Arm/Vorderbein für die Ego-Hand). */
@Mixin(ModelPart.class)
public interface ModelPartAccessor {

    @Accessor("children")
    Map<String, ModelPart> trycity$children();
}
