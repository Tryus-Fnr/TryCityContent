package de.leon.trycity.morph;

import de.leon.trycity.content.ContentModule;
import de.leon.trycity.net.TryCityChannel;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.projectile.Projectile;

/**
 * Sich selbst als Morph-Mob sehen (Admins, {@code /morph}).
 *
 * Der Server (TryusCloudGlobalServer {@code MorphManager}) meldet im Kanal
 * {@code trycity:morph}, als was man gerade verwandelt ist – beim Verwandeln, beim
 * Betreten eines Servers und sobald der Client den Kanal anmeldet. Ohne Mod ändert
 * sich nichts: Paper schickt das Paket nur an Clients, die den Kanal kennen.
 * Ereignisse (Spezial-Animation) kommen getrennt in {@code trycity:morph_event}.
 */
public final class MorphModule implements ContentModule {

    @Override
    public String id() {
        return "morph";
    }

    @Override
    public void init() {
        // Eigene Aussehen des Servers (Choice.custom) – neue hier eintragen
        MorphLooks.register(GhostLook.ID, new GhostLook());

        TryCityChannel.listen("morph", MorphClient::apply);
        TryCityChannel.listen("morph_event", MorphClient::event);
        ClientTickEvents.END_CLIENT_TICK.register(client -> MorphClient.tick());
        // Schlag und Schuss für die Posen – wie der Server sie zählt
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (level.isClientSide() && player == Minecraft.getInstance().player) MorphPoses.melee();
            return InteractionResult.PASS;
        });
        ClientEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof Projectile projectile && MorphClient.active()
                    && projectile.getOwner() == Minecraft.getInstance().player) {
                MorphPoses.shot();
            }
        });
        // Jeder Server meldet seinen Stand neu – vorher gilt nichts
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> MorphClient.clear());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> MorphClient.clear());
    }
}
