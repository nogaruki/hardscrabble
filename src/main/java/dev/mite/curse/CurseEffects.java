package dev.mite.curse;

import dev.mite.enchanting.StrongboxBlock;
import dev.mite.metabolism.MetabolismContent;
import dev.mite.metabolism.Nutrition;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import java.util.Map;
import java.util.WeakHashMap;
import net.neoforged.neoforge.entity.PartEntity;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.Prediction;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.UseRemainder;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.component.Weapon;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.living.ArmorHurtEvent;
import net.neoforged.neoforge.event.entity.living.LivingBreatheEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The 16 curse effects (EF-MAL-02), each hooked on an existing event, no mixin (ET-O-02). Clumsiness' crafting part is read
 * by the crafting domain through {@link Curses#isClumsy}. Every hook goes through {@link Curses#trigger}, which reveals the
 * curse the first time it bites. Unverified factors are marked [NON VÉRIFIÉ].
 */
final class CurseEffects {
    private CurseEffects() {}

    /** #1 equipment wears this many times faster [NON VÉRIFIÉ]. */
    static final int DECAY_FACTOR = 2;
    /** #2 air drains this many times faster underwater [NON VÉRIFIÉ]. */
    static final int BREATH_FACTOR = 4;
    /** #8 chance per second to drop the held item [NON VÉRIFIÉ]. */
    static final float DROP_CHANCE = 1 / 60f;
    /** #9 horizontal velocity multiplier per tick in vegetation [NON VÉRIFIÉ]; vertical speed and fall distance are kept. */
    static final double TANGLED = 0.5;
    /** #13-16 chance that a melee swing at the feared mob misses [NON VÉRIFIÉ]. */
    static final float FEAR_MISS = 0.5f;

    /** Last melee swing of each player (server): its target and game tick, to tell the target from the sweep's other victims. */
    private record Swing(int target, long tick) {}
    private static final Map<Player, Swing> SWINGS = new WeakHashMap<>();

    private static final Player.BedSleepingProblem NO_SLEEP = new Player.BedSleepingProblem(Component.translatable("hardscrabble.curse.cant_sleep.refused"));

    /** #4, #5, #6: true when a curse forbids eating (protein or milk: animal, phytonutrients: plant) or drinking this item. */
    static boolean refused(Player p, ItemStack stack) {
        if (drinkable(stack) && Curses.trigger(p, Curse.CANT_DRINK)) return true;
        Nutrition n = stack.typeHolder().getData(MetabolismContent.NUTRITION);
        boolean animal = n != null && n.protein() || stack.is(Tags.Items.BUCKETS_MILK) || stack.is(Tags.Items.DRINKS_MILK);
        return animal && Curses.trigger(p, Curse.CANT_EAT_ANIMALS) || n != null && n.phyto() && Curses.trigger(p, Curse.CANT_EAT_PLANTS);
    }

    /** #6 (O-2): drinks, and the bowl dishes (soups, stews, milk bowl) that leave their bowl, which are drunk too. */
    static boolean drinkable(ItemStack stack) {
        UseRemainder rest = stack.get(DataComponents.USE_REMAINDER);
        return stack.getUseAnimation() == ItemUseAnimation.DRINK || rest != null && rest.convertInto().item().value() == Items.BOWL;
    }

    static boolean feared(Player player, Entity target) {
        Curse c = target instanceof Spider ? Curse.FEAR_OF_SPIDERS : target instanceof Wolf ? Curse.FEAR_OF_WOLVES
                : target instanceof Creeper ? Curse.FEAR_OF_CREEPERS : target.is(EntityTypeTags.UNDEAD) ? Curse.FEAR_OF_UNDEAD : null;
        return c != null && Curses.trigger(player, c);
    }

    /**
     * Both sides, before movement. #3: the client no longer starts sprinting once the curse is known ({@link Curses#canSprint});
     * the server stops (and reveals) a sprint. #9: velocity, not makeStuckInBlock, which would reset the fall distance.
     */
    static void tickMovement(Player p) {
        if (p.isSprinting() && Curses.trigger(p, Curse.CANT_RUN)) p.setSprinting(false);
        if (p.level().getBlockState(p.blockPosition()).is(Curses.ENTANGLING) && Curses.trigger(p, Curse.ENTANGLEMENT)) {
            p.setDeltaMovement(p.getDeltaMovement().multiply(TANGLED, 1, TANGLED));
        }
    }

    /** #11: chests, strongboxes and the modern storage that would bypass them (O-1): barrels and shulker boxes. */
    static boolean isChest(BlockState state) {
        return state.is(Tags.BlockItems.CHESTS.block()) || state.is(Tags.BlockItems.BARRELS.block()) || state.is(BlockTags.SHULKER_BOXES)
                || state.getBlock() instanceof StrongboxBlock;
    }

    /** #11 (O-1): the chest screens opened another way (chest minecart or boat, from the seat or by clicking) close at once. */
    static boolean isChestMenu(AbstractContainerMenu menu) {
        return menu instanceof ChestMenu || menu instanceof ShulkerBoxMenu;
    }

    /** Server, once a second: armor (#10), endermen (#7), clumsy drop (#8). */
    static void tickServer(ServerPlayer p) {
        Curse c = Curses.active(p);
        if (c == Curse.CANT_WEAR_ARMOR) {
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                if (slot.isArmor() && !p.getItemBySlot(slot).isEmpty() && Curses.trigger(p, c)) {
                    ItemStack armor = p.getItemBySlot(slot);
                    p.setItemSlot(slot, ItemStack.EMPTY);
                    if (!p.getInventory().add(armor)) p.drop(armor, false, Prediction.SERVER_ONLY);
                }
            }
        } else if (c == Curse.ENDERMEN_ENEMY) {
            for (Enderman e : p.level().getEntitiesOfClass(Enderman.class, p.getBoundingBox().inflate(16))) {
                if (e.getTarget() == null && Curses.trigger(p, c)) e.setTarget(p);
            }
        } else if (c == Curse.CLUMSINESS && !p.getMainHandItem().isEmpty() && p.getRandom().nextFloat() < DROP_CHANCE && Curses.trigger(p, c)) {
            p.drop(true);
        }
    }

    static void register(IEventBus bus) {
        bus.addListener((PlayerTickEvent.Pre e) -> tickMovement(e.getEntity()));
        bus.addListener((PlayerTickEvent.Post e) -> {
            if (e.getEntity() instanceof ServerPlayer p && p.tickCount % 20 == 10) tickServer(p);
        });
        bus.addListener((ArmorHurtEvent e) -> {
            if (e.getEntity() instanceof Player p && Curses.trigger(p, Curse.EQUIPMENT_DECAY)) {
                e.getArmorMap().values().forEach(a -> a.newDamage *= DECAY_FACTOR);
            }
        });
        // #1 attack wear: vanilla applied the weapon's own wear once, add the rest (block wear: HarvestRules via Curses.wearFactor).
        // Vanilla wears the weapon for the swing's target only: the mobs a sweep hits in the same tick cost nothing.
        bus.addListener((LivingDamageEvent.Post e) -> {
            if (!e.getSource().is(DamageTypes.PLAYER_ATTACK) || !(e.getSource().getDirectEntity() instanceof Player p)) return;
            Swing swing = SWINGS.get(p);
            if (swing != null && swing.tick() == p.level().getGameTime() && swing.target() != e.getEntity().getId()) return;
            Weapon weapon = p.getMainHandItem().get(DataComponents.WEAPON);
            if (weapon != null && p.getMainHandItem().isDamageableItem() && Curses.trigger(p, Curse.EQUIPMENT_DECAY)) {
                p.getMainHandItem().hurtAndBreak((DECAY_FACTOR - 1) * weapon.itemDamagePerAttack(), p, EquipmentSlot.MAINHAND);
            }
        });
        bus.addListener((LivingBreatheEvent e) -> {
            if (!e.canBreathe() && e.getEntity() instanceof Player p && Curses.trigger(p, Curse.CANT_HOLD_BREATH)) {
                e.setConsumeAirAmount(e.getConsumeAirAmount() * BREATH_FACTOR);
            }
        });
        bus.addListener((LivingEntityUseItemEvent.Start e) -> {
            if (e.getEntity() instanceof Player p && refused(p, e.getItem())) e.setCanceled(true);
        });
        bus.addListener((PlayerInteractEvent.RightClickBlock e) -> {
            if (isChest(e.getLevel().getBlockState(e.getPos())) && Curses.trigger(e.getEntity(), Curse.CANT_OPEN_CHESTS)) e.setCanceled(true);
        });
        bus.addListener((PlayerContainerEvent.Open e) -> {
            if (e.getEntity() instanceof ServerPlayer p && isChestMenu(e.getContainer()) && Curses.trigger(p, Curse.CANT_OPEN_CHESTS)) p.closeContainer();
        });
        bus.addListener((CanPlayerSleepEvent e) -> {
            if (e.getProblem() == null && Curses.trigger(e.getEntity(), Curse.CANT_SLEEP)) e.setProblem(NO_SLEEP);
        });
        // Server only: the client's own roll would disagree half the time. A miss sounds like a swing that does no damage.
        bus.addListener((AttackEntityEvent e) -> {
            if (!(e.getEntity() instanceof ServerPlayer p)) return;
            Entity target = e.getTarget() instanceof PartEntity<?> part ? part.getParent() : e.getTarget();
            SWINGS.put(p, new Swing(target.getId(), p.level().getGameTime()));
            if (feared(p, e.getTarget()) && p.getRandom().nextFloat() < FEAR_MISS) {
                e.setCanceled(true);
                p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_ATTACK_NODAMAGE, p.getSoundSource(), 1, 1);
            }
        });
    }
}
