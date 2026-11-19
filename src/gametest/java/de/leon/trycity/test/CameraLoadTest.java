package de.leon.trycity.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.gui.components.debug.DebugScreenEntryStatus;
import net.minecraft.server.packs.repository.PackRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Selbsttest GPU-Last der Freecam-Erkennung ({@code -Pnur=last}): Wie lange dauert ein Bild je
 * Last-Stufe, wenn die Kamera vom Körper weg ist – und kostet die Last bei richtiger Kamera
 * wirklich nichts? Misst die Wanduhr-Zeit je Test-Tick (= ein Bild im Testbetrieb).
 *
 * <p>Marker-Farbe wie {@code MarkerCode}: Rot = 0b01 B LLL T A (B Schwarzbild, LLL Stufe,
 * T Prüflast immer, A Last bei falscher Kamera), Alpha 3 = normal.</p>
 */
public class CameraLoadTest implements FabricClientGameTest {

    private static int farbe(boolean schwarz, int stufe, boolean pruef, boolean last) {
        int r = 0x40 | (schwarz ? 0x20 : 0) | (stufe & 7) << 2 | (pruef ? 2 : 0) | (last ? 1 : 0);
        return 3 << 24 | r << 16 | 0x80 << 8 | 0x10;
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        if (!IrisOutlineTest.dran("last")) return;
        context.runOnClient(client -> {
            PackRepository packs = client.getResourcePackRepository();
            packs.reload();
            List<String> selected = new ArrayList<>(packs.getSelectedIds());
            if (!selected.contains("file/Tryus")) selected.add("file/Tryus");
            packs.setSelected(selected);
        });
        CompletableFuture<Void> reload = context.computeOnClient(Minecraft::reloadResourcePacks);
        context.waitFor(client -> reload.isDone());

        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            //? if <26.2 {
            world.getClientLevel().waitForChunksRender();
            //?} else
            /*world.getConnection().waitForChunksRender();*/
            world.getServer().runCommand("time set noon");
            world.getServer().runCommand("weather clear");
            context.runOnClient(client -> IrisOutlineTest.hideGui(client, true));
            CameraGuardTest.marker(world);
            world.getServer().runCommand("execute at @p run summon armor_stand ~6 ~1 ~3 "
                    + "{Invisible:1b,NoGravity:1b,Marker:1b,Rotation:[150f,10f]}");
            context.waitTicks(30);
            String size = context.computeOnClient(client -> client.getWindow().getWidth() + "x" + client.getWindow().getHeight());
            System.out.println("LAST-TEST: Fenster " + size);

            setze(world, context, farbe(false, 0, false, false));
            double basis = messe(context, "Kamera am Körper, keine Last");
            setze(world, context, farbe(false, 7, false, true));
            messe(context, "Kamera am Körper, Last Stufe 7 an (darf nichts kosten)");
            setze(world, context, farbe(false, 3, true, false));
            messe(context, "Kamera am Körper, Prüflast Stufe 3");

            CameraGuardTest.kamera(context, true);
            setze(world, context, farbe(false, 0, false, false));
            double frei = messe(context, "Freecam, keine Last");
            for (int stufe = 0; stufe <= 7; stufe++) {
                setze(world, context, farbe(false, stufe, false, true));
                messe(context, "Freecam, Stufe " + stufe + " (ohne Schwarzbild)");
            }
            setze(world, context, farbe(true, 5, false, true));
            messe(context, "Freecam, Stufe 5 mit Schwarzbild");
            CameraGuardTest.kamera(context, false);
            System.out.printf("LAST-TEST: Basis %.2f ms, Freecam ohne Last %.2f ms%n", basis, frei);
        }
    }

    private static void setze(TestSingleplayerContext world, ClientGameTestContext context, int argb) {
        world.getServer().runCommand("data merge entity @e[tag=tcg_d,limit=1] {background:" + argb + "}");
        context.waitTicks(10);
    }

    /**
     * GPU-Zeit je Bild über den GPU-Timer des Clients (F3-Eintrag „GPU-Auslastung“): Auslastung in
     * Prozent der Bilddauer (im Testbetrieb ein Tick). Median aus 40 Ticks.
     */
    private static double messe(ClientGameTestContext context, String was) {
        context.runOnClient(client -> client.debugEntries.setStatus(DebugScreenEntries.GPU_UTILIZATION, DebugScreenEntryStatus.ALWAYS_ON));
        double[] werte = new double[40];
        for (int i = 0; i < werte.length; i++) {
            long t0 = System.nanoTime();
            context.waitTicks(1);
            double dauer = (System.nanoTime() - t0) / 1e6;
            werte[i] = context.computeOnClient(Minecraft::getGpuUtilization) / 100.0 * dauer;
        }
        java.util.Arrays.sort(werte);
        double gpu = werte[werte.length / 2];
        System.out.printf("LAST-TEST: %-55s GPU %7.2f ms je Bild%n", was, gpu);
        return gpu;
    }
}
