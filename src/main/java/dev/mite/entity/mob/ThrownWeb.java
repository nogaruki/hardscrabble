package dev.mite.entity.mob;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.event.EventHooks;

/**
 * EF-MOB-12 (R178): web thrown by spiders. It leaves a cobweb where it lands (on the struck entity's feet or against the
 * struck face) when mob griefing allows; a fiery web (Demon Spider, EF-MOB-09) also sets the struck entity on fire.
 */
public class ThrownWeb extends ThrowableItemProjectile {
    public static final int FIRE_SECONDS = 3; // [INFÉRÉ]
    private boolean fiery;

    public ThrownWeb(EntityType<? extends ThrownWeb> type, Level level) {
        super(type, level);
    }

    public ThrownWeb(Level level, LivingEntity owner, boolean fiery) {
        super(MobContent.THROWN_WEB.get(), owner, level, new ItemStack(Items.COBWEB));
        this.fiery = fiery;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean("Fiery", fiery);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        fiery = input.getBooleanOr("Fiery", false);
    }

    @Override
    protected Item getDefaultItem() {
        return Items.COBWEB;
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);
        if (fiery) hit.getEntity().igniteForSeconds(FIRE_SECONDS);
        placeWeb(hit.getEntity().blockPosition());
    }

    @Override
    protected void onHitBlock(BlockHitResult hit) {
        super.onHitBlock(hit);
        placeWeb(hit.getBlockPos().relative(hit.getDirection()));
    }

    @Override
    protected void onHit(HitResult hit) {
        super.onHit(hit);
        if (!level().isClientSide()) discard();
    }

    private void placeWeb(BlockPos pos) {
        if (level() instanceof ServerLevel level && level.getBlockState(pos).isAir() && EventHooks.canEntityGrief(level, getOwner()))
            level.setBlockAndUpdate(pos, Blocks.COBWEB.defaultBlockState());
    }

    /** Throws a web at the target's body. */
    public static void throwAt(Mob thrower, LivingEntity target, boolean fiery) {
        ThrownWeb web = new ThrownWeb(thrower.level(), thrower, fiery);
        double dx = target.getX() - thrower.getX(), dz = target.getZ() - thrower.getZ();
        double dy = target.getY(0.33) - web.getY() + Math.sqrt(dx * dx + dz * dz) * 0.2;
        web.shoot(dx, dy, dz, 1.2F, 4.0F);
        thrower.level().addFreshEntity(web);
    }

    /**
     * Added to every spider (vanilla and MITE). Runs beside the melee goal: one web every {@link #COOLDOWN} ticks at a seen
     * target 4 to 16 blocks away [INFÉRÉ, R178 gives no numbers].
     */
    public static final class ThrowGoal extends Goal {
        public static final int COOLDOWN = 100;
        private static final double MIN = 4, MAX = 16;
        private final Mob spider;
        private long nextThrow;

        public ThrowGoal(Mob spider) {
            this.spider = spider;
        }

        @Override
        public boolean canUse() {
            LivingEntity target = spider.getTarget();
            if (target == null || !target.isAlive() || spider.level().getGameTime() < nextThrow) return false;
            double d = spider.distanceToSqr(target);
            return d >= MIN * MIN && d <= MAX * MAX && spider.getSensing().hasLineOfSight(target);
        }

        @Override
        public void start() {
            throwAt(spider, spider.getTarget(), spider instanceof DemonSpider);
            nextThrow = spider.level().getGameTime() + COOLDOWN;
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }
    }
}
