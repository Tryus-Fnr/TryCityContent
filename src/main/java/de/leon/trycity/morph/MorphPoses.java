package de.leon.trycity.morph;

import de.leon.trycity.mixin.BlazeInvoker;
import de.leon.trycity.mixin.EnderManAccessor;
import de.leon.trycity.mixin.LivingEntityAccessor;
import de.leon.trycity.mixin.SpellcasterIllagerAccessor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.animal.fox.Fox;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.panda.Panda;
import net.minecraft.world.entity.animal.polarbear.PolarBear;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.Zoglin;
import net.minecraft.world.entity.monster.creaking.Creaking;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.illager.Evoker;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.monster.illager.SpellcasterIllager;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Posen der eigenen Figur – dieselben, die die anderen sehen (TryusCloudGlobalServer
 * {@code MorphAnimation}), nur direkt vom eigenen Spieler abgelesen statt über
 * Metadaten vom Server:
 * <ul>
 *   <li><b>Gegenstand benutzen</b> (jedes Bild, {@link #useItem}): Bogen spannen,
 *       Armbrust laden, Dreizack, Schild, Essen.</li>
 *   <li><b>Aggressiv</b>: solange Bogen/Armbrust/Dreizack gespannt werden und kurz nach
 *       einem Schlag oder Schuss – Skelett mit Bogen-Pose, Zombie hebt die Arme,
 *       Ertrunkener holt mit dem Dreizack aus.</li>
 *   <li><b>Angriff</b>: Ghast, Lohe, Plagegeist, Enderman, Eisbär, Magier; Golem,
 *       Verwüster, Hoglin, Zoglin, Wärter und Knarz spielen ihren Schlag.</li>
 *   <li><b>Schleichen</b>: Wolf, Katze, Papagei, Fuchs, Panda setzen sich im Stand,
 *       der Fuchs schleicht geduckt.</li>
 *   <li><b>Spezial</b> (Schleichen + F): kommt vom Server ({@code trycity:morph_event}),
 *       der über Abklingzeit und Dauer entscheidet – Creeper bläht sich auf, Ziege
 *       senkt den Kopf, Golem hält eine Blume hin …</li>
 * </ul>
 * Was dabei über die Zeit läuft (Aufblähen, Golem-Schlag, Eisbär stellt sich auf),
 * rechnet das Mob selbst in seinem Tick ({@link MorphClient#tick}).
 *
 * <p>Zeiten wie auf dem Server, damit es für einen selbst genauso aussieht.</p>
 */
final class MorphPoses {

    private static final long MELEE_MS = 1_200L;
    private static final long SHOT_MS = 600L;
    /** Ein Schwung trifft oft mehrere – der Golem schlägt trotzdem nur einmal. */
    private static final long FRESH_MELEE_MS = 250L;
    private static final double STILL_SQ = 0.005 * 0.005;
    private static final int STILL_TICKS = 5;

    // Entity-Ereignisse (handleEntityEvent)
    private static final byte EVENT_ATTACK = 4;
    private static final byte EVENT_WOLF_SHAKE = 8;
    private static final byte EVENT_GOLEM_OFFER_FLOWER = 11;
    private static final byte EVENT_GOLEM_STOP_FLOWER = 34;
    private static final byte EVENT_GOAT_LOWER_HEAD = 58;
    private static final byte EVENT_GOAT_RAISE_HEAD = 59;
    private static final byte EVENT_WARDEN_SONIC_BOOM = 62;

    // Zauber (SpellcasterIllager.IllagerSpell – geschützt, daher die IDs)
    private static final byte SPELL_NONE = 0, SPELL_FANGS = 2, SPELL_WOLOLO = 3, SPELL_DISAPPEAR = 4, SPELL_BLINDNESS = 5;

    // LivingEntity-Flags
    private static final int USING = 1;
    private static final int OFF_HAND = 2;

    private static long meleeAt;
    private static long shotAt;
    private static long specialUntil;
    private static boolean specialRunning;
    private static boolean specialStart;
    private static boolean attack;
    private static int stillTicks;

    private MorphPoses() {}

    /** Neuer Morph oder neuer Server: nichts mitnehmen. */
    static void reset() {
        meleeAt = shotAt = specialUntil = 0L;
        specialRunning = specialStart = attack = false;
        stillTicks = 0;
    }

    /** Der eigene Spieler schlägt ein Wesen. */
    static void melee() {
        long now = System.currentTimeMillis();
        if (now - meleeAt > FRESH_MELEE_MS) attack = true;
        meleeAt = now;
    }

    /** Ein eigenes Geschoss ist in der Welt aufgetaucht (Pfeil, Dreizack, Schneeball …). */
    static void shot() {
        shotAt = System.currentTimeMillis();
    }

    /** Der Server hat die Spezial-Animation gestartet. */
    static void special(long millis) {
        if (millis <= 0) return;
        specialUntil = System.currentTimeMillis() + millis;
        specialStart = true;
    }

    // ── jedes Bild ───────────────────────────────────────────────────────────

    /**
     * „Benutzt gerade“ vom Spieler auf die Figur. Der benutzte Gegenstand muss genau
     * der in der Hand der Figur sein – daran erkennt das Item-Modell, dass es der
     * gespannte Bogen ist.
     */
    static void useItem(LocalPlayer player, LivingEntity mob) {
        LivingEntityAccessor ziel = (LivingEntityAccessor) mob;
        boolean using = player.isUsingItem();
        boolean offHand = using && player.getUsedItemHand() == InteractionHand.OFF_HAND;
        if (mob.isUsingItem() != using) ziel.trycity$setLivingEntityFlag(USING, using);
        if ((mob.getUsedItemHand() == InteractionHand.OFF_HAND) != offHand) {
            ziel.trycity$setLivingEntityFlag(OFF_HAND, offHand);
        }
        ItemStack item = using ? mob.getItemInHand(player.getUsedItemHand()) : ItemStack.EMPTY;
        if (ziel.trycity$useItem() != item) ziel.trycity$setUseItem(item);
        ziel.trycity$setUseItemRemaining(using ? player.getUseItemRemainingTicks() : 0);
    }

    // ── jeder Tick ───────────────────────────────────────────────────────────

    /** Posen aus dem, was der Spieler gerade tut. Einmal je Client-Tick. */
    static void tick(LocalPlayer player, Entity figure) {
        long now = System.currentTimeMillis();
        double dx = player.getX() - player.xo, dz = player.getZ() - player.zo;
        stillTicks = dx * dx + dz * dz < STILL_SQ ? stillTicks + 1 : 0;

        ItemStack using = player.isUsingItem() ? player.getUseItem() : ItemStack.EMPTY;
        boolean usingCrossbow = using.is(Items.CROSSBOW);
        boolean usingRanged = usingCrossbow || using.is(Items.BOW) || using.is(Items.TRIDENT);
        boolean melee = now - meleeAt < MELEE_MS;
        boolean shot = now - shotAt < SHOT_MS;
        boolean special = now < specialUntil;
        boolean sneaking = player.isShiftKeyDown();
        boolean sitting = sneaking && stillTicks >= STILL_TICKS;

        if (figure instanceof Mob mob) {
            mob.setLeftHanded(player.getMainArm() == HumanoidArm.LEFT);
            mob.setAggressive(usingRanged || melee || shot);
        }
        if (figure instanceof Creeper creeper) creeper.setSwellDir(special ? 1 : -1);
        if (figure instanceof Ghast ghast) ghast.setCharging(melee || shot || special);
        if (figure instanceof Blaze blaze) ((BlazeInvoker) blaze).trycity$setCharged(melee || shot || special);
        if (figure instanceof Vex vex) vex.setIsCharging(melee || special);
        if (figure instanceof EnderMan enderman) {
            enderman.getEntityData().set(EnderManAccessor.trycity$dataCreepy(), melee || special);
        }
        if (figure instanceof Pillager pillager) pillager.setChargingCrossbow(usingCrossbow);
        if (figure instanceof Piglin piglin) {
            piglin.setChargingCrossbow(usingCrossbow);
            piglin.setDancing(special);
        }
        if (figure instanceof SpellcasterIllager caster) {
            caster.getEntityData().set(SpellcasterIllagerAccessor.trycity$dataSpell(), spell(caster, melee, special));
        }
        if (figure instanceof PolarBear bear) bear.setStanding(melee || special);
        if (figure instanceof Bat bat) bat.setResting(false);
        if (figure instanceof TamableAnimal tame) tame.setInSittingPose(sitting);
        if (figure instanceof Fox fox) {
            fox.setSitting(sitting);
            fox.setIsCrouching(sneaking && !sitting);
        }
        if (figure instanceof Panda panda) panda.sit(sitting);
        if (figure instanceof Allay allay) allay.setDancing(special);

        if (attack) {
            attack = false;
            if (attacksByEvent(figure)) figure.handleEntityEvent(EVENT_ATTACK);
        }
        if (specialStart) {
            specialStart = false;
            if (figure instanceof Goat) figure.handleEntityEvent(EVENT_GOAT_LOWER_HEAD);
            if (figure instanceof IronGolem) figure.handleEntityEvent(EVENT_GOLEM_OFFER_FLOWER);
            if (figure instanceof Warden) figure.handleEntityEvent(EVENT_WARDEN_SONIC_BOOM);
            if (figure instanceof Wolf) figure.handleEntityEvent(EVENT_WOLF_SHAKE);
        }
        if (specialRunning && !special) {
            if (figure instanceof Goat) figure.handleEntityEvent(EVENT_GOAT_RAISE_HEAD);
            if (figure instanceof IronGolem) figure.handleEntityEvent(EVENT_GOLEM_STOP_FLOWER);
        }
        specialRunning = special;
    }

    /** Magier: Fangzähne beim Schlag, Wololo als Spezial; Illusionist: Blindheit / Verschwinden. */
    private static byte spell(SpellcasterIllager caster, boolean melee, boolean special) {
        boolean evoker = caster instanceof Evoker;
        if (special) return evoker ? SPELL_WOLOLO : SPELL_DISAPPEAR;
        if (melee) return evoker ? SPELL_FANGS : SPELL_BLINDNESS;
        return SPELL_NONE;
    }

    /** Spielen ihren Schlag über Entity-Ereignis 4. */
    private static boolean attacksByEvent(Entity figure) {
        return figure instanceof IronGolem || figure instanceof Ravager || figure instanceof Hoglin
                || figure instanceof Zoglin || figure instanceof Warden || figure instanceof Creaking;
    }
}
