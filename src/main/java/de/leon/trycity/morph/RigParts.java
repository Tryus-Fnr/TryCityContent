package de.leon.trycity.morph;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix4f;

/**
 * Die Teile einer Verwandlung aus Teilen ({@link RigLook}) für ein Bild: je Teil das
 * aufgelöste Item-Modell und seine Transformation im Modell-Raum (Blöcke ab den Füßen,
 * vorn +Z) – genau das, was beim Zuschauer ein ItemDisplay mit derselben Transformation
 * zeigt. Hängt am Zeichenstand des unsichtbaren Rüstungsständers
 * ({@code ArmorStandRendererMixin}).
 */
public record RigParts(ItemStackRenderState[] items, Matrix4f[] transforms) {

    /**
     * Abgeben wie ein ItemDisplay ({@code ItemDisplayRenderer}): Transformation, dann 180°
     * um Y, dann das Item. Vorher in Blickrichtung drehen – Körper plus Kopf, also wohin
     * der Spieler schaut (wie auf dem Server). {@code poseStack} steht am Fußpunkt.
     */
    public void submit(LivingEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector) {
        float heading = state.bodyRot + state.yRot;
        // Als Matrix: mulPose(Quaternion) gibt es ab 26.3 nicht mehr, mulPose(Matrix4fc) überall
        Matrix4f turn = new Matrix4f().rotationY((float) Math.toRadians(-heading));
        Matrix4f pose = new Matrix4f();
        for (int i = 0; i < items.length; i++) {
            if (items[i].isEmpty()) continue;
            pose.set(turn).mul(transforms[i]).rotateY((float) Math.PI);
            poseStack.pushPose();
            poseStack.mulPose(pose);
            items[i].submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
            poseStack.popPose();
        }
    }
}
