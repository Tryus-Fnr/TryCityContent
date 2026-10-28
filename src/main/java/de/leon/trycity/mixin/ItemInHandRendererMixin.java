package de.leon.trycity.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import de.leon.trycity.morph.MobArm;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
//? if <26.3 {
import net.minecraft.client.renderer.ItemInHandRenderer;
//?} else
/*import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;*/

/**
 * Ego-Sicht: die Hand unten wird zur Hand (oder Pfote, Huf …) des Mobs
 * ({@link MobArm}). Alle Verschiebungen vorher bleiben vanilla – nur der Arm selbst
 * wird getauscht. Gilt für die leere Hand und für die Hände beim Kartenhalten.
 *
 * <p>Bis 26.2 zeichnen {@code renderPlayerArm} und {@code renderMapHand} den Arm selbst;
 * ab 26.3 heißt die Klasse {@code FirstPersonHandsAndItemsRenderer} und beide gehen über
 * {@code renderPlayerHand}.</p>
 */
//? if <26.3 {
@Mixin(ItemInHandRenderer.class)
//?} else
/*@Mixin(FirstPersonHandsAndItemsRenderer.class)*/
abstract class ItemInHandRendererMixin {

    //? if <26.3 {
    private static final String ARM = "renderPlayerArm(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IFFLnet/minecraft/world/entity/HumanoidArm;)V";
    private static final String KARTE = "renderMapHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/world/entity/HumanoidArm;)V";
    //?} else
    /*private static final String HAND = "renderPlayerHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/world/entity/HumanoidArm;Lnet/minecraft/client/renderer/state/level/PlayerRenderState;)V";*/

    //? if <26.3 {
    @WrapOperation(method = {ARM, KARTE}, at = @At(value = "INVOKE",
    //?} else
    /*@WrapOperation(method = HAND, at = @At(value = "INVOKE",*/
            target = "Lnet/minecraft/client/renderer/entity/player/AvatarRenderer;renderRightHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;Z)V"))
    private void trycity$rechts(AvatarRenderer<?> renderer, PoseStack pose, SubmitNodeCollector collector, int light,
                                Identifier skin, boolean sleeve, Operation<Void> original) {
        if (!MobArm.render(pose, collector, light, true)) original.call(renderer, pose, collector, light, skin, sleeve);
    }

    //? if <26.3 {
    @WrapOperation(method = {ARM, KARTE}, at = @At(value = "INVOKE",
    //?} else
    /*@WrapOperation(method = HAND, at = @At(value = "INVOKE",*/
            target = "Lnet/minecraft/client/renderer/entity/player/AvatarRenderer;renderLeftHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;Z)V"))
    private void trycity$links(AvatarRenderer<?> renderer, PoseStack pose, SubmitNodeCollector collector, int light,
                               Identifier skin, boolean sleeve, Operation<Void> original) {
        if (!MobArm.render(pose, collector, light, false)) original.call(renderer, pose, collector, light, skin, sleeve);
    }
}
