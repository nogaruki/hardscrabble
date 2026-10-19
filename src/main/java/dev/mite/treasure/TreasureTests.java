package dev.mite.treasure;

import static dev.mite.core.Material.*;

import dev.mite.MiteMod;
import dev.mite.combat.CombatContent;
import dev.mite.config.MiteConfig;
import dev.mite.core.Material;
import dev.mite.crafting.CrafterRules;
import dev.mite.gametest.MiteGameTests;
import dev.mite.material.MaterialItems;
import dev.mite.metabolism.MetabolismTests;
import dev.mite.player.ProgressionService;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.minecraft.world.level.block.entity.EnchantingTableBlockEntity;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.enchanting.EnchantmentLevelSetEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** GameTests for EF-CBT-10 (G07, G08), EF-CBT-12 (G12), EF-ENC-03 (H03), EF-ENC-07 (H08), EF-UI-09 (P05) and A08. H06 is enchanting_bottles. */
public final class TreasureTests {
    private TreasureTests() {}

    private static final List<ArmorType> PIECES = List.of(ArmorType.HELMET, ArmorType.CHESTPLATE, ArmorType.LEGGINGS, ArmorType.BOOTS);

    private static ServerPlayer wearing(GameTestHelper h, Material material) {
        ServerPlayer p = MetabolismTests.player(h);
        if (material == null) return p;
        for (ArmorType t : PIECES) {
            ItemStack stack = new ItemStack(CombatContent.armorItem(material, t, false));
            p.setItemSlot(t.getSlot(), stack);
            // the mock player never ticks, so apply the equipment modifiers by hand
            stack.forEachModifier(t.getSlot(), (attr, mod) -> p.getAttribute(attr).addOrUpdateTransientModifier(mod));
        }
        h.assertTrue(p.getArmorValue() > 0, "armor applied");
        return p;
    }

    /** Health lost by a fresh player in that armor (null = none) from one hit. */
    private static float loss(GameTestHelper h, Material material, java.util.function.Function<GameTestHelper, DamageSource> source) {
        ServerPlayer p = wearing(h, material);
        p.setHealth(p.getMaxHealth());
        float before = p.getHealth();
        p.hurtServer(h.getLevel(), source.apply(h), 2);
        return before - p.getHealth();
    }

    /** Chestplate wear from one vanilla wear point of that source (armor stand: a player's wear goes to a random location). */
    private static int wear(GameTestHelper h, Material material, DamageSource source) {
        var stand = h.spawn(EntityTypes.ARMOR_STAND, 2, 1, 2);
        stand.setItemSlot(EquipmentSlot.CHEST, new ItemStack(CombatContent.armorItem(material, ArmorType.CHESTPLATE, false)));
        net.neoforged.neoforge.common.CommonHooks.onArmorHurt(source, new EquipmentSlot[]{EquipmentSlot.CHEST}, 1, stand);
        int wear = stand.getItemBySlot(EquipmentSlot.CHEST).getDamageValue();
        stand.discard();
        return wear;
    }

    private static boolean produces(GameTestHelper h, Item item) {
        return h.getLevel().getServer().getRecipeManager().getRecipes().stream().anyMatch((RecipeHolder<?> r) -> r.value().display().stream()
                .anyMatch(d -> d.result().resolveForFirstStack(net.minecraft.world.item.crafting.display.SlotDisplayContext.fromLevel(h.getLevel())).is(item)));
    }

    private static boolean achieved(GameTestHelper h, ServerPlayer p, String name) {
        var adv = h.getLevel().getServer().getAdvancements().get(MiteMod.id(name));
        h.assertTrue(adv != null, "missing advancement " + name);
        return p.getAdvancements().getOrStartProgress(adv).isDone();
    }

    public static void register() {
        // A-4: enchanting.coins_enabled=false refuses minting and spending coins.
        MiteGameTests.add("treasure_coins_config", 20, h -> {
            ServerPlayer p = MetabolismTests.player(h);
            ItemStack coin = new ItemStack(TreasureContent.COINS.get(COPPER).get());
            p.setItemInHand(InteractionHand.MAIN_HAND, coin.copy());
            String refusal = MiteGameTests.withConfig(MiteConfig.COINS_ENABLED, false, () -> TreasureContent.mintRefusal(p, coin));
            var spent = MiteGameTests.withConfig(MiteConfig.COINS_ENABLED, false, () -> coin.getItem().use(h.getLevel(), p, InteractionHand.MAIN_HAND));
            String refusalOn = MiteGameTests.withConfig(MiteConfig.COINS_ENABLED, true, () -> TreasureContent.mintRefusal(p, coin));
            h.assertValueEqual(refusal, "hardscrabble.coin.disabled", "mint refusal with coins off");
            h.assertTrue(spent == InteractionResult.PASS && p.getMainHandItem().getCount() == 1, "coin spent with coins off");
            h.assertFalse("hardscrabble.coin.disabled".equals(refusalOn), "minting refused with coins on");
            h.succeed();
        });
        // EF-CBT-10 (G07): a full silver set always wards off poison; no armor does not.
        MiteGameTests.add("treasure_silver_armor_poison", 20, h -> {
            ServerPlayer silver = wearing(h, SILVER), bare = wearing(h, null);
            silver.addEffect(new MobEffectInstance(MobEffects.POISON, 100));
            bare.addEffect(new MobEffectInstance(MobEffects.POISON, 100));
            h.assertFalse(silver.hasEffect(MobEffects.POISON), "silver set poisoned");
            h.assertTrue(bare.hasEffect(MobEffects.POISON), "bare player not poisoned");
            h.succeed();
        });
        // EF-CBT-10 (G08): lava wears iron x10, never adamantium (changelog R178); fire ignores metal armor.
        MiteGameTests.add("treasure_lava_and_fire", 20, h -> {
            var sources = h.getLevel().damageSources();
            int cactus = wear(h, IRON, sources.cactus());
            h.assertTrue(cactus > 0, "cactus wears the chestplate");
            h.assertValueEqual(wear(h, IRON, sources.lava()), cactus * 10, "iron lava wear");
            h.assertValueEqual(wear(h, ADAMANTIUM, sources.lava()), 0, "adamantium lava wear");
            h.assertValueEqual(loss(h, IRON, x -> x.getLevel().damageSources().inFire()), loss(h, null, x -> x.getLevel().damageSources().inFire()), "fire through iron");
            h.assertTrue(loss(h, IRON, x -> x.getLevel().damageSources().cactus()) < loss(h, null, x -> x.getLevel().damageSources().cactus()), "iron stops cactus");
            h.succeed();
        });
        // EF-CBT-12 (G12): silver horse armor is worn in the body slot with the MITE horse protection (dom-G-13), and has a recipe.
        MiteGameTests.add("treasure_silver_horse_armor", 40, h -> {
            Horse horse = h.spawn(EntityTypes.HORSE, 2, 1, 2);
            ItemStack armor = new ItemStack(TreasureContent.SILVER_HORSE_ARMOR.get());
            h.assertTrue(armor.get(DataComponents.EQUIPPABLE).slot() == EquipmentSlot.BODY, "body slot");
            h.assertTrue(produces(h, armor.getItem()), "recipe");
            horse.setItemSlot(EquipmentSlot.BODY, armor);
            int expected = (int) dev.mite.combat.CombatFormulas.horseArmor(SILVER);
            h.succeedWhen(() -> h.assertValueEqual(horse.getArmorValue(), expected, "horse armor"));
        });
        // EF-ENC-03 (H03): the emerald table works like the vanilla one but caps the level at 15.
        MiteGameTests.add("treasure_emerald_enchanting_table", 20, h -> {
            BlockPos lesser = new BlockPos(1, 1, 1), vanilla = new BlockPos(5, 1, 5);
            h.setBlock(lesser, TreasureContent.EMERALD_ENCHANTING_TABLE.get());
            h.setBlock(vanilla, Blocks.ENCHANTING_TABLE);
            h.assertTrue(h.getBlockEntity(lesser, EnchantingTableBlockEntity.class) != null, "block entity");
            ServerPlayer p = MetabolismTests.player(h);
            Vec3 at = Vec3.atCenterOf(h.absolutePos(lesser.above()));
            p.setPos(at.x, at.y, at.z);
            h.assertTrue(new TreasureContent.Menu(1, p.getInventory(), ContainerLevelAccess.create(h.getLevel(), h.absolutePos(lesser))).stillValid(p), "menu valid");
            for (BlockPos pos : List.of(lesser, vanilla)) {
                var e = new EnchantmentLevelSetEvent(h.getLevel(), h.absolutePos(pos), 2, 15, new ItemStack(Items.BOOK), 30);
                NeoForge.EVENT_BUS.post(e);
                h.assertValueEqual(e.getEnchantLevel(), pos == lesser ? TreasureContent.LESSER_MAX_LEVEL : 30, "level at " + pos);
            }
            h.assertTrue(produces(h, TreasureContent.EMERALD_ENCHANTING_TABLE.get().asItem()), "recipe");
            h.succeed();
        });
        // EF-ENC-07 (H08, changelog R166): every coin costs its value to mint, refused without the XP; spending gives it back.
        MiteGameTests.add("treasure_coins", 100, h -> {
            ServerPlayer p = MetabolismTests.player(h);
            ItemStack mithril = new ItemStack(TreasureContent.COINS.get(MITHRIL).get());
            ItemStack minted = new ItemStack(TreasureContent.COINS.get(COPPER).get());
            h.assertTrue(TreasureContent.mintRefusal(p, minted) != null, "copper minting refused at 0 XP");
            h.assertTrue(TreasureContent.mintRefusal(p, mithril) != null, "mithril minting refused at 0 XP");
            ProgressionService.setXp(p, 3000);
            h.assertTrue(TreasureContent.mintRefusal(p, mithril) == null, "mithril minting at 3000 XP");
            NeoForge.EVENT_BUS.post(new PlayerEvent.ItemCraftedEvent(p, mithril, new SimpleContainer(9)));
            h.assertValueEqual(ProgressionService.xp(p), 500, "XP after minting");
            h.assertTrue(TreasureContent.mintRefusal(p, minted) == null, "copper minting at 500 XP");
            NeoForge.EVENT_BUS.post(new PlayerEvent.ItemCraftedEvent(p, minted, new SimpleContainer(9)));
            h.assertValueEqual(ProgressionService.xp(p), 495, "copper minting debits 5 XP");
            ItemStack copper = new ItemStack(TreasureContent.COINS.get(COPPER).get(), 2);
            p.setItemInHand(InteractionHand.MAIN_HAND, copper);
            p.gameMode.useItem(p, h.getLevel(), copper, InteractionHand.MAIN_HAND);
            h.assertValueEqual(ProgressionService.xp(p), 500, "XP after spending a copper coin");
            h.assertValueEqual(copper.getCount(), 1, "coin spent");
            for (var coin : TreasureContent.COINS.entrySet()) {
                h.assertTrue(produces(h, coin.getValue().get()), "mint recipe " + coin.getKey());
                h.assertTrue(produces(h, MaterialItems.nugget(coin.getKey())), "melt recipe " + coin.getKey());
            }
            LootTable table = h.getLevel().getServer().reloadableRegistries().getLootTable(BuiltInLootTables.SIMPLE_DUNGEON);
            LootParams params = new LootParams.Builder(h.getLevel()).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(h.absolutePos(BlockPos.ZERO)))
                    .create(LootContextParamSets.CHEST);
            List<ItemStack> loot = new ArrayList<>();
            for (int i = 0; i < 200; i++) loot.addAll(table.getRandomItems(params));
            h.assertTrue(loot.stream().anyMatch(s -> s.getItem() instanceof TreasureContent.CoinItem), "coins in dungeon chests");
            h.assertTrue(loot.stream().anyMatch(LoreBooks::isLore), "Funzang books in dungeon chests");
            h.succeed();
        });
        // RE-04: a crafter never mints coins (nobody pays the XP), even with crafter_restriction off; melting a coin stays allowed.
        MiteGameTests.add("treasure_coins_crafter", 20, h -> {
            h.setBlock(1, 1, 1, Blocks.CRAFTER);
            BlockPos abs = h.absolutePos(new BlockPos(1, 1, 1));
            CrafterBlockEntity crafter = (CrafterBlockEntity) h.getLevel().getBlockEntity(abs);
            boolean restricted = MiteConfig.CRAFTER_RESTRICTION.get();
            boolean mint, melt;
            try {
                MiteConfig.CRAFTER_RESTRICTION.set(false);
                crafter.setItem(0, new ItemStack(Items.COPPER_NUGGET));
                mint = CrafterRules.refuse(h.getLevel(), abs);
                crafter.setItem(0, new ItemStack(TreasureContent.COINS.get(COPPER).get()));
                melt = CrafterRules.refuse(h.getLevel(), abs);
            } finally {
                MiteConfig.CRAFTER_RESTRICTION.set(restricted);
            }
            h.assertTrue(mint, "crafter minted a coin with crafter_restriction off");
            h.assertFalse(melt, "crafter refused to melt a coin with crafter_restriction off");
            h.succeed();
        });
        // EF-UI-09 (P05): reading a Funzang book grants enlightenment; a player's own book does not, even signed by a player named
        // Funzang (no translated Funzang name). The loot pool is the only definition of a Funzang book, so the test rolls the dungeon chests.
        MiteGameTests.add("treasure_lore_enlightenment", 20, h -> {
            ServerPlayer p = MetabolismTests.player(h);
            ItemStack own = new ItemStack(Items.WRITTEN_BOOK);
            own.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough("Notes"), "test-mock-player", 0,
                    List.of(Filterable.passThrough(Component.literal("hi"))), true));
            p.setItemInHand(InteractionHand.MAIN_HAND, own);
            p.gameMode.useItem(p, h.getLevel(), own, InteractionHand.MAIN_HAND);
            h.assertFalse(achieved(h, p, LoreBooks.ACHIEVEMENT), "enlightenment from a player's book");
            ItemStack forged = new ItemStack(Items.WRITTEN_BOOK);
            forged.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough("On Patience"), LoreBooks.AUTHOR, 0,
                    List.of(Filterable.passThrough(Component.literal("hi"))), true));
            h.assertFalse(LoreBooks.isLore(forged), "a book signed by a player named Funzang is a lore book");
            p.setItemInHand(InteractionHand.MAIN_HAND, forged);
            p.gameMode.useItem(p, h.getLevel(), forged, InteractionHand.MAIN_HAND);
            h.assertFalse(achieved(h, p, LoreBooks.ACHIEVEMENT), "enlightenment from a book signed Funzang");
            LootTable dungeon = h.getLevel().getServer().reloadableRegistries().getLootTable(BuiltInLootTables.SIMPLE_DUNGEON);
            LootParams chest = new LootParams.Builder(h.getLevel()).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(h.absolutePos(BlockPos.ZERO)))
                    .create(LootContextParamSets.CHEST);
            ItemStack book = Stream.generate(() -> dungeon.getRandomItems(chest)).limit(1000).flatMap(List::stream)
                    .filter(s -> s.is(Items.WRITTEN_BOOK)).findFirst().orElseThrow();
            h.assertTrue(LoreBooks.isLore(book), "rolled Funzang book");
            h.assertTrue(java.util.stream.IntStream.range(0, LoreBooks.TEXT.length)
                    .anyMatch(i -> book.getHoverName().equals(Component.translatable(LoreBooks.titleKey(i)))), "translated name " + book.getHoverName());
            p.setItemInHand(InteractionHand.MAIN_HAND, book);
            p.gameMode.useItem(p, h.getLevel(), book, InteractionHand.MAIN_HAND);
            h.assertTrue(achieved(h, p, LoreBooks.ACHIEVEMENT), "enlightenment after reading");
            h.succeed();
        });
        // A08: the reference tables list every armor piece and the coin values.
        MiteGameTests.add("treasure_reference_tables", 20, h -> {
            h.assertValueEqual(ReferenceTables.armor().split("\n").length, CombatContent.ARMORS.size() + 2, "armor rows");
            h.assertTrue(ReferenceTables.coins().contains("| hardscrabble:mithril_coin | 2500 | 2500 |"), "mithril coin row");
            h.assertTrue(ReferenceTables.tools().contains("hardscrabble:flint_knife"), "tool rows");
            h.succeed();
        });
    }
}
