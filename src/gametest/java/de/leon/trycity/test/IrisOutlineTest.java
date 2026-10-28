package de.leon.trycity.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import de.leon.trycity.compat.Mc;
import de.leon.trycity.net.TryCityChannel;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.repository.PackRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Selbsttest: Claim-Grenze aus dem Tryus-Pack mit und ohne Iris-Shaderpack.
 *
 * <p>Welt anlegen, Tryus-Pack ein, Kamera nach Osten, davor eine Claim-Wand wie vom
 * Server (Kamera-Marker + ein Wand-Marker, gleiche Modelle und Glow-Farben wie
 * {@code ClaimBorderShader}) und ein leuchtendes Schwein. Bilder ohne und mit
 * Shaderpack nach {@code versions/<v>/build/run/clientGameTest/screenshots}.</p>
 *
 * <p>Danach der Morph: Zombie in F5, als Hand in der Ego-Sicht, im Inventar, und der
 * Geist – das Paket kommt wie vom Server über den Kanal {@code trycity:morph}.</p>
 */
public class IrisOutlineTest implements FabricClientGameTest {

    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> {
            PackRepository packs = client.getResourcePackRepository();
            packs.reload();
            List<String> selected = new ArrayList<>(packs.getSelectedIds());
            // 26.3 versteht das #moj_import der Pack-Shader (Text, Claim-Grenze) nicht – das Pack
            // lässt sich dort nicht laden. Dann nur Mod-Teile: Outline nach Iris am leuchtenden Schwein, Morph.
            //? if <26.3
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
            world.getServer().runCommand("tp @p ~ ~ ~ -90 8");
            context.waitTicks(10);

            BlockPos at = context.computeOnClient(client -> client.player.blockPosition());
            int chunkX = at.getX() >> 4;
            int chunkZ = at.getZ() >> 4;
            // Wand: Ebene x = (Chunk + 2) * 16 vor der Kamera, sechs Chunks lang (claim_border.glsl).
            int line = chunkX + 2;
            int start = chunkZ - 3;
            int word = 1 | ((line & 63) << 4) | ((start & 127) << 10) | (6 << 17);
            world.getServer().runCommand(marker("frame", 1));
            world.getServer().runCommand(marker("wall_00", word));
            world.getServer().runCommand("summon pig ~5 ~ ~2 {Glowing:1b,NoAI:1b}");
            context.runOnClient(client -> hideGui(client, true));
            context.waitTicks(40);
            shot(context, "1_ohne_shader");

            context.runOnClient(client -> setShaders(true));
            context.waitTicks(200);
            shot(context, "2_mit_shader");

            // Duell-Zone wie StormZoneShader: Kamera-Marker mit Rahmenwort 0 + storm_0..7.
            world.getServer().runCommand("kill @e[type=item_display]");
            double x = context.computeOnClient(client -> client.player.getX());
            double z = context.computeOnClient(client -> client.player.getZ());
            stormZone(world, x + 10, z, 30);   // drinnen, Wand 40 Blöcke voraus
            context.waitTicks(40);
            shot(context, "3_zone_drinnen_mit_shader");
            world.getServer().runCommand("kill @e[type=item_display]");
            stormZone(world, x - 40, z, 20);   // draußen im Sturm, Zone hinter der Kamera
            context.waitTicks(40);
            shot(context, "4_im_sturm_mit_shader");

            context.runOnClient(client -> setShaders(false));
            context.waitTicks(40);
            shot(context, "5_im_sturm_ohne_shader");

            // Morph über den echten Paket-Weg (trycity:morph) – Figur in F5, Mob-Hand, Inventar, Geist
            world.getServer().runCommand("kill @e[type=!player]");
            morph(world, "{\"v\":1,\"active\":true,\"entity\":\"minecraft:zombie\",\"name\":true}");
            context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
            context.waitTicks(20);
            shot(context, "6_morph_zombie_f5");
            context.runOnClient(client -> {
                client.options.setCameraType(CameraType.FIRST_PERSON);
                hideGui(client, false);
            });
            context.waitTicks(20);
            shot(context, "7_morph_zombie_hand");
            context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
            context.waitTicks(20);
            shot(context, "8_morph_zombie_inventar");
            context.setScreen(() -> null);
            morph(world, "{\"v\":1,\"active\":true,\"entity\":\"minecraft:armor_stand\",\"custom\":\"tryus:geist\"}");
            context.runOnClient(client -> {
                client.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                hideGui(client, true);
            });
            context.waitTicks(20);
            shot(context, "9_morph_geist_f5");
        }
    }

    /** HUD/Hand aus (F1) – ab 26.2 hängt das am Hud und lässt sich nur umschalten. */
    private static void hideGui(Minecraft client, boolean hide) {
        //? if <26.2 {
        client.options.hideGui = hide;
        //?} else
        /*if (client.gui.hud.isHidden() != hide) client.gui.hud.toggle();*/
    }

    /** Wie der Server ({@code TryCityModChannel.send}): JSON im Kanal trycity:morph an den Spieler. */
    private static void morph(TestSingleplayerContext world, String json) {
        world.getServer().runOnServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
            CustomPacketPayload.Type<TryCityChannel.Nachricht> type =
                    new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("trycity", "morph"));
            ServerPlayNetworking.send(player, new TryCityChannel.Nachricht(type, json));
        });
    }

    /** Stehende Zone um (x, z) mit Radius r - Wörter wie StormZoneShader.update. */
    private static void stormZone(TestSingleplayerContext world, double x, double z, double r) {
        int cx = (int) Math.floorMod(Math.round(x * 8.0), 1L << 24);
        int cz = (int) Math.floorMod(Math.round(z * 8.0), 1L << 24);
        int radius = (int) Math.round(r * 8.0);
        int[] words = {cx, cz, cx, cz, radius, radius, 1 << 15, 0};
        world.getServer().runCommand(marker("frame", 0));
        for (int i = 0; i < words.length; i++) {
            world.getServer().runCommand(marker("storm_" + i, words[i]));
        }
    }

    /**
     * Bild wie F2 aus dem zuletzt normal gezeichneten Bild. Das Bildschirmfoto des
     * Testsystems zeichnet selbst ein Bild und bringt Sodium zum Absturz ("Global
     * terrain uniforms have not been updated").
     */
    private static void shot(ClientGameTestContext context, String name) {
        context.runOnClient(client -> Screenshot.grab(client.gameDirectory, name + ".png",
                Mc.mainTarget(), 1, message -> {}));
        context.waitTicks(10);
    }

    /** Marker wie ClaimBorderShader: Papier mit Item-Modell, leuchtend, Glow-Farbe = Datenwort. */
    private static String marker(String model, int glow) {
        return "summon item_display ~ ~4 ~ {item:{id:\"minecraft:paper\",count:1,components:{"
                + "\"minecraft:item_model\":\"smpglobal:claim_border/" + model + "\"}},"
                + "Glowing:1b,glow_color_override:" + glow + ",width:0f,height:0f,view_range:32f,"
                + "brightness:{sky:15,block:15}}";
    }

    /** Iris-Shader an/aus über die öffentliche API (per Reflexion – Iris ist nur zur Laufzeit da). */
    private static void setShaders(boolean on) {
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object instance = api.getMethod("getInstance").invoke(null);
            Object config = api.getMethod("getConfig").invoke(instance);
            Class.forName("net.irisshaders.iris.api.v0.IrisApiConfig")
                    .getMethod("setShadersEnabledAndApply", boolean.class).invoke(config, on);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Iris-API nicht erreichbar", e);
        }
    }
}
