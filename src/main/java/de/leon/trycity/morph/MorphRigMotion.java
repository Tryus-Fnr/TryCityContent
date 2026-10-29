package de.leon.trycity.morph;

/**
 * Welche Animation eines {@link MorphRig} gerade wie weit und wie stark läuft – dieselbe
 * Rechnung wie im Server-Plugin (TryusCloudGlobalServer {@code MorphRigMotion}), damit man
 * sich selbst so laufen sieht wie die anderen einen. Dazu hier: der Stand des letzten
 * Ticks, damit zwischen zwei Ticks Bild für Bild weitergerechnet werden kann
 * ({@link #pose(MorphRig, MorphRig.Pose, MorphRig.Pose, float)}).
 *
 * <ul>
 *   <li><b>Idle</b> läuft immer, nach Uhr.</li>
 *   <li><b>Walk</b> läuft nach Weg: ein Durchgang (zwei Schritte) je {@link MorphRig#stride} Blöcke.</li>
 *   <li><b>Attack</b> einmal je Schlag, blendet kurz ein und am Ende wieder aus.</li>
 *   <li><b>Sneak</b> (wenn das Rig eine hat) beim Schleichen obendrauf – alles darunter läuft weiter.</li>
 * </ul>
 */
final class MorphRigMotion {

    static final double FULL_WALK_SPEED = 2.5;
    static final double STILL_SPEED = 0.15;
    static final float WEIGHT_FOLLOW = 0.3f;
    static final float ATTACK_IN = 0.08f;
    static final float ATTACK_OUT = 0.25f;
    static final double ATTACK_RESTART = 0.5;
    static final float SNEAK_FOLLOW = 0.45f;

    double idleTime;
    double walkTime;
    float walkWeight;
    double attackTime = -1;
    float sneakWeight;

    // Stand vor dem letzten Tick – zum Weiterrechnen zwischen zwei Ticks
    private double idleTimeO;
    private double walkTimeO;
    private float walkWeightO;
    private double attackTimeO = -1;
    private float sneakWeightO;

    void tick(MorphRig rig, double seconds, double speed, boolean sneaks) {
        idleTimeO = idleTime;
        walkTimeO = walkTime;
        walkWeightO = walkWeight;
        attackTimeO = attackTime;
        sneakWeightO = sneakWeight;

        idleTime += seconds;
        double moving = speed < STILL_SPEED ? 0 : speed;
        walkTime += moving * seconds / rig.stride * rig.walk.length();
        float target = (float) Math.min(1.0, moving / FULL_WALK_SPEED);
        walkWeight += (target - walkWeight) * WEIGHT_FOLLOW;
        if (walkWeight < 0.001f) walkWeight = 0f;
        float sneakTarget = sneaks && rig.sneak != null ? 1f : 0f;
        sneakWeight += (sneakTarget - sneakWeight) * SNEAK_FOLLOW;
        if (Math.abs(sneakTarget - sneakWeight) < 0.001f) sneakWeight = sneakTarget;
        if (attackTime >= 0) {
            attackTime += seconds;
            if (attackTime > rig.attack.length() + ATTACK_OUT) attackTime = -1;
        }
    }

    void attack() {
        if (attackTime < 0 || attackTime > ATTACK_RESTART) {
            attackTime = 0;
            attackTimeO = 0;
        }
    }

    /** Die Haltung zwischen dem letzten und diesem Tick ({@code partialTick} 0..1). */
    void pose(MorphRig rig, MorphRig.Pose out, MorphRig.Pose scratch, float partialTick) {
        double idle = lerp(idleTimeO, idleTime, partialTick);
        double walk = lerp(walkTimeO, walkTime, partialTick);
        float weight = (float) lerp(walkWeightO, walkWeight, partialTick);
        double attack = attackTime < 0 ? -1 : attackTimeO < 0 ? attackTime : lerp(attackTimeO, attackTime, partialTick);

        rig.sample(rig.idle, idle, out);
        if (weight > 0f) {
            rig.sample(rig.walk, walk, scratch);
            rig.blend(out, scratch, weight, null);
        }
        float attackWeight = attackWeight(rig, attack);
        if (attackWeight > 0f) {
            rig.sample(rig.attack, attack, scratch);
            rig.blend(out, scratch, attackWeight, rig.attack);
        }
        float sneak = (float) lerp(sneakWeightO, sneakWeight, partialTick);
        if (sneak > 0f && rig.sneak != null) {
            rig.sample(rig.sneak, 0, scratch);
            rig.addOn(out, scratch, sneak, rig.sneak);
        }
    }

    private static float attackWeight(MorphRig rig, double time) {
        if (time < 0) return 0f;
        double in = time / ATTACK_IN;
        double out = (rig.attack.length() + ATTACK_OUT - time) / ATTACK_OUT;
        return (float) Math.max(0.0, Math.min(1.0, Math.min(in, out)));
    }

    private static double lerp(double a, double b, float f) {
        return a + (b - a) * f;
    }
}
