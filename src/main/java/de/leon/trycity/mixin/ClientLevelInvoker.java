package de.leon.trycity.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.LevelEntityGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Die Wesen der Client-Welt für die Schatten-Welt des Mob-Verhaltens ({@code ShadowLevel}). */
@Mixin(ClientLevel.class)
public interface ClientLevelInvoker {

    @Invoker("getEntities")
    LevelEntityGetter<Entity> trycity$getEntities();
}
