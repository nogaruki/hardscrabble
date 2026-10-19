package dev.mite.entity.mob;

import net.minecraft.core.registries.BuiltInRegistries;
import java.util.function.Function;
import net.minecraft.data.tags.TagAppender;
import dev.mite.MiteMod;
import dev.mite.material.MaterialItems;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.EntityLootSubProvider;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.data.loot.LootTableSubProvider;
import net.minecraft.data.tags.DamageTypeTagsProvider;
import net.minecraft.data.tags.EntityTypeTagsProvider;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.random.Weighted;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings.SpawnerData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.EnchantedCountIncreaseFunction;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.predicates.LootItemKilledByPlayerCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemEntityPropertyCondition;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.advancements.predicates.MinMaxBounds;
import net.minecraft.advancements.predicates.entity.CubeMobPredicate;
import net.minecraft.advancements.predicates.entity.EntityPredicate;
import net.minecraft.world.level.storage.loot.providers.number.floats.ContextFloatProviders;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Mob datagen: spawn egg and gelatinous ball models, loot tables, entity and damage type tags, natural spawns (EF-MOB-01..20). */
public final class MobData {
    private MobData() {}

    /** sounds.json of the MITE mob voices: vanilla sound events replayed at another pitch and volume (MobSounds). */
    public static final class Sounds extends net.neoforged.neoforge.common.data.SoundDefinitionsProvider {
        public Sounds(PackOutput output) {
            super(output, dev.mite.MiteMod.MODID);
        }

        @Override
        public void registerSounds() {
            for (MobSounds.Def d : MobSounds.DEFS) {
                var def = definition().subtitle("subtitles.hardscrabble." + MobSounds.id(d.mob(), d.kind()));
                for (MobSounds.Layer l : d.layers())
                    def.with(sound(net.minecraft.resources.Identifier.withDefaultNamespace(l.event()), net.neoforged.neoforge.common.data.SoundDefinition.SoundType.EVENT)
                            .pitch(l.pitch()).volume(l.volume()));
                add(MobSounds.holder(d.mob(), d.kind()), def);
            }
        }
    }

    public static final class Models extends ModelProvider {
        public Models(PackOutput output) {
            super(output, MiteMod.MODID);
        }

        @Override
        protected void registerModels(BlockModelGenerators blocks, ItemModelGenerators items) {
            MobContent.EGGS.forEach(e -> items.generateFlatItem(e.get(), ModelTemplates.FLAT_ITEM));
            MobContent.GELATINOUS_BALLS.forEach(e -> items.generateFlatItem(e.get(), ModelTemplates.FLAT_ITEM));
        }

        @Override
        protected Stream<? extends Holder<Block>> getKnownBlocks() {
            return Stream.empty();
        }

        @Override
        protected Stream<? extends Holder<Item>> getKnownItems() {
            return MobContent.ITEMS.getEntries().stream();
        }

        @Override
        public String getName() {
            return "Mob spawn egg models";
        }
    }

    public static final LootTableProvider.SubProviderEntry LOOT = new LootTableProvider.SubProviderEntry(Loot::new, LootContextParamSets.ENTITY);

    /** Zombie-like rotten flesh; the Wight adds its silver nugget (EF-MOB-02); the Wood Spider drops like a cave spider. */
    private static final class Loot extends EntityLootSubProvider {
        Loot(LootTableSubProvider.Context context) {
            super(FeatureFlags.REGISTRY.allFlags(), context);
        }

        private LootPool.Builder pool(Item item, int min, int max) {
            return LootPool.lootPool().setRolls(ContextIntProviders.exactly(1)).add(LootItem.lootTableItem(item)
                    .apply(SetItemCountFunction.setCount(ContextIntProviders.between(min, max)))
                    .apply(EnchantedCountIncreaseFunction.lootingMultiplier(enchantments, ContextFloatProviders.between(0.0F, 1.0F))));
        }

        @Override
        public void generate() {
            for (var flesh : List.of(MobContent.GHOUL, MobContent.REVENANT, MobContent.INVISIBLE_STALKER))
                add(flesh.get(), LootTable.lootTable().withPool(pool(Items.ROTTEN_FLESH, 0, 2)));
            add(MobContent.WIGHT.get(), LootTable.lootTable().withPool(pool(Items.ROTTEN_FLESH, 0, 2))
                    .withPool(LootPool.lootPool().setRolls(ContextIntProviders.exactly(1)).add(LootItem.lootTableItem(MaterialItems.SILVER_NUGGET.get()))));
            // EF-MOB-19 / EF-MET-08: skeleton bones; the Longdead drops its ancient metal arrows, recyclable into nuggets [INFÉRÉ].
            add(MobContent.LONGDEAD.get(), LootTable.lootTable().withPool(pool(Items.BONE, 0, 2))
                    .withPool(pool(dev.mite.underworld.UnderworldContent.ANCIENT_METAL_ARROW.get(), 0, 2)));
            // EF-MOB-20: skeleton bones [INFÉRÉ for the Bone Lords].
            for (var bones : List.of(MobContent.LONGDEAD_GUARDIAN, MobContent.BONE_LORD, MobContent.ANCIENT_BONE_LORD))
                add(bones.get(), LootTable.lootTable().withPool(pool(Items.BONE, 0, 2)));
            add(MobContent.DIRE_WOLF.get(), LootTable.lootTable().withPool(pool(Items.LEATHER, 0, 2))); // [INFÉRÉ] MITE wolves give leather
            // EF-MOB-13: creeper gunpowder [INFÉRÉ].
            add(MobContent.INFERNAL_CREEPER.get(), LootTable.lootTable().withPool(pool(Items.GUNPOWDER, 0, 2)));
            // EF-MOB-08/09/10/11: spider drops.
            for (var spider : List.of(MobContent.WOOD_SPIDER, MobContent.DEMON_SPIDER, MobContent.PHASE_SPIDER, MobContent.BLACK_WIDOW))
                add(spider.get(), LootTable.lootTable().withPool(pool(Items.STRING, 0, 2)).withPool(pool(Items.SPIDER_EYE, -1, 1).when(LootItemKilledByPlayerCondition.killedByPlayer())));
            // Nothing: the immaterial Shadow and the Hellhound (no leather, EF-MOB-03/07), the elementals (EF-MOB-14/15), the Clay Golem,
            // the bats and the silverfish (EF-MOB-15/17/18) [INFÉRÉ except the Hellhound].
            for (var empty : List.of(MobContent.SHADOW, MobContent.HELLHOUND, MobContent.FIRE_ELEMENTAL, MobContent.EARTH_ELEMENTAL, MobContent.CLAY_GOLEM,
                    MobContent.VAMPIRE_BAT, MobContent.GIANT_VAMPIRE_BAT, MobContent.NIGHTWING, MobContent.COPPERSPINE, MobContent.NETHERSPAWN,
                    MobContent.HOARY_SILVERFISH)) add(empty.get(), LootTable.lootTable());
            // EF-MOB-16: like the slime, 0 to 2 coloured balls from the tiniest cubes.
            var cubes = List.of(MobContent.JELLY, MobContent.BLOB, MobContent.OOZE, MobContent.PUDDING);
            for (int i = 0; i < cubes.size(); i++)
                add(cubes.get(i).get(), LootTable.lootTable().withPool(pool(MobContent.GELATINOUS_BALLS.get(i).get(), 0, 2).when(LootItemEntityPropertyCondition.hasProperties(
                        LootContext.EntityTarget.THIS, EntityPredicate.Builder.entity().cubeMob(CubeMobPredicate.sized(MinMaxBounds.Ints.exactly(1)))))));
        }

        @Override
        protected Stream<EntityType<?>> getKnownEntityTypes() {
            return MobContent.ENTITIES.getEntries().stream().map(Holder::value);
        }
    }

    /** Undead mobs join minecraft:zombies or minecraft:skeletons (undead, smite, silver) and burn in daylight; the Wight is immaterial. Called by MiteEntityTypeTags. */
    public static void entityTypeTags(Function<TagKey<EntityType<?>>, TagAppender<EntityType<?>>> tag) {
        for (TagKey<EntityType<?>> t : List.of(EntityTypeTags.ZOMBIES, EntityTypeTags.BURN_IN_DAYLIGHT))
            tag.apply(t).add(key(MobContent.GHOUL.get()), key(MobContent.WIGHT.get()), key(MobContent.REVENANT.get()), key(MobContent.INVISIBLE_STALKER.get()));
        tag.apply(EntityTypeTags.ARTHROPOD).add(key(MobContent.WOOD_SPIDER.get()), key(MobContent.DEMON_SPIDER.get()), key(MobContent.PHASE_SPIDER.get()),
                key(MobContent.BLACK_WIDOW.get()), key(MobContent.COPPERSPINE.get()), key(MobContent.NETHERSPAWN.get()), key(MobContent.HOARY_SILVERFISH.get()));
        for (TagKey<EntityType<?>> t : List.of(EntityTypeTags.SKELETONS, EntityTypeTags.BURN_IN_DAYLIGHT))
            tag.apply(t).add(key(MobContent.LONGDEAD.get()), key(MobContent.LONGDEAD_GUARDIAN.get()), key(MobContent.BONE_LORD.get()), key(MobContent.ANCIENT_BONE_LORD.get()));
        tag.apply(MobContent.IMMATERIAL).add(key(MobContent.WIGHT.get()), key(MobContent.SHADOW.get()), key(MobContent.NIGHTWING.get()));
    }

    private static ResourceKey<EntityType<?>> key(EntityType<?> type) {
        return BuiltInRegistries.ENTITY_TYPE.getResourceKey(type).orElseThrow();
    }

    /** ET-J-03: fire and lava (is_fire), magic, thorns and /kill hurt immaterial mobs. */
    public static final class DamageTags extends DamageTypeTagsProvider {
        public DamageTags(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
            super(output, lookup, MiteMod.MODID);
        }

        @Override
        protected void addTags(HolderLookup.Provider registries) {
            tag(MobContent.HURTS_IMMATERIAL).addTag(DamageTypeTags.IS_FIRE).addTag(DamageTypeTags.BYPASSES_INVULNERABILITY)
                    .add(DamageTypes.MAGIC, DamageTypes.INDIRECT_MAGIC, DamageTypes.THORNS);
        }

        @Override
        public String getName() {
            return "Tags - hardscrabble mob damage types";
        }
    }

    private static void spawn(BootstrapContext<BiomeModifier> c, String name, HolderSet<Biome> biomes, EntityType<?> type, int weight) {
        spawn(c, name, biomes, type, weight, 1, 2);
    }

    private static void spawn(BootstrapContext<BiomeModifier> c, String name, HolderSet<Biome> biomes, EntityType<?> type, int weight, int min, int max) {
        c.register(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, MiteMod.id("spawn_" + name)),
                BiomeModifiers.AddSpawnsBiomeModifier.singleSpawn(biomes, new Weighted<>(new SpawnerData(type, UniformInt.of(min, max)), weight)));
    }

    /** Spawn weights of EF-MOB: depth mobs 10 in every Overworld biome (depth checked by the spawn predicate), Wood Spider 20 in forests, Longdeads in the Underworld. */
    public static void biomeModifiers(BootstrapContext<BiomeModifier> c) {
        var biomes = c.lookup(Registries.BIOME);
        HolderSet<Biome> overworld = biomes.getOrThrow(BiomeTags.IS_OVERWORLD);
        spawn(c, "ghoul", overworld, MobContent.GHOUL.get(), 10);
        spawn(c, "wight", overworld, MobContent.WIGHT.get(), 10);
        spawn(c, "revenant", overworld, MobContent.REVENANT.get(), 10);
        spawn(c, "wood_spider", biomes.getOrThrow(BiomeTags.IS_FOREST), MobContent.WOOD_SPIDER.get(), 20);
        // EF-MOB-03/04/07/09: depth mobs; Demon Spider weight 5 [INFÉRÉ, the spec gives 5 for the Phase Spider row only].
        spawn(c, "shadow", overworld, MobContent.SHADOW.get(), 10);
        spawn(c, "invisible_stalker", overworld, MobContent.INVISIBLE_STALKER.get(), 10);
        spawn(c, "hellhound", overworld, MobContent.HELLHOUND.get(), 10);
        spawn(c, "demon_spider", overworld, MobContent.DEMON_SPIDER.get(), 5);
        // EF-MOB-06: Dire Wolf packs of 2 to 4 in the snowy taiga, weight 10 [INFÉRÉ].
        spawn(c, "dire_wolf", HolderSet.direct(biomes.getOrThrow(Biomes.SNOWY_TAIGA)), MobContent.DIRE_WOLF.get(), 10, 2, 4);
        // EF-MOB-19 / EF-IA-06: Longdead 40 in the Underworld; Guardian weight 10 [INFÉRÉ, the spec gives one weight for the pair].
        HolderSet<Biome> underworld = biomes.getOrThrow(dev.mite.underworld.UnderworldContent.IS_UNDERWORLD);
        spawn(c, "longdead", underworld, MobContent.LONGDEAD.get(), 40);
        spawn(c, "longdead_guardian", underworld, MobContent.LONGDEAD_GUARDIAN.get(), 10);
        // EF-MOB-15/20: Earth Elemental 10 and Bone Lord 5 (depth by the spawn predicate), Ancient Bone Lord 5 in the Underworld.
        spawn(c, "earth_elemental", overworld, MobContent.EARTH_ELEMENTAL.get(), 10);
        spawn(c, "bone_lord", overworld, MobContent.BONE_LORD.get(), 5);
        spawn(c, "ancient_bone_lord", underworld, MobContent.ANCIENT_BONE_LORD.get(), 5);
        // EF-MOB-14: deep lava of the Overworld, weight 10 [INFÉRÉ: no weight in the spec].
        spawn(c, "fire_elemental", overworld, MobContent.FIRE_ELEMENTAL.get(), 10);
        // EF-MOB-10/11/15: Phase Spider 5, Black Widow 10 [INFÉRÉ: no weight], Clay Golem 50 (by clay, spawn predicate).
        spawn(c, "phase_spider", overworld, MobContent.PHASE_SPIDER.get(), 5);
        spawn(c, "black_widow", overworld, MobContent.BLACK_WIDOW.get(), 10);
        spawn(c, "clay_golem", overworld, MobContent.CLAY_GOLEM.get(), 50);
        // EF-MOB-16: Jelly 30, Blob 30, Ooze 20, Pudding 30.
        spawn(c, "jelly", overworld, MobContent.JELLY.get(), 30);
        spawn(c, "blob", overworld, MobContent.BLOB.get(), 30);
        spawn(c, "ooze", overworld, MobContent.OOZE.get(), 20);
        spawn(c, "pudding", overworld, MobContent.PUDDING.get(), 30);
        // EF-MOB-17: Vampire Bat 20, Nightwing 4, Giant Vampire Bat 5 [INFÉRÉ: no weight].
        spawn(c, "vampire_bat", overworld, MobContent.VAMPIRE_BAT.get(), 20);
        spawn(c, "giant_vampire_bat", overworld, MobContent.GIANT_VAMPIRE_BAT.get(), 5);
        spawn(c, "nightwing", overworld, MobContent.NIGHTWING.get(), 4);
        // EF-MOB-18: weight 10 each [INFÉRÉ]: Copperspine near copper, Netherspawn in the Nether [INFÉRÉ from the name], Hoary in the Underworld.
        spawn(c, "copperspine", overworld, MobContent.COPPERSPINE.get(), 10);
        spawn(c, "netherspawn", biomes.getOrThrow(BiomeTags.IS_NETHER), MobContent.NETHERSPAWN.get(), 10);
        spawn(c, "hoary_silverfish", underworld, MobContent.HOARY_SILVERFISH.get(), 10);
        // Changelog l.301 (R156): silverfish in any biome, more in mountains (1.6.4 extreme hills = windswept hills, and the peaks)
        // [INFÉRÉ: weight 5, +10 in #is_mountain and #is_hill].
        spawn(c, "silverfish", overworld, EntityTypes.SILVERFISH, 5);
        spawn(c, "silverfish_mountain", biomes.getOrThrow(BiomeTags.IS_MOUNTAIN), EntityTypes.SILVERFISH, 10);
        spawn(c, "silverfish_hill", biomes.getOrThrow(BiomeTags.IS_HILL), EntityTypes.SILVERFISH, 10);
        // EF-IA-06 / K-2: the spider weighs 80 instead of 100 wherever it spawns.
        c.register(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, MiteMod.id("spider_weight")), new SpawnWeightModifier(overworld, EntityTypes.SPIDER, 80));
    }
}
