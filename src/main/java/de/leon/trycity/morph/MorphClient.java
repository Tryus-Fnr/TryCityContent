package de.leon.trycity.morph;

import com.google.gson.JsonObject;
import de.leon.trycity.TryCityClient;
import de.leon.trycity.compat.Mc;
import de.leon.trycity.mixin.EntityInvoker;
import de.leon.trycity.mixin.WalkAnimationStateAccessor;
//? if >=26.3 {
/*import de.leon.trycity.mixin.LivingEntityAccessor;
import de.leon.trycity.mixin.SwingStateAccessor;
*///?}
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.WalkAnimationState;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Der eigene Morph im eigenen Client.
 *
 * <h2>Die Figur</h2>
 * Ein Mob (oder ein eigenes Aussehen, {@link MorphLook}), das nie in die Welt kommt:
 * es tickt nicht, niemand sonst sieht es. Bei jedem Zeichnen bekommt es Position,
 * Drehung, Laufanimation, Schlag, Treffer-Rot, Hände und Rüstung des Spielers
 * ({@link #sync}); mit {@code /morph name an} auch den eigenen Namen samt Unterzeile wie
 * bei den anderen. Gezeichnet wird es statt des Spielers in der Welt (Third Person,
 * {@code LevelRendererMixin}) und im Inventar ({@code InventoryScreenMixin}); in der
 * Ego-Sicht wird die Hand zu der des Mobs ({@link MobArm}).
 *
 * <h2>Posen und Schritte</h2>
 * Posen wie bei den anderen ({@link MorphPoses}); einmal je Tick läuft der Tick des
 * Mobs selbst ({@link #tick}), damit zeitliche Animationen weiterlaufen. Schritte
 * klingen wie das Mob, im Takt des Mobs ({@link #stepSound}).
 *
 * <h2>Größe</h2>
 * Der Server macht den Spieler per Scale-Attribut so hoch wie das Mob – damit Hitbox
 * und Augen auf beiden Seiten stimmen. Die Figur hat ein eigenes Attribut (1) und
 * erscheint deshalb in ihrer echten Größe, nicht noch einmal verkleinert.
 *
 * <h2>Hitbox und Augen</h2>
 * Im Stehen und Schleichen nimmt der Client die Maße des Mobs ({@link #dimensions}),
 * aber nie kleiner als die des Servers: eine kleinere Box ließe den Client in
 * Lücken laufen, die der Server nicht erlaubt – er würde zurückgesetzt. Die Augen
 * sitzen auf Mob-Höhe. Schwimmen, Gleiten und Schlafen bleiben vanilla.
 */
public final class MorphClient {

    private static @Nullable MorphState state;
    private static @Nullable Entity figur;
    /** Für diesen Zustand ließ sich keine Figur bauen – nicht jedes Bild neu versuchen. */
    private static @Nullable MorphState kaputt;
    /** Der Tick dieser Figur ist einmal abgestürzt – dann nur noch Posen, kein Tick. */
    private static @Nullable MorphState tickKaputt;
    /** Gerade spielt die Figur einen Schritt – ihr Geräusch geht an den Spieler. */
    private static boolean schritt;

    private MorphClient() {}

    // ── Zustand vom Server ───────────────────────────────────────────────────

    static void apply(JsonObject json) {
        set(MorphState.fromJson(json));
    }

    /** Verbindung weg oder neuer Server: erst wieder, was der Server meldet. */
    static void clear() {
        set(null);
    }

    private static void set(@Nullable MorphState next) {
        if (Objects.equals(next, state)) return;
        state = next;
        figur = null;
        kaputt = null;
        tickKaputt = null;
        MorphPoses.reset();
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) player.refreshDimensions();
        TryCityClient.LOG.info("[TryCity] Morph: {}", next == null ? "aus" : next);
    }

    public static boolean active() {
        return state != null;
    }

    // ── Die Figur ────────────────────────────────────────────────────────────

    /**
     * Die Figur für diese Entity – nur wenn es der eigene Spieler ist und ein Morph
     * läuft, sonst null. Abgeglichen mit dem Spieler, fertig zum Zeichnen. Render-Thread.
     */
    public static @Nullable Entity figureFor(Entity entity) {
        Minecraft mc = Minecraft.getInstance();
        if (state == null || entity != mc.player) return null;
        Entity figure = figure();
        if (figure != null) sync(mc.player, figure);
        return figure;
    }

    /** Die Figur, wie sie für die Hand in der Ego-Sicht gebraucht wird – null ohne Morph. */
    static @Nullable Entity figureForHand() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null ? null : figureFor(player);
    }

    static MorphLook look() {
        return state == null ? MorphLooks.STANDARD : MorphLooks.lookFor(state);
    }

    private static @Nullable Entity figure() {
        Minecraft mc = Minecraft.getInstance();
        if (state == null || mc.level == null) return null;
        if (figur != null && figur.level() == mc.level) return figur;
        if (Objects.equals(kaputt, state)) return null;
        try {
            figur = MorphLooks.create(state, mc.level);
        } catch (RuntimeException e) {
            figur = null;
            TryCityClient.LOG.warn("[TryCity] Morph-Figur {} nicht gebaut: {}", state, e.toString());
        }
        if (figur == null) {
            kaputt = state;
        } else {
            // Nie schieben: im Tick drückt ein Mob Spieler weg, die in ihm stehen – also einen selbst
            figur.noPhysics = true;
            // Ab 26.2 bekommt ein Wesen seine ID erst beim Eintragen in die Welt, und das
            // Zeichnen fragt sie ab („before ID assignment“). Negativ: kommt vom Server nie vor.
            figur.setId(FIGUR_ID);
        }
        return figur;
    }

    /** Entity-ID der Figur – negativ, damit sie nie mit einer echten aus der Welt zusammenfällt. */
    private static final int FIGUR_ID = -1_700_000;

    /** Alles, was man beim Zeichnen sieht, vom Spieler auf die Figur. */
    private static void sync(LocalPlayer player, Entity figure) {
        figure.setPos(player.getX(), player.getY(), player.getZ());
        figure.xo = player.xo;
        figure.yo = player.yo;
        figure.zo = player.zo;
        figure.xOld = player.xOld;
        figure.yOld = player.yOld;
        figure.zOld = player.zOld;
        figure.setYRot(player.getYRot());
        figure.yRotO = player.yRotO;
        figure.setXRot(player.getXRot());
        figure.xRotO = player.xRotO;
        figure.tickCount = player.tickCount;
        figure.setOnGround(player.onGround());
        figure.setShiftKeyDown(player.isShiftKeyDown());
        figure.setSprinting(player.isSprinting());
        MorphLook look = look();
        figure.setInvisible(look.alwaysInvisible() || player.isInvisible());

        if (figure instanceof LivingEntity mob) {
            mob.yBodyRot = player.yBodyRot;
            mob.yBodyRotO = player.yBodyRotO;
            mob.yHeadRot = player.yHeadRot;
            mob.yHeadRotO = player.yHeadRotO;
            copySwing(player, mob);
            mob.hurtTime = player.hurtTime;
            mob.hurtDuration = player.hurtDuration;
            mob.deathTime = player.deathTime;
            copyWalk(player.walkAnimation, mob.walkAnimation);
            ausruestung(mob, EquipmentSlot.MAINHAND, player.getMainHandItem());
            ausruestung(mob, EquipmentSlot.OFFHAND, player.getOffhandItem());
            if (look.showsArmor()) {
                for (EquipmentSlot slot : RUESTUNG) ausruestung(mob, slot, player.getItemBySlot(slot));
            }
            MorphPoses.useItem(player, mob);
        }
        name(player, figure);
        look.sync(player, figure);
    }

    /** Schlag-Animation 1:1 – bis 26.2 Felder am LivingEntity, ab 26.3 im {@code SwingState}. */
    private static void copySwing(LivingEntity from, LivingEntity to) {
        //? if <26.3 {
        to.attackAnim = from.attackAnim;
        to.oAttackAnim = from.oAttackAnim;
        to.swinging = from.swinging;
        to.swingingArm = from.swingingArm;
        to.swingTime = from.swingTime;
        //?} else {
        /*SwingStateAccessor quelle = (SwingStateAccessor) ((LivingEntityAccessor) from).trycity$swingState();
        SwingStateAccessor ziel = (SwingStateAccessor) ((LivingEntityAccessor) to).trycity$swingState();
        ziel.trycity$setCurrentSwing(quelle.trycity$currentSwing());
        ziel.trycity$setTicks(quelle.trycity$ticks());
        ziel.trycity$setOldAnimation(quelle.trycity$oldAnimation());
        ziel.trycity$setAnimation(quelle.trycity$animation());
        *///?}
    }

    /** Mit welcher Hand der Spieler gerade zuschlägt – null, wenn er nicht schlägt. */
    static @Nullable InteractionHand swingingHand(LivingEntity player) {
        //? if <26.3 {
        return player.swinging ? player.swingingArm : null;
        //?} else
        /*return player.isSwinging() ? player.getCurrentSwing().hand() : null;*/
    }

    private static void copyWalk(WalkAnimationState from, WalkAnimationState to) {
        WalkAnimationStateAccessor quelle = (WalkAnimationStateAccessor) from;
        WalkAnimationStateAccessor ziel = (WalkAnimationStateAccessor) to;
        ziel.trycity$setSpeedOld(quelle.trycity$speedOld());
        ziel.trycity$setSpeed(quelle.trycity$speed());
        ziel.trycity$setPosition(quelle.trycity$position());
        ziel.trycity$setPositionScale(quelle.trycity$positionScale());
    }

    private static final EquipmentSlot[] RUESTUNG = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    /** Nur bei Änderung neu setzen – sonst zählt das Mob jedes Bild ein neues Item. */
    private static void ausruestung(LivingEntity mob, EquipmentSlot slot, ItemStack item) {
        if (!ItemStack.matches(mob.getItemBySlot(slot), item)) mob.setItemSlot(slot, item.copy());
    }

    // ── Name über der Figur ──────────────────────────────────────────────────

    /**
     * {@code /morph name an}: die Figur trägt den eigenen Namen, wie ihn die anderen sehen
     * – Team-Prefix, Farbe und Suffix aus dem eigenen Scoreboard
     * ({@code getDisplayName}). Der Server zeigt ihn einem selbst nicht
     * ({@code MorphNames} lässt den Besitzer aus), deshalb macht es die Mod.
     */
    private static void name(LocalPlayer player, Entity figure) {
        boolean zeigen = state != null && state.name();
        Component name = zeigen ? player.getDisplayName() : null;
        if (!Objects.equals(figure.getCustomName(), name)) figure.setCustomName(name);
        if (figure.isCustomNameVisible() != zeigen) figure.setCustomNameVisible(zeigen);
    }

    /**
     * Nach dem Zeichnen-Vorbereiten der Figur in der Welt: zum Namen auch die
     * Unterzeile (BELOW_NAME) des Spielers – die Figur hat als Mob keinen eigenen Score.
     */
    public static void decorate(EntityRenderState renderState) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || state == null || !state.name() || renderState.nameTag == null) return;
        if (renderState.scoreText == null) renderState.scoreText = player.belowNameDisplay();
    }

    // ── Tick ─────────────────────────────────────────────────────────────────

    /** Ereignis vom Server im Kanal {@code trycity:morph_event}, z. B. {@code {"event":"special","ms":2000}}. */
    static void event(JsonObject json) {
        if (state == null || !json.has("event")) return;
        if ("special".equals(json.get("event").getAsString()) && json.has("ms")) {
            MorphPoses.special(json.get("ms").getAsLong());
        }
    }

    /**
     * Einmal je Client-Tick: Posen setzen und den Tick des Mobs laufen lassen – so wie
     * der Client es mit jedem Mob in der Welt tut. Darin laufen zeitliche Animationen
     * (Creeper bläht sich auf, Golem schlägt, Eisbär stellt sich auf) und die eigenen
     * Partikel des Mobs. Nur wenn man die Figur sieht (F5 oder Inventar) – in der
     * Ego-Sicht stünde sonst z. B. der Rauch der Lohe vor den Augen.
     */
    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (state == null || player == null) return;
        Entity figure = figure();
        if (figure == null) return;
        sync(player, figure);
        MorphPoses.tick(player, figure);
        boolean sichtbar = !mc.options.getCameraType().isFirstPerson() || Mc.screen() instanceof AbstractContainerScreen<?>;
        if (!sichtbar || Objects.equals(tickKaputt, state)) return;
        try {
            figure.tick();
        } catch (RuntimeException e) {
            tickKaputt = state;
            TryCityClient.LOG.warn("[TryCity] Tick der Morph-Figur {} abgeschaltet: {}", state, e.toString());
        }
    }

    // ── Schritte ─────────────────────────────────────────────────────────────

    /** Je Mob-Klasse: hat sie ein eigenes Schritt-Geräusch ({@code playStepSound} überschrieben)? */
    private static final Map<Class<?>, Boolean> EIGENER_SCHRITT = new ConcurrentHashMap<>();

    /**
     * Der eigene Schritt (PlayerMixin): wie das Mob. Mobs ohne eigenes Geräusch klingen
     * wie der Block – dann bleibt es beim Spieler (false). Mobs, die beim Laufen gar
     * nichts machen (Fledermaus, Silberfisch, Wächter …), sind still. true = erledigt.
     */
    public static boolean stepSound(LocalPlayer player, BlockPos pos, BlockState block) {
        if (state == null) return false;
        Entity figure = figure();
        if (figure == null) return false;
        EntityInvoker mob = (EntityInvoker) figure;
        if (!mob.trycity$movementEmission().emitsSounds()) return true;
        if (!EIGENER_SCHRITT.computeIfAbsent(figure.getClass(), MorphClient::hatEigenenSchritt)) return false;
        figure.setPos(player.getX(), player.getY(), player.getZ());
        schritt = true;
        try {
            mob.trycity$playStepSound(pos, block);
        } finally {
            schritt = false;
        }
        return true;
    }

    private static boolean hatEigenenSchritt(Class<?> type) {
        for (Class<?> c = type; c != null && c != Entity.class && c != LivingEntity.class; c = c.getSuperclass()) {
            for (Method method : c.getDeclaredMethods()) {
                Class<?>[] params = method.getParameterTypes();
                if (method.getName().equals("playStepSound") && params.length == 2
                        && params[0] == BlockPos.class && params[1] == BlockState.class) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * EntityMixin: ein Geräusch der Figur während {@link #stepSound}. Mobs spielen es
     * für „alle außer niemand“ – das spielt der Client nie selbst, hier also von Hand
     * (wie beim eigenen Spieler). true = erledigt.
     */
    public static boolean playFigureSound(Entity entity, SoundEvent sound, float volume, float pitch) {
        if (!schritt || entity != figur) return false;
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null && !entity.isSilent()) {
            level.playLocalSound(entity.getX(), entity.getY(), entity.getZ(), sound, entity.getSoundSource(),
                    volume, pitch, false);
        }
        return true;
    }

    /** EntityMixin: Schritt-Takt des Mobs für den eigenen Spieler (Schildkröte trippelt). */
    public static float nextStep(Entity entity, float vanilla) {
        if (state == null || figur == null || entity != Minecraft.getInstance().player) return vanilla;
        figur.moveDist = entity.moveDist;
        return ((EntityInvoker) figur).trycity$nextStep();
    }

    // ── Kamera in F5 ─────────────────────────────────────────────────────────

    /**
     * Abstand der Kamera hinter den Augen: der größere aus Vanilla (4 Blöcke mal Scale
     * des Spielers – mit Morph geschrumpft) und dem, den das Mob selbst hätte (Scale und
     * {@code camera_distance} der Figur, also 4 Blöcke; als Baby die Hälfte wie bei
     * echten Babys). Große Mobs behalten den weiteren Abstand vom Server-Scale. Wände
     * kürzen danach wie immer ({@code getMaxZoom}).
     */
    public static float cameraDistance(@Nullable Entity cameraEntity, float vanilla) {
        if (state == null || cameraEntity == null || cameraEntity != Minecraft.getInstance().player) return vanilla;
        if (!(figure() instanceof LivingEntity mob)) return vanilla;
        double abstand = mob.getAttributes().hasAttribute(Attributes.CAMERA_DISTANCE)
                ? mob.getAttributeValue(Attributes.CAMERA_DISTANCE) : 4.0;
        return Math.max(vanilla, mob.getScale() * (float) abstand);
    }

    // ── Sichtfeld ────────────────────────────────────────────────────────────

    /**
     * Der Name des Tempo-Modifikators, den der Server beim Morph setzt
     * (TryusCloudGlobalServer {@code MorphAbilities}: {@code tryuscloudglobal:morph_speed},
     * „mal Gesamtergebnis“). Nur der Pfad zählt – falls das Plugin mal anders heißt.
     */
    private static final String MORPH_TEMPO = "morph_speed";

    /**
     * Das Tempo ohne den Morph-Anteil – für das Sichtfeld. Der Modifikator multipliziert
     * das Gesamtergebnis mit (1 + Betrag), also wird genau das wieder herausgeteilt.
     * Sprinten und Tränke bleiben drin.
     */
    public static double speedWithoutMorph(LivingEntity player, double value) {
        AttributeInstance tempo = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (tempo == null) return value;
        for (AttributeModifier modifier : tempo.getModifiers()) {
            if (!MORPH_TEMPO.equals(modifier.id().getPath())) continue;
            if (modifier.operation() != AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) continue;
            double faktor = 1.0 + modifier.amount();
            if (faktor > 0.001) value /= faktor;
        }
        return value;
    }

    // ── Hitbox und Augen ─────────────────────────────────────────────────────

    /**
     * Maße des eigenen Spielers mit Morph: Breite und Höhe des Mobs, aber nie kleiner
     * als die des Servers (Scale-Attribut); Augen auf Mob-Höhe. Nur im Stehen und
     * Schleichen – Schwimmen, Gleiten und Schlafen bleiben, wie der Server rechnet.
     */
    public static EntityDimensions dimensions(EntityDimensions server, Pose pose) {
        if (pose != Pose.STANDING && pose != Pose.CROUCHING) return server;
        Entity figure = figure();
        if (figure == null) return server;
        EntityDimensions mob = figure.getDimensions(Pose.STANDING);
        float breite = Math.max(server.width(), mob.width());
        float hoehe = Math.max(server.height(), mob.height());
        float augen = Math.max(0.1f, Math.min(mob.eyeHeight(), hoehe - 0.05f));
        return EntityDimensions.scalable(breite, hoehe).withEyeHeight(augen);
    }
}
