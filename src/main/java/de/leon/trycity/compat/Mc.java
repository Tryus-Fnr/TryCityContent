package de.leon.trycity.compat;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

/**
 * Was zwischen den Minecraft-Versionen umgezogen ist – an einer Stelle, damit der übrige
 * Code für 26.1.2, 26.2 und 26.3 gleich bleibt. Ab 26.2 hängt der offene Bildschirm an
 * {@code gui} und das Hauptbild am {@code GameRenderer}.
 */
public final class Mc {

    private Mc() {}

    public static @Nullable Screen screen() {
        //? if <26.2 {
        return Minecraft.getInstance().screen;
        //?} else
        /*return Minecraft.getInstance().gui.screen();*/
    }

    public static RenderTarget mainTarget() {
        //? if <26.2 {
        return Minecraft.getInstance().getMainRenderTarget();
        //?} else
        /*return Minecraft.getInstance().gameRenderer.mainRenderTarget();*/
    }
}
