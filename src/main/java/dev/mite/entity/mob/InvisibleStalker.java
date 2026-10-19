package dev.mite.entity.mob;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * EF-MOB-04 Invisible Stalker: always invisible, yet never fully hidden from players, so the renderer draws it as a faint
 * translucent ghost ("quasi invisible"; duration and visual cues [NON VÉRIFIÉ], ET-J-06).
 */
public class InvisibleStalker extends MiteZombie {
    public InvisibleStalker(EntityType<? extends InvisibleStalker> type, Level level) {
        super(type, level, 2);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return attributes(20, 4, 0.23); // health 20 [NON VÉRIFIÉ] (Q-23)
    }

    @Override
    public boolean isInvisible() {
        return true;
    }

    @Override
    public boolean isInvisibleTo(Player player) {
        return false;
    }
}
