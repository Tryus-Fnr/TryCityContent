package de.leon.trycity.morph;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Das Gerüst einer Verwandlung aus mehreren Teilen (TrippelT): Knochen, Teile und
 * Animationen aus {@code /assets/trycity/morph/<name>.json}. Die Datei schreibt das
 * Pack-Werkzeug {@code tools/make_morph_rigs.py} aus dem Blockbench-Export. Dieselbe Datei
 * und dieselbe Rechnung stecken im Server-Plugin (TryusCloudGlobalServer {@code MorphRig}):
 * man sieht sich selbst so, wie die anderen einen sehen – nur Bild für Bild statt in
 * gleitenden Schritten.
 *
 * <h2>Was gerechnet wird</h2>
 * Eine Haltung ({@link Pose}) hat je Knochen Drehung, Lage (Pixel zum Eltern-Knochen) und
 * Größe. {@link #place} macht daraus je Teil die Transformation seines ItemDisplays im
 * Modell-Raum: Blöcke ab den Füßen, vorn +Z, Größe schon mit {@link #scale}. Die
 * 180°-Drehung, die der Client beim Zeichnen eines ItemDisplays selbst dazugibt, ist
 * herausgerechnet. Blickrichtung und Mitfahr-Höhe kommen beim Aufrufer dazu.
 *
 * <h2>Teil im Modell</h2>
 * Jedes Teil ist ein eigenes Pack-Modell, so verschoben, dass seine Mitte bei (8, 8, 8)
 * liegt (Minecraft erlaubt nur −16..32). Wo darin der Drehpunkt seines Knochens sitzt,
 * steht in {@link Part#pivot}.
 *
 * <p>Unveränderlich nach dem Laden – darf von jedem Thread gelesen werden.</p>
 */
final class MorphRig {

    /** Die drei Kanäle einer Spur. */
    static final int ROT = 0, POS = 1, SCALE = 2;

    /** Ein Knochen: Eltern-Index (-1 = Wurzel) und Lage seines Drehpunkts zum Eltern-Drehpunkt in Pixeln. */
    record Bone(String name, int parent, Vector3f offset) {}

    /** Ein Teil = ein ItemDisplay: welcher Knochen es trägt, sein Pack-Modell, Drehpunkt im Modell (Pixel). */
    record Part(String name, int bone, int cmd, Vector3f pivot) {}

    /**
     * Keyframes eines Kanals, linear: Zeiten in Sekunden, je Zeit die Werte – Drehung
     * als Quaternion (x, y, z, w), Lage in Pixeln (x, y, z), Größe (s).
     */
    record Track(float[] times, float[][] values) {}

    /** Eine Animation: Spuren je Knochen und Kanal ({@code tracks[knochen][kanal]}, null = keine). */
    record Animation(String name, float length, boolean loop, Track[][] tracks) {
        boolean animates(int bone) {
            Track[] t = tracks[bone];
            return t != null && (t[ROT] != null || t[POS] != null || t[SCALE] != null);
        }
    }

    /** Eine Haltung: je Knochen Drehung, Lage zum Eltern-Knochen (Pixel), Größe. */
    static final class Pose {
        final Quaternionf[] rot;
        final Vector3f[] pos;
        final float[] scale;

        Pose(int bones) {
            rot = new Quaternionf[bones];
            pos = new Vector3f[bones];
            scale = new float[bones];
            for (int i = 0; i < bones; i++) {
                rot[i] = new Quaternionf();
                pos[i] = new Vector3f();
            }
        }
    }

    /** Wo ein Teil steht – fertig für Translation, linke Drehung und Größe eines ItemDisplays. */
    static final class Placement {
        final Vector3f translation = new Vector3f();
        final Quaternionf rotation = new Quaternionf();
        float scale;
    }

    /** Die 180° um Y, die der Client beim Zeichnen eines ItemDisplays dazugibt (ItemDisplayRenderer). */
    private static final Quaternionf FLIP = new Quaternionf().rotateY((float) Math.PI);

    /** Modell → Welt: so groß wird ein Pixel des Modells (1/16 Block mal Maßstab). */
    final float scale;
    /** Blöcke je Lauf-Durchgang (Walk einmal ganz) – siehe {@link MorphRigMotion}. */
    final float stride;
    final Bone[] bones;
    final Part[] parts;
    final Animation idle;
    final Animation walk;
    final Animation attack;

    private MorphRig(float scale, float stride, Bone[] bones, Part[] parts, Animation idle, Animation walk, Animation attack) {
        this.scale = scale;
        this.stride = stride;
        this.bones = bones;
        this.parts = parts;
        this.idle = idle;
        this.walk = walk;
        this.attack = attack;
    }

    /** Lädt ein Rig aus den Ressourcen der Mod, z. B. {@code /assets/trycity/morph/trippelt.json}. */
    static MorphRig load(String resource) throws IOException {
        try (InputStream in = MorphRig.class.getResourceAsStream(resource)) {
            if (in == null) throw new IOException("Rig fehlt: " + resource);
            return parse(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject());
        }
    }

    static MorphRig parse(JsonObject json) {
        JsonArray boneArray = json.getAsJsonArray("bones");
        Bone[] bones = new Bone[boneArray.size()];
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < bones.length; i++) {
            JsonObject b = boneArray.get(i).getAsJsonObject();
            String name = b.get("name").getAsString();
            JsonElement parent = b.get("parent");
            int parentIndex = parent == null || parent.isJsonNull() ? -1 : index.getOrDefault(parent.getAsString(), -2);
            if (parentIndex == -2) throw new IllegalArgumentException("Eltern-Knochen fehlt vor " + name);
            bones[i] = new Bone(name, parentIndex, vec(b.getAsJsonArray("offset")));
            index.put(name, i);
        }
        JsonArray partArray = json.getAsJsonArray("parts");
        Part[] parts = new Part[partArray.size()];
        for (int i = 0; i < parts.length; i++) {
            JsonObject p = partArray.get(i).getAsJsonObject();
            Integer bone = index.get(p.get("bone").getAsString());
            if (bone == null) throw new IllegalArgumentException("Knochen fehlt: " + p.get("bone"));
            parts[i] = new Part(p.get("name").getAsString(), bone, p.get("cmd").getAsInt(), vec(p.getAsJsonArray("pivot")));
        }
        JsonObject anims = json.getAsJsonObject("animations");
        float stride = json.has("stride") ? json.get("stride").getAsFloat() : 2.4f;
        return new MorphRig(json.get("scale").getAsFloat(), stride, bones, parts,
                animation(anims, "idle", index, bones.length),
                animation(anims, "walk", index, bones.length),
                animation(anims, "attack", index, bones.length));
    }

    private static Animation animation(JsonObject anims, String name, Map<String, Integer> index, int boneCount) {
        JsonObject a = anims.getAsJsonObject(name);
        if (a == null) throw new IllegalArgumentException("Animation fehlt: " + name);
        Track[][] tracks = new Track[boneCount][];
        for (Map.Entry<String, JsonElement> entry : a.getAsJsonObject("bones").entrySet()) {
            Integer bone = index.get(entry.getKey());
            if (bone == null) throw new IllegalArgumentException("Animation auf unbekanntem Knochen: " + entry.getKey());
            JsonObject channels = entry.getValue().getAsJsonObject();
            tracks[bone] = new Track[] {
                    track(channels.getAsJsonArray("rot")),
                    track(channels.getAsJsonArray("pos")),
                    track(channels.getAsJsonArray("scale"))};
        }
        return new Animation(name, a.get("length").getAsFloat(), a.get("loop").getAsBoolean(), tracks);
    }

    private static Track track(JsonArray keys) {
        if (keys == null || keys.isEmpty()) return null;
        float[] times = new float[keys.size()];
        float[][] values = new float[keys.size()][];
        for (int k = 0; k < times.length; k++) {
            JsonArray key = keys.get(k).getAsJsonArray();
            times[k] = key.get(0).getAsFloat();
            values[k] = new float[key.size() - 1];
            for (int v = 1; v < key.size(); v++) values[k][v - 1] = key.get(v).getAsFloat();
        }
        return new Track(times, values);
    }

    private static Vector3f vec(JsonArray a) {
        return new Vector3f(a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat());
    }

    // ── Haltungen ────────────────────────────────────────────────────────────

    Pose newPose() {
        Pose pose = new Pose(bones.length);
        rest(pose);
        return pose;
    }

    /** Ruhe: keine Drehung, jeder Knochen an seinem Platz, Größe 1. */
    void rest(Pose out) {
        for (int i = 0; i < bones.length; i++) rest(out, i);
    }

    private void rest(Pose out, int bone) {
        out.rot[bone].identity();
        out.pos[bone].set(bones[bone].offset);
        out.scale[bone] = 1f;
    }

    /**
     * Die Haltung einer Animation zum Zeitpunkt {@code seconds}: Schleifen laufen im Kreis,
     * die übrigen bleiben am Ende stehen. Knochen ohne Spur stehen in Ruhe.
     */
    void sample(Animation animation, double seconds, Pose out) {
        float t = time(animation, seconds);
        float[] tmp = new float[4];
        for (int i = 0; i < bones.length; i++) {
            rest(out, i);
            Track[] tracks = animation.tracks[i];
            if (tracks == null) continue;
            if (tracks[ROT] != null) {
                sampleRotation(tracks[ROT], t, out.rot[i]);
            }
            if (tracks[POS] != null) {
                sampleLinear(tracks[POS], t, tmp);
                out.pos[i].set(tmp[0], tmp[1], tmp[2]);
            }
            if (tracks[SCALE] != null) {
                sampleLinear(tracks[SCALE], t, tmp);
                out.scale[i] = tmp[0];
            }
        }
    }

    static float time(Animation animation, double seconds) {
        if (animation.length <= 0f) return 0f;
        if (animation.loop) {
            double t = seconds % animation.length;
            return (float) (t < 0 ? t + animation.length : t);
        }
        return (float) Math.max(0.0, Math.min(seconds, animation.length));
    }

    /**
     * Mischt {@code other} mit Gewicht {@code weight} in {@code base}. Mit
     * {@code onlyAnimatedBy} nur die Knochen, die diese Animation bewegt – der Rest
     * behält, was er hat (ein Schlag lässt die Beine weiterlaufen, die er nicht kennt).
     */
    void blend(Pose base, Pose other, float weight, Animation onlyAnimatedBy) {
        if (weight <= 0f) return;
        float w = Math.min(1f, weight);
        for (int i = 0; i < bones.length; i++) {
            if (onlyAnimatedBy != null && !onlyAnimatedBy.animates(i)) continue;
            base.rot[i].slerp(other.rot[i], w);
            base.pos[i].lerp(other.pos[i], w);
            base.scale[i] += (other.scale[i] - base.scale[i]) * w;
        }
    }

    private static void sampleLinear(Track track, float t, float[] out) {
        float[] times = track.times;
        float[][] values = track.values;
        int n = times.length;
        if (t <= times[0] || n == 1) {
            System.arraycopy(values[0], 0, out, 0, values[0].length);
            return;
        }
        if (t >= times[n - 1]) {
            System.arraycopy(values[n - 1], 0, out, 0, values[n - 1].length);
            return;
        }
        int k = 1;
        while (k < n - 1 && times[k] < t) k++;
        float span = times[k] - times[k - 1];
        float f = span <= 0f ? 1f : (t - times[k - 1]) / span;
        for (int v = 0; v < values[k].length; v++) out[v] = values[k - 1][v] + (values[k][v] - values[k - 1][v]) * f;
    }

    private static void sampleRotation(Track track, float t, Quaternionf out) {
        float[] times = track.times;
        float[][] values = track.values;
        int n = times.length;
        if (t <= times[0] || n == 1) {
            set(out, values[0]);
            return;
        }
        if (t >= times[n - 1]) {
            set(out, values[n - 1]);
            return;
        }
        int k = 1;
        while (k < n - 1 && times[k] < t) k++;
        float span = times[k] - times[k - 1];
        float f = span <= 0f ? 1f : (t - times[k - 1]) / span;
        set(out, values[k - 1]);
        out.slerp(new Quaternionf(values[k][0], values[k][1], values[k][2], values[k][3]), f);
    }

    private static void set(Quaternionf out, float[] v) {
        out.set(v[0], v[1], v[2], v[3]);
    }

    // ── Teile ────────────────────────────────────────────────────────────────

    Placement[] newPlacements() {
        Placement[] out = new Placement[parts.length];
        for (int i = 0; i < out.length; i++) out[i] = new Placement();
        return out;
    }

    /**
     * Wo die Teile in dieser Haltung stehen: im Modell-Raum (Blöcke ab den Füßen, vorn +Z).
     *
     * <p>Je Knochen die Welt-Lage aus der Kette: Drehpunkt = Eltern-Lage + Eltern-Drehung ·
     * (Eltern-Größe · eigene Lage). Ein Teil-Vertex v (Modell-Pixel) liegt dann bei
     * {@code k·(t + q·s·(v − pivot))} mit k = {@link #scale}/16. Das ItemDisplay zeichnet
     * {@code T + R·S·FLIP·(v/16 − ½)} – also {@code R = q·FLIP}, {@code S = scale·s} und
     * {@code T = k·t − q·S·(pivot/16 − ½)}.</p>
     */
    void place(Pose pose, Placement[] out) {
        int n = bones.length;
        Quaternionf[] worldRot = new Quaternionf[n];
        Vector3f[] worldPos = new Vector3f[n];
        float[] worldScale = new float[n];
        for (int i = 0; i < n; i++) {
            int parent = bones[i].parent;
            if (parent < 0) {
                worldPos[i] = new Vector3f(pose.pos[i]);
                worldRot[i] = new Quaternionf(pose.rot[i]);
                worldScale[i] = pose.scale[i];
            } else {
                Vector3f p = new Vector3f(pose.pos[i]).mul(worldScale[parent]);
                worldRot[parent].transform(p);
                worldPos[i] = p.add(worldPos[parent]);
                worldRot[i] = new Quaternionf(worldRot[parent]).mul(pose.rot[i]);
                worldScale[i] = worldScale[parent] * pose.scale[i];
            }
        }
        float k = scale / 16f;
        Vector3f c = new Vector3f();
        for (int i = 0; i < parts.length; i++) {
            Part part = parts[i];
            Placement placement = out[i];
            Quaternionf q = worldRot[part.bone];
            float s = scale * worldScale[part.bone];
            c.set(part.pivot).div(16f).sub(0.5f, 0.5f, 0.5f).mul(s);
            q.transform(c);
            placement.translation.set(worldPos[part.bone]).mul(k).sub(c);
            placement.rotation.set(q).mul(FLIP);
            placement.scale = s;
        }
    }
}
