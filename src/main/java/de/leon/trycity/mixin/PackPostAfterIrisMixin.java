package de.leon.trycity.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import de.leon.trycity.TryCityClient;
import de.leon.trycity.shader.IrisCompat;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Claim-Grenzen und Duell-Zone des Servers auch mit Iris-Shaderpack.
 *
 * <p>Beides zeichnet das Resourcepack in der Outline-Kette
 * ({@code post_effect/entity_outline.json}): sie läuft im Bild-Ablauf von
 * {@code renderLevel} mit. Mit Shaderpack schreibt Iris danach – beim
 * {@code popMatrix} am Ende von {@code renderLevel} – sein eigenes Bild über das
 * Hauptbild, und was die Kette hineingemalt hat, ist weg. Das normale Leuchten
 * überlebt nur, weil Vanilla es erst später ({@code doEntityOutline}) darüberlegt.</p>
 *
 * <p>Deshalb: läuft ein Shaderpack, bleibt die Kette im Bild-Ablauf aus und läuft
 * stattdessen ganz am Ende von {@code renderLevel}, nach Iris – auf dessen fertigem
 * Bild, mit derselben Tiefe und demselben Outline-Puffer. Es ist dieselbe Kette aus
 * den aktiven Packs (Server-Pack oder lokal eingeschaltet): die Mod hat keine eigene
 * Kopie, ändert der Server sein Pack, gilt nach dem Neuladen sofort das Neue.
 * Ohne Shaderpack (oder ohne Iris) läuft alles wie bisher.</p>
 */
@Mixin(LevelRenderer.class)
abstract class PackPostAfterIrisMixin {

    /**
     * Der Outline-Puffer selbst. Nicht {@code entityOutlineTarget()}: das liefert den
     * Griff aus dem Bild-Ablauf, und den leert {@code targets.clear()} kurz vor dem Ende.
     */
    @Shadow
    private RenderTarget entityOutlineTarget;

    /** Kette, die in diesem Bild noch nach Iris laufen soll (null = keine). */
    @Unique
    private PostChain trycity$afterIris;

    @WrapOperation(method = "renderLevel(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lorg/joml/Matrix4fc;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;ZLnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/PostChain;addToFrame(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;IILnet/minecraft/client/renderer/PostChain$TargetBundle;)V",
                    ordinal = 0))
    private void trycity$outlineLater(PostChain chain, FrameGraphBuilder frame, int width, int height,
                                      PostChain.TargetBundle targets, Operation<Void> original) {
        // ordinal 0 = die Outline-Kette (die Transparenz-Kette kommt danach).
        if (IrisCompat.shaderPackActive()) {
            trycity$afterIris = chain;
            return;
        }
        original.call(chain, frame, width, height, targets);
    }

    @Inject(method = "renderLevel(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lorg/joml/Matrix4fc;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;ZLnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;)V",
            at = @At("RETURN"))
    private void trycity$outlineAfterIris(GraphicsResourceAllocator allocator, DeltaTracker deltaTracker, boolean renderOutline,
                                          CameraRenderState cameraState, Matrix4fc modelViewMatrix, GpuBufferSlice terrainFog,
                                          Vector4f fogColor, boolean shouldRenderSky, ChunkSectionsToRender chunkSectionsToRender,
                                          CallbackInfo ci) {
        PostChain chain = trycity$afterIris;
        trycity$afterIris = null;
        if (chain == null) return;
        RenderTarget outline = entityOutlineTarget;
        if (outline == null) return;
        try {
            // Wie im Bild-Ablauf von renderLevel: Hauptbild und Outline-Puffer von außen.
            RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
            FrameGraphBuilder frame = new FrameGraphBuilder();
            LevelTargetBundle targets = new LevelTargetBundle();
            targets.main = frame.importExternal("main", main);
            targets.entityOutline = frame.importExternal("entity_outline", outline);
            chain.addToFrame(frame, main.width, main.height, targets);
            frame.execute(allocator);
        } catch (Throwable e) {
            TryCityClient.LOG.warn("[TryCity] Outline-Kette nach Iris fehlgeschlagen", e);
        }
    }
}
