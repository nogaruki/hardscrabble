package dev.mite.entity.mob;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * EF-MOB-17 Nightwing: immaterial vampire bat (tag {@code hardscrabble:immaterial}, fire immune type): only silver, enchanted weapons,
 * magic and the sun hurt it; sunlight kills it, as the Shadow. Scale 2 [INFÉRÉ], XP 10.
 */
public class Nightwing extends VampireBat {
    public Nightwing(EntityType<? extends Nightwing> type, Level level) {
        super(type, level, 10);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (tickCount % 20 == 0 && Shadow.inSunlight(this)) kill(level);
    }
}
