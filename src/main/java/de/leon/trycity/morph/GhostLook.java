package de.leon.trycity.morph;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Rotations;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
//? if <26.2 {
import net.minecraft.world.entity.EntityType;
//?} else
/*import net.minecraft.world.entity.EntityTypes;*/
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;

import java.util.List;

/**
 * Der Geist ({@code /morph geist}, Spookytime): genau wie ihn andere sehen
 * (TryusCloudGlobalServer {@code MorphLook.ghost}) – ein unsichtbarer Rüstungsständer
 * ohne Bodenplatte, der das Geist-Modell (Papier, custom_model_data 2705, aus dem
 * Server-Pack) als Helm trägt. Die Arme zeigen nach vorn wie beim Zombie und halten
 * die Hände des Spielers; beim Schlagen kippt der Arm um 40° (wie auf dem Server).
 */
final class GhostLook implements MorphLook {

    static final String ID = "tryus:geist";
    private static final float MODELL = 2705f;
    private static final float ARM_VORN = -90f;
    private static final float SCHLAG = 40f;

    @Override
    public Entity create(ClientLevel level, MorphState state) {
        //? if <26.2 {
        ArmorStand stand = EntityType.ARMOR_STAND.create(level, EntitySpawnReason.LOAD);
        //?} else
        /*ArmorStand stand = EntityTypes.ARMOR_STAND.create(level, EntitySpawnReason.LOAD);*/
        if (stand == null) return null;
        stand.setInvisible(true);
        stand.setNoBasePlate(true);
        stand.setShowArms(true);
        stand.setRightArmPose(new Rotations(ARM_VORN, 0, 0));
        stand.setLeftArmPose(new Rotations(ARM_VORN, 0, 0));
        ItemStack modell = new ItemStack(Items.PAPER);
        modell.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(MODELL), List.of(), List.of(), List.of()));
        stand.setItemSlot(EquipmentSlot.HEAD, modell);
        return stand;
    }

    @Override
    public void sync(LocalPlayer player, Entity figure) {
        if (!(figure instanceof ArmorStand stand)) return;
        InteractionHand schlag = MorphClient.swingingHand(player);
        boolean haupt = schlag == InteractionHand.MAIN_HAND;
        boolean neben = schlag == InteractionHand.OFF_HAND;
        stand.setRightArmPose(new Rotations(haupt ? ARM_VORN - SCHLAG : ARM_VORN, 0, 0));
        stand.setLeftArmPose(new Rotations(neben ? ARM_VORN - SCHLAG : ARM_VORN, 0, 0));
    }

    /** Der Helm-Slot trägt das Geist-Modell – keine Rüstung darüber. */
    @Override
    public boolean showsArmor() {
        return false;
    }

    @Override
    public boolean alwaysInvisible() {
        return true;
    }

    /** Der Ständer ist unsichtbar – in der Ego-Sicht also keine Hand, nur das Item. */
    @Override
    public boolean mobArm() {
        return false;
    }
}
