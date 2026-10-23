package de.leon.trycity.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import de.leon.trycity.morph.MorphClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Im Inventar (Überleben und Kreativ) steht statt des Spielers die Morph-Figur. Die
 * Drehung zur Maus setzt Vanilla danach auf den Zustand – das passt für jedes Mob.
 * Die Größe wird so angepasst, dass ein Huhn nicht winzig und ein Pferd nicht
 * abgeschnitten ist: das Mob füllt den Rahmen etwa wie ein Spieler.
 */
@Mixin(InventoryScreen.class)
abstract class InventoryScreenMixin {

    /** So hoch ist ein Spieler – die Größe im Inventar ist darauf ausgelegt. */
    private static final float SPIELER = 1.8f;

    // Mit Signatur: es gibt auch die (nicht statische) extractRenderState des ganzen Fensters
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;",
            at = @At("HEAD"), cancellable = true)
    private static void trycity$alsMob(LivingEntity entity, CallbackInfoReturnable<EntityRenderState> cir) {
        Entity figure = MorphClient.figureFor(entity);
        if (figure == null) return;
        EntityRenderState state = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(figure).createRenderState(figure, 1.0F);
        state.shadowPieces.clear();
        state.outlineColor = 0;
        // Kein Namensschild im Inventar – Vanilla zeigt dort auch keins
        state.nameTag = null;
        state.scoreText = null;
        cir.setReturnValue(state);
    }

    @ModifyVariable(method = "extractEntityInInventoryFollowsMouse(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIIIIFFFLnet/minecraft/world/entity/LivingEntity;)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 4)
    private static int trycity$groesse(int size, @Local(argsOnly = true) LivingEntity entity) {
        Entity figure = MorphClient.figureFor(entity);
        if (figure == null) return size;
        EntityDimensions masse = figure.getDimensions(Pose.STANDING);
        float groesste = Math.max(masse.height(), masse.width() * 1.15f);
        if (groesste <= 0.01f) return size;
        return Math.round(Mth.clamp(size * SPIELER / groesste, size * 0.35f, size * 1.6f));
    }
}
