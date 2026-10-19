package dev.mite.entity.mob;

import dev.mite.config.MiteConfig;
import dev.mite.mixin.AbstractCubeMobAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.cubemob.AbstractCubeMob;
import net.minecraft.world.entity.monster.cubemob.MagmaCube;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.RedstoneWallTorchBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Rules shared by every gelatinous cube, the vanilla slime included (EF-MOB-16, changelog R134/R137/R143):
 * they hunt animals (l.494) and villagers (l.457); an item that strikes them is eaten if susceptible (l.491); arrows shot into
 * an Ooze or a Pudding dissolve (l.492); sheep lose their wool to pepsin or acid (l.402); the slime digests dropped flesh like
 * the Jelly (l.480); cubes and magma cubes knock wall torches down when they bump into them (l.490). Slimes already cannot jump
 * over fences, gates and walls (l.497): their jump stays under the 1.5 block of those collision boxes.
 */
public final class CubeRules {
    private CubeRules() {}

    private static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Slime slime)) return;
        slime.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(slime, AbstractVillager.class, true));
        slime.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(slime, Animal.class, true));
    }

    private static void onTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof AbstractCubeMob cube) || !(cube.level() instanceof ServerLevel level)) return;
        if (!(cube instanceof Slime) && !(cube instanceof MagmaCube)) return;
        if (cube.horizontalCollision) knockTorches(level, cube);
        if (!(cube instanceof Slime slime)) return;
        // Vanilla cubes only hurt players and iron golems on contact: a hunted animal or villager is hurt the same way.
        if (slime.getTarget() instanceof LivingEntity prey && !(prey instanceof Player) && !(prey instanceof IronGolem)
                && slime.getBoundingBox().inflate(0.1).intersects(prey.getBoundingBox()) && ((AbstractCubeMobAccessor) slime).mite$canDealDamage()) {
            ((AbstractCubeMobAccessor) slime).mite$dealDamage(prey);
        }
        if (!(slime instanceof GelatinousCube) && slime.tickCount % GelatinousCube.DISSOLVE_INTERVAL == 0 && MiteConfig.GELATINOUS_DISSOLVE_ITEMS.get())
            level.getEntitiesOfClass(ItemEntity.class, slime.getBoundingBox(), item -> GelatinousCube.dissolves(false, item.getItem())).forEach(item -> {
                item.discard();
                slime.playSound(SoundEvents.GENERIC_EXTINGUISH_FIRE, 0.5F, 1.5F);
            });
    }

    /** l.490: a wall torch in the way of a bumping cube falls (respects mobGriefing). */
    public static void knockTorches(ServerLevel level, AbstractCubeMob cube) {
        if (!EventHooks.canEntityGrief(level, cube)) return;
        BlockPos.betweenClosedStream(cube.getBoundingBox().inflate(0.3, 0, 0.3)).filter(p -> wallTorch(level.getBlockState(p)))
                .map(BlockPos::immutable).toList().forEach(p -> level.destroyBlock(p, true, cube));
    }

    static boolean wallTorch(BlockState state) {
        return state.getBlock() instanceof WallTorchBlock || state.getBlock() instanceof RedstoneWallTorchBlock;
    }

    /** True for the acid kinds (Ooze, Pudding); the slime, the Jelly and the Blob secrete pepsin. */
    static boolean acidic(Slime slime) {
        return slime instanceof GelatinousCube cube && cube.kind.acidic();
    }

    /** l.491: the item that strikes a cube is eaten by its pepsin or acid if susceptible. */
    private static void onAttack(AttackEntityEvent event) {
        Player player = event.getEntity();
        ItemStack held = player.getMainHandItem();
        if (!player.level().isClientSide() && event.getTarget() instanceof Slime slime && GelatinousCube.dissolves(acidic(slime), held))
            GelatinousCube.corrode(held, player, EquipmentSlot.MAINHAND);
    }

    /** l.492: an arrow shot into an Ooze or a Pudding dissolves at once, unrecoverable. */
    private static void onImpact(ProjectileImpactEvent event) {
        if (event.getProjectile() instanceof AbstractArrow arrow && !arrow.level().isClientSide()
                && event.getRayTraceResult() instanceof EntityHitResult hit && hit.getEntity() instanceof Slime slime && acidic(slime)) {
            arrow.playSound(SoundEvents.GENERIC_EXTINGUISH_FIRE, 0.5F, 1.5F);
            arrow.discard();
            event.setCanceled(true);
        }
    }

    /** l.402 (R143): a sheep hurt by pepsin or acid loses its wool. */
    private static void onDamage(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof Sheep sheep && event.getSource().getEntity() instanceof Slime && event.getInflictedDamage() > 0) sheep.setSheared(true);
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(CubeRules::onJoin);
        NeoForge.EVENT_BUS.addListener(CubeRules::onTick);
        NeoForge.EVENT_BUS.addListener(CubeRules::onAttack);
        NeoForge.EVENT_BUS.addListener(CubeRules::onImpact);
        NeoForge.EVENT_BUS.addListener(CubeRules::onDamage);
    }
}
