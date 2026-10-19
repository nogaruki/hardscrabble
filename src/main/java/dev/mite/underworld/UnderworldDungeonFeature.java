package dev.mite.underworld;

import com.mojang.serialization.MapCodec;
import dev.mite.entity.mob.MobContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.MonsterRoomFeature;

/**
 * EF-DIM-04: the vanilla monster room, with the Underworld chest loot table and a Longdead spawner (one in four a Longdead
 * Guardian [INFÉRÉ]). Vanilla chests sit on the spawner's layer within 3 blocks of it.
 */
public record UnderworldDungeonFeature() implements Feature {
    public static final MapCodec<UnderworldDungeonFeature> CODEC = MapCodec.unit(UnderworldDungeonFeature::new);

    /**
     * The vanilla room needs a fully solid floor and ceiling and 1 to 5 two-high openings, which the Underworld's large
     * 3D caverns almost never offer (0 rooms in 81 chunks, e2e suite). One failed attempt in FALLBACK_ODDS carves a room
     * into solid rock next to a cave instead. ponytail: fixed odds tuned to about one room per 12 chunks, twice the Overworld
     * dungeons of a playtest world (799 spawners in 22782 chunks); tune in playtest.
     */
    static final int FALLBACK_ODDS = 25;

    /** Longest tunnel, beyond the wall, from a carved room to the cave it opens onto. */
    static final int TUNNEL = 8;

    /**
     * A 7x6x7 cobblestone room (5x4x5 inside) carved where the whole box is replaceable solid rock, with a 1x2 doorway and
     * tunnel to a cave at most {@link #TUNNEL} blocks from its wall. Without the tunnel the room was sealed in the rock: 18 of
     * the 20 rooms of the e2e area could not be found by walking the caverns, as vanilla rooms always can.
     */
    @SuppressWarnings("deprecation") // isSolid, as in the vanilla monster room
    static boolean carveRoom(WorldGenLevel level, RandomSource random, BlockPos origin) {
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-3, -1, -3), origin.offset(3, 4, 3))) {
            BlockState state = level.getBlockState(pos);
            if (!state.isSolid() || state.is(BlockTags.FEATURES_CANNOT_REPLACE)) return false;
        }
        Direction door = null;
        int length = 0;
        for (int i = 0, first = random.nextInt(4); i < 4 && door == null; i++) {
            Direction dir = Direction.from2DDataValue(first + i);
            for (int d = 4; d <= 3 + TUNNEL; d++) {
                BlockPos pos = origin.relative(dir, d);
                if (level.isEmptyBlock(pos) && level.isEmptyBlock(pos.above())) {
                    door = dir;
                    length = d;
                    break;
                }
                if (level.getBlockState(pos).is(BlockTags.FEATURES_CANNOT_REPLACE) || level.getBlockState(pos.above()).is(BlockTags.FEATURES_CANNOT_REPLACE))
                    break;
            }
        }
        if (door == null) return false;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-3, -1, -3), origin.offset(3, 4, 3))) {
            boolean shell = Math.abs(pos.getX() - origin.getX()) == 3 || Math.abs(pos.getZ() - origin.getZ()) == 3
                    || pos.getY() == origin.getY() - 1 || pos.getY() == origin.getY() + 4;
            BlockState wall = random.nextInt(4) == 0 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState();
            level.setBlock(pos, shell ? wall : Blocks.AIR.defaultBlockState(), 2);
        }
        for (int d = 3; d < length; d++) {
            level.setBlock(origin.relative(door, d), Blocks.CAVE_AIR.defaultBlockState(), 2);
            level.setBlock(origin.relative(door, d).above(), Blocks.CAVE_AIR.defaultBlockState(), 2);
        }
        level.setBlock(origin, Blocks.SPAWNER.defaultBlockState(), 2);
        level.setBlock(origin.offset(2, 0, 1), Blocks.CHEST.defaultBlockState(), 2);
        if (random.nextBoolean()) level.setBlock(origin.offset(-2, 0, -1), Blocks.CHEST.defaultBlockState(), 2);
        return true;
    }

    @Override
    public MapCodec<UnderworldDungeonFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
        if (!new MonsterRoomFeature().place(level, generator, random, origin)
                && (random.nextInt(FALLBACK_ODDS) != 0 || !carveRoom(level, random, origin))) return false;
        furnish(level, random, origin);
        return true;
    }

    /**
     * The vanilla room keeps the blocks features cannot replace and sets its spawner last, at the origin: on such a block the
     * spawner is skipped and vanilla logs "Failed to fetch mob spawner entity" (MonsterRoomFeatureMixin). Seen on the bedrock
     * plates of the Underworld (every Y of the logged errors is a plate row); a chest of an overlapping room does the same.
     */
    public static boolean spawnerFits(WorldGenLevel level, BlockPos origin) {
        BlockState state = level.getBlockState(origin);
        return state.is(Blocks.SPAWNER) || !state.is(BlockTags.FEATURES_CANNOT_REPLACE);
    }

    /** The room's chests get the Underworld loot table and its spawner a Longdead or a Longdead Guardian. */
    static void furnish(WorldGenLevel level, RandomSource random, BlockPos origin) {
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-3, 0, -3), origin.offset(3, 0, 3))) {
            if (level.getBlockState(pos).is(Blocks.CHEST)) RandomizableContainer.setBlockEntityLootTable(level, random, pos, UnderworldContent.DUNGEON_CHEST);
        }
        if (level.getBlockEntity(origin) instanceof SpawnerBlockEntity spawner)
            spawner.setEntityId(random.nextInt(4) == 0 ? MobContent.LONGDEAD_GUARDIAN.get() : MobContent.LONGDEAD.get(), random);
    }
}
