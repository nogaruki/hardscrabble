package dev.mite.player;

import net.minecraft.world.entity.player.Player;

/** Single source of the MITE player level used by caps and bonuses: the level of the MITE total XP (B02, B03). */
public final class PlayerLevel {
    private PlayerLevel() {}

    public static int get(Player player) {
        return ProgressionService.level(player);
    }
}
