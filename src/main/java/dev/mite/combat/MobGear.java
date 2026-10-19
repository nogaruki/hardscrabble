package dev.mite.combat;

import static dev.mite.core.Material.RUSTED_IRON;

import com.google.common.base.Suppliers;
import dev.mite.core.Material;
import dev.mite.core.MoonCalendar;
import dev.mite.core.ToolType;
import dev.mite.harvest.HarvestContent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.ArmorType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

/** Vanilla mob equipment and drops (EF-IA-05, K05): rusted iron instead of iron (EF-MET-09), skeleton blades, zombie nuggets, leaders. */
public final class MobGear {
    private MobGear() {}

    /** Mob gear swapped at spawn: iron, chainmail and diamond become rusted iron (mobs never carry real iron); modern vanilla
     *  weapons outside the MITE progression (trident, stone sword, golden spear, fishing rod) are replaced or dropped. */
    private static final Supplier<Map<Item, Item>> RUSTED = Suppliers.memoize(() -> {
        Map<Item, Item> map = new HashMap<>();
        for (ArmorType type : CombatContent.PIECES) {
            EquipmentSlot slot = type.getSlot();
            map.put(Mob.getEquipmentForSlot(slot, 3), CombatContent.armorItem(RUSTED_IRON, type, true)); // chainmail
            map.put(Mob.getEquipmentForSlot(slot, 4), CombatContent.armorItem(RUSTED_IRON, type, false)); // iron
            map.put(Mob.getEquipmentForSlot(slot, 5), CombatContent.armorItem(RUSTED_IRON, type, false)); // diamond
        }
        map.put(Items.IRON_SWORD, CombatContent.RUSTED_IRON_SWORD.get());
        map.put(Items.IRON_SPEAR, CombatContent.RUSTED_IRON_SWORD.get());
        map.put(Items.IRON_SHOVEL, CombatContent.RUSTED_IRON_DAGGER.get());
        map.put(Items.IRON_AXE, CombatContent.RUSTED_IRON_SWORD.get()); // vindicators
        map.put(Items.TRIDENT, CombatContent.RUSTED_IRON_SWORD.get()); // drowned
        map.put(Items.STONE_SWORD, CombatContent.RUSTED_IRON_SWORD.get()); // wither skeletons (no stone tools, EF-OUT-07)
        map.put(Items.GOLDEN_SPEAR, Items.GOLDEN_SWORD); // piglins, zombified piglins
        map.put(Items.FISHING_ROD, Items.AIR); // drowned (blacklisted, EF-PRG-04)
        return map;
    });

    /**
     * EF-IA-05 / changelog l.115 (R180) and K-4: 1 skeleton in {@link #SKELETON_MELEE_ODDS} is a melee skeleton with a wooden club
     * instead of a bow; after day 10 of the world, it occasionally ({@link #SKELETON_BLADE_ODDS}: 1 in 2) carries a rusted iron
     * dagger or sword instead of the club [INFÉRÉ: the odds; 1 blade skeleton in 20 overall, as before].
     */
    public static final int SKELETON_BLADE_DAY = 10, SKELETON_MELEE_ODDS = 10, SKELETON_BLADE_ODDS = 2;
    /** EF-IA-05: a zombie leader gets +1 to 4 health instead of the vanilla x2 to x5. */
    static final Identifier LEADER_ID = Identifier.withDefaultNamespace("leader_zombie_bonus");

    /** Applies the spawn rules to a freshly spawned mob, MITE mobs included (the Revenant's chainmail is rusted too). Public for tests. */
    public static void equip(Mob mob, long day, RandomSource random) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            Item rusted = RUSTED.get().get(mob.getItemBySlot(slot).getItem());
            if (rusted != null) mob.setItemSlot(slot, new ItemStack(rusted));
        }
        // Arbitrage n°22 (audit 2 m3/G-2): the crossbow is an iron-age weapon; pillagers (unlocked outposts) and piglins keep shooting it but never drop it.
        if (mob.getMainHandItem().is(Items.CROSSBOW)) mob.setDropChance(EquipmentSlot.MAINHAND, 0);
        if (mob.getType() == EntityTypes.SKELETON && random.nextInt(SKELETON_MELEE_ODDS) == 0) {
            Item weapon = day > SKELETON_BLADE_DAY && random.nextInt(SKELETON_BLADE_ODDS) == 0
                    ? random.nextBoolean() ? CombatContent.RUSTED_IRON_DAGGER.get() : CombatContent.RUSTED_IRON_SWORD.get()
                    : HarvestContent.item(ToolType.CLUB, Material.WOOD);
            mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(weapon));
        }
        AttributeInstance health = mob.getAttribute(Attributes.MAX_HEALTH);
        AttributeModifier leader = health == null ? null : health.getModifier(LEADER_ID);
        if (leader != null && leader.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) {
            health.addOrReplacePermanentModifier(new AttributeModifier(LEADER_ID, 1 + random.nextInt(4), AttributeModifier.Operation.ADD_VALUE));
            mob.setHealth(mob.getMaxHealth());
        }
    }

    /**
     * Spawn rules, once per mob: MITE gear, then (l.841, monsters) the vanilla enchantments rolled off. A portal trip re-adds
     * the mob as new and a conversion keeps the gear (player items included).
     */
    static void onJoin(EntityJoinLevelEvent event) {
        if (!event.loadedFromDisk() && !event.getLevel().isClientSide() && event.getEntity() instanceof Mob mob && CombatContent.once(mob)) {
            equip(mob, MoonCalendar.day(event.getLevel().getDefaultClockTime()), mob.getRandom());
            if (mob.getType().getCategory() == net.minecraft.world.entity.MobCategory.MONSTER) dev.mite.entity.ai.MobRules.disenchantGear(mob, mob.getRandom());
        }
    }

    /**
     * EF-IA-05: the zombie's rare iron ingot becomes a copper, silver or iron nugget; the drowned's copper ingot (11 %, 1.17) a
     * copper nugget (audit 2 moderne m8: no copper without gravel). Loot-table ingots come one at a time; a bigger stack is one
     * the zombie picked up and is left alone. ponytail: a single picked-up ingot is still turned into a nugget, compare with the
     * equipment before death if that matters.
     */
    static void onDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Zombie zombie)) return;
        List<Item> nuggets = List.of(Items.COPPER_NUGGET, dev.mite.material.MaterialItems.SILVER_NUGGET.get(), Items.IRON_NUGGET);
        for (ItemEntity drop : event.getDrops()) {
            if (drop.getItem().getCount() > 1) continue;
            if (drop.getItem().is(Items.IRON_INGOT)) drop.setItem(new ItemStack(nuggets.get(zombie.getRandom().nextInt(nuggets.size()))));
            else if (drop.getItem().is(Items.COPPER_INGOT)) drop.setItem(new ItemStack(Items.COPPER_NUGGET));
        }
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(MobGear::onJoin);
        NeoForge.EVENT_BUS.addListener(MobGear::onDrops);
    }
}
