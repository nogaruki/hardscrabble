package dev.mite.entity.ai;

import dev.mite.combat.Archery;
import dev.mite.entity.mob.DemonSpider;
import dev.mite.entity.mob.EarthElemental;
import dev.mite.entity.mob.Ghoul;
import dev.mite.mixin.ZombieVillagerAccessor;
import java.util.Collection;
import java.util.List;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.RestrictSunGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.golem.SnowGolem;
import net.minecraft.world.entity.animal.feline.Ocelot;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.entity.monster.cubemob.MagmaCube;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.spider.CaveSpider;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingConversionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jspecify.annotations.Nullable;

/**
 * EF-IA-10 side rules: predation (K06), endermen and pearls (K07), ghasts (K09), magma cubes (K10), small rules (K11),
 * strongholds (K13), and the vanilla mob behaviours of the changelog: skeletons (l.139, l.591, l.832, l.839), endermen
 * (l.590), silverfish (l.302), iron golems (l.280), zombie villagers (l.241), ocelots and snow golems (l.433-434) and spawned gear (l.841).
 */
public final class MobRules {
    private MobRules() {}

    /** EF-IA-10: a ghast spawn is refused 80 % of the time with another ghast within 48 blocks. */
    static final double GHAST_RADIUS = 48;
    static final float GHAST_REFUSAL = 0.8f;
    /** EF-IA-10: no ghast spawns near a player [INFÉRÉ: no distance in the spec, same 48 blocks]. */
    static final double GHAST_PLAYER_RADIUS = 48;
    /** l.591: wander speed of skeletons and ghouls, against 1.0 [INFÉRÉ: "decreased"]. */
    public static final double SLOW_WANDER_SPEED = 0.7;
    /** l.280: extra horizontal reach of the iron golem, in blocks, over the mob default 0.83 [INFÉRÉ: "increased"]. */
    public static final double IRON_GOLEM_EXTRA_REACH = 1;
    /** l.841: chance that an enchanted spawned piece of gear loses its enchantments [INFÉRÉ: "much lower chance"]. */
    public static final float GEAR_DISENCHANT_CHANCE = 0.75f;

    // ---- K06 predation, K07 endermen, skeletons, silverfish, iron golems ----

    private static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getEntity() instanceof Zombie zombie && zombie.is(AiContent.DIGGER) && !(zombie instanceof EarthElemental)) {
            zombie.goalSelector.addGoal(6, new FetchItemGoal(zombie, (m, s) -> zombieFood(s), MobRules::eatMeat));
            zombie.targetSelector.addGoal(5, new NearestAttackableTargetGoal<>(zombie, Animal.class, true).setUnseenMemoryTicks(AiContent.DIGGER_UNSEEN_MEMORY));
        } else if (event.getEntity() instanceof Spider spider) {
            if (spider instanceof CaveSpider || spider instanceof DemonSpider) dayHostile(spider);
            spider.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(spider, Chicken.class, true));
        } else if (event.getEntity() instanceof Enderman enderman) {
            enderman.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(enderman, Player.class, true, (t, l) -> t instanceof Player p && carriesEnderItem(p)));
            enderman.goalSelector.addGoal(6, new FetchItemGoal(enderman, MobRules::endermanWants, MobRules::endermanTakes));
        } else if (event.getEntity() instanceof AbstractSkeleton skeleton) {
            skeleton.goalSelector.removeAllGoals(g -> g instanceof RestrictSunGoal);
            skeleton.goalSelector.addGoal(2, new MeleeSunGoal(skeleton));
            skeleton.goalSelector.addGoal(4, new FetchItemGoal(skeleton, (m, s) -> {
                int held = bowRank(m.getMainHandItem());
                return held >= 0 && bowRank(s) > held;
            }, MobRules::takeBow));
        } else if (event.getEntity() instanceof Silverfish silverfish) {
            silverfish.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(silverfish, AbstractVillager.class, true));
            silverfish.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(silverfish, Animal.class, true));
        } else if (event.getEntity() instanceof IronGolem golem) {
            golem.goalSelector.removeAllGoals(g -> g instanceof MeleeAttackGoal);
            golem.goalSelector.addGoal(1, new ReachMeleeAttackGoal(golem, 1.0, true, IRON_GOLEM_EXTRA_REACH));
        } else if (event.getEntity() instanceof Ocelot ocelot) {
            ocelot.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(ocelot, Bat.class, false)); // l.433 (R140)
        } else if (event.getEntity() instanceof SnowGolem snowman) {
            snowman.goalSelector.addGoal(0, new FloatGoal(snowman)); // l.434 (R140): swims; the stroll goal already leads it out of water
        }
        if (event.getEntity() instanceof AbstractSkeleton || event.getEntity() instanceof Ghoul) slowStroll((PathfinderMob) event.getEntity());
    }

    /**
     * EF-IA-04 / EF-MOB-08/09 (changelog: cave and demon spiders are no longer peaceful in daylight): the vanilla spider goals
     * give up above brightness 0.5, plain melee and target goals replace them. Covers the Wood Spider (a cave spider).
     */
    static void dayHostile(Spider spider) {
        spider.goalSelector.removeAllGoals(g -> g instanceof MeleeAttackGoal);
        spider.goalSelector.addGoal(4, new MeleeAttackGoal(spider, 1.0, true));
        spider.targetSelector.removeAllGoals(g -> g instanceof NearestAttackableTargetGoal);
        spider.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(spider, Player.class, true));
        spider.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(spider, IronGolem.class, true));
    }

    /** EF-IA-10: an ender pearl or an eye of ender anywhere in the inventory. */
    public static boolean carriesEnderItem(Player player) {
        return player.getInventory().contains(MobRules::enderItem);
    }

    static boolean enderItem(ItemStack stack) {
        return stack.is(Items.ENDER_PEARL) || stack.is(Items.ENDER_EYE);
    }

    /** EF-IA-10: the meat zombies eat; not rotten flesh (vanilla puts it in the meat tag), the drop of slain zombies. */
    static boolean zombieFood(ItemStack stack) {
        return stack.is(ItemTags.MEAT) && !stack.is(Items.ROTTEN_FLESH);
    }

    /** EF-IA-10: zombies walk to meat lying on the ground and eat it, healing its nutrition [INFÉRÉ: heal]. */
    static void eatMeat(PathfinderMob mob, ItemEntity meat) {
        ItemStack stack = meat.getItem();
        FoodProperties food = stack.get(DataComponents.FOOD);
        if (food != null) mob.heal(food.nutrition());
        stack.shrink(1);
        if (stack.isEmpty()) meat.discard();
    }

    /** l.590: an enderman picks up ender pearls and eyes, stacking them in its hand; they drop when it dies. */
    static boolean endermanWants(PathfinderMob enderman, ItemStack stack) {
        ItemStack held = enderman.getMainHandItem();
        return enderItem(stack) && (held.isEmpty() || ItemStack.isSameItemSameComponents(held, stack) && held.getCount() < held.getMaxStackSize());
    }

    static void endermanTakes(PathfinderMob enderman, ItemEntity item) {
        ItemStack held = enderman.getMainHandItem(), stack = item.getItem();
        int moved = held.isEmpty() ? stack.getCount() : Math.min(stack.getCount(), held.getMaxStackSize() - held.getCount());
        enderman.take(item, moved);
        if (held.isEmpty()) enderman.setItemSlot(EquipmentSlot.MAINHAND, stack.split(moved));
        else {
            held.grow(moved);
            stack.shrink(moved);
        }
        enderman.setGuaranteedDrop(EquipmentSlot.MAINHAND);
        if (stack.isEmpty()) item.discard();
    }

    // ---- skeletons (l.139, l.591, l.839) ----

    /** l.839: wooden bow 0 < ancient metal < mithril (EF-CBT-07), enchantments break ties; -1 for anything else. */
    public static int bowRank(ItemStack stack) {
        if (!(stack.getItem() instanceof BowItem)) return -1;
        int material = stack.getItem() instanceof Archery.MiteBow bow ? 1 + bow.material.ordinal() : 0;
        return material * 100 + stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).size();
    }

    /** l.839: the better bow replaces the held one, which is dropped. */
    static void takeBow(PathfinderMob skeleton, ItemEntity item) {
        ItemStack old = skeleton.getMainHandItem();
        skeleton.take(item, 1);
        skeleton.setItemSlot(EquipmentSlot.MAINHAND, item.getItem().split(1));
        skeleton.setGuaranteedDrop(EquipmentSlot.MAINHAND);
        if (item.getItem().isEmpty()) item.discard();
        if (!old.isEmpty() && skeleton.level() instanceof ServerLevel level) skeleton.spawnAtLocation(level, old);
    }

    /** l.139: a skeleton with a melee weapon walks into sunlight to reach its target; idle, or with a bow, it shuns the sun as in vanilla. */
    static final class MeleeSunGoal extends RestrictSunGoal {
        private final PathfinderMob mob;

        MeleeSunGoal(PathfinderMob mob) {
            super(mob);
            this.mob = mob;
        }

        @Override
        public boolean canUse() {
            return super.canUse() && !chasingWithMelee(mob);
        }
    }

    /** l.139: a target and no bow in hand. */
    static boolean chasingWithMelee(PathfinderMob mob) {
        return mob.getTarget() != null && !(mob.getMainHandItem().getItem() instanceof ProjectileWeaponItem);
    }

    /** l.591: skeletons and ghouls wander at {@link #SLOW_WANDER_SPEED}. */
    static void slowStroll(PathfinderMob mob) {
        List<WrappedGoal> strolls = mob.goalSelector.getAvailableGoals().stream().filter(w -> w.getGoal() instanceof RandomStrollGoal).toList();
        for (WrappedGoal w : strolls) {
            mob.goalSelector.removeGoal(w.getGoal());
            mob.goalSelector.addGoal(w.getPriority(), new SlowStrollGoal(mob));
        }
    }

    static final class SlowStrollGoal extends WaterAvoidingRandomStrollGoal {
        SlowStrollGoal(PathfinderMob mob) {
            super(mob, SLOW_WANDER_SPEED);
        }
    }

    /** l.280: a melee goal whose reach is extended by {@code extraReach} blocks horizontally. */
    static final class ReachMeleeAttackGoal extends MeleeAttackGoal {
        private final double extraReach;

        ReachMeleeAttackGoal(PathfinderMob mob, double speed, boolean followEvenIfNotSeen, double extraReach) {
            super(mob, speed, followEvenIfNotSeen);
            this.extraReach = extraReach;
        }

        @Override
        protected boolean canPerformAttack(LivingEntity target) {
            return isTimeToAttack() && reaches(mob, target, extraReach) && mob.getSensing().hasLineOfSight(target);
        }
    }

    /** Vanilla reach (box inflated by sqrt(2.04) - 0.6 horizontally) plus {@code extra}. */
    static boolean reaches(Mob mob, LivingEntity target, double extra) {
        double reach = Math.sqrt(2.04) - 0.6 + extra;
        return mob.isWithinMeleeAttackRange(target) || mob.getBoundingBox().inflate(reach, 0, reach).intersects(target.getHitbox());
    }

    /** l.841: spawned gear keeps its enchantments only {@code 1 - GEAR_DISENCHANT_CHANCE} of the time. */
    public static void disenchantGear(Mob mob, RandomSource random) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = mob.getItemBySlot(slot);
            if (stack.isEnchanted() && random.nextFloat() < GEAR_DISENCHANT_CHANCE) stack.set(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        }
    }

    /** l.832: skeleton archers drop half as many arrows (each arrow kept with a chance of 1/2) [INFÉRÉ: "fewer"]. */
    private static void onDrops(LivingDropsEvent event) {
        if (event.getEntity() instanceof AbstractSkeleton skeleton) fewerArrows(event.getDrops(), skeleton.getRandom());
    }

    static void fewerArrows(Collection<ItemEntity> drops, RandomSource random) {
        for (ItemEntity drop : drops) {
            ItemStack stack = drop.getItem();
            if (!stack.is(Items.ARROW)) continue;
            int kept = 0;
            for (int i = 0; i < stack.getCount(); i++) if (random.nextBoolean()) kept++;
            stack.setCount(kept);
        }
        drops.removeIf(d -> d.getItem().isEmpty());
    }

    /** l.241 (R163): an enchanted golden apple converts a weakened zombie villager at once (vanilla only takes the golden apple, slowly). */
    static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        ItemStack stack = event.getItemStack();
        if (!stack.is(Items.ENCHANTED_GOLDEN_APPLE) || !(event.getTarget() instanceof ZombieVillager zombie) || !zombie.hasEffect(MobEffects.WEAKNESS)
                || zombie.isConverting()) return;
        if (!event.getLevel().isClientSide()) {
            stack.consume(1, event.getEntity());
            ((ZombieVillagerAccessor) zombie).mite$startConverting(event.getEntity().getUUID(), 0);
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    // ---- K09 ghasts, K13 strongholds ----

    private static void onPositionCheck(MobSpawnEvent.PositionCheck event) {
        if (event.getSpawnType() == EntitySpawnReason.NATURAL && event.getEntity().getType() == EntityTypes.GHAST
                && ghastRefused(event.getLevel().getLevel(), new Vec3(event.getX(), event.getY(), event.getZ()), event.getEntity().getRandom())) {
            event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
        }
    }

    /** EF-IA-10: never near a player, 80 % refused near another ghast. */
    static boolean ghastRefused(ServerLevel level, Vec3 pos, RandomSource random) {
        if (level.getNearestPlayer(pos.x, pos.y, pos.z, GHAST_PLAYER_RADIUS, false) != null) return true;
        return !level.getEntitiesOfClass(Ghast.class, AABB.ofSize(pos, 2 * GHAST_RADIUS, 2 * GHAST_RADIUS, 2 * GHAST_RADIUS),
                g -> g.distanceToSqr(pos) <= GHAST_RADIUS * GHAST_RADIUS).isEmpty() && random.nextFloat() < GHAST_REFUSAL;
    }

    /**
     * EF-IA-10 / ET-K-05: hostile density rises inside strongholds and Nether fortresses: each natural monster spawn there
     * brings one more of its kind [INFÉRÉ: x2, the spec gives no figure, Q-21]. Changelog l.708 (R121): near a stronghold the
     * density builds up as the player gets closer, with a {@link #strongholdBonus} chance of that extra monster.
     */
    private static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        Mob mob = event.getEntity();
        if (event.isSpawnCancelled() || event.getSpawnType() != EntitySpawnReason.NATURAL || mob.getType().getCategory() != MobCategory.MONSTER) return;
        ServerLevel level = event.getLevel().getLevel();
        BlockPos pos = BlockPos.containing(event.getX(), event.getY(), event.getZ());
        if (inFortress(level, pos) || mob.getRandom().nextFloat() < strongholdBonus(strongholdDistance(level, pos)))
            mob.getType().spawn(level, pos, EntitySpawnReason.REINFORCEMENT);
    }

    /** Changelog l.708: radius of the build-up around a stronghold [INFÉRÉ: 256 blocks, vanilla gives no figure]. */
    static final double STRONGHOLD_REACH = 256;

    /** Chance of one extra monster: 1 at the stronghold, falling linearly to 0 at {@link #STRONGHOLD_REACH}. */
    static float strongholdBonus(double distance) {
        return (float) Math.clamp(1 - distance / STRONGHOLD_REACH, 0, 1);
    }

    /** Horizontal distance to the nearest stronghold start (the precomputed ring positions), infinite without strongholds. */
    static double strongholdDistance(ServerLevel level, BlockPos pos) {
        var state = level.getChunkSource().getGeneratorState();
        double best = Double.POSITIVE_INFINITY;
        var stronghold = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(BuiltinStructures.STRONGHOLD);
        if (stronghold.isEmpty()) return best;
        for (StructurePlacement placement : state.getPlacementsForStructure(stronghold.get())) {
            if (!(placement instanceof ConcentricRingsStructurePlacement rings)) continue;
            List<ChunkPos> starts = state.getRingPositionsFor(rings);
            if (starts != null) best = Math.min(best, nearest(starts, pos));
        }
        return best;
    }

    /** Horizontal distance from {@code pos} to the middle of the nearest chunk of {@code starts}, infinite when empty. */
    static double nearest(List<ChunkPos> starts, BlockPos pos) {
        double best = Double.POSITIVE_INFINITY;
        for (ChunkPos c : starts) best = Math.min(best, Math.hypot(c.getMiddleBlockX() - pos.getX(), c.getMiddleBlockZ() - pos.getZ()));
        return best;
    }

    static boolean inFortress(ServerLevel level, BlockPos pos) {
        return level.structureManager().getStructureWithPieceAt(pos, s -> s.is(BuiltinStructures.STRONGHOLD) || s.is(BuiltinStructures.FORTRESS)).isValid();
    }

    // ---- K10 magma cubes, K11 small rules ----

    /**
     * EF-IA-10: a magma cube is hurt by an attacker only through a tool efficient against stone (arrows and bare hands do
     * nothing; fire, lava and other sourceless damage keep vanilla rules). Skeletons ignore skeleton arrows.
     */
    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        var source = event.getSource();
        if (target instanceof MagmaCube && source.getEntity() != null && !efficientOnStone(source.getWeaponItem())
                || target instanceof AbstractSkeleton && source.getDirectEntity() instanceof AbstractArrow arrow && arrow.getOwner() instanceof AbstractSkeleton) {
            event.setCanceled(true);
        }
    }

    static boolean efficientOnStone(@Nullable ItemStack weapon) {
        return weapon != null && weapon.getDestroySpeed(Blocks.STONE.defaultBlockState()) > 1;
    }

    /** EF-IA-10 / ET-K-07: a villager killed by a mob holding a weapon does not become a zombie villager. */
    private static void onConversion(LivingConversionEvent.Pre event) {
        if (event.getEntity() instanceof Villager villager && event.getOutcome() == EntityTypes.ZOMBIE_VILLAGER
                && villager.getLastHurtByMob() instanceof Mob killer && !killer.getMainHandItem().isEmpty()) {
            event.setCanceled(true);
        }
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(MobRules::onJoin);
        NeoForge.EVENT_BUS.addListener(MobRules::onPositionCheck);
        // LOW: after the handlers that cancel a spawn (the Infernal Creeper swap of MobContent), so a cancelled spawn brings no extra monster.
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, MobRules::onFinalizeSpawn);
        NeoForge.EVENT_BUS.addListener(MobRules::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(MobRules::onConversion);
        NeoForge.EVENT_BUS.addListener(MobRules::onDrops);
        NeoForge.EVENT_BUS.addListener(MobRules::onEntityInteract);
    }
}
