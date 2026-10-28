package de.leon.trycity.morph;

import de.leon.trycity.TryCityClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
//? if <26.2 {
import net.minecraft.world.entity.EntityType;
//?} else
/*import net.minecraft.world.entity.EntityTypes;*/
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.io.IOException;
import java.util.List;

/**
 * Eine Verwandlung aus Teilen – TrippelT ({@code /morph trippelt}) und Skibidi
 * ({@code /morph skibidi}): genau wie die anderen sie sehen (TryusCloudGlobalServer
 * {@code MorphModel}), nur besser.
 * Beim Zuschauer gleiten die Teile in Schritten von 2–4 Ticks zur nächsten Haltung; hier
 * wird jedes Bild aus den Keyframes gerechnet, mit dem eigenen Laufen, Schlagen und Drehen
 * ohne Verzögerung.
 *
 * <p>Die Figur ist ein unsichtbarer Rüstungsständer – er zeichnet nichts, trägt nur den
 * Zeichenstand. Die Teile hängen sich daran ({@code ArmorStandRendererMixin}): je Teil das
 * Papier-Modell aus dem Server-Pack ({@code custom_model_data} 2730–2749) an seiner Stelle.
 * In der Ego-Sicht keine Hand, nur das Item; Hitbox und Augen bleiben die des Spielers.</p>
 */
final class RigLook implements MorphLook {

    static final String TRIPPELT = "tryus:trippelt";
    static final String SKIBIDI = "tryus:skibidi";
    /** Treffer-Rot wie auf dem Server: Tönung über custom_model_data (tintindex 0 in jeder Fläche). */
    private static final int HURT_TINT = 0xFF6E6E;

    private final String resource;
    private @Nullable MorphRig rig;
    private boolean broken;
    private final MorphRigMotion motion = new MorphRigMotion();
    private MorphRig.Pose pose;
    private MorphRig.Pose scratch;
    private MorphRig.Placement[] placements;
    private ItemStack[] items;
    private ItemStack[] hurtItems;

    /** @param name Rig unter {@code /assets/trycity/morph/<name>.json} */
    RigLook(String name) {
        this.resource = "/assets/trycity/morph/" + name + ".json";
    }

    /** Das Rig – beim ersten Gebrauch geladen; ließ es sich nicht laden, bleibt die Figur leer. */
    private @Nullable MorphRig rig() {
        if (rig != null || broken) return rig;
        try {
            MorphRig loaded = MorphRig.load(resource);
            pose = loaded.newPose();
            scratch = loaded.newPose();
            placements = loaded.newPlacements();
            items = new ItemStack[loaded.parts.length];
            hurtItems = new ItemStack[loaded.parts.length];
            for (int i = 0; i < items.length; i++) {
                float cmd = loaded.parts[i].cmd();
                items[i] = model(cmd, List.of());
                hurtItems[i] = model(cmd, List.of(HURT_TINT));
            }
            rig = loaded;
        } catch (IOException | RuntimeException e) {
            broken = true;
            TryCityClient.LOG.warn("[TryCity] Morph-Rig {} nicht geladen: {}", resource, e.toString());
        }
        return rig;
    }

    private static ItemStack model(float cmd, List<Integer> colors) {
        ItemStack stack = new ItemStack(Items.PAPER);
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(cmd), List.of(), List.of(), colors));
        return stack;
    }

    @Override
    public Entity create(ClientLevel level, MorphState state) {
        //? if <26.2 {
        ArmorStand stand = EntityType.ARMOR_STAND.create(level, EntitySpawnReason.LOAD);
        //?} else
        /*ArmorStand stand = EntityTypes.ARMOR_STAND.create(level, EntitySpawnReason.LOAD);*/
        if (stand == null) return null;
        stand.setInvisible(true);
        stand.setNoBasePlate(true);
        return stand;
    }

    /** Je Client-Tick: weiterlaufen – nach dem eigenen Weg, wie der Server es tut. */
    @Override
    public void tick(LocalPlayer player) {
        MorphRig r = rig();
        if (r == null) return;
        boolean walks = !player.isPassenger() && !player.getAbilities().flying && !player.isFallFlying() && !player.isSwimming();
        double dx = player.getX() - player.xo, dz = player.getZ() - player.zo;
        double speed = walks ? Math.sqrt(dx * dx + dz * dz) * 20.0 : 0.0;
        motion.tick(r, 0.05, speed);
    }

    @Override
    public void attack() {
        motion.attack();
    }

    /** Schleichen + F (vom Server): den Ruf spielt der Server, hier der Schlag. */
    @Override
    public void special(long millis) {
        motion.attack();
    }

    /** Die Teile für dieses Bild – null, wenn man nichts sehen soll (unsichtbar, Rig kaputt). */
    @Nullable RigParts parts(LocalPlayer player, Entity figure, float partialTick) {
        MorphRig r = rig();
        if (r == null || player.isInvisible()) return null;
        motion.pose(r, pose, scratch, partialTick);
        r.place(pose, placements);
        boolean hurt = player.hurtTime > 0;
        ItemModelResolver resolver = Minecraft.getInstance().getItemModelResolver();
        ItemStackRenderState[] states = new ItemStackRenderState[placements.length];
        Matrix4f[] transforms = new Matrix4f[placements.length];
        for (int i = 0; i < placements.length; i++) {
            states[i] = new ItemStackRenderState();
            resolver.updateForNonLiving(states[i], hurt ? hurtItems[i] : items[i], ItemDisplayContext.NONE, figure);
            MorphRig.Placement p = placements[i];
            transforms[i] = new Matrix4f().translation(p.translation).rotate(p.rotation).scale(p.scale);
        }
        return new RigParts(states, transforms);
    }

    /** Rüstung trüge der unsichtbare Ständer in der Luft – die Modelle bringen alles selbst mit. */
    @Override
    public boolean showsArmor() {
        return false;
    }

    @Override
    public boolean alwaysInvisible() {
        return true;
    }

    @Override
    public boolean mobArm() {
        return false;
    }

    /** Der Server lässt den Spieler Spieler-groß; die Modelle sind auf etwa seine Größe skaliert. */
    @Override
    public boolean keepsPlayerSize() {
        return true;
    }
}
