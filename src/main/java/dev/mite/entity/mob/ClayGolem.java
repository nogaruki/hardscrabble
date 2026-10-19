package dev.mite.entity.mob;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** EF-MOB-15 Clay Golem: the clay Earth Elemental, 6 damage, hurt only by a shovel (a tool efficient against clay). */
public class ClayGolem extends EarthElemental {
    /** Spawns with clay within this many blocks [INFÉRÉ: the spec gives only the weight 50]. */
    public static final int CLAY_RADIUS = 2;

    public ClayGolem(EntityType<? extends ClayGolem> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return attributes(30, 6, 0.2).add(Attributes.FOLLOW_RANGE, 20).add(Attributes.KNOCKBACK_RESISTANCE, 1);
    }

    @Override
    protected BlockState material() {
        return Blocks.CLAY.defaultBlockState();
    }

    public static boolean checkSpawnRules(EntityType<? extends Mob> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        return Monster.checkMonsterSpawnRules(type, level, reason, pos, random) && BlockPos.betweenClosedStream(pos.offset(-CLAY_RADIUS, -CLAY_RADIUS, -CLAY_RADIUS),
                pos.offset(CLAY_RADIUS, CLAY_RADIUS, CLAY_RADIUS)).anyMatch(p -> level.getBlockState(p).is(Blocks.CLAY));
    }
}
