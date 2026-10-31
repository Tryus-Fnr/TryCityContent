package de.leon.trycity.morph;

import de.leon.trycity.mixin.ClientLevelInvoker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

/**
 * Eine Server-Welt für das Mob-Verhalten ({@link MobBehavior}), die keine ist.
 *
 * <p>Vanilla-Goals und das Brain verlangen eine {@code ServerLevel}
 * ({@code Goal.getServerLevel}, {@code Brain.tick}, {@code customServerAiStep}) – im Client
 * gibt es keine. Diese hier entsteht ohne Konstruktor (alle Felder leer) und reicht nur
 * weiter, was das Denken braucht: Blöcke, Licht, Zeit, Zufall, Wesen und Spieler, alles aus
 * der Client-Welt und ohne einen selbst (man ist ja das Mob), dazu die Vanilla-Spielregeln.
 * Schreiben geht ins Leere: Blöcke, neue Wesen, Geräusche, Partikel, Ereignisse.</p>
 *
 * <p>Was sie nicht kennt (Dorf, Raids, POI), wirft – dann fällt nur dieses
 * Goal bzw. dieses Verhalten weg ({@link SafeGoal}, {@code BrainMixin}), der Rest läuft.</p>
 */
public final class ShadowLevel extends ServerLevel {

    private ClientLevel client;
    private @Nullable GameRules regeln;

    /** Nie aufgerufen – angelegt wird nur über {@link #of}, ohne Konstruktor. */
    @SuppressWarnings("DataFlowIssue")
    private ShadowLevel() {
        super(null, null, null, null, null, null, false, 0L, null, false);
        throw new AssertionError();
    }

    static ShadowLevel of(ClientLevel level) {
        try {
            // Der echte Konstruktor bräuchte einen ganzen Server – angelegt wird ohne ihn
            Class<?> type = Class.forName("sun.misc.Unsafe");
            Field feld = type.getDeclaredField("theUnsafe");
            feld.setAccessible(true);
            Method anlegen = type.getMethod("allocateInstance", Class.class);
            ShadowLevel shadow = (ShadowLevel) anlegen.invoke(feld.get(null), ShadowLevel.class);
            shadow.client = level;
            return shadow;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Schatten-Welt nicht angelegt", e);
        }
    }

    private static @Nullable Player self() {
        return Minecraft.getInstance().player;
    }

    @Override
    public String toString() {
        return "ShadowLevel[" + client + "]";
    }

    // ── Lesen: aus der Client-Welt ───────────────────────────────────────────

    @Override
    public BlockState getBlockState(BlockPos pos) {
        return client.getBlockState(pos);
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return client.getFluidState(pos);
    }

    @Override
    public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
        return client.getBlockEntity(pos);
    }

    @Override
    public @Nullable ChunkAccess getChunk(int x, int z, ChunkStatus status, boolean load) {
        return client.getChunk(x, z, status, load);
    }

    @Override
    public boolean hasChunk(int x, int z) {
        return client.hasChunk(x, z);
    }

    @Override
    public boolean isLoaded(BlockPos pos) {
        return client.isLoaded(pos);
    }

    @Override
    public @Nullable BlockGetter getChunkForCollisions(int x, int z) {
        return client.getChunkForCollisions(x, z);
    }

    @Override
    public int getHeight(Heightmap.Types type, int x, int z) {
        return client.getHeight(type, x, z);
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return client.getLightEngine();
    }

    @Override
    public BiomeManager getBiomeManager() {
        return client.getBiomeManager();
    }

    @Override
    public int getSkyDarken() {
        return client.getSkyDarken();
    }

    @Override
    public boolean isBrightOutside() {
        return client.isBrightOutside();
    }

    @Override
    public boolean canSeeSky(BlockPos pos) {
        return client.canSeeSky(pos);
    }

    @Override
    public DimensionType dimensionType() {
        return client.dimensionType();
    }

    @Override
    public Holder<DimensionType> dimensionTypeRegistration() {
        return client.dimensionTypeRegistration();
    }

    @Override
    public ResourceKey<Level> dimension() {
        return client.dimension();
    }

    @Override
    public int getSeaLevel() {
        return client.getSeaLevel();
    }

    @Override
    public WorldBorder getWorldBorder() {
        return client.getWorldBorder();
    }

    @Override
    public RandomSource getRandom() {
        return client.getRandom();
    }

    @Override
    public RegistryAccess registryAccess() {
        return client.registryAccess();
    }

    @Override
    public FeatureFlagSet enabledFeatures() {
        return client.enabledFeatures();
    }

    @Override
    public EnvironmentAttributeSystem environmentAttributes() {
        return client.environmentAttributes();
    }

    @Override
    public LevelData getLevelData() {
        return client.getLevelData();
    }

    @Override
    public long getGameTime() {
        return client.getGameTime();
    }

    @Override
    public Difficulty getDifficulty() {
        return client.getDifficulty();
    }

    @Override
    public DamageSources damageSources() {
        return client.damageSources();
    }

    /**
     * Die Regeln des Servers kennt der Client nicht – also Vanilla-Standard (Mob-Griefing an,
     * universeller Zorn aus …). Ohne Regeln bliebe z. B. die Ziege beim Rammen hängen.
     */
    @Override
    public GameRules getGameRules() {
        if (regeln == null) regeln = new GameRules(client.enabledFeatures());
        return regeln;
    }

    @Override
    public DifficultyInstance getCurrentDifficultyAt(BlockPos pos) {
        return new DifficultyInstance(client.getDifficulty(), client.getGameTime(), 0L, 0.0F);
    }

    // ── Wesen und Spieler: aus der Client-Welt, ohne einen selbst ────────────

    @Override
    protected LevelEntityGetter<Entity> getEntities() {
        return ((ClientLevelInvoker) client).trycity$getEntities();
    }

    @Override
    public @Nullable Entity getEntity(int id) {
        Entity entity = client.getEntity(id);
        return entity == self() ? null : entity;
    }

    @Override
    public Collection<EnderDragonPart> dragonParts() {
        return client.dragonParts();
    }

    /**
     * Die anderen Spieler der Client-Welt. Keine {@code ServerPlayer} – die Aufrufer brauchen
     * aber nur {@code Player}; die zwei Sensoren, die wirklich {@code ServerPlayer} verlangen,
     * laufen über {@code TemptingSensorMixin}/{@code PlayerSensorMixin}.
     */
    @Override
    @SuppressWarnings("unchecked")
    public List<ServerPlayer> players() {
        List<Player> andere = new ArrayList<>(client.players());
        andere.remove(self());
        return (List<ServerPlayer>) (List<?>) andere;
    }

    @Override
    public List<Entity> getEntities(@Nullable Entity except, AABB box, Predicate<? super Entity> selector) {
        Player self = self();
        return client.getEntities(except, box, entity -> entity != self && selector.test(entity));
    }

    @Override
    public <T extends Entity> List<T> getEntities(EntityTypeTest<Entity, T> type, AABB box, Predicate<? super T> selector) {
        List<T> result = new ArrayList<>();
        getEntities(type, box, selector, result, Integer.MAX_VALUE);
        return result;
    }

    @Override
    public <T extends Entity> void getEntities(EntityTypeTest<Entity, T> type, AABB box, Predicate<? super T> selector,
                                               List<? super T> output) {
        getEntities(type, box, selector, output, Integer.MAX_VALUE);
    }

    @Override
    public <T extends Entity> void getEntities(EntityTypeTest<Entity, T> type, AABB box, Predicate<? super T> selector,
                                               List<? super T> output, int max) {
        Player self = self();
        client.getEntities(type, box, entity -> entity != self && selector.test(entity), output, max);
    }

    // ── Schreiben: geht ins Leere ────────────────────────────────────────────

    @Override
    public boolean setBlock(BlockPos pos, BlockState state, int flags) {
        return false;
    }

    @Override
    public boolean setBlock(BlockPos pos, BlockState state, int flags, int recursionLeft) {
        return false;
    }

    @Override
    public boolean removeBlock(BlockPos pos, boolean movedByPiston) {
        return false;
    }

    @Override
    public boolean destroyBlock(BlockPos pos, boolean dropResources, @Nullable Entity breaker, int recursionLeft) {
        return false;
    }

    @Override
    public void setBlockEntity(BlockEntity blockEntity) {}

    @Override
    public void removeBlockEntity(BlockPos pos) {}

    @Override
    public void sendBlockUpdated(BlockPos pos, BlockState old, BlockState current, int updateFlags) {}

    @Override
    public void blockEvent(BlockPos pos, Block block, int b0, int b1) {}

    @Override
    public void destroyBlockProgress(int id, BlockPos pos, int progress) {}

    @Override
    public boolean addFreshEntity(Entity entity) {
        return false;
    }

    @Override
    public boolean addWithUUID(Entity entity) {
        return false;
    }

    @Override
    public boolean tryAddFreshEntityWithPassengers(Entity entity) {
        return false;
    }

    @Override
    public void broadcastEntityEvent(Entity entity, byte event) {}

    @Override
    public void broadcastDamageEvent(Entity entity, DamageSource source) {}

    @Override
    public void playSeededSound(@Nullable Entity except, double x, double y, double z, Holder<SoundEvent> sound,
                                SoundSource source, float volume, float pitch, long seed) {}

    @Override
    public void playSeededSound(@Nullable Entity except, Entity sourceEntity, Holder<SoundEvent> sound,
                                SoundSource source, float volume, float pitch, long seed) {}

    // Die playSound-Varianten holen vorher einen Zufallswert aus einem Feld, das hier leer ist
    @Override
    public void playSound(@Nullable Entity except, BlockPos pos, SoundEvent sound, SoundSource source, float volume, float pitch) {}

    @Override
    public void playSound(@Nullable Entity except, double x, double y, double z, SoundEvent sound, SoundSource source) {}

    @Override
    public void playSound(@Nullable Entity except, double x, double y, double z, SoundEvent sound, SoundSource source,
                          float volume, float pitch) {}

    @Override
    public void playSound(@Nullable Entity except, double x, double y, double z, Holder<SoundEvent> sound,
                          SoundSource source, float volume, float pitch) {}

    @Override
    public void playSound(@Nullable Entity except, Entity sourceEntity, SoundEvent sound, SoundSource source,
                          float volume, float pitch) {}

    @Override
    public void levelEvent(@Nullable Entity source, int type, BlockPos pos, int data) {}

    @Override
    public void globalLevelEvent(int type, BlockPos pos, int data) {}

    @Override
    public void gameEvent(Holder<GameEvent> gameEvent, Vec3 position, GameEvent.Context context) {}

    @Override
    public void addParticle(ParticleOptions particle, double x, double y, double z, double xd, double yd, double zd) {}

    @Override
    public <T extends ParticleOptions> int sendParticles(T particle, double x, double y, double z, int count,
                                                         double xDist, double yDist, double zDist, double speed) {
        return 0;
    }

    @Override
    public <T extends ParticleOptions> int sendParticles(T particle, boolean overrideLimiter, boolean alwaysShow,
                                                         double x, double y, double z, int count,
                                                         double xDist, double yDist, double zDist, double speed) {
        return 0;
    }

    @Override
    public <T extends ParticleOptions> boolean sendParticles(ServerPlayer player, T particle, boolean overrideLimiter,
                                                             boolean alwaysShow, double x, double y, double z, int count,
                                                             double xDist, double yDist, double zDist, double speed) {
        return false;
    }
}
