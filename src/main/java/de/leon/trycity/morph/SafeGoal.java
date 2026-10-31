package de.leon.trycity.morph;

import de.leon.trycity.TryCityClient;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ein Vanilla-Goal im Mob-Verhalten ({@link MobBehavior}), abgesichert. Es läuft 1:1 –
 * wirft es aber im Client (meist, weil es etwas braucht, was nur der Server hat: Dorf,
 * Spielregeln, Raids), fällt es bei diesem Mob weg und die übrigen laufen weiter.
 */
final class SafeGoal extends Goal {

    /** Jede Goal-Klasse nur einmal ins Log. */
    private static final Set<String> GEMELDET = ConcurrentHashMap.newKeySet();

    private final Goal goal;
    private boolean kaputt;

    private SafeGoal(Goal goal) {
        this.goal = goal;
    }

    /** Alle Goals dieser Auswahl einpacken – gleiche Rangfolge, gleiche Reihenfolge. */
    static void wrapAll(GoalSelector selector) {
        List<WrappedGoal> alle = new ArrayList<>(selector.getAvailableGoals());
        selector.removeAllGoals(goal -> true);
        for (WrappedGoal wrapped : alle) {
            Goal goal = wrapped.getGoal();
            selector.addGoal(wrapped.getPriority(), goal instanceof SafeGoal ? goal : new SafeGoal(goal));
        }
    }

    private void kaputt(Throwable e) {
        kaputt = true;
        if (GEMELDET.add(goal.getClass().getName())) {
            TryCityClient.LOG.info("[TryCity] Mob-Verhalten: {} geht im Client nicht ({})",
                    goal.getClass().getSimpleName(), e.toString());
        }
    }

    @Override
    public boolean canUse() {
        if (kaputt) return false;
        try {
            return goal.canUse();
        } catch (RuntimeException | LinkageError e) {
            kaputt(e);
            return false;
        }
    }

    @Override
    public boolean canContinueToUse() {
        if (kaputt) return false;
        try {
            return goal.canContinueToUse();
        } catch (RuntimeException | LinkageError e) {
            kaputt(e);
            return false;
        }
    }

    @Override
    public void start() {
        if (kaputt) return;
        try {
            goal.start();
        } catch (RuntimeException | LinkageError e) {
            kaputt(e);
        }
    }

    @Override
    public void stop() {
        try {
            goal.stop();
        } catch (RuntimeException | LinkageError e) {
            kaputt(e);
        }
    }

    @Override
    public void tick() {
        if (kaputt) return;
        try {
            goal.tick();
        } catch (RuntimeException | LinkageError e) {
            kaputt(e);
        }
    }

    @Override
    public boolean isInterruptable() {
        return goal.isInterruptable();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return goal.requiresUpdateEveryTick();
    }

    @Override
    public EnumSet<Flag> getFlags() {
        return goal.getFlags();
    }

    @Override
    public String toString() {
        return goal.toString();
    }
}
