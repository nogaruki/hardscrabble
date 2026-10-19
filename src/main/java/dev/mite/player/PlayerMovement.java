package dev.mite.player;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;

/** Player movement rules read by mixins (no event exposes them): climbing speed (changelog l.47) and fence hopping (l.602). */
public final class PlayerMovement {
    private PlayerMovement() {}

    private static final Identifier SPRINTING = Identifier.withDefaultNamespace("sprinting");
    /** Vanilla sprint bonus (ADD_MULTIPLIED_TOTAL 0.3): sprinting does not climb faster. */
    private static final double SPRINT_FACTOR = 1.3;

    /**
     * Changelog l.47 (R1xx): speed modifiers (Swiftness, Slowness, paralysis, speed enchantment) scale a player's climbing
     * speed. The factor is the movement speed over its base, sprint excluded; 1 for other mobs.
     */
    public static double climbFactor(LivingEntity entity) {
        if (!(entity instanceof Player)) return 1;
        AttributeInstance speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null || speed.getBaseValue() <= 0) return 1;
        double value = speed.getValue() / (speed.getModifier(SPRINTING) != null ? SPRINT_FACTOR : 1);
        return value / speed.getBaseValue();
    }

    /** Changelog l.602 (R1xx): players hop over fences, which collide as high as they look (1 block) for them only. */
    public static boolean hopsFence(Block block, CollisionContext context) {
        return block instanceof FenceBlock && context instanceof EntityCollisionContext e && e.getEntity() instanceof Player;
    }
}
