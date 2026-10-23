package de.leon.trycity.mixin;

import de.leon.trycity.morph.MorphClient;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Kamera-Abstand in F5: Vanilla nimmt 4 Blöcke mal Scale des Spielers – und den
 * schrumpft der Server beim Morph auf Mob-Höhe. Bei der Schildkröte stünde die
 * Kamera dann keinen Block hinter den Augen, mitten im Panzer der Figur, die ja in
 * echter Größe gezeichnet wird. Mit Morph zählt deshalb auch der Abstand, den das
 * Mob selbst hätte ({@link MorphClient#cameraDistance}).
 */
@Mixin(Camera.class)
abstract class CameraMixin {

    @Shadow private Entity entity;

    @ModifyArg(method = "alignWithEntity(F)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Camera;getMaxZoom(F)F"))
    private float trycity$abstandWieMob(float distance) {
        return MorphClient.cameraDistance(entity, distance);
    }
}
