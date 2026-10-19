package dev.mite.entity.ai;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * A mob without a target walks to a dropped item it wants and takes it: skeleton archers and better bows (changelog l.839),
 * endermen and ender pearls or eyes (l.590). The mob decides what it wants and what taking means.
 */
public class FetchItemGoal extends Goal {
    /** Search radius and chance per tick to look around [INFÉRÉ]. */
    public static final double RANGE = 8;
    static final int SEARCH_ODDS = 10;
    /** An item still out of reach after this many ticks is given up, the path is renewed every {@link #REPATH_TICKS} [INFÉRÉ]. */
    static final int GIVE_UP_TICKS = 200, REPATH_TICKS = 10;
    private final PathfinderMob mob;
    private final BiPredicate<PathfinderMob, ItemStack> wants;
    private final BiConsumer<PathfinderMob, ItemEntity> take;
    private @Nullable ItemEntity item;
    /** ponytail: only the last item given up is remembered; a set if a mob ever faces several unreachable items at once. */
    private @Nullable ItemEntity unreachable;
    private int ticks;

    public FetchItemGoal(PathfinderMob mob, BiPredicate<PathfinderMob, ItemStack> wants, BiConsumer<PathfinderMob, ItemEntity> take) {
        this.mob = mob;
        this.wants = wants;
        this.take = take;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (mob.getTarget() != null || mob.getRandom().nextInt(SEARCH_ODDS) != 0) return false;
        item = mob.level().getEntitiesOfClass(ItemEntity.class, mob.getBoundingBox().inflate(RANGE), e -> e != unreachable && !e.hasPickUpDelay() && wants.test(mob, e.getItem()))
                .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(mob))).orElse(null);
        return item != null;
    }

    @Override
    public boolean canContinueToUse() {
        return item != null && item.isAlive() && mob.getTarget() == null && wants.test(mob, item.getItem()) && ticks < GIVE_UP_TICKS;
    }

    @Override
    public void start() {
        ticks = 0;
    }

    @Override
    public void tick() {
        if (item == null) return;
        if (mob.distanceToSqr(item) > 2.25) {
            // Ground navigation builds no path for a mob in the air (just spawned, jumping, knocked back): only a failed path from
            // the ground or water proves the item unreachable, otherwise the next repath tries again.
            if (++ticks >= GIVE_UP_TICKS || ticks % REPATH_TICKS == 1 && mob.getNavigation().isDone() && !mob.getNavigation().moveTo(item, 1.0)
                    && (mob.onGround() || mob.isInLiquid())) {
                unreachable = item;
                ticks = GIVE_UP_TICKS;
            }
            return;
        }
        take.accept(mob, item);
        item = null;
    }

    @Override
    public void stop() {
        item = null;
        mob.getNavigation().stop();
    }
}
