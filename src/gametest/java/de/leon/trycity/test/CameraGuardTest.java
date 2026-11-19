package de.leon.trycity.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import de.leon.trycity.compat.Mc;
import net.minecraft.client.Screenshot;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.decoration.ArmorStand;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Selbsttest Kamera-Schutz ({@code -Pnur=kamera}): Tryus-Pack, Marker wie TryusAntiXray
 * ({@code camera.CameraGuard}) – Interaction (Höhe 464) als Passagier auf dem Spieler, darauf ein
 * Text-Display mit Marker-Farbe (normal, Größe 1, F5-Abstand 4), Translation zurück aufs Auge.
 *
 * <p>Erwartet: Ich-Perspektive, F5 hinten und vorne hell; Kamera auf einem Rüstungsständer
 * daneben („Freecam“) schwarz; zurück hell; Spieler in einem Steinwürfel, dessen Abschnitte der
 * Client von draußen nicht zeichnet, Kamera draußen ebenfalls schwarz (Marker über der Bauhöhe).
 * Bilder {@code k*.png} in {@code screenshots/}.</p>
 */
public class CameraGuardTest implements FabricClientGameTest {

    /** MarkerCode.argb(NORMAL, 1.0, 4.0) = 0x037C8010. */
    private static final int MARKER = 0x037C8010;
    /** MarkerCode.lift(384) für die Oberwelt. */
    private static final float LIFT = 464f;

    private final List<String> fehler = new ArrayList<>();

    @Override
    public void runTest(ClientGameTestContext context) {
        if (!IrisOutlineTest.dran("kamera")) return;
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
            world.getServer().runCommand("tp @p ~ ~ ~ -90 10");
            context.runOnClient(client -> IrisOutlineTest.hideGui(client, true));
            context.waitTicks(20);
            pruefe(context, "k0_ohne_marker", false);
            marker(world);
            context.waitTicks(30);
            lage(context);
            // Pause (Alpha 2): Marker darf nie etwas tun
            pruefe(context, "k0_pause", false);
            world.getServer().runCommand("data merge entity @e[tag=tcg_d,limit=1] {background:" + MARKER + "}");
            context.waitTicks(5);

            pruefe(context, "k1_ich", false);
            context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            context.waitTicks(10);
            pruefe(context, "k2_f5_hinten", false);
            context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
            context.waitTicks(10);
            pruefe(context, "k3_f5_vorne", false);
            context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));

            // „Freecam“: Kamera auf einen Rüstungsständer 6 Blöcke daneben, Körper bleibt stehen
            world.getServer().runCommand("execute at @p run summon armor_stand ~6 ~1 ~3 "
                    + "{Invisible:1b,NoGravity:1b,Marker:1b,Rotation:[150f,10f],Tags:[\"tcg_cam\"]}");
            context.waitTicks(5);
            kamera(context, true);
            context.waitTicks(10);
            pruefe(context, "k4_freecam", true);
            kamera(context, false);
            context.waitTicks(10);
            pruefe(context, "k5_zurueck", false);

            // Weit weg: Kamera 400 Blöcke vom Körper (weiter als die Sichtweite). Dort baut der Client
            // den Abschnitt des Körpers nicht mehr, ein Marker auf Kopfhöhe würde nie gezeichnet –
            // darum steht er über der Bauhöhe. Die Kamera ist ein Ständer nur im Client.
            context.runOnClient(client -> {
                ArmorStand far = new ArmorStand(client.level, client.player.getX() + 400, client.player.getY() + 20,
                        client.player.getZ());
                far.setId(987654);
                far.setYRot(90f);
                far.setXRot(10f);
                far.setInvisible(true);
                client.level.addEntity(far);
                client.setCameraEntity(far);
            });
            context.waitTicks(40);
            //? if <26.3 {
            boolean gebaut = context.computeOnClient(client -> client.levelRenderer.isSectionCompiledAndVisible(client.player.blockPosition()));
            //?} else
            /*boolean gebaut = context.computeOnClient(client -> client.levelRenderer.isSectionCompiledAndVisible(client.player.blockPosition(), 0L));*/
            System.out.println("KAMERA-TEST: Abschnitt des Körpers aus 400 Blöcken gebaut/sichtbar: " + gebaut);
            pruefe(context, "k6_weit_weg", true);
            kamera(context, false);
            context.waitTicks(20);
            pruefe(context, "k7_zurueck", false);

            // Schleichen: die Augenhöhe gleitet (1,62 → 1,27), der Marker springt sofort mit dem
            // Kasten – jedes Bild des Übergangs muss hell bleiben (normales Fenster)
            context.runOnClient(client -> client.options.keyShift.setDown(true));
            serie(context, "k8_hinknien", 6, false);
            context.runOnClient(client -> client.options.keyShift.setDown(false));
            serie(context, "k8_aufstehen", 6, false);

            // Kriechen (gleiche Haltung wie Schwimmen/Gleiten): Block auf Kopfhöhe. Übergang bis
            // 1,2 Blöcke – der Server schaltet hier auf „weit“ (niedrige Decke daneben), wie hier.
            world.getServer().runCommand("data merge entity @e[tag=tcg_d,limit=1] {background:"
                    + (MARKER & 0x00FFFFFF | 0x04000000) + "}");
            context.waitTicks(5);
            world.getServer().runCommand("execute at @p run fill ~-1 ~1 ~-1 ~1 ~1 ~1 minecraft:stone");
            serie(context, "k9_kriechen", 6, false);
            world.getServer().runCommand("execute at @p run fill ~-1 ~1 ~-1 ~1 ~1 ~1 minecraft:air");
            serie(context, "k9_aufstehen", 6, false);
            // Schwimmen: unter Wasser, Sprint + vorwärts → Haltung SWIMMING. Der Server schaltet im
            // Wasser auf „weit“ (schon vor dem Losschwimmen), hier ist es noch weit.
            world.getServer().runCommand("execute at @p run fill ~-6 ~ ~-6 ~6 ~2 ~6 minecraft:water");
            context.waitTicks(10);
            context.runOnClient(client -> {
                client.options.keySprint.setDown(true);
                client.options.keyUp.setDown(true);
            });
            serie(context, "k10_losschwimmen", 8, false);
            context.waitTicks(10);
            pruefe(context, "k10_schwimmen", false);
            context.runOnClient(client -> {
                client.options.keySprint.setDown(false);
                client.options.keyUp.setDown(false);
            });
            serie(context, "k10_anhalten", 8, false);
            world.getServer().runCommand("execute at @p run fill ~-8 ~-1 ~-8 ~8 ~3 ~8 minecraft:air replace minecraft:water");
            context.waitTicks(20);

            // Danach wieder normal (draußen an Land)
            world.getServer().runCommand("data merge entity @e[tag=tcg_d,limit=1] {background:" + MARKER + "}");
            context.waitTicks(10);
            pruefe(context, "k9_normal_danach", false);

            // Sitzen (Lore, Boot, Pferd, Roller – alles Passagier): Einsteigen, Ich, F5, Aussteigen.
            // Aussteigen setzt den Spieler per Teleport um, der Marker rückt einen Tick später nach
            // (ohne Pause zwei schwarze Bilder). Das Plugin pausiert dabei – hier genauso.
            world.getServer().runCommand("execute at @p run summon minecart ~ ~ ~ {Tags:[\"tcg_sitz\"]}");
            context.waitTicks(5);
            world.getServer().runCommand("ride @p mount @e[tag=tcg_sitz,limit=1]");
            serie(context, "k11_einsteigen", 6, false);
            context.waitTicks(10);
            pruefe(context, "k11_sitzen", false);
            context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            context.waitTicks(10);
            pruefe(context, "k11_sitzen_f5", false);
            context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
            context.waitTicks(5);
            // data merge geht erst am Tick-Ende raus, der Teleport sofort – also vorher schicken
            // (das Plugin schickt die Pause im Absteige-Event direkt, vor dem Teleport)
            world.getServer().runCommand("data merge entity @e[tag=tcg_d,limit=1] {background:"
                    + (MARKER & 0x00FFFFFF | 0x02000000) + "}");
            context.waitTicks(2);
            world.getServer().runCommand("ride @p dismount");
            serie(context, "k11_aussteigen", 6, false);
            world.getServer().runCommand("data merge entity @e[tag=tcg_d,limit=1] {background:" + MARKER + "}");
            context.waitTicks(10);
            pruefe(context, "k11_danach", false);
            // und die Freecam greift danach wieder
            world.getServer().runCommand("execute at @p run summon armor_stand ~-5 ~2 ~4 "
                    + "{Invisible:1b,NoGravity:1b,Marker:1b,Rotation:[200f,10f]}");
            context.waitTicks(5);
            kamera(context, true);
            context.waitTicks(10);
            pruefe(context, "k12_freecam_danach", true);
            kamera(context, false);
        }
        if (!fehler.isEmpty()) throw new AssertionError("Kamera-Schutz: " + String.join("; ", fehler));
        System.out.println("KAMERA-TEST: alles wie erwartet");
    }

    /** Marker wie TryusAntiXray, nur als echte Wesen im Einzelspieler. */
    static void marker(TestSingleplayerContext world) {
        world.getServer().runCommand("execute at @p run summon interaction ~ ~ ~ "
                + "{width:0f,height:" + LIFT + "f,Tags:[\"tcg_i\"]}");
        // Wie das Plugin: erst in Pause (Alpha 2) setzen, scharf erst wenn er am Körper hängt –
        // sonst ist das Bild einen Tick lang schwarz, solange er noch an den Füßen steht
        world.getServer().runCommand("execute at @p run summon text_display ~ ~ ~ {text:\" \",background:"
                + (MARKER & 0x00FFFFFF | 0x02000000) + ",default_background:0b,see_through:0b,billboard:\"fixed\",view_range:100f,width:0f,height:0f,"
                + "transformation:{translation:[0f," + (-LIFT - 0.18f) + "f,0f],left_rotation:[0f,0f,0f,1f],"
                + "scale:[0.001f,0.001f,0.001f],right_rotation:[0f,0f,0f,1f]},Tags:[\"tcg_d\"]}");
        world.getServer().runCommand("ride @e[tag=tcg_d,limit=1] mount @e[tag=tcg_i,limit=1]");
        // Auf einen Spieler setzt der Server nichts (startRiding lehnt Spieler als Reittier ab) –
        // das Plugin schickt das Passagier-Paket nur an den Spieler selbst, hier genauso.
        world.getServer().runOnServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
            for (Entity e : player.level().getAllEntities()) {
                if (!(e instanceof Interaction)) continue;
                FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                buf.writeVarInt(player.getId());
                buf.writeVarIntArray(new int[] {e.getId()});
                player.connection.send(ClientboundSetPassengersPacket.STREAM_CODEC.decode(buf));
                buf.release();
            }
        });
    }

    /** Wo stehen Marker, Interaction und Kamera im Client? */
    private static void lage(ClientGameTestContext context) {
        context.runOnClient(client -> {
            System.out.println("KAMERA-TEST: Spieler " + client.player.position() + " Augen " + client.player.getEyeHeight());
            for (Entity e : client.level.entitiesForRendering()) {
                if (e instanceof Interaction || e instanceof Display.TextDisplay) {
                    System.out.println("KAMERA-TEST: " + e.getClass().getSimpleName() + " id " + e.getId() + " bei "
                            + e.position() + " reitet auf " + (e.getVehicle() == null ? "nichts"
                            : e.getVehicle().getClass().getSimpleName() + " " + e.getVehicle().getId()));
                }
            }
        });
    }

    static void kamera(ClientGameTestContext context, boolean frei) {
        context.runOnClient(client -> {
            if (!frei) {
                client.setCameraEntity(client.player);
                return;
            }
            for (Entity e : client.level.entitiesForRendering()) {
                if (e instanceof ArmorStand) {
                    client.setCameraEntity(e);
                    return;
                }
            }
            throw new IllegalStateException("kein Rüstungsständer für die Kamera");
        });
    }

    /** Bilder in jedem der nächsten Ticks (Übergänge), danach alle prüfen. */
    private void serie(ClientGameTestContext context, String name, int bilder, boolean schwarzErwartet) {
        List<String> namen = new ArrayList<>();
        for (int i = 0; i < bilder; i++) {
            String n = name + "_" + i;
            namen.add(n);
            context.computeOnClient(client -> new File(client.gameDirectory, "screenshots/" + n + ".png")).delete();
            context.runOnClient(client -> Screenshot.grab(client.gameDirectory, n + ".png", Mc.mainTarget(), 1, message -> {}));
            context.waitTicks(1);
        }
        context.waitTicks(10);
        for (String n : namen) bewerte(context, n, schwarzErwartet);
    }

    /** Bild machen und prüfen, ob es (fast) ganz schwarz ist. */
    private void pruefe(ClientGameTestContext context, String name, boolean schwarzErwartet) {
        context.computeOnClient(client -> new File(client.gameDirectory, "screenshots/" + name + ".png")).delete();
        IrisOutlineTest.shot(context, name);
        bewerte(context, name, schwarzErwartet);
    }

    private void bewerte(ClientGameTestContext context, String name, boolean schwarzErwartet) {
        File file = context.computeOnClient(client -> new File(client.gameDirectory, "screenshots/" + name + ".png"));
        for (int i = 0; i < 40 && !file.exists(); i++) context.waitTicks(2);
        context.waitTicks(4);
        double hell;
        try {
            BufferedImage img = ImageIO.read(file);
            double sum = 0;
            int n = 0;
            for (int y = 0; y < img.getHeight(); y += 4) {
                for (int x = 0; x < img.getWidth(); x += 4) {
                    int rgb = img.getRGB(x, y);
                    sum += (0.2126 * (rgb >> 16 & 0xFF) + 0.7152 * (rgb >> 8 & 0xFF) + 0.0722 * (rgb & 0xFF)) / 255.0;
                    n++;
                }
            }
            hell = sum / n;
        } catch (Exception e) {
            fehler.add(name + ": Bild nicht lesbar (" + e + ")");
            return;
        }
        // Das Schwarzbild ist exakt 0; in einem Block stecken (Kriechen) ist dunkel, aber nicht 0
        boolean schwarz = hell < 0.005;
        System.out.printf("KAMERA-TEST: %s → %s (Helligkeit %.3f, erwartet %s)%n", name,
                schwarz ? "schwarz" : "hell", hell, schwarzErwartet ? "schwarz" : "hell");
        if (schwarz != schwarzErwartet) {
            fehler.add(name + " ist " + (schwarz ? "schwarz" : "hell") + String.format(" (%.3f)", hell));
        }
    }
}
