package dev.mite.enchanting;

import dev.mite.core.Material;
import dev.mite.gametest.MiteGameTests;
import dev.mite.material.MaterialItems;
import dev.mite.metabolism.MetabolismTests;
import dev.mite.player.ProgressionService;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import java.util.List;
import java.util.stream.LongStream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/** GameTests for EF-ENC-01, EF-ENC-05, EF-ENC-11 and EF-PRG-04 / EF-MON-06. */
public final class EnchantingTests {
    private EnchantingTests() {}

    private static final BlockPos POS = new BlockPos(3, 1, 3);
    /** EF-PRG-04 CA-2: rolls per chest loot table. */
    private static final int ROLLS = 200;

    public static void register() {
        // EF-ENC-01 CA-2: repairing a copper pickaxe on a copper anvil consumes 6 copper nuggets and no XP; the repair is full.
        MiteGameTests.add("enchanting_anvil_repair", 20, h -> {
            ServerPlayer player = MetabolismTests.player(h);
            ProgressionService.setXp(player, 1000);
            h.setBlock(POS, EnchantingContent.ANVILS.get(Material.COPPER).get());
            MiteAnvilMenu menu = menu(h, player, Material.COPPER);
            ItemStack pickaxe = new ItemStack(Items.COPPER_PICKAXE);
            pickaxe.setDamageValue(1000);
            menu.getSlot(0).set(pickaxe);
            menu.getSlot(1).set(new ItemStack(Items.COPPER_NUGGET, 10));
            h.assertValueEqual(menu.getCost(), 6, "nugget cost");
            menu.clicked(2, 0, ContainerInput.PICKUP, player);
            ItemStack repaired = menu.getCarried();
            h.assertTrue(repaired.is(Items.COPPER_PICKAXE), "repaired pickaxe taken");
            h.assertValueEqual(repaired.getDamageValue(), 0, "full repair");
            h.assertValueEqual(menu.getSlot(1).getItem().getCount(), 4, "nuggets left");
            h.assertTrue(menu.getSlot(0).getItem().isEmpty(), "input consumed");
            h.assertValueEqual(ProgressionService.xp(player), 1000, "no XP spent");
            // Wrong metal and too few nuggets give nothing.
            menu.getSlot(0).set(pickaxe.copy());
            menu.getSlot(1).set(new ItemStack(Items.IRON_NUGGET, 10));
            h.assertTrue(menu.getSlot(2).getItem().isEmpty(), "iron nuggets cannot repair copper");
            menu.getSlot(1).set(new ItemStack(Items.COPPER_NUGGET, 5));
            h.assertTrue(menu.getSlot(2).getItem().isEmpty(), "5 nuggets are not enough");
            h.succeed();
        });
        // EF-ENC-01 CA-1: a mithril pickaxe cannot be repaired on an iron (vanilla) anvil, but can on a mithril anvil.
        MiteGameTests.add("enchanting_anvil_refuses_harder", 20, h -> {
            ServerPlayer player = (ServerPlayer) h.makeMockServerPlayer(GameType.SURVIVAL);
            h.setBlock(POS, Blocks.ANVIL);
            ItemStack pickaxe = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("hardscrabble", "mithril_pickaxe")));
            pickaxe.setDamageValue(10);
            ItemStack nuggets = new ItemStack(dev.mite.material.MaterialItems.MITHRIL_NUGGET.get(), 6);
            h.assertValueEqual(MiteAnvilMenu.repairCost(pickaxe, nuggets, Material.IRON), 0, "mithril on iron anvil");
            h.assertValueEqual(MiteAnvilMenu.repairCost(pickaxe, nuggets, Material.MITHRIL), 6, "mithril on mithril anvil");
            // EF-PRG-03: the refusal message is shown for a too-hard item, not for a repairable or undamaged one.
            h.assertTrue(MiteAnvilMenu.tooHard(pickaxe, Material.IRON) && !MiteAnvilMenu.tooHard(pickaxe, Material.MITHRIL)
                    && !MiteAnvilMenu.tooHard(new ItemStack(pickaxe.getItem()), Material.IRON), "too hard");
            MiteAnvilMenu menu = menu(h, player, Material.IRON);
            menu.getSlot(0).set(pickaxe);
            menu.getSlot(1).set(nuggets);
            h.assertTrue(menu.getSlot(2).getItem().isEmpty(), "no result on the iron anvil");
            h.succeed();
        });
        // X11 (H-1): MITE armor repairs on the anvil of its metal: a mithril chestplate takes 2 x 8 mithril nuggets on a mithril anvil,
        // a silver chainmail chestplate 2 x 4 (8 chains = 32 nuggets, rounded up to 4 ingots); leather is not anvil-repairable.
        MiteGameTests.add("enchanting_anvil_repairs_armor", 20, h -> {
            ItemStack chest = new ItemStack(dev.mite.combat.CombatContent.armorItem(Material.MITHRIL, net.minecraft.world.item.equipment.ArmorType.CHESTPLATE, false));
            chest.setDamageValue(100);
            h.assertValueEqual(MiteAnvilMenu.repairCost(chest, new ItemStack(MaterialItems.MITHRIL_NUGGET.get(), 64), Material.MITHRIL), 16, "mithril chestplate");
            h.assertValueEqual(MiteAnvilMenu.repairCost(chest, new ItemStack(MaterialItems.MITHRIL_NUGGET.get(), 64), Material.IRON), 0, "mithril chestplate on iron anvil");
            h.assertTrue(MiteAnvilMenu.tooHard(chest, Material.IRON), "mithril chestplate too hard for iron");
            ItemStack chain = new ItemStack(dev.mite.combat.CombatContent.armorItem(Material.SILVER, net.minecraft.world.item.equipment.ArmorType.CHESTPLATE, true));
            chain.setDamageValue(100);
            h.assertValueEqual(MiteAnvilMenu.repairCost(chain, new ItemStack(MaterialItems.SILVER_NUGGET.get(), 64), Material.SILVER), 8, "silver chainmail chestplate");
            ItemStack adamantium = new ItemStack(dev.mite.combat.CombatContent.armorItem(Material.ADAMANTIUM, net.minecraft.world.item.equipment.ArmorType.BOOTS, false));
            adamantium.setDamageValue(100);
            h.assertValueEqual(MiteAnvilMenu.repairCost(adamantium, new ItemStack(MaterialItems.ADAMANTIUM_NUGGET.get(), 64), Material.ADAMANTIUM), 8, "adamantium boots");
            ItemStack leather = new ItemStack(Items.LEATHER_CHESTPLATE);
            leather.setDamageValue(100);
            h.assertTrue(MiteAnvilMenu.repairInfo(leather).isEmpty(), "leather on the anvil");
            h.succeed();
        });
        // EF-ENC-01: a repair that rolls under the wear chance wears a MITE anvil one stage (0 -> 1 -> 2), then destroys it; above it, nothing.
        MiteGameTests.add("enchanting_anvil_wear", 20, h -> {
            h.setBlock(POS, EnchantingContent.ANVILS.get(Material.COPPER).get());
            BlockPos abs = h.absolutePos(POS);
            long wears = LongStream.range(0, 1_000_000).filter(s -> RandomSource.create(s).nextFloat() < 0.001f).findFirst().orElseThrow();
            long spares = LongStream.range(0, 1_000_000).filter(s -> RandomSource.create(s).nextFloat() > 0.999f).findFirst().orElseThrow();
            MiteAnvilMenu.wear(h.getLevel(), abs, Material.COPPER, RandomSource.create(spares));
            h.assertBlockProperty(POS, MiteAnvilBlock.DAMAGE, 0);
            for (int stage = 1; stage <= 2; stage++) {
                MiteAnvilMenu.wear(h.getLevel(), abs, Material.COPPER, RandomSource.create(wears));
                h.assertBlockProperty(POS, MiteAnvilBlock.DAMAGE, stage);
            }
            MiteAnvilMenu.wear(h.getLevel(), abs, Material.COPPER, RandomSource.create(wears));
            h.assertBlockPresent(Blocks.AIR, POS);
            h.succeed();
        });
        // H-3: the EF-ENC-02 difficulty caps limit the table level: iron 16, adamantium 22, mithril 30 (others 30).
        MiteGameTests.add("enchanting_material_caps", 20, h -> {
            h.assertValueEqual(EnchantingTable.levelCap(Material.IRON), 16, "iron cap");
            h.assertValueEqual(EnchantingTable.levelCap(Material.ADAMANTIUM), 22, "adamantium cap");
            h.assertValueEqual(EnchantingTable.levelCap(Material.MITHRIL), 30, "mithril cap");
            h.assertValueEqual(EnchantingTable.levelCap(Material.COPPER), 30, "copper uncapped");
            var event = new net.neoforged.neoforge.event.enchanting.EnchantmentLevelSetEvent(h.getLevel(), h.absolutePos(POS), 2, 15,
                    new ItemStack(Items.IRON_PICKAXE), 30);
            EnchantingTable.onLevelSet(event);
            h.assertValueEqual(event.getEnchantLevel(), 16, "iron pickaxe offered level");
            h.succeed();
        });
        // EF-ENC-05: Mending and Infinity are out of the enchanting table, trades and random loot.
        MiteGameTests.add("enchanting_mending_unobtainable", 20, h -> {
            var enchantments = h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            for (ResourceKey<Enchantment> key : List.of(Enchantments.MENDING, Enchantments.INFINITY)) {
                Holder<Enchantment> e = enchantments.getOrThrow(key);
                for (TagKey<Enchantment> tag : List.of(EnchantmentTags.IN_ENCHANTING_TABLE, EnchantmentTags.TRADEABLE, EnchantmentTags.ON_RANDOM_LOOT)) {
                    h.assertFalse(e.is(tag), key.identifier() + " in " + tag.location());
                }
            }
            h.assertTrue(enchantments.getOrThrow(Enchantments.SHARPNESS).is(EnchantmentTags.IN_ENCHANTING_TABLE), "sharpness stays");
            h.succeed();
        });
        // EF-ENC-11 CA-1: no netherite upgrade recipe; netherite ingots keep their recipe.
        MiteGameTests.add("enchanting_no_netherite_upgrade", 20, h -> {
            var recipes = h.getLevel().getServer().getRecipeManager();
            for (String r : EnchantingData.NetheriteRemovals.RECIPES) {
                String id = "minecraft:" + r.substring(r.indexOf('/') + 1) + "_smithing";
                h.assertFalse(recipes.byKey(ResourceKey.create(Registries.RECIPE, Identifier.parse(id))).isPresent(), id + " should be removed");
            }
            h.assertTrue(recipes.byKey(ResourceKey.create(Registries.RECIPE, Identifier.parse("minecraft:netherite_ingot"))).isPresent(), "netherite ingot stays");
            h.succeed();
        });
        // EF-PRG-04 CA-2 / EF-MON-06 (B3): N rolls of every structure loot table (and Hero of the Village gift) yield no blacklisted item, no Mending/Infinity, no
        // replaced metal or gem (iron ingot, diamond, golden pickaxe...), at most 2 emeralds a stack; Ancient Cities give ancient metal nuggets.
        MiteGameTests.add("enchanting_loot_blacklist", 100, h -> {
            MinecraftServer server = h.getLevel().getServer();
            LootParams params = new LootParams.Builder(h.getLevel()).withParameter(LootContextParams.ORIGIN, net.minecraft.world.phys.Vec3.atCenterOf(h.absolutePos(POS)))
                    .create(LootContextParamSets.CHEST);
            var tables = server.reloadableRegistries().lookup().lookupOrThrow(Registries.LOOT_TABLE).listElementIds()
                    .filter(k -> EnchantingData.STRUCTURE_TABLES.stream().anyMatch(k.identifier().getPath()::startsWith)).toList();
            var replaced = EnchantingData.STRUCTURE_RULES.stream().flatMap(r -> r.from().stream().filter(i -> i != r.to())).toList();
            int ancient = 0;
            h.assertTrue(tables.size() > 20, "chest loot tables found: " + tables.size());
            h.assertTrue(tables.stream().anyMatch(k -> k.identifier().getPath().startsWith("gameplay/hero_of_the_village/")), "hero of the village gifts checked");
            for (ResourceKey<LootTable> key : tables) {
                LootTable table = server.reloadableRegistries().getLootTable(key);
                for (int i = 0; i < ROLLS; i++) {
                    for (ItemStack stack : table.getRandomItems(params)) {
                        h.assertFalse(stack.is(EnchantingContent.LOOT_BLACKLIST), stack + " from " + key.identifier());
                        h.assertFalse(replaced.contains(stack.getItem()), stack + " not replaced in " + key.identifier());
                        h.assertFalse(stack.is(Items.EMERALD) && stack.getCount() > 2, stack + " from " + key.identifier());
                        if (stack.is(MaterialItems.ANCIENT_METAL_NUGGET) && key == BuiltInLootTables.ANCIENT_CITY) ancient++;
                        ItemEnchantments e = stack.getOrDefault(EnchantmentHelper.getComponentType(stack), ItemEnchantments.EMPTY);
                        h.assertFalse(e.keySet().stream().anyMatch(x -> x.is(EnchantingContent.UNOBTAINABLE)), stack + " enchanted from " + key.identifier());
                        h.assertFalse(stack.is(Items.ENCHANTED_BOOK) && stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY).isEmpty(),
                                "empty book from " + key.identifier());
                    }
                }
            }
            h.assertTrue(ancient > 0, "no ancient metal nugget in " + ROLLS + " Ancient City chests");
            h.succeed();
        });
        // audit 2 X2 / X3: nugget piles shrink like ingots (iron nuggets per shipwreck treasure ~17 in vanilla, now ~10; per ruined
        // portal ~8, now ~1.2); abandoned camps give no copper ingot or equipment, shears or bucket.
        MiteGameTests.add("enchanting_loot_nuggets", 100, h -> {
            var tables = h.getLevel().getServer().reloadableRegistries();
            LootParams params = new LootParams.Builder(h.getLevel()).withParameter(LootContextParams.ORIGIN, net.minecraft.world.phys.Vec3.atCenterOf(h.absolutePos(POS)))
                    .create(LootContextParamSets.CHEST);
            int shipwreck = 0, portal = 0;
            for (int i = 0; i < ROLLS; i++) {
                for (ItemStack s : tables.getLootTable(BuiltInLootTables.SHIPWRECK_TREASURE).getRandomItems(params)) if (s.is(Items.IRON_NUGGET)) shipwreck += s.getCount();
                for (ItemStack s : tables.getLootTable(BuiltInLootTables.RUINED_PORTAL).getRandomItems(params)) {
                    if (s.is(Items.IRON_NUGGET)) portal += s.getCount();
                }
                for (var camp : List.of(BuiltInLootTables.ABANDONED_CAMP_COMMON_CHEST, BuiltInLootTables.ABANDONED_CAMP_SECRET_CHEST)) {
                    for (ItemStack s : tables.getLootTable(camp).getRandomItems(params)) {
                        h.assertFalse(s.is(Items.COPPER_INGOT) || s.is(Items.SHEARS) || s.is(Items.BUCKET) || s.is(Items.COPPER_SWORD) || s.is(Items.COPPER_CHESTPLATE),
                                camp.identifier() + " gives " + s);
                    }
                }
            }
            h.assertTrue(portal <= 3 * ROLLS, "iron nuggets per ruined portal: " + (float) portal / ROLLS);
            h.assertTrue(shipwreck <= 12 * ROLLS, "iron nuggets per shipwreck treasure: " + (float) shipwreck / ROLLS);
            h.succeed();
        });
    }

    private static MiteAnvilMenu menu(GameTestHelper h, ServerPlayer player, Material anvil) {
        return new MiteAnvilMenu(1, player.getInventory(), ContainerLevelAccess.create(h.getLevel(), h.absolutePos(POS)), anvil);
    }
}
