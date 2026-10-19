package dev.mite.trading;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import dev.mite.combat.CombatContent;
import dev.mite.combat.MobGear;
import dev.mite.core.Material;
import dev.mite.enchanting.EnchantingContent;
import dev.mite.entity.mob.MobContent;
import dev.mite.entity.mob.Revenant;
import dev.mite.gametest.MiteGameTests;
import dev.mite.worldgen.village.VillageLock;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.structures.IglooPieces;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.VillagerTrade;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/** GameTests for trading, bartering and mob equipment (M1, M2, M17, esprit-contournements-04/07, esprit-moderne-07). */
public final class TradingTests {
    private TradingTests() {}

    /** Modern vanilla weapons a mob must never hold (EF-PRG-04, EF-MET-09). */
    private static final Set<Item> FORBIDDEN = Set.of(Items.TRIDENT, Items.IRON_AXE, Items.STONE_SWORD, Items.GOLDEN_SPEAR, Items.IRON_SWORD,
            Items.FISHING_ROD, Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.DIAMOND_HELMET);

    private static boolean forbidden(ItemStack stack) {
        return FORBIDDEN.contains(stack.getItem()) || stack.is(EnchantingContent.PROGRESSION_BLACKLIST);
    }

    private static List<String> gear(Mob mob) {
        List<String> bad = new ArrayList<>();
        for (EquipmentSlot slot : EquipmentSlot.values()) if (forbidden(mob.getItemBySlot(slot))) bad.add(slot + "=" + mob.getItemBySlot(slot).getItem());
        return bad;
    }

    private static Item item(JsonElement gives) {
        String id = gives.isJsonObject() ? gives.getAsJsonObject().get("id").getAsString() : gives.getAsString();
        return BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
    }

    public static void register() {
        // A-4 (EF-CFG-02 CA-1): wandering_trader=false keeps the wandering trader away even once villages are open.
        MiteGameTests.add("config_wandering_trader", 20, h -> {
            MinecraftServer server = h.getLevel().getServer();
            boolean[] allowed = MiteGameTests.withConfig(dev.mite.config.MiteConfig.VILLAGE_LOCK, false, () -> {
                VillageLock.refresh(server);
                return new boolean[] {MiteGameTests.withConfig(dev.mite.config.MiteConfig.WANDERING_TRADER, false, Trading::wanderingTraderAllowed), Trading.wanderingTraderAllowed()};
            });
            VillageLock.refresh(server);
            h.assertFalse(allowed[0], "wandering trader with wandering_trader=false");
            h.assertTrue(allowed[1], "no wandering trader with wandering_trader=true and villages open");
            h.succeed();
        });

        // M1 / EF-PRG-04 CA-1: no villager or wandering trader trade gives a blacklisted item or enchanted equipment (books excepted).
        MiteGameTests.add("trading_registry_audit", 20, h -> {
            var registries = h.getLevel().registryAccess();
            var ops = registries.createSerializationContext(JsonOps.INSTANCE);
            List<String> problems = new ArrayList<>();
            var resources = h.getLevel().getServer().getResourceManager();
            registries.lookupOrThrow(Registries.VILLAGER_TRADE).listElements().forEach(ref -> {
                Identifier file = ref.key().identifier().withPath(p -> "villager_trade/" + p + ".json");
                // ponytail: the opt-in "Villager Trade Rebalance" experiment (enabled by the GameTest server) is not recalibrated
                if (resources.getResource(file).map(r -> r.sourcePackId().contains("trade_rebalance")).orElse(false)) return;
                JsonObject json = VillagerTrade.CODEC.encodeStart(ops, ref.value()).getOrThrow().getAsJsonObject();
                Item gives = item(json.get("gives"));
                if (new ItemStack(gives).is(EnchantingContent.PROGRESSION_BLACKLIST)) problems.add(ref.key().identifier() + " gives " + gives);
                if (gives != Items.ENCHANTED_BOOK && String.valueOf(json.get("given_item_modifier")).contains("enchant")) problems.add(ref.key().identifier() + " enchants " + gives);
            });
            h.assertTrue(problems.isEmpty(), problems.size() + " trade problems: " + problems);
            h.succeed();
        });

        // audit 2 moderne m4: the novice armorer, weaponsmith and fletcher (levels 1-3) sell no iron, iron chainmail or crossbow.
        MiteGameTests.add("trading_novice_no_iron", 20, h -> {
            var registries = h.getLevel().registryAccess();
            var ops = registries.createSerializationContext(JsonOps.INSTANCE);
            var resources = h.getLevel().getServer().getResourceManager();
            Set<Item> iron = Set.of(Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS, Items.IRON_SWORD, Items.IRON_AXE,
                    Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS, Items.CROSSBOW);
            List<String> problems = new ArrayList<>();
            int checked = 0;
            for (var ref : registries.lookupOrThrow(Registries.VILLAGER_TRADE).listElements().toList()) {
                String path = ref.key().identifier().getPath();
                if (!path.matches("(armorer|weaponsmith|fletcher)/[123]/.*")) continue;
                Identifier file = ref.key().identifier().withPath(p -> "villager_trade/" + p + ".json");
                if (resources.getResource(file).map(r -> r.sourcePackId().contains("trade_rebalance")).orElse(false)) continue;
                checked++;
                Item gives = item(VillagerTrade.CODEC.encodeStart(ops, ref.value()).getOrThrow().getAsJsonObject().get("gives"));
                if (iron.contains(gives)) problems.add(path + " gives " + gives);
            }
            h.assertTrue(checked >= 10, "novice trades checked: " + checked);
            h.assertTrue(problems.isEmpty(), problems.toString());
            h.succeed();
        });

        // audit 2 X1: a used-up trade stays locked (no 1.14 restock at the job site) and a trade pops no XP orb.
        MiteGameTests.add("trading_no_restock_no_xp", 20, h -> {
            ServerLevel level = h.getLevel();
            Villager villager = EntityTypes.VILLAGER.spawn(level, h.absolutePos(new BlockPos(3, 1, 3)), EntitySpawnReason.SPAWN_ITEM_USE);
            MerchantOffer offer = new MerchantOffer(new ItemCost(Items.STICK, 32), new ItemStack(Items.EMERALD), 16, 0, 0.05f); // no level-up
            MerchantOffers offers = new MerchantOffers();
            offers.add(offer);
            villager.overrideOffers(offers);
            for (int i = 0; i < 16; i++) villager.notifyTrade(offer);
            h.assertTrue(offer.isOutOfStock(), "trade used up");
            h.assertFalse(villager.shouldRestock(level), "villager wants to restock");
            h.assertFalse(villager.canRestock(), "trading screen announces a restock");
            h.assertTrue(offer.isOutOfStock(), "trade restocked");
            h.assertTrue(level.getEntitiesOfClass(ExperienceOrb.class, villager.getBoundingBox().inflate(3)).isEmpty(), "trade XP orbs");
            villager.discard();
            h.succeed();
        });

        // audit 2 X5: the igloo pieces place no villager nor zombie villager (other template entities stay).
        MiteGameTests.add("trading_igloo_no_villagers", 20, h -> {
            var piece = new IglooPieces.IglooPiece(h.getLevel().getServer().getStructureTemplateManager(), Identifier.withDefaultNamespace("igloo/bottom"),
                    h.absolutePos(new BlockPos(3, 1, 3)), Rotation.NONE, 3);
            h.assertTrue(piece.placeSettings().getProcessors().contains(Trading.NO_VILLAGERS), "igloo processors " + piece.placeSettings().getProcessors());
            for (var type : List.of(EntityTypes.VILLAGER, EntityTypes.ZOMBIE_VILLAGER, EntityTypes.ARMOR_STAND)) {
                CompoundTag nbt = new CompoundTag();
                nbt.putString("id", net.minecraft.world.entity.EntityType.getKey(type).toString());
                var info = new StructureTemplate.StructureEntityInfo(net.minecraft.world.phys.Vec3.ZERO, BlockPos.ZERO, nbt);
                var out = Trading.NO_VILLAGERS.processEntity(h.getLevel(), BlockPos.ZERO, info, info, piece.placeSettings(), null);
                h.assertTrue((out == null) == (type != EntityTypes.ARMOR_STAND), type + " kept: " + (out != null));
            }
            h.succeed();
        });

        // audit 2 moderne m8: the drowned's copper ingot becomes a copper nugget.
        MiteGameTests.add("trading_drowned_copper_nugget", 20, h -> {
            ServerLevel level = h.getLevel();
            Mob drowned = EntityTypes.DROWNED.create(level, EntitySpawnReason.MOB_SUMMONED);
            drowned.snapTo(h.absoluteVec(new net.minecraft.world.phys.Vec3(3, 1, 3)));
            List<ItemEntity> drops = new ArrayList<>(List.of(new ItemEntity(level, drowned.getX(), drowned.getY(), drowned.getZ(), new ItemStack(Items.COPPER_INGOT))));
            NeoForge.EVENT_BUS.post(new LivingDropsEvent(drowned, level.damageSources().generic(), drops, true));
            h.assertTrue(drops.getFirst().getItem().is(Items.COPPER_NUGGET), "drowned drop " + drops.getFirst().getItem());
            h.succeed();
        });

        // M2: the wandering trader spawner waits for the village unlock; its offers buy nothing and sell no enchanted pickaxe.
        MiteGameTests.add("trading_wandering_trader", 20, h -> {
            MinecraftServer server = h.getLevel().getServer();
            VillageLock.Progress progress = VillageLock.progress(server);
            boolean before = progress.villagesUnlocked;
            BlockPos pos = h.absolutePos(new BlockPos(3, 1, 3));
            progress.villagesUnlocked = false;
            VillageLock.refresh(server);
            h.assertTrue(EntityTypes.WANDERING_TRADER.spawn(h.getLevel(), pos, EntitySpawnReason.EVENT) == null, "no wandering trader while villages are locked");
            progress.villagesUnlocked = true;
            VillageLock.refresh(server);
            WanderingTrader trader = EntityTypes.WANDERING_TRADER.spawn(h.getLevel(), pos, EntitySpawnReason.EVENT);
            progress.villagesUnlocked = before;
            VillageLock.refresh(server);
            h.assertTrue(trader != null, "a wandering trader once villages are unlocked");
            for (MerchantOffer offer : trader.getOffers()) {
                h.assertFalse(offer.getResult().is(Items.EMERALD), "the wandering trader buys " + offer.getBaseCostA());
                h.assertFalse(offer.getResult().is(Items.IRON_PICKAXE) || offer.getResult().isEnchanted(), "the wandering trader sells " + offer.getResult());
            }
            trader.discard();
            h.succeed();
        });

        // esprit-contournements-04 / esprit-moderne-07: wither skeletons, vindicators, drowned and piglins hold no forbidden vanilla weapon.
        MiteGameTests.add("trading_mob_weapons", 20, h -> {
            ServerLevel level = h.getLevel();
            BlockPos pos = h.absolutePos(new BlockPos(3, 1, 3));
            for (var type : List.of(EntityTypes.WITHER_SKELETON, EntityTypes.VINDICATOR)) { // their finalizeSpawn always arms them
                Mob mob = type.spawn(level, pos, EntitySpawnReason.SPAWN_ITEM_USE);
                h.assertTrue(gear(mob).isEmpty() && !mob.getMainHandItem().isEmpty(), type + " gear " + gear(mob) + " / " + mob.getMainHandItem());
                mob.discard();
            }
            Object[][] cases = {{EntityTypes.DROWNED, Items.TRIDENT}, {EntityTypes.DROWNED, Items.FISHING_ROD}, {EntityTypes.PIGLIN, Items.GOLDEN_SPEAR},
                    {EntityTypes.ZOMBIFIED_PIGLIN, Items.GOLDEN_SPEAR}, {EntityTypes.VINDICATOR, Items.IRON_AXE}, {EntityTypes.WITHER_SKELETON, Items.STONE_SWORD}};
            for (Object[] c : cases) {
                Mob mob = (Mob) ((net.minecraft.world.entity.EntityType<?>) c[0]).create(level, EntitySpawnReason.MOB_SUMMONED);
                mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack((Item) c[1]));
                MobGear.equip(mob, 1, RandomSource.create(0));
                h.assertTrue(gear(mob).isEmpty(), c[0] + " keeps " + gear(mob));
            }
            h.succeed();
        });

        // M17 / dom-J-04/12: MITE zombies get no vanilla leader bonus nor reinforcements, and the Revenant wears rusted chainmail.
        MiteGameTests.add("trading_mite_zombie_spawn", 20, h -> {
            ServerLevel level = h.getLevel();
            DifficultyInstance hard = new DifficultyInstance(Difficulty.HARD, 10_000_000L, 10_000_000L, 1f); // special multiplier 1: 5 % leaders in vanilla
            for (var type : List.of(MobContent.GHOUL.get(), MobContent.WIGHT.get(), MobContent.REVENANT.get())) {
                for (int i = 0; i < 100; i++) {
                    Mob mob = type.create(level, EntitySpawnReason.NATURAL);
                    mob.snapTo(h.absoluteVec(new net.minecraft.world.phys.Vec3(3, 1, 3)));
                    net.neoforged.neoforge.event.EventHooks.finalizeMobSpawn(mob, level, hard, EntitySpawnReason.NATURAL, null);
                    MobGear.equip(mob, 1, mob.getRandom());
                    h.assertTrue(mob.getAttribute(Attributes.MAX_HEALTH).getModifier(Identifier.withDefaultNamespace("leader_zombie_bonus")) == null, type + " leader bonus");
                    h.assertValueEqual(mob.getAttributeValue(Attributes.SPAWN_REINFORCEMENTS_CHANCE), 0.0, "reinforcement chance");
                    h.assertTrue(gear(mob).isEmpty(), type + " gear " + gear(mob));
                    if (mob instanceof Revenant) h.assertTrue(mob.getItemBySlot(EquipmentSlot.CHEST).is(CombatContent.armorItem(Material.RUSTED_IRON, ArmorType.CHESTPLATE, true)),
                            "revenant chest " + mob.getItemBySlot(EquipmentSlot.CHEST));
                }
            }
            h.succeed();
        });

        // M1 / esprit-contournements-07: iron golems drop nuggets; bartering gives no iron, obsidian, enchanted iron boots or dried ghast.
        MiteGameTests.add("trading_golem_and_barter_loot", 20, h -> {
            ServerLevel level = h.getLevel();
            var tables = level.getServer().reloadableRegistries();
            Mob piglin = EntityTypes.PIGLIN.create(level, EntitySpawnReason.MOB_SUMMONED);
            LootTable barter = tables.getLootTable(BuiltInLootTables.PIGLIN_BARTERING);
            LootParams barterParams = new LootParams.Builder(level).withParameter(LootContextParams.THIS_ENTITY, piglin).create(LootContextParamSets.PIGLIN_BARTER);
            Set<Item> banned = Set.of(Items.IRON_NUGGET, Items.OBSIDIAN, Items.IRON_BOOTS, Items.DRIED_GHAST);
            for (int i = 0; i < 1000; i++) {
                for (ItemStack stack : barter.getRandomItems(barterParams)) h.assertFalse(banned.contains(stack.getItem()), "barter gives " + stack);
            }
            Mob golem = EntityTypes.IRON_GOLEM.create(level, EntitySpawnReason.MOB_SUMMONED);
            golem.snapTo(h.absoluteVec(new net.minecraft.world.phys.Vec3(3, 1, 3)));
            LootParams golemParams = new LootParams.Builder(level).withParameter(LootContextParams.THIS_ENTITY, golem).withParameter(LootContextParams.ORIGIN, golem.position())
                    .withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().generic()).create(LootContextParamSets.ENTITY);
            int nuggets = 0;
            for (ItemStack stack : tables.getLootTable(EntityTypes.IRON_GOLEM.getDefaultLootTable().orElseThrow()).getRandomItems(golemParams)) {
                h.assertFalse(stack.is(Items.IRON_INGOT), "iron golem drops ingots");
                if (stack.is(Items.IRON_NUGGET)) nuggets += stack.getCount();
            }
            h.assertValueEqual(nuggets, 0, "iron golem nuggets without a player kill (X4, see shortcut_golem_one_nugget)");
            h.succeed();
        });
    }
}
