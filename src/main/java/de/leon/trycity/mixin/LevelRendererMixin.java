package de.leon.trycity.mixin;

import de.leon.trycity.morph.MorphClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
//? if <26.2 {
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//?} else {
/*import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.extract.LevelExtractor;
*///?}

/**
 * In der Welt (Third Person, F5): statt des eigenen Spielers die Morph-Figur
 * zeichnen. In der Ego-Sicht holt Vanilla den eigenen Spieler hier gar nicht ab.
 *
 * <p>Ab 26.2 sammelt {@code LevelExtractor} die Wesen ein. Dort nur der Aufruf in der
 * Wesen-Schleife ({@code extractVisibleEntities}): ab 26.3 holt {@code extractPlayerState}
 * über dieselbe Methode den Spieler-Zustand für die Ego-Hand und verlangt dafür einen
 * Spieler ({@code AvatarRenderState}) – den muss es weiter bekommen.</p>
 */
//? if <26.2 {
@Mixin(LevelRenderer.class)
//?} else
/*@Mixin(LevelExtractor.class)*/
abstract class LevelRendererMixin {

    //? if <26.2 {
    @Inject(method = "extractEntity(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;",
            at = @At("HEAD"), cancellable = true)
    private void trycity$alsMob(Entity entity, float partialTick, CallbackInfoReturnable<EntityRenderState> cir) {
        EntityRenderState figure = trycity$figureState(entity, partialTick);
        if (figure != null) cir.setReturnValue(figure);
    }
    //?} else {
    /*@WrapOperation(method = "extractVisibleEntities(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;Lnet/minecraft/client/DeltaTracker;Lnet/minecraft/client/renderer/state/level/LevelRenderState;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/extract/LevelExtractor;extractEntity(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;"))
    private EntityRenderState trycity$alsMob(LevelExtractor extractor, Entity entity, float partialTick,
                                             Operation<EntityRenderState> original) {
        EntityRenderState figure = trycity$figureState(entity, partialTick);
        return figure != null ? figure : original.call(extractor, entity, partialTick);
    }
    *///?}

    /** Zeichenstand der Figur, wenn das der eigene Spieler mit Morph ist – sonst null. */
    @Unique
    private static @Nullable EntityRenderState trycity$figureState(Entity entity, float partialTick) {
        Entity figure = MorphClient.figureFor(entity);
        if (figure == null) return null;
        EntityRenderState state = Minecraft.getInstance().getEntityRenderDispatcher().extractEntity(figure, partialTick);
        MorphClient.decorate(state);
        return state;
    }
}
