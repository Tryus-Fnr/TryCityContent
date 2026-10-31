package de.leon.trycity.morph;

import com.mojang.blaze3d.platform.InputConstants;
import de.leon.trycity.TryCityClient;
import de.leon.trycity.compat.Mc;
import de.leon.trycity.mixin.ClientInputAccessor;
import de.leon.trycity.mixin.EntityInvoker;
import de.leon.trycity.mixin.LivingEntityAccessor;
import de.leon.trycity.mixin.LocalPlayerAccessor;
import de.leon.trycity.mixin.MobAccessor;
import de.leon.trycity.mixin.PlayerAccessor;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mob-Verhalten: als Morph bewegt man sich wie das echte Mob – an- und ausschalten mit einer
 * Taste (Steuerung → TryCity, Standard J). Nur bei echten Mobs; eigene Aussehen des Servers
 * (Geist, TrippelT, Skibidi) haben keins, dort tut die Taste nichts.
 *
 * <h2>Wie</h2>
 * Ein zweites Exemplar des Mobs – das Gehirn – steht jeden Tick, wo man selbst steht, und
 * denkt mit dem Vanilla-Code, Schritt für Schritt wie {@code Mob.serverAiStep}: Goals
 * (herumlaufen, umschauen, Spieler anschauen, dem Weizen folgen, fliehen, jagen …) bzw. das
 * Brain bei Ziege, Axolotl, Frosch & Co., dann Wegfindung ({@code PathNavigation}) und die
 * Steuerteile für Laufen, Blick und Springen. Was das Mob damit tun würde, wird zur eigenen
 * Eingabe: Laufrichtung und Tempo, Springen, Sprinten, Blick. Bewegt wird also der
 * Spieler, ganz normal – der Server sieht nur Tasten und Maus, an den Plugins ändert sich
 * nichts.
 *
 * <h2>Server-Welt</h2>
 * Manche Goals verlangen eine Server-Welt; sie bekommen {@link ShadowLevel} (liest aus der
 * Client-Welt, schreibt nichts). Was trotzdem nur der Server kann (Dorf, Spielregeln,
 * Raids), fällt einzeln weg ({@link SafeGoal}, {@code BrainMixin}). Angriffe treffen nie –
 * man holt nur aus. Man selbst kommt für das Mob nicht vor (nicht Ziel, nicht angeschaut).
 *
 * <h2>Selbst steuern</h2>
 * Maus, Lauf-, Sprung- oder Schleichtaste, Klicks oder ein offenes Fenster: man steuert
 * sofort selbst. Nach {@link #RUHE} Ticks ohne eigene Eingabe macht das Mob weiter.
 */
public final class MobBehavior {

    /** So lange (Ticks) ohne eigene Eingabe, bis das Mob wieder übernimmt. */
    private static final int RUHE = 40;
    /** Sprinten macht 30 % schneller (Modifikator auf das Gesamt-Tempo). */
    private static final double SPRINT = 1.3;
    /** Entity-ID des Gehirns – negativ wie die der Figur, nie eine aus der Welt. */
    private static final int GEHIRN_ID = -1_700_001;

    private static @Nullable KeyMapping taste;
    private static boolean an;
    /** Ticks ohne eigene Eingabe (bis {@link #RUHE}). */
    private static int ruhe;
    /** Maus seit dem letzten Tick bewegt. */
    private static boolean maus;
    /** Beim nächsten Steuern neu anfangen: Gehirn auf den eigenen Stand. */
    private static boolean neu = true;
    /** Gerade denkt das Gehirn (Mixins: Schatten-Welt, nichts bauen, man selbst zählt nicht). */
    private static boolean denkt;
    /** In diesem Tick steuert das Mob. */
    private static boolean steuert;
    private static boolean ausgeholt;
    private static Input eingabe = Input.EMPTY;
    private static Vec2 richtung = Vec2.ZERO;
    private static float blickYO, blickY, blickXO, blickX;
    private static long letzterTreffer;
    private static int fehler;

    private static @Nullable Mob gehirn;
    private static @Nullable MorphState gehirnFuer;
    /** Für diesen Zustand gibt es kein Gehirn (kein Mob) – nicht jeden Tick neu versuchen. */
    private static @Nullable MorphState ohneGehirn;
    private static @Nullable ShadowLevel schatten;
    /** Sensoren und Brain-Verhalten, die im Client nicht gehen – je Gehirn. */
    private static Set<Object> kaputt = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<String> GEMELDET = ConcurrentHashMap.newKeySet();

    private MobBehavior() {}

    static void init() {
        KeyMapping.Category kategorie = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath(TryCityClient.MOD_ID, "morph"));
        taste = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.trycity.mob_behavior", InputConstants.KEY_J, kategorie));
        ClientTickEvents.START_CLIENT_TICK.register(client -> tick());
    }

    // ── Für die Mixins ───────────────────────────────────────────────────────

    public static boolean thinking() {
        return denkt;
    }

    public static ServerLevel shadow() {
        return schatten;
    }

    /** Sensor oder Brain-Verhalten nicht schon als „geht im Client nicht“ bekannt? */
    public static boolean usable(Object teil) {
        return !kaputt.contains(teil);
    }

    public static void broken(Object teil, Throwable e) {
        kaputt.add(teil);
        if (GEMELDET.add(teil.getClass().getName())) {
            TryCityClient.LOG.info("[TryCity] Mob-Verhalten: {} geht im Client nicht ({})",
                    teil.getClass().getSimpleName(), e.toString());
        }
    }

    /** Das Gehirn greift an (Schatten-Welt): nur ausholen, einmal je Tick. */
    public static void attack(@Nullable Entity angreifer) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (!denkt || angreifer != gehirn || ausgeholt || player == null) return;
        ausgeholt = true;
        Mc.swing(player);
    }

    /** Man dreht sich selbst (Maus, Controller) – sofort selbst steuern. */
    public static void turned(Entity entity) {
        if (entity != Minecraft.getInstance().player) return;
        maus = true;
        selbst();
    }

    private static void selbst() {
        ruhe = 0;
        steuert = false;
        neu = true;
    }

    // ── Tick ─────────────────────────────────────────────────────────────────

    /** Am Anfang jedes Client-Ticks – vor dem Spieler, der die Eingabe gleich liest. */
    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        steuert = false;
        ausgeholt = false;
        while (taste != null && taste.consumeClick()) umschalten(mc, player);
        if (!an) return;
        MorphState state = MorphClient.state();
        if (player == null || mc.level == null || !moeglich(state)) {
            aus();
            return;
        }
        // Ein offenes Fenster zählt wie eigene Eingabe – danach erst 2 s Ruhe
        if (maus || eigeneEingabe(mc.options) || Mc.screen() != null) ruhe = 0;
        else if (ruhe < RUHE) ruhe++;
        maus = false;
        if (ruhe < RUHE || !darfSteuern(mc, player)) {
            neu = true;
            return;
        }
        Mob mob = gehirn(state, mc.level);
        if (mob == null) {
            aus();
            return;
        }
        if (neu) {
            anfangen(player, mob);
            neu = false;
        }
        if (!denken(player, mob)) return;
        steuern(player, mob);
        steuert = true;
    }

    private static void umschalten(Minecraft mc, @Nullable LocalPlayer player) {
        MorphState state = MorphClient.state();
        // Nur als echtes Mob – ohne Morph oder mit eigenem Aussehen tut die Taste nichts
        if (player == null || mc.level == null || !moeglich(state) || gehirn(state, mc.level) == null) return;
        an = !an;
        // Gleich loslegen – eine Mausbewegung von vor dem Drücken zählt nicht
        ruhe = RUHE;
        maus = false;
        neu = true;
        player.sendOverlayMessage(Component.translatable(an ? "trycity.mob_behavior.on" : "trycity.mob_behavior.off"));
    }

    private static boolean moeglich(@Nullable MorphState state) {
        return state != null && state.custom() == null;
    }

    private static void aus() {
        an = false;
        gehirn = null;
        gehirnFuer = null;
        neu = true;
    }

    /** Sprinten zählt nicht: mit „Sprinten umschalten“ bliebe die Taste dauerhaft gedrückt. */
    private static boolean eigeneEingabe(Options o) {
        return o.keyUp.isDown() || o.keyDown.isDown() || o.keyLeft.isDown() || o.keyRight.isDown()
                || o.keyJump.isDown() || o.keyShift.isDown() || o.keyAttack.isDown() || o.keyUse.isDown();
    }

    private static boolean darfSteuern(Minecraft mc, LocalPlayer player) {
        return player.isAlive() && !player.isPassenger() && !player.isSpectator() && !player.isSleeping()
                && !player.isFallFlying() && mc.getCameraEntity() == player;
    }

    // ── Gehirn ───────────────────────────────────────────────────────────────

    private static @Nullable Mob gehirn(MorphState state, ClientLevel level) {
        if (gehirn != null && state.equals(gehirnFuer) && gehirn.level() == level && !gehirn.isRemoved()) return gehirn;
        gehirn = null;
        if (state.equals(ohneGehirn)) return null;
        Entity entity;
        try {
            entity = MorphLooks.STANDARD.create(level, state);
        } catch (RuntimeException e) {
            entity = null;
        }
        if (!(entity instanceof Mob mob)) {
            ohneGehirn = state;
            return null;
        }
        mob.setId(GEHIRN_ID);
        mob.noPhysics = true;
        mob.setSilent(true);
        MobAccessor zugang = (MobAccessor) mob;
        schatten = ShadowLevel.of(level);
        // Der Konstruktor trägt die Goals nur in einer Server-Welt ein. Manche holen oder merken
        // sich die Welt schon beim Anlegen (BreedGoal, FollowOwnerGoal …) – also kurz in der
        // Schatten-Welt; was sie sich merken, liest dann aus dem Client und schreibt nichts
        EntityInvoker welt = (EntityInvoker) mob;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) mob.setPos(player.getX(), player.getY(), player.getZ());
        denkt = true;
        welt.trycity$setLevel(schatten);
        try {
            // Lithium gibt Client-Wesen kein eigenes Brain, sondern allen dasselbe leere (auch dem
            // Spieler) – das Gehirn braucht ein echtes, wie es Vanilla im Konstruktor baut
            LivingEntityAccessor koerper = (LivingEntityAccessor) mob;
            if (player != null && mob.getBrain() == player.getBrain()) {
                koerper.trycity$setBrain(koerper.trycity$makeBrain(Brain.Packed.EMPTY));
            }
            try {
                zugang.trycity$registerGoals();
            } catch (RuntimeException e) {
                TryCityClient.LOG.warn("[TryCity] Mob-Verhalten {}: Goals nicht vollständig ({})", state.entity(), e.toString());
            }
            try {
                // Wie bei jedem Mob, das Vanilla erscheinen lässt: Startwerte (Ziege: Ramm-Pause,
                // Skelett: Bogen samt Bogen-Goal, Fuchs: Ziele)
                mob.finalizeSpawn(schatten, schatten.getCurrentDifficultyAt(mob.blockPosition()), EntitySpawnReason.NATURAL, null);
            } catch (RuntimeException e) {
                TryCityClient.LOG.info("[TryCity] Mob-Verhalten {}: Startwerte nur zum Teil ({})", state.entity(), e.toString());
            }
        } finally {
            welt.trycity$setLevel(level);
            denkt = false;
        }
        // Beim Erscheinen kann ein Reiter oder Reittier dazukommen (Hühnerjockey) oder ein
        // anderes Alter – das Gehirn läuft allein und so alt wie der Morph
        mob.stopRiding();
        mob.ejectPassengers();
        mob.setBaby(state.baby());
        SafeGoal.wrapAll(zugang.trycity$goalSelector());
        SafeGoal.wrapAll(zugang.trycity$targetSelector());
        kaputt = Collections.newSetFromMap(new IdentityHashMap<>());
        gehirn = mob;
        gehirnFuer = state;
        neu = true;
        fehler = 0;
        return mob;
    }

    /** Das Mob steht und schaut, wie man gerade selbst; alter Weg vergessen. */
    private static void anfangen(LocalPlayer player, Mob mob) {
        mob.setPos(player.getX(), player.getY(), player.getZ());
        mob.setYRot(player.yBodyRot);
        mob.yBodyRot = player.yBodyRot;
        mob.yHeadRot = player.getYRot();
        mob.setXRot(player.getXRot());
        mob.getNavigation().stop();
        mob.getMoveControl().setWait();
        mob.xxa = 0.0F;
        mob.yya = 0.0F;
        mob.zza = 0.0F;
        blickYO = blickY = player.getYRot();
        blickXO = blickX = player.getXRot();
    }

    /** Ein Tick Denken – wie {@code Mob.serverAiStep}. false = ging schief, diesen Tick nicht steuern. */
    private static boolean denken(LocalPlayer player, Mob mob) {
        abgleichen(player, mob);
        MobAccessor zugang = (MobAccessor) mob;
        denkt = true;
        try {
            mob.getSensing().tick();
            GoalSelector ziele = zugang.trycity$targetSelector();
            GoalSelector goals = zugang.trycity$goalSelector();
            if ((mob.tickCount + mob.getId()) % 2 != 0 && mob.tickCount > 1) {
                ziele.tickRunningGoals(false);
                goals.tickRunningGoals(false);
            } else {
                ziele.tick();
                goals.tick();
            }
            mob.getNavigation().tick();
            mobTick(zugang, mob);
            mob.getMoveControl().tick();
            mob.getLookControl().tick();
            mob.getJumpControl().tick();
            // Körper dreht nach (BodyRotationControl) – sonst Teil von LivingEntity.tick
            zugang.trycity$tickHeadTurn(mob.yBodyRot);
            fehler = 0;
            return true;
        } catch (RuntimeException | LinkageError e) {
            if (fehler++ == 0) TryCityClient.LOG.warn("[TryCity] Mob-Verhalten {}: {}", gehirnFuer, e.toString());
            if (fehler > RUHE) {
                // Geht dauerhaft nicht – aus, statt jeden Tick neu zu scheitern
                ohneGehirn = gehirnFuer;
                aus();
            }
            return false;
        } finally {
            denkt = false;
        }
    }

    /**
     * {@code customServerAiStep}: Brain-Mobs ticken hier ihr Brain und wählen ihre Tätigkeit.
     * Was danach nur der Server kann (Handel, Umwandeln …), darf scheitern – das Brain lief schon.
     */
    private static void mobTick(MobAccessor zugang, Mob mob) {
        try {
            zugang.trycity$customServerAiStep(schatten);
        } catch (RuntimeException | LinkageError e) {
            if (GEMELDET.add("tick:" + mob.getClass().getName())) {
                TryCityClient.LOG.info("[TryCity] Mob-Verhalten: Tick von {} nur zum Teil im Client ({})",
                        mob.getClass().getSimpleName(), e.toString());
            }
        }
    }

    /** Was das Mob von sich selbst wissen muss – vom eigenen Spieler, der es ja ist. */
    private static void abgleichen(LocalPlayer player, Mob mob) {
        mob.xo = mob.getX();
        mob.yo = mob.getY();
        mob.zo = mob.getZ();
        mob.yRotO = mob.getYRot();
        mob.xRotO = mob.getXRot();
        mob.yBodyRotO = mob.yBodyRot;
        mob.yHeadRotO = mob.yHeadRot;
        mob.setPos(player.getX(), player.getY(), player.getZ());
        mob.setOnGround(player.onGround());
        mob.horizontalCollision = player.horizontalCollision;
        mob.verticalCollision = player.verticalCollision;
        mob.fallDistance = player.fallDistance;
        ((EntityInvoker) mob).trycity$updateFluidInteraction();
        // Nach dem Wasser-Abgleich: dessen Strömung soll nicht als Wunsch des Mobs zählen
        mob.setDeltaMovement(player.getDeltaMovement());
        mob.setSharedFlagOnFire(player.isOnFire());
        mob.setTicksFrozen(player.getTicksFrozen());
        mob.setAirSupply(Math.round((float) mob.getMaxAirSupply() * player.getAirSupply() / Math.max(1, player.getMaxAirSupply())));
        mob.setHealth(Math.max(1.0F, mob.getMaxHealth() * player.getHealth() / player.getMaxHealth()));
        mob.deathTime = 0;
        mob.tickCount++;
        // Vanilla setzt das zurück, solange ein Spieler nah ist – man selbst steht ja drin
        mob.setNoActionTime(0);
        treffer(player, mob);
    }

    /**
     * Treffer: was einen selbst trifft, trifft das Mob (Panik, zurückschlagen). Der Client
     * kennt die Schadensquelle aus dem Schadens-Paket. Den Angreifer vergisst Vanilla nach
     * 100 Ticks ({@code LivingEntity.baseTick}) – das Gehirn tickt nicht selbst, also hier.
     */
    private static void treffer(LocalPlayer player, Mob mob) {
        long wann = ((LivingEntityAccessor) player).trycity$lastDamageStamp();
        DamageSource quelle = player.getLastDamageSource();
        if (quelle != null && wann != letzterTreffer) {
            letzterTreffer = wann;
            LivingEntityAccessor ziel = (LivingEntityAccessor) mob;
            ziel.trycity$setLastDamageSource(quelle);
            ziel.trycity$setLastDamageStamp(wann);
            if (quelle.getEntity() instanceof LivingEntity taeter && taeter != player) mob.setLastHurtByMob(taeter);
        }
        LivingEntity angreifer = mob.getLastHurtByMob();
        if (angreifer != null && (!angreifer.isAlive() || mob.tickCount - mob.getLastHurtByMobTimestamp() > 100)) {
            mob.setLastHurtByMob(null);
        }
    }

    // ── Steuern ──────────────────────────────────────────────────────────────

    /** Aus dem, was das Mob jetzt tun würde, die eigene Eingabe und den Blick machen. */
    private static void steuern(LocalPlayer player, Mob mob) {
        // Wie LivingEntity.travel die Eingabe des Mobs nimmt: Richtung (Drehung des Mobs) und Stärke
        float x = mob.xxa;
        float z = mob.zza;
        float laenge = Mth.sqrt(x * x + z * z);
        double staerke = Math.min(laenge, 1.0F) * mob.getSpeed();
        double wx = 0.0;
        double wz = 0.0;
        if (laenge > 1.0E-4F) {
            float sin = Mth.sin(mob.getYRot() * Mth.DEG_TO_RAD);
            float cos = Mth.cos(mob.getYRot() * Mth.DEG_TO_RAD);
            wx = (x * cos - z * sin) / laenge;
            wz = (z * cos + x * sin) / laenge;
        }
        // Mobs, die ihr Tempo direkt setzen statt zu laufen (Fledermaus, Ghast, Phantom, Fisch
        // steigt, Wolf springt auf die Beute)
        Vec3 schub = mob.getDeltaMovement().subtract(player.getDeltaMovement());
        double schubSeite = Math.sqrt(schub.x * schub.x + schub.z * schub.z);
        double gehen = gehTempo(player);
        if (staerke < 1.0E-4 && schubSeite > 1.0E-3) {
            wx = schub.x / schubSeite;
            wz = schub.z / schubSeite;
            staerke = gehen;
        }
        // Wie LivingEntity.aiStep vor travel
        mob.xxa *= 0.98F;
        mob.zza *= 0.98F;

        // Blick = Kopf des Mobs (LookControl)
        blickYO = blickY;
        blickXO = blickX;
        blickY = blickYO + Mth.wrapDegrees(mob.yHeadRot - blickYO);
        blickX = Mth.clamp(mob.getXRot(), -90.0F, 90.0F);
        player.setYRot(blickY);
        player.setXRot(blickX);
        player.setYHeadRot(blickY);

        Abilities faehig = player.getAbilities();
        boolean wasser = player.isInWater() || player.isInLava();
        boolean hoch = mob.yya > 0.01F || schub.y > 0.02;
        boolean runter = mob.yya < -0.01F || schub.y < -0.02;
        // Flieger (Biene, Papagei, Allay …) heben ab, wenn der Server Fliegen erlaubt
        if (hoch && !wasser && faehig.mayfly && !faehig.flying) {
            faehig.flying = true;
            player.onUpdateAbilities();
        }

        // Tempo: volle Taste = Gehen des Spielers (der Server gleicht es dem Mob an). Im Flug
        // zählt das Flug-Tempo des Spielers – dann volle Richtung, sobald das Mob fliegt
        double anteil = gehen > 1.0E-6 ? staerke / gehen : 0.0;
        boolean sprint = anteil > 1.05;
        if (sprint) anteil /= SPRINT;
        if (faehig.flying) {
            anteil = staerke > 1.0E-4 ? 1.0 : 0.0;
            sprint = false;
        }
        anteil = Math.min(anteil, 1.0);
        if (!sprint && player.isSprinting()) player.setSprinting(false);
        Vec2 v = Vec2.ZERO;
        if (anteil > 0.01) {
            // Weltrichtung in Richtung zum eigenen Blick umrechnen
            float sin = Mth.sin(blickY * Mth.DEG_TO_RAD);
            float cos = Mth.cos(blickY * Mth.DEG_TO_RAD);
            float dx = (float) (wx * cos + wz * sin);
            float dz = (float) (wz * cos - wx * sin);
            // LocalPlayer.modifyInput streckt schräge Eingaben aufs Quadrat – hier vorher heraus
            float l = (float) anteil / zumQuadrat(dx, dz);
            v = new Vec2(dx * l, dz * l);
        }

        boolean schwebt = faehig.flying || wasser && !player.onGround();
        boolean springen = ((LivingEntityAccessor) mob).trycity$jumping() || schub.y > 0.1 || schwebt && hoch;
        boolean schleichen = schwebt && runter;
        eingabe = new Input(v.y > 0.3F, v.y < -0.3F, v.x > 0.3F, v.x < -0.3F, springen, schleichen, sprint);
        richtung = v;
    }

    /** Wie {@code LocalPlayer.distanceToUnitSquare}: wie weit eine Richtung bis zum Rand des Quadrats reicht. */
    private static float zumQuadrat(float x, float z) {
        float ax = Math.abs(x);
        float az = Math.abs(z);
        float tan = az > ax ? ax / az : az / Math.max(ax, 1.0E-6F);
        return Mth.sqrt(1.0F + tan * tan);
    }

    /** Tempo des Spielers beim Gehen – das Attribut ohne Sprint-Zuschlag. */
    private static double gehTempo(LocalPlayer player) {
        double tempo = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
        return player.isSprinting() ? tempo / SPRINT : tempo;
    }

    /**
     * KeyboardInputMixin: die Tasten sind gelesen – steuert das Mob, kommt seine Eingabe
     * hinein. Hat man doch selbst gedrückt, gewinnt man selbst.
     */
    public static void input(ClientInput input) {
        if (!steuert) return;
        Input selbst = input.keyPresses;
        if (selbst.forward() || selbst.backward() || selbst.left() || selbst.right() || selbst.jump() || selbst.shift()) {
            selbst();
            return;
        }
        input.keyPresses = eingabe;
        ((ClientInputAccessor) input).trycity$setMoveVector(richtung);
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            // Stöße auf Springen/Vorwärts sind nie Doppeltipps (Fliegen umschalten, Sprinten)
            ((PlayerAccessor) player).trycity$setJumpTriggerTime(0);
            ((LocalPlayerAccessor) player).trycity$setSprintTriggerTime(0);
        }
    }

    /** MouseHandlerMixin, jedes Bild: Blick gleitet vom letzten zum neuen Tick-Blick des Mobs. */
    public static void frame() {
        if (!steuert) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        float t = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        player.setYRot(Mth.lerp(t, blickYO, blickY));
        player.setXRot(Mth.lerp(t, blickXO, blickX));
    }
}
