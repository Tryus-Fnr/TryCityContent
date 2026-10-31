package de.leon.trycity.test;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.InputConstants;
import de.leon.trycity.morph.MobBehavior;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.util.UUID;

/**
 * Selbsttest Mob-Verhalten ({@link MobBehavior}), über die echte Taste (J) und das echte
 * Morph-Paket:
 * <ul>
 *   <li>Wolf: an – läuft und schaut von selbst; Maus bewegt = man steuert selbst, nach 2 s
 *       ohne Eingabe übernimmt der Wolf wieder; aus.</li>
 *   <li>Schaf (Goals, {@code TemptGoal}) und Ziege (Brain, {@code TemptingSensor}): folgen
 *       einem anderen Spieler mit Weizen in der Hand – der steht nur im Client, wie ein
 *       Mitspieler auf dem Server.</li>
 *   <li>Durchlauf über viele Mobs: bei keinem gibt das Mob-Verhalten auf.</li>
 *   <li>Biene: mit Flug-Erlaubnis wie vom Server hebt sie von selbst ab.</li>
 *   <li>Geist: die Taste tut nichts.</li>
 * </ul>
 * Bilder 20–23 in {@code screenshots/}.
 */
public class MobBehaviorTest implements FabricClientGameTest {

    @Override
    public void runTest(ClientGameTestContext context) {
        if (!IrisOutlineTest.dran("mob")) return;
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            //? if <26.2 {
            world.getClientLevel().waitForChunksRender();
            //?} else
            /*world.getConnection().waitForChunksRender();*/
            world.getServer().runCommand("time set noon");
            world.getServer().runCommand("weather clear");
            // Ebene Wiese ohne Hindernisse
            world.getServer().runCommand("execute at @p run fill ~-15 ~-1 ~-15 ~15 ~-1 ~15 minecraft:grass_block");
            world.getServer().runCommand("execute at @p run fill ~-15 ~ ~-15 ~15 ~6 ~15 minecraft:air");
            world.getServer().runCommand("kill @e[type=!player]");
            context.runOnClient(client -> IrisOutlineTest.hideGui(client, true));
            context.waitTicks(10);

            // Wolf: an – er läuft und schaut von selbst
            IrisOutlineTest.morph(world, "{\"v\":1,\"active\":true,\"entity\":\"minecraft:wolf\"}");
            context.waitTicks(5);
            Vec3 start = context.computeOnClient(client -> client.player.position());
            float blick = context.computeOnClient(client -> client.player.getYRot());
            context.getInput().pressKey(InputConstants.KEY_J);
            context.waitTicks(3);
            check(context, true, "Taste schaltet das Mob-Verhalten nicht ein");
            context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            context.waitFor(client -> client.player.position().distanceTo(start) > 1.5
                    || Math.abs(client.player.getYRot() - blick) > 20.0F, 1200);
            context.waitTicks(20);
            IrisOutlineTest.shot(context, "20_mob_wolf_f5");

            // Maus: sofort selbst steuern; nach 2 s Ruhe macht der Wolf weiter. Gedreht wie
            // MouseHandler.turnPlayer – im Testfenster ist die Maus nicht gefangen
            context.runOnClient(client -> client.player.turn(40.0, 0.0));
            context.waitTicks(1);
            check(context, false, "Mausbewegung unterbricht das Mob-Verhalten nicht");
            context.waitTicks(30);
            check(context, false, "Mob-Verhalten übernimmt schon vor 2 s Ruhe");
            context.waitTicks(15);
            check(context, true, "Nach 2 s ohne Eingabe übernimmt das Mob nicht wieder");
            context.getInput().pressKey(InputConstants.KEY_J);
            context.waitTicks(2);
            check(context, false, "Taste schaltet das Mob-Verhalten nicht aus");

            // Schaf: folgt dem Spieler mit Weizen (TemptGoal)
            IrisOutlineTest.morph(world, "{\"v\":1,\"active\":true,\"entity\":\"minecraft:sheep\"}");
            context.waitTicks(5);
            context.runOnClient(client -> mitspieler(client, 7.0));
            context.getInput().pressKey(InputConstants.KEY_J);
            context.waitTicks(3);
            check(context, true, "Mob-Verhalten als Schaf nicht an");
            context.waitFor(client -> abstand(client) < 3.5, 600);
            context.waitTicks(10);
            IrisOutlineTest.shot(context, "21_mob_schaf_weizen");

            // Ziege (Brain): folgt ebenso – bleibt an, nur das Mob wechselt
            IrisOutlineTest.morph(world, "{\"v\":1,\"active\":true,\"entity\":\"minecraft:goat\"}");
            context.runOnClient(client -> mitspieler(client, 8.0));
            context.waitFor(client -> abstand(client) < 3.5, 600);
            context.waitTicks(10);
            IrisOutlineTest.shot(context, "22_mob_ziege_weizen");

            // Viele Mobs nacheinander: das Mob-Verhalten darf bei keinem aufgeben (Log zeigt, was im Client wegfällt)
            for (String mob : DURCHLAUF) {
                IrisOutlineTest.morph(world, "{\"v\":1,\"active\":true,\"entity\":\"minecraft:" + mob + "\"}");
                context.waitTicks(40);
                check(context, true, "Mob-Verhalten gibt auf als " + mob);
            }

            // Flieger: der Server erlaubt das Fliegen (wie MorphAbilities) – die Biene hebt von selbst ab
            world.getServer().runCommand("kill @e[type=!player]");
            fliegen(world, true);
            IrisOutlineTest.morph(world, "{\"v\":1,\"active\":true,\"entity\":\"minecraft:bee\"}");
            context.waitTicks(5);
            double boden = context.computeOnClient(client -> client.player.getY());
            context.waitFor(client -> client.player.getAbilities().flying && client.player.getY() > boden + 1.0, 1200);
            context.waitTicks(10);
            IrisOutlineTest.shot(context, "23_mob_biene_fliegt");
            fliegen(world, false);
            context.getInput().pressKey(InputConstants.KEY_J);
            context.waitTicks(2);

            // Eigene Aussehen haben kein Mob-Verhalten
            IrisOutlineTest.morph(world, "{\"v\":1,\"active\":true,\"entity\":\"minecraft:armor_stand\",\"custom\":\"tryus:geist\"}");
            context.waitTicks(5);
            context.getInput().pressKey(InputConstants.KEY_J);
            context.waitTicks(3);
            check(context, false, "Geist hat Mob-Verhalten bekommen");
            context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
        }
    }

    private static final int MITSPIELER_ID = 1_700_100;

    /** Goal- und Brain-Mobs, Flieger, Wassertiere, Reittiere, Monster. */
    private static final String[] DURCHLAUF = {
            "zombie", "skeleton", "creeper", "spider", "enderman", "slime", "witch", "piglin", "hoglin",
            "iron_golem", "villager", "cow", "pig", "chicken", "rabbit", "fox", "cat", "horse", "llama",
            "axolotl", "frog", "camel", "armadillo", "sniffer", "allay", "bee", "parrot", "bat", "phantom",
            "dolphin", "cod", "turtle", "warden", "breeze", "creaking"};

    /** Ein anderer Spieler mit Weizen in der Hand, {@code weit} Blöcke östlich – nur im Client, wie vom Server gemeldet. */
    private static void mitspieler(Minecraft client, double weit) {
        RemotePlayer alt = client.level.getEntity(MITSPIELER_ID) instanceof RemotePlayer r ? r : null;
        RemotePlayer anderer = alt != null ? alt
                : new RemotePlayer(client.level, new GameProfile(UUID.nameUUIDFromBytes("trycity-test".getBytes()), "Mitspieler"));
        anderer.setId(MITSPIELER_ID);
        anderer.setPos(client.player.getX() + weit, client.player.getY(), client.player.getZ());
        anderer.setYRot(90.0F);
        anderer.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WHEAT));
        if (alt == null) client.level.addEntity(anderer);
    }

    /** Fliegen erlauben wie der Server bei Flieger-Morphs (TryusCloudGlobalServer {@code MorphAbilities}). */
    private static void fliegen(TestSingleplayerContext world, boolean erlaubt) {
        world.getServer().runOnServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
            player.getAbilities().mayfly = erlaubt;
            if (!erlaubt) player.getAbilities().flying = false;
            player.onUpdateAbilities();
        });
    }

    private static double abstand(Minecraft client) {
        return client.level.getEntity(MITSPIELER_ID) instanceof RemotePlayer anderer
                ? client.player.distanceTo(anderer) : Double.MAX_VALUE;
    }

    private static void check(ClientGameTestContext context, boolean steuert, String fehler) {
        if (context.computeOnClient(client -> steuert()) != steuert) throw new AssertionError(fehler);
    }

    /** Steuert das Mob gerade (privates Feld, nur für den Test)? */
    private static boolean steuert() {
        try {
            Field feld = MobBehavior.class.getDeclaredField("steuert");
            feld.setAccessible(true);
            return feld.getBoolean(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
