package de.leon.trycity.net;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.leon.trycity.TryCityClient;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.function.Consumer;

/**
 * Pakete vom Server an die Mod – ein Kanal je Extra: {@code trycity:<name>}.
 *
 * <h2>Format</h2>
 * Ein einziger Text im Minecraft-Format (VarInt-Länge + UTF-8), darin JSON. Mit JSON
 * kann der Server später Felder ergänzen, ohne dass alte Mods brechen – sie lesen
 * einfach, was sie kennen. Auf dem Server baut {@code TryCityModChannel}
 * (TryusCloudGlobalServer) genau dieses Format.
 *
 * <h2>Neues Extra</h2>
 * {@code TryCityChannel.listen("name", json -> ...)} im {@code init()} des Moduls –
 * mehr braucht es nicht. Der Handler läuft im Render-Thread.
 */
public final class TryCityChannel {

    /** Ein Paket in einem unserer Kanäle: der rohe JSON-Text. */
    public record Nachricht(CustomPacketPayload.Type<Nachricht> type, String json) implements CustomPacketPayload {}

    private TryCityChannel() {}

    public static void listen(String name, Consumer<JsonObject> handler) {
        CustomPacketPayload.Type<Nachricht> type =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(TryCityClient.MOD_ID, name));
        StreamCodec<RegistryFriendlyByteBuf, Nachricht> codec = StreamCodec.of(
                (buf, nachricht) -> ByteBufCodecs.STRING_UTF8.encode(buf, nachricht.json()),
                buf -> new Nachricht(type, ByteBufCodecs.STRING_UTF8.decode(buf)));
        PayloadTypeRegistry.clientboundPlay().register(type, codec);
        ClientPlayNetworking.registerGlobalReceiver(type, (nachricht, context) -> {
            JsonObject json;
            try {
                json = JsonParser.parseString(nachricht.json()).getAsJsonObject();
            } catch (RuntimeException e) {
                TryCityClient.LOG.warn("[TryCity] Paket {} nicht lesbar: {}", name, e.toString());
                return;
            }
            handler.accept(json);
        });
    }
}
