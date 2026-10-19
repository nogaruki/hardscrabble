package dev.mite.underworld;

import com.mojang.serialization.JsonOps;
import dev.mite.MiteMod;
import dev.mite.entity.mob.MobContent;
import dev.mite.gametest.MiteGameTests;
import dev.mite.material.MaterialItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import java.util.List;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/** GameTests for EF-DIM-04 (dungeon loot), EF-MET-08 (ancient metal sources, arrow recycling), EF-MOB-19 (Longdeads). */
public final class UnderworldTests {
    private UnderworldTests() {}

    public static void register() {
        // EF-DIM-04 CA-1: 100 dungeon chests hold ancient metal ingots and nuggets.
        MiteGameTests.add("underworld_dungeon_loot", 20, h -> {
            LootTable table = h.getLevel().getServer().reloadableRegistries().getLootTable(UnderworldContent.DUNGEON_CHEST);
            LootParams params = new LootParams.Builder(h.getLevel()).withParameter(LootContextParams.ORIGIN, h.absoluteVec(net.minecraft.world.phys.Vec3.ZERO))
                    .create(LootContextParamSets.CHEST);
            RandomSource random = RandomSource.create(1);
            int ingots = 0, nuggets = 0;
            for (int i = 0; i < 100; i++) {
                for (ItemStack s : table.getRandomItems(params, random)) {
                    if (s.is(MaterialItems.ANCIENT_METAL_INGOT.get())) ingots++;
                    if (s.is(MaterialItems.ANCIENT_METAL_NUGGET.get())) nuggets++;
                }
            }
            h.assertTrue(ingots > 0 && nuggets > 0, "ancient metal in 100 chests: " + ingots + " ingot stacks, " + nuggets + " nugget stacks");
            h.assertTrue(h.getLevel().registryAccess().lookupOrThrow(Registries.PLACED_FEATURE).get(UnderworldData.PLACED).isPresent(), "dungeon placed feature");
            h.succeed();
        });
        // EF-DIM-04: the fallback room is carved only into a box of replaceable solid rock (the permanent Mantle refuses it, like
        // bedrock) next to a cave it opens onto, then its chests get the Underworld loot table and its spawner a Longdead or a
        // Longdead Guardian.
        MiteGameTests.add("underworld_dungeon_room", 20, h -> {
            var level = h.getLevel();
            BlockPos origin = h.absolutePos(new BlockPos(3, 0, 3));
            for (BlockPos p : BlockPos.betweenClosed(origin.offset(-3, -1, -3), origin.offset(3, 4, 3))) level.setBlock(p, Blocks.STONE.defaultBlockState(), 2);
            Block mantle = dev.mite.dimension.UnderworldContent.MANTLE.get();
            for (Block permanent : new Block[] {mantle, dev.mite.worldgen.WorldgenContent.CORE.get()})
                for (var tag : List.of(BlockTags.WITHER_IMMUNE, BlockTags.DRAGON_IMMUNE, BlockTags.FEATURES_CANNOT_REPLACE))
                    h.assertTrue(permanent.defaultBlockState().is(tag), permanent + " not in " + tag.location());
            level.setBlock(origin.offset(1, 2, 1), mantle.defaultBlockState(), 2);
            h.assertFalse(UnderworldDungeonFeature.carveRoom(level, RandomSource.create(1), origin), "room carved through the Mantle");
            h.assertTrue(level.getBlockState(origin).is(Blocks.STONE), "refused room changed the rock");
            level.setBlock(origin.offset(1, 2, 1), Blocks.STONE.defaultBlockState(), 2);
            RandomSource random = RandomSource.create(1);
            h.assertTrue(UnderworldDungeonFeature.carveRoom(level, random, origin), "room not carved into solid stone");
            // The air of the test area around the box is the cave: one wall gets a two-high doorway onto it.
            h.assertTrue(net.minecraft.core.Direction.Plane.HORIZONTAL.stream().anyMatch(d -> level.getBlockState(origin.relative(d, 3)).isAir()
                    && level.getBlockState(origin.relative(d, 3).above()).isAir()), "no doorway onto the cave");
            UnderworldDungeonFeature.furnish(level, random, origin);
            BlockState floor = level.getBlockState(origin.below());
            h.assertTrue(level.getBlockState(origin.above()).isAir() && (floor.is(Blocks.COBBLESTONE) || floor.is(Blocks.MOSSY_COBBLESTONE)), "room shell and interior");
            var entity = level.getBlockEntity(origin) instanceof SpawnerBlockEntity s ? s.getSpawner().getOrCreateDisplayEntity(level, origin) : null;
            h.assertTrue(entity != null && (entity.getType() == MobContent.LONGDEAD.get() || entity.getType() == MobContent.LONGDEAD_GUARDIAN.get()),
                    "spawner entity " + (entity == null ? null : entity.getType()));
            int chests = 0;
            for (BlockPos p : BlockPos.betweenClosed(origin.offset(-3, 0, -3), origin.offset(3, 0, 3))) {
                if (!(level.getBlockEntity(p) instanceof ChestBlockEntity chest)) continue;
                h.assertValueEqual(chest.getLootTable(), UnderworldContent.DUNGEON_CHEST, "chest loot table");
                chests++;
            }
            h.assertTrue(chests >= 1, "no chest in the room");
            h.succeed();
        });
        // A vanilla monster room on a bedrock plate (or any block features cannot replace) is refused instead of built without its
        // spawner ("Failed to fetch mob spawner entity" in the log); on stone the same room is built with its spawner.
        MiteGameTests.add("underworld_dungeon_on_bedrock", 20, h -> {
            var level = h.getLevel();
            BlockPos origin = h.absolutePos(new BlockPos(3, 1, 3));
            for (BlockPos p : BlockPos.betweenClosed(origin.offset(-3, 0, -3), origin.offset(3, 4, 3))) level.setBlock(p, Blocks.STONE.defaultBlockState(), 2);
            level.setBlock(origin.offset(3, 0, 0), Blocks.AIR.defaultBlockState(), 2); // the room's one opening
            level.setBlock(origin.offset(3, 1, 0), Blocks.AIR.defaultBlockState(), 2);
            long seed = 0; // vanilla draws the x then z radius as 2 + nextInt(2): radii 2 fit the 7x7 platform
            while (!smallRoom(RandomSource.create(seed))) seed++;
            var generator = level.getChunkSource().getGenerator();
            var room = new net.minecraft.world.level.levelgen.feature.MonsterRoomFeature();
            level.setBlock(origin, Blocks.BEDROCK.defaultBlockState(), 2);
            h.assertFalse(room.place(level, generator, RandomSource.create(seed), origin), "room built around bedrock");
            h.assertTrue(level.getBlockState(origin.above()).is(Blocks.STONE), "refused room changed the rock");
            level.setBlock(origin, Blocks.STONE.defaultBlockState(), 2);
            h.assertTrue(room.place(level, generator, RandomSource.create(seed), origin), "room not built in stone");
            h.assertTrue(level.getBlockEntity(origin) instanceof SpawnerBlockEntity, "room without spawner");
            h.succeed();
        });
        // EF-MET-08: the ancient metal arrow is fired by bows and recycles into a nugget.
        MiteGameTests.add("underworld_arrow_recycling", 20, h -> {
            Item arrow = UnderworldContent.ANCIENT_METAL_ARROW.get();
            h.assertTrue(new ItemStack(arrow).is(ItemTags.ARROWS), "ancient metal arrow in minecraft:arrows");
            RecipeHolder<?> recipe = h.getLevel().getServer().getRecipeManager()
                    .byKey(ResourceKey.create(Registries.RECIPE, Identifier.parse(UnderworldData.RECYCLE_RECIPE))).orElse(null);
            h.assertTrue(recipe != null, "recycling recipe " + UnderworldData.RECYCLE_RECIPE);
            h.succeed();
        });
        // EF-MET-08 CA-1: no vanilla chest loot table names the ancient metal ingot.
        MiteGameTests.add("underworld_no_overworld_ingot", 20, h -> {
            HolderLookup.Provider registries = h.getLevel().getServer().reloadableRegistries().lookup();
            var ops = registries.createSerializationContext(JsonOps.INSTANCE);
            String ingot = BuiltInRegistries.ITEM.getKey(MaterialItems.ANCIENT_METAL_INGOT.get()).toString();
            int checked = 0;
            for (var holder : registries.lookupOrThrow(Registries.LOOT_TABLE).listElements().toList()) {
                Identifier id = holder.key().identifier();
                if (!id.getNamespace().equals("minecraft") || !id.getPath().startsWith("chests/")) continue;
                String json = LootTable.DIRECT_CODEC.encodeStart(ops, holder.value()).getOrThrow().toString();
                h.assertFalse(json.contains(ingot), id + " yields " + ingot);
                checked++;
            }
            h.assertTrue(checked > 20, "vanilla chest tables checked: " + checked);
            h.succeed();
        });
        // EF-MOB-19 CA-1: attributes, XP (skeleton base 5) and ancient metal gear of naturally spawned Longdeads.
        MiteGameTests.add("underworld_longdead", 20, h -> {
            Mob longdead = longdead(h, MobContent.LONGDEAD.get(), 12, 6, 15);
            h.assertTrue(longdead.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.BOW), "longdead bow");
            h.assertTrue(longdead.getItemBySlot(EquipmentSlot.HEAD).is(ancientMetal("helmet")), "longdead helmet");
            h.assertTrue(longdead.getProjectile(longdead.getMainHandItem()).is(UnderworldContent.ANCIENT_METAL_ARROW.get()), "longdead arrows");
            Mob guardian = longdead(h, MobContent.LONGDEAD_GUARDIAN.get(), 24, 8, 25);
            h.assertTrue(guardian.getItemBySlot(EquipmentSlot.MAINHAND).is(ancientMetal("sword")), "guardian sword");
            h.assertTrue(guardian.getItemBySlot(EquipmentSlot.CHEST).is(ancientMetal("chestplate")), "guardian chestplate");
            h.assertTrue(guardian.is(net.minecraft.tags.EntityTypeTags.UNDEAD), "guardian undead");
            h.succeed();
        });
    }

    private static boolean smallRoom(RandomSource random) {
        return random.nextInt(2) == 0 && random.nextInt(2) == 0;
    }

    private static Item ancientMetal(String name) {
        return BuiltInRegistries.ITEM.getValue(MiteMod.id("ancient_metal_" + name));
    }

    private static Mob longdead(GameTestHelper h, EntityType<? extends Mob> type, double health, double damage, int xp) {
        Mob mob = h.spawn(type, 3, 1, 3); // no equipment: worn items add random XP
        String name = type.toShortString();
        h.assertValueEqual(mob.getAttributeValue(Attributes.MAX_HEALTH), health, name + " max health");
        h.assertValueEqual(mob.getAttributeValue(Attributes.ATTACK_DAMAGE), damage, name + " attack damage");
        h.assertTrue(Math.abs(mob.getAttributeValue(Attributes.MOVEMENT_SPEED) - 0.29) < 1e-6, name + " speed");
        h.assertValueEqual(mob.getAttributeValue(Attributes.FOLLOW_RANGE), 40.0, name + " follow range");
        h.assertValueEqual(mob.getExperienceReward(h.getLevel(), null), xp, name + " XP");
        return h.spawn(type, new BlockPos(3, 1, 3), EntitySpawnReason.NATURAL);
    }
}
