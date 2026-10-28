package de.leon.trycity.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import de.leon.trycity.morph.MorphClient;
import de.leon.trycity.morph.RigParts;
import de.leon.trycity.morph.RigRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ArmorStandRenderer;
import net.minecraft.client.renderer.entity.state.ArmorStandRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Verwandlung aus Teilen (TrippelT): die eigene Figur ist ein unsichtbarer Rüstungsständer,
 * an seinem Zeichenstand hängen die Teile ({@link MorphClient#rigParts}). Beim Vorbereiten
 * kommen sie dazu, beim Abgeben werden sie gezeichnet – in der Welt (F5) wie im Inventar.
 * Alle anderen Rüstungsständer bekommen null und bleiben, wie sie sind.
 */
@Mixin(ArmorStandRenderer.class)
abstract class ArmorStandRendererMixin {

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/decoration/ArmorStand;Lnet/minecraft/client/renderer/entity/state/ArmorStandRenderState;F)V",
            at = @At("TAIL"))
    private void trycity$teile(ArmorStand stand, ArmorStandRenderState state, float partialTick, CallbackInfo ci) {
        ((RigRenderState) state).trycity$setRig(MorphClient.rigParts(stand, partialTick));
    }

    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/ArmorStandRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("TAIL"))
    private void trycity$teileZeichnen(ArmorStandRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                                       CameraRenderState camera, CallbackInfo ci) {
        RigParts parts = ((RigRenderState) state).trycity$rig();
        if (parts != null) parts.submit(state, poseStack, collector);
    }
}
