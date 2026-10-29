package de.leon.trycity.morph;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.IOException;
import java.util.Locale;

/**
 * Selbsttest: eine Verwandlung aus Teilen so in die Welt stellen, wie ein Zuschauer sie
 * vom Server bekommt (TryusCloudGlobalServer {@code MorphModel}) – unsichtbarer
 * Rüstungsständer, darauf je Teil ein mitfahrendes ItemDisplay mit derselben
 * Transformation: Blickrichtung hineingedreht, um die Höhe des Ständers nach unten.
 */
public final class RigSummon {

    /** Mitfahr-Punkt des Rüstungsständers über den Füßen (seine Höhe). */
    private static final float ATTACH_Y = 1.975f;

    private RigSummon() {}

    /**
     * @param name Rig ({@code trippelt})
     * @param anim {@code idle}, {@code walk} oder {@code attack}
     * @param time Sekunden in die Animation
     * @param yaw  Blickrichtung (Minecraft-Yaw)
     * @param at   Koordinaten im Befehl, z. B. {@code ~ ~ ~2}
     */
    public static String command(String name, String anim, double time, float yaw, String at) throws IOException {
        return command(name, anim, time, yaw, at, false);
    }

    /** Wie oben, mit {@code sneak} = geduckt (Sneak-Haltung obendrauf wie beim Schleichen). */
    public static String command(String name, String anim, double time, float yaw, String at, boolean sneak)
            throws IOException {
        MorphRig rig = MorphRig.load("/assets/trycity/morph/" + name + ".json");
        MorphRig.Pose pose = rig.newPose();
        MorphRig.Animation animation = switch (anim) {
            case "walk" -> rig.walk;
            case "attack" -> rig.attack;
            default -> rig.idle;
        };
        rig.sample(animation, time, pose);
        if (sneak && rig.sneak != null) {
            MorphRig.Pose scratch = rig.newPose();
            rig.sample(rig.sneak, 0, scratch);
            rig.addOn(pose, scratch, 1f, rig.sneak);
        }
        MorphRig.Placement[] placements = rig.newPlacements();
        rig.place(pose, placements);
        Quaternionf heading = new Quaternionf().rotateY((float) Math.toRadians(-yaw));

        StringBuilder out = new StringBuilder("summon armor_stand ").append(at)
                .append(" {Invisible:1b,NoBasePlate:1b,Rotation:[").append(f(yaw)).append(",0f],Passengers:[");
        for (int i = 0; i < placements.length; i++) {
            MorphRig.Placement p = placements[i];
            Vector3f t = heading.transform(new Vector3f(p.translation));
            t.y -= ATTACH_Y;
            Quaternionf r = new Quaternionf(heading).mul(p.rotation);
            if (i > 0) out.append(',');
            out.append("{id:\"minecraft:item_display\",item:{id:\"minecraft:paper\",count:1,components:{")
                    .append("\"minecraft:custom_model_data\":{floats:[").append(f(rig.parts[i].cmd())).append("]}}},")
                    .append("transformation:{translation:[").append(f(t.x)).append(',').append(f(t.y)).append(',').append(f(t.z))
                    .append("],left_rotation:[").append(f(r.x)).append(',').append(f(r.y)).append(',').append(f(r.z)).append(',').append(f(r.w))
                    .append("],scale:[").append(f(p.scale)).append(',').append(f(p.scale)).append(',').append(f(p.scale))
                    .append("],right_rotation:[0f,0f,0f,1f]}}");
        }
        return out.append("]}").toString();
    }

    private static String f(float v) {
        return String.format(Locale.ROOT, "%.5ff", v);
    }
}
