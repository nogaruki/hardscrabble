package dev.mite.progression;

import dev.mite.MiteMod;
import dev.mite.dimension.UnderworldContent;
import dev.mite.dimension.runegate.Runegate;
import dev.mite.farming.FarmingContent;
import dev.mite.metabolism.MiteSleep;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.predicates.ItemPredicate;
import net.minecraft.advancements.predicates.entity.EntityEquipmentPredicate;
import net.minecraft.advancements.predicates.entity.EntityPredicate;
import net.minecraft.advancements.triggers.ChangeDimensionTrigger;
import net.minecraft.advancements.triggers.ConsumeItemTrigger;
import net.minecraft.advancements.triggers.Criterion;
import net.minecraft.advancements.triggers.EnchantedItemTrigger;
import net.minecraft.advancements.triggers.InventoryChangeTrigger;
import net.minecraft.advancements.triggers.KilledTrigger;
import net.minecraft.advancements.triggers.PlayerTrigger;
import dev.mite.harvest.ToolStats;
import java.util.Comparator;
import java.util.function.Function;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.data.tags.TagAppender;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.advancements.AdvancementSubProvider;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import org.jspecify.annotations.Nullable;

/**
 * MITE achievements (EF-UI-07, ET-P-01, P01) as per-player advancements, unlockable in any order, plus the portal ones of
 * EF-DIM-02 CA-2. Items obtained use vanilla {@code inventory_changed}, crafts use {@code hardscrabble:crafted_item}, the explorer
 * and the code-driven ones use {@code hardscrabble:event}. Not ported (their content does not exist yet): makeMycelium.
 * The meaning of fineDining, plantDoctor, supersizeMe and crystalBreaker is not in the spec (only their keys) [INFÉRÉ].
 */
public final class MiteAdvancements extends AdvancementSubProvider {
    /** EF-UI-07: the explorer achievement, 10 km from the world spawn. */
    public static final int EXPLORER_DISTANCE = 10_000;

    /** id, EN title, EN description, FR title, FR description. Read by MiteLang. */
    public static final String[][] TEXT = {
            {"root", "Minecraft Is Too Easy", "Survive in a harder world", "Minecraft est trop facile", "Survivre dans un monde plus rude"},
            {"stick_picker", "Stick Picker", "Pick up a stick", "Ramasseur de bâtons", "Ramasser un bâton"},
            {"seeds", "Seed Gatherer", "Find some seeds", "Cueilleur", "Trouver des graines"},
            {"eggs", "Egg Hunt", "Find an egg", "Chasse aux œufs", "Trouver un œuf"},
            {"flint_finder", "Flint Finder", "Find flint", "Trouveur de silex", "Trouver du silex"},
            {"cutting_edge", "Cutting Edge", "Craft a flint knife", "Tranchant", "Fabriquer un couteau en silex"},
            {"mine_wood", "Getting Wood", "Obtain a log", "Du bois", "Obtenir une bûche"},
            {"build_workbench", "Workbench", "Craft a workbench", "Établi", "Fabriquer un établi"},
            {"build_club", "Caveman", "Craft a wooden cudgel", "Homme des cavernes", "Fabriquer un gourdin en bois"},
            {"build_axe", "Chop Chop", "Craft a hatchet or an axe", "Coupe-coupe", "Fabriquer une hachette ou une hache"},
            {"build_shovel", "Dig In", "Craft a shovel", "À la pelle", "Fabriquer une pelle"},
            {"nuggets", "Small Change", "Obtain a metal nugget", "Petite monnaie", "Obtenir une pépite de métal"},
            {"build_torches", "Let There Be Light", "Craft torches", "Que la lumière soit", "Fabriquer des torches"},
            {"build_oven", "Now You're Cooking", "Craft a clay oven", "Aux fourneaux", "Fabriquer un four en argile"}, // changelog l.749
            {"make_bread", "Baker", "Obtain bread", "Boulanger", "Obtenir du pain"},
            {"flour", "Miller", "Obtain flour", "Meunier", "Obtenir de la farine"},
            {"fine_dining", "Fine Dining", "Eat a cooked dish", "Gastronomie", "Manger un plat cuisiné"},
            {"supersize_me", "Supersize Me", "Fill your satiety and nutrition to the maximum of 20", "Super size me",
                    "Remplir sa satiété et sa nutrition jusqu'au maximum de 20"},
            {"plant_doctor", "Plant Doctor", "Cure a blighted crop with bone meal", "Phytothérapeute", "Soigner une culture malade avec de la poudre d'os"},
            {"bake_cake", "Piece of Cake", "Craft a cake", "Du gâteau", "Fabriquer un gâteau"},
            {"build_hoe", "Time to Farm!", "Craft a hoe or a mattock", "L'heure de cultiver !", "Fabriquer une houe ou un hoyau"},
            {"build_scythe", "Reaper", "Craft a scythe", "Faucheur", "Fabriquer une faux"},
            {"soil_enrichment", "Soil Enrichment", "Apply manure to farmland", "Engrais", "Épandre du fumier sur une terre labourée"}, // l.749
            {"kill_cow", "Beef", "Kill a cow", "Du bœuf", "Tuer une vache"},
            {"build_pickaxe", "Pick Up the Pace", "Craft a pickaxe", "Pioche", "Fabriquer une pioche"},
            {"better_tools", "Metalworks", "Craft a metal workbench", "Atelier de métal", "Fabriquer un établi en métal"},
            {"build_furnace", "Hot Topic", "Craft a furnace", "Sujet brûlant", "Fabriquer un fourneau"},
            {"acquire_iron", "Iron Age", "Obtain an iron ingot", "Âge du fer", "Obtenir un lingot de fer"},
            {"obsidian_furnace", "Black Furnace", "Craft an obsidian furnace", "Four noir", "Fabriquer un four en obsidienne"},
            {"mithril_ingot", "Legendary Metal", "Obtain a mithril ingot", "Métal légendaire", "Obtenir un lingot de mithril"},
            {"netherrack_furnace", "Hellfire", "Craft a netherrack furnace", "Feu infernal", "Fabriquer un four en netherrack"},
            {"adamantium_ingot", "Unbreakable", "Obtain an adamantium ingot", "Incassable", "Obtenir un lingot d'adamantium"},
            {"wear_leather", "Dressed Up", "Wear a piece of leather armor", "Bien habillé", "Porter une pièce d'armure en cuir"},
            {"build_chain_mail", "Chain Reaction", "Craft a piece of chainmail", "Maille à maille", "Fabriquer une pièce de cotte de mailles"},
            {"wear_all_plate_armor", "Tin Can", "Wear a full set of metal plate armor", "Boîte de conserve", "Porter une armure de plaques complète"},
            {"wear_all_adamantium_plate_armor", "Walking Fortress", "Wear a full set of adamantium plate armor", "Forteresse ambulante",
                    "Porter une armure de plaques complète en adamantium"},
            {"fishing_rod", "Gone Fishing", "Craft a fishing rod", "Partie de pêche", "Fabriquer une canne à pêche"},
            {"crystal_breaker", "Crystal Breaker", "Mine a diamond, emerald or quartz ore", "Briseur de cristaux",
                    "Miner un minerai de diamant, d'émeraude ou de quartz"},
            {"diamonds", "Diamonds", "Obtain a diamond", "Diamants", "Obtenir un diamant"},
            {"emeralds", "Emeralds", "Obtain an emerald", "Émeraudes", "Obtenir une émeraude"},
            {"bookshelves", "Librarian", "Craft a bookshelf", "Bibliothécaire", "Fabriquer une bibliothèque"},
            {"enchanter", "Enchanter", "Enchant an item", "Enchanteur", "Enchanter un objet"},
            {"seaworthy", "Seaworthy", "Craft a boat", "Navigable", "Fabriquer un bateau"},
            {"portal", "Down Under", "Travel to the Underworld", "Les profondeurs", "Voyager dans l'Underworld"},
            {"portal_to_nether", "Deeper Still", "Travel from the Underworld to the Nether", "Plus bas encore", "Voyager de l'Underworld au Nether"},
            {"runegate", "Gatekeeper", "Travel through a runegate", "Gardien des portes", "Voyager par une runegate"},
            {"kill_dragon", "Dragon Slayer", "Kill the Ender Dragon", "Tueur de dragon", "Tuer l'Ender Dragon"}, // changelog l.420 [INFÉRÉ: name]
            {"explorer", "Explorer", "Travel 10 km away from the world spawn", "Explorateur", "S'éloigner de 10 km du point d'apparition"},
            {"well_rested", "Well Rested", "Sleep for 6000 ticks in a row", "Bien reposé", "Dormir 6 000 ticks d'affilée"},
            {"enlightenment", "Enlightenment", "Read the writings of Father Funzang", "Illumination", "Lire les écrits du Père Funzang"},
    };

    private final HolderGetter<Item> items;
    private final HolderGetter<EntityType<?>> entities;

    public MiteAdvancements(BootstrapContext<Advancement> output) {
        super(output);
        this.items = output.lookup(Registries.ITEM);
        this.entities = output.lookup(Registries.ENTITY_TYPE);
    }

    @Override
    public void generate() {
        AdvancementHolder root = Advancement.Builder.advancement()
                .rootDisplay(Items.FLINT, title("root"), description("root"), Identifier.withDefaultNamespace("gui/advancements/backgrounds/stone"),
                        AdvancementType.TASK, false, false, false)
                .addCriterion("tick", PlayerTrigger.TriggerInstance.tick()).save(output, MiteMod.id("root").toString());

        // Survival
        AdvancementHolder stick = add(root, "stick_picker", Items.STICK, has(Items.STICK));
        add(root, "seeds", Items.WHEAT_SEEDS, has(Tags.Items.SEEDS));
        add(root, "eggs", Items.EGG, has(Tags.Items.EGGS));
        AdvancementHolder flint = add(stick, "flint_finder", Items.FLINT, has(Items.FLINT));
        // Tree order = what the game requires (changelog l.573: "dependencies make more sense"): no log without a hatchet (2x2),
        // no workbench without a log, no pickaxe or hoe without a metal workbench, leather from cows.
        add(flint, "cutting_edge", MiteMod.item("flint_knife"), crafted(MiteMod.item("flint_knife")));
        AdvancementHolder axe = add(flint, "build_axe", MiteMod.item("flint_hatchet"), crafted(ItemTags.AXES));
        AdvancementHolder wood = add(axe, "mine_wood", Items.OAK_LOG, has(ItemTags.LOGS));
        AdvancementHolder bench = add(wood, "build_workbench", Items.CRAFTING_TABLE, crafted(matching("hardscrabble", ".+_workbench", Items.CRAFTING_TABLE)));
        add(wood, "build_club", MiteMod.item("wooden_cudgel"), crafted(MiteMod.item("wooden_cudgel")));
        add(bench, "build_shovel", MiteMod.item("flint_shovel"), crafted(ItemTags.SHOVELS));
        add(bench, "build_torches", Items.TORCH, crafted(Items.TORCH));
        AdvancementHolder nuggets = add(axe, "nuggets", Items.IRON_NUGGET, has(Tags.Items.NUGGETS));

        // Food and farming
        AdvancementHolder oven = add(bench, "build_oven", MiteMod.item("clay_furnace"), crafted(MiteMod.item("clay_furnace")));
        AdvancementHolder bread = add(oven, "make_bread", Items.BREAD, has(Items.BREAD));
        add(bread, "bake_cake", Items.CAKE, crafted(Items.CAKE));
        add(oven, "flour", FarmingContent.FLOUR.get(), has(FarmingContent.FLOUR.get()));
        AdvancementHolder dish = add(oven, "fine_dining", FarmingContent.BEEF_STEW.get(), ConsumeItemTrigger.TriggerInstance.usedItem(
                item(FarmingContent.DISHES.stream().map(DeferredItem::get).toArray(ItemLike[]::new))));
        add(dish, "supersize_me", Items.COOKED_BEEF, AdvancementType.GOAL, MiteTriggers.Event.of("supersize_me")); // MetabolismService.eat
        AdvancementHolder metalBench = add(nuggets, "better_tools", MiteMod.item("copper_workbench"),
                crafted(matching("hardscrabble", "(copper|silver|gold|iron|ancient_metal|mithril|adamantium)_workbench")));
        AdvancementHolder hoe = add(metalBench, "build_hoe", Items.COPPER_HOE, crafted(ItemTags.HOES)); // hoes and mattocks (itemTags)
        add(hoe, "build_scythe", MiteMod.item("copper_scythe"), crafted(matching("hardscrabble", ".+_scythe")));
        add(hoe, "plant_doctor", Items.WHEAT, MiteTriggers.Event.of("plant_doctor")); // FarmingRules bone meal on blight
        add(hoe, "soil_enrichment", FarmingContent.MANURE.get(), MiteTriggers.Event.of("soil_enrichment")); // P-1: ManureItem on farmland
        AdvancementHolder cow = add(axe, "kill_cow", Items.BEEF, KilledTrigger.TriggerInstance.playerKilledEntity(EntityPredicate.Builder.entity().of(entities, EntityTypes.COW)));

        // Metallurgy
        AdvancementHolder pickaxe = add(metalBench, "build_pickaxe", Items.COPPER_PICKAXE, crafted(ItemTags.PICKAXES));
        AdvancementHolder furnace = add(pickaxe, "build_furnace", Items.FURNACE, crafted(Items.FURNACE));
        AdvancementHolder iron = add(furnace, "acquire_iron", Items.IRON_INGOT, has(Items.IRON_INGOT));
        AdvancementHolder obsidianFurnace = add(iron, "obsidian_furnace", MiteMod.item("obsidian_furnace"), crafted(MiteMod.item("obsidian_furnace")));
        AdvancementHolder mithril = add(obsidianFurnace, "mithril_ingot", MiteMod.item("mithril_ingot"), AdvancementType.GOAL, has(MiteMod.item("mithril_ingot")));
        AdvancementHolder netherrackFurnace = add(mithril, "netherrack_furnace", MiteMod.item("netherrack_furnace"), crafted(MiteMod.item("netherrack_furnace")));
        add(netherrackFurnace, "adamantium_ingot", MiteMod.item("adamantium_ingot"), AdvancementType.CHALLENGE, has(MiteMod.item("adamantium_ingot")));
        add(mithril, "diamonds", Items.DIAMOND, has(Items.DIAMOND));
        add(iron, "crystal_breaker", Items.DIAMOND_ORE, MiteTriggers.Event.of("crystal_breaker"));
        add(iron, "emeralds", Items.EMERALD, has(Items.EMERALD));

        // Equipment
        AdvancementHolder leather = add(cow, "wear_leather", Items.LEATHER_CHESTPLATE,
                wearing(Items.LEATHER_HELMET, null, null, null), wearing(null, Items.LEATHER_CHESTPLATE, null, null),
                wearing(null, null, Items.LEATHER_LEGGINGS, null), wearing(null, null, null, Items.LEATHER_BOOTS));
        AdvancementHolder chain = add(leather, "build_chain_mail", Items.CHAINMAIL_CHESTPLATE, crafted(matching("(hardscrabble|minecraft)", ".*chainmail_.+")));
        String plate = "(copper|iron|golden|silver|rusted_iron|ancient_metal|mithril|adamantium)_";
        AdvancementHolder plateArmor = add(chain, "wear_all_plate_armor", Items.IRON_CHESTPLATE, AdvancementType.GOAL, PlayerTrigger.TriggerInstance.located(
                EntityPredicate.Builder.entity().equipment(EntityEquipmentPredicate.Builder.equipment()
                        .head(item(matching("(hardscrabble|minecraft)", plate + "helmet"))).chest(item(matching("(hardscrabble|minecraft)", plate + "chestplate")))
                        .legs(item(matching("(hardscrabble|minecraft)", plate + "leggings"))).feet(item(matching("(hardscrabble|minecraft)", plate + "boots"))))));
        add(plateArmor, "wear_all_adamantium_plate_armor", MiteMod.item("adamantium_chestplate"), AdvancementType.CHALLENGE, PlayerTrigger.TriggerInstance.located(
                EntityPredicate.Builder.entity().equipment(EntityEquipmentPredicate.Builder.equipment()
                        .head(item(MiteMod.item("adamantium_helmet"))).chest(item(MiteMod.item("adamantium_chestplate")))
                        .legs(item(MiteMod.item("adamantium_leggings"))).feet(item(MiteMod.item("adamantium_boots"))))));
        add(bench, "fishing_rod", Items.FISHING_ROD, crafted(matching("hardscrabble", ".+_fishing_rod", Items.FISHING_ROD)));
        AdvancementHolder books = add(iron, "bookshelves", Items.BOOKSHELF, crafted(Items.BOOKSHELF));
        add(books, "enchanter", Items.ENCHANTING_TABLE, EnchantedItemTrigger.TriggerInstance.enchantedItem());

        // Exploration (EF-DIM-02 CA-2: portal, portalToNether)
        add(bench, "seaworthy", Items.OAK_BOAT, crafted(ItemTags.BOATS));
        AdvancementHolder portal = add(iron, "portal", Items.OBSIDIAN, AdvancementType.GOAL, ChangeDimensionTrigger.TriggerInstance.changedDimensionTo(UnderworldContent.LEVEL));
        AdvancementHolder nether = add(portal, "portal_to_nether", Items.NETHERRACK, AdvancementType.GOAL,
                ChangeDimensionTrigger.TriggerInstance.changedDimension(UnderworldContent.LEVEL, Level.NETHER));
        // Changelog l.420 (R1xx): an achievement for beating the Ender Dragon. The extra requirement it names is not documented.
        add(nether, "kill_dragon", Items.DRAGON_HEAD, AdvancementType.CHALLENGE,
                KilledTrigger.TriggerInstance.playerKilledEntity(EntityPredicate.Builder.entity().of(entities, EntityTypes.ENDER_DRAGON)));
        add(mithril, "runegate", Runegate.MITHRIL_RUNESTONE.get(), AdvancementType.GOAL, MiteTriggers.Event.of("runegate")); // Runegate.teleport
        add(root, "explorer", Items.COMPASS, AdvancementType.CHALLENGE, MiteTriggers.Event.of("explorer"));
        add(books, "enlightenment", Items.WRITTEN_BOOK, MiteTriggers.Event.of("enlightenment")); // EF-UI-09: treasure.LoreBooks
        // EF-NUT-11: awarded by MiteSleep through its "slept" criterion.
        Advancement.Builder.advancement().parent(root).display(Items.STRAW_BED, title("well_rested"), description("well_rested"), AdvancementType.TASK, true, false, false)
                .addCriterion("slept", MiteTriggers.Event.of("well_rested")).save(output, MiteSleep.WELL_RESTED.toString());
    }

    private AdvancementHolder add(AdvancementHolder parent, String name, ItemLike icon, Criterion<?>... anyOf) {
        return add(parent, name, icon, AdvancementType.TASK, anyOf);
    }

    /** One advancement granted by any of its criteria. */
    private AdvancementHolder add(AdvancementHolder parent, String name, ItemLike icon, AdvancementType type, Criterion<?>... anyOf) {
        Advancement.Builder b = Advancement.Builder.advancement().parent(parent)
                .display(icon.asItem(), title(name), description(name), type, true, type != AdvancementType.TASK, false);
        return anyOf(b, anyOf).save(output, MiteMod.id(name).toString());
    }

    /** Granted by any of the criteria, named c0..cN. */
    private static Advancement.Builder anyOf(Advancement.Builder b, Criterion<?>... criteria) {
        b.requirements(AdvancementRequirements.Strategy.OR);
        for (int i = 0; i < criteria.length; i++) b.addCriterion("c" + i, criteria[i]);
        return b;
    }

    private static Component title(String name) {
        return Component.translatable("advancements.hardscrabble." + name + ".title");
    }

    private static Component description(String name) {
        return Component.translatable("advancements.hardscrabble." + name + ".description");
    }

    /** Registered items whose namespace and path match the regexes, plus {@code extra}. */
    private static ItemLike[] matching(String namespace, String path, ItemLike... extra) {
        var found = BuiltInRegistries.ITEM.keySet().stream().filter(k -> k.getNamespace().matches(namespace) && k.getPath().matches(path))
                .sorted().map(BuiltInRegistries.ITEM::getValue);
        return java.util.stream.Stream.concat(found, java.util.Arrays.stream(extra).map(ItemLike::asItem)).toArray(ItemLike[]::new);
    }

    private ItemPredicate.Builder item(ItemLike... of) {
        return ItemPredicate.Builder.item().of(items, of);
    }

    private Criterion<?> has(ItemLike... of) {
        return InventoryChangeTrigger.TriggerInstance.hasItems(of);
    }

    private Criterion<?> has(TagKey<Item> tag) {
        return InventoryChangeTrigger.TriggerInstance.hasItems(ItemPredicate.Builder.item().of(items, tag));
    }

    private Criterion<?> crafted(ItemLike... of) {
        return MiteTriggers.Crafted.of(item(of).build());
    }

    private Criterion<?> crafted(TagKey<Item> tag) {
        return MiteTriggers.Crafted.of(ItemPredicate.Builder.item().of(items, tag).build());
    }

    private Criterion<?> wearing(@Nullable Item head, @Nullable Item chest, @Nullable Item legs, @Nullable Item feet) {
        EntityEquipmentPredicate.Builder e = EntityEquipmentPredicate.Builder.equipment();
        if (head != null) e.head(item(head));
        if (chest != null) e.chest(item(chest));
        if (legs != null) e.legs(item(legs));
        if (feet != null) e.feet(item(feet));
        return PlayerTrigger.TriggerInstance.located(EntityPredicate.Builder.entity().equipment(e));
    }

    /** EF-UI-07 explorer: horizontal distance from the Overworld spawn. */
    static void checkExplorer(ServerPlayer p) {
        if (p.level().dimension() != Level.OVERWORLD) return;
        BlockPos spawn = p.level().getRespawnData().pos();
        double dx = p.getX() - spawn.getX(), dz = p.getZ() - spawn.getZ();
        if (dx * dx + dz * dz >= (double) EXPLORER_DISTANCE * EXPLORER_DISTANCE) MiteTriggers.EVENT.get().trigger(p, "explorer");
    }

    /** crystalBreaker [INFÉRÉ]: diamond, emerald and quartz ores. */
    static boolean isCrystalOre(BlockState state) {
        return state.is(Tags.BlockItems.ORES_DIAMOND.block()) || state.is(Tags.BlockItems.ORES_EMERALD.block()) || state.is(Tags.BlockItems.ORES_QUARTZ.block());
    }

    /**
     * dom-P-02: MITE tools join the vanilla tool tags, so build_axe/shovel/hoe/pickaxe and other mods see them. Changelog
     * l.753: every hoe and every mattock counts for "Time to Farm". Called by MiteItemTags (the only item tags provider).
     */
    public static void itemTags(Function<TagKey<Item>, TagAppender<Item>> tag) {
        var tools = ToolStats.all().entrySet().stream().sorted(Comparator.comparing(e -> BuiltInRegistries.ITEM.getKey(e.getKey()))).toList(); // stable file order
        for (var e : tools) {
            if (!BuiltInRegistries.ITEM.getKey(e.getKey()).getNamespace().equals(MiteMod.MODID)) continue;
            TagKey<Item> t = switch (e.getValue().type()) {
                case PICKAXE -> ItemTags.PICKAXES;
                case HOE, MATTOCK -> ItemTags.HOES;
                case AXE, HATCHET -> ItemTags.AXES;
                case SHOVEL -> ItemTags.SHOVELS;
                default -> null;
            };
            if (t != null) tag.apply(t).add(BuiltInRegistries.ITEM.getResourceKey(e.getKey()).orElseThrow());
        }
    }

    /**
     * dom-P-05: vanilla story advancements that ask for removed items (stone pickaxe, diamond armor) are rewritten for MITE
     * items, same ids and parents: any pickaxe for upgrade_tools, any mithril armor piece for shiny_gear (diamond tier).
     */
    public static final class Story extends AdvancementSubProvider {
        private final HolderGetter<Item> items;

        public Story(BootstrapContext<Advancement> output) {
            super(output);
            this.items = output.lookup(Registries.ITEM);
        }

        @Override
        public void generate() {
            story("upgrade_tools", "mine_stone", Items.COPPER_PICKAXE, InventoryChangeTrigger.TriggerInstance.hasItems(ItemPredicate.Builder.item().of(items, ItemTags.PICKAXES)));
            story("shiny_gear", "mine_diamond", MiteMod.item("mithril_chestplate"), Stream.of("helmet", "chestplate", "leggings", "boots")
                    .map(s -> InventoryChangeTrigger.TriggerInstance.hasItems(MiteMod.item("mithril_" + s))).toArray(Criterion<?>[]::new));
        }

        private void story(String name, String parent, ItemLike icon, Criterion<?>... anyOf) {
            Advancement.Builder b = Advancement.Builder.advancement().parent(new AdvancementHolder(Identifier.withDefaultNamespace("story/" + parent), null)) // id only
                    .display(icon.asItem(), Component.translatable("advancements.story." + name + ".title"),
                            Component.translatable("advancements.story." + name + ".description"), AdvancementType.TASK, true, true, false);
            anyOf(b, anyOf).save(output, "minecraft:story/" + name);
        }
    }

    public static void register(IEventBus modBus) {
        MiteTriggers.register(modBus);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.ItemCraftedEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) MiteTriggers.CRAFTED.get().trigger(p, e.getCrafting());
        });
        NeoForge.EVENT_BUS.addListener((PlayerTickEvent.Post e) -> {
            if (e.getEntity() instanceof ServerPlayer p && p.tickCount % 100 == 0) checkExplorer(p);
        });
        // Lowest priority: only blocks really broken (not cancelled by a harvest rule) count.
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (BreakBlockEvent e) -> {
            if (e.getPlayer() instanceof ServerPlayer p && isCrystalOre(e.getState())) MiteTriggers.EVENT.get().trigger(p, "crystal_breaker");
        });
    }
}
