package dev.mite.combat;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import org.jspecify.annotations.Nullable;

/**
 * Arbitrage n°7 (flight after the Dragon) and n°18 (mace): items of {@link CombatContent#POST_DRAGON} drop (GLM {@code post_dragon}),
 * craft and serve only for a player who killed the Dragon. The dried ghast is crafted (8 ghast tears + soul sand) and its happy ghast
 * flies, so its recipe is refused and the happy ghast cannot be ridden before {@code end/kill_dragon} (audit 2 X7).
 */
public final class PostDragon {
    private PostDragon() {}

    public static final Identifier KILL_DRAGON = Identifier.withDefaultNamespace("end/kill_dragon");

    public static boolean killedDragon(ServerPlayer player) {
        var dragon = player.level().getServer().getAdvancements().get(KILL_DRAGON);
        return dragon != null && player.getAdvancements().getOrStartProgress(dragon).isDone();
    }

    /** Crafting refusal key (called by {@code TimedCrafting}), or null when the result is not reserved or the player killed the Dragon. */
    public static @Nullable String craftRefusal(Player player, ItemStack result) {
        return result.is(CombatContent.POST_DRAGON) && player instanceof ServerPlayer sp && !killedDragon(sp) ? "hardscrabble.crafting.post_dragon" : null;
    }

    /** X7: a player who has not killed the Dragon cannot mount a happy ghast, however it was obtained (crafter, trade, another player). */
    private static void onMount(EntityMountEvent event) {
        if (event.isMounting() && event.getEntityBeingMounted() instanceof HappyGhast && event.getEntityMounting() instanceof ServerPlayer player
                && !killedDragon(player)) {
            event.setCanceled(true);
            player.sendOverlayMessage(Component.translatable("hardscrabble.combat.happy_ghast_post_dragon"));
        }
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(PostDragon::onMount);
    }
}
