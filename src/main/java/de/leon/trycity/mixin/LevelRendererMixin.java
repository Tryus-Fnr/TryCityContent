package de.leon.trycity.mixin;

import de.leon.trycity.morph.MorphClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * In der Welt (Third Person, F5): statt des eigenen Spielers die Morph-Figur
 * zeichnen. In der Ego-Sicht holt Vanilla den eigenen Spieler hier gar nicht ab.
 */
@Mixin(LevelRenderer.class)
abstract class LevelRendererMixin {

    @Inject(method = "extractEntity(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;",
            at = @At("HEAD"), cancellable = true)
    private void trycity$alsMob(Entity entity, float partialTick, CallbackInfoReturnable<EntityRenderState> cir) {
        Entity figure = MorphClient.figureFor(entity);
        if (figure != null) {
            EntityRenderState state = Minecraft.getInstance().getEntityRenderDispatcher().extractEntity(figure, partialTick);
            MorphClient.decorate(state);
            cir.setReturnValue(state);
        }
    }
}
