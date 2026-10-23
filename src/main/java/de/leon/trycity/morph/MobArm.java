package de.leon.trycity.morph;

import com.mojang.blaze3d.vertex.PoseStack;
import de.leon.trycity.mixin.ModelPartAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Die Hand in der Ego-Sicht als die des Mobs.
 *
 * Vanilla zeichnet dort den rechten bzw. linken Arm des Spieler-Modells mit dem Skin
 * ({@code AvatarRenderer#renderHand}). Hier kommt stattdessen der passende Teil aus dem
 * Modell des Mobs mit dessen Textur: der Arm, und wer keine Arme hat, das Vorderbein
 * (beim Schwein also der Huf). Er wird so hingesetzt wie der Spieler-Arm – alle
 * Verschiebungen davor (Schwingen, Gehen) bleiben vanilla.
 *
 * Ohne passenden Teil oder bei unsichtbaren Figuren (Geist): keine Hand, nur das Item.
 */
public final class MobArm {

    private static final List<String> RECHTS = List.of("right_arm", "right_front_leg", "right_leg", "right_wing");
    private static final List<String> LINKS = List.of("left_arm", "left_front_leg", "left_leg", "left_wing");

    private MobArm() {}

    /**
     * @return true, wenn hier entschieden wurde (Mob-Hand gezeichnet oder bewusst keine) –
     *         false = kein Morph, vanilla zeichnet den Spieler-Arm
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static boolean render(PoseStack pose, SubmitNodeCollector collector, int light, boolean rechts) {
        Entity figure = MorphClient.figureForHand();
        if (figure == null) return false;
        if (!MorphClient.look().mobArm() || figure.isInvisible()) return true;

        EntityRenderer<?, ?> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(figure);
        if (!(renderer instanceof LivingEntityRenderer living)) return true;
        ModelPart arm = find(living.getModel().root(), rechts ? RECHTS : LINKS);
        if (arm == null) return true;

        EntityRenderState state = ((EntityRenderer) renderer).createRenderState(figure, 1.0F);
        if (!(state instanceof LivingEntityRenderState)) return true;
        Identifier textur = living.getTextureLocation((LivingEntityRenderState) state);

        // Wie der Spieler-Arm: Gelenk an der Schulter, leicht nach außen gekippt
        arm.resetPose();
        arm.visible = true;
        arm.x = rechts ? -5f : 5f;
        arm.y = 2f;
        arm.z = 0f;
        arm.xRot = 0f;
        arm.yRot = 0f;
        arm.zRot = rechts ? 0.1f : -0.1f;
        collector.submitModelPart(arm, pose, RenderTypes.entityTranslucent(textur), light, OverlayTexture.NO_OVERLAY, null);
        return true;
    }

    /** Der erste Teil mit einem dieser Namen, irgendwo im Modell. */
    private static @Nullable ModelPart find(ModelPart root, List<String> namen) {
        for (String name : namen) {
            ModelPart treffer = search(root, name);
            if (treffer != null) return treffer;
        }
        return null;
    }

    private static @Nullable ModelPart search(ModelPart part, String name) {
        Map<String, ModelPart> kinder = ((ModelPartAccessor) (Object) part).trycity$children();
        ModelPart direkt = kinder.get(name);
        if (direkt != null) return direkt;
        for (ModelPart kind : kinder.values()) {
            ModelPart tiefer = search(kind, name);
            if (tiefer != null) return tiefer;
        }
        return null;
    }
}
