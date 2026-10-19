package dev.mite.entity.mob;

import dev.mite.combat.CombatContent;
import dev.mite.core.Material;
import dev.mite.player.PlayerLevel;
import dev.mite.player.ProgressionService;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * EF-MOB-02 Wight: immaterial (entity tag {@code hardscrabble:immaterial}, see {@link MobContent#hurtsImmaterial}); each hit has a 40 %
 * chance to drain XP. Silver armor protects from the drain (EF-CBT, silver armor).
 */
public class Wight extends MiteZombie {
    public static final float DRAIN_CHANCE = 0.4f;

    public Wight(EntityType<? extends Wight> type, Level level) {
        super(type, level, 2);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return attributes(20, 5, 0.25); // health 20 [NON VÉRIFIÉ] (Q-23)
    }

    /** EF-MOB-02: XP drained from a player of the given level. */
    public static int drainAmount(int level) {
        return Math.max((level + 1) * 10, 20);
    }

    /** Drains the player's XP unless a silver armor piece is worn. Returns whether XP was drained. */
    public static boolean drainXp(Player player) {
        if (wearsSilver(player)) return false;
        ProgressionService.setXp(player, ProgressionService.xp(player) - drainAmount(PlayerLevel.get(player)));
        return true;
    }

    /** Silver armor (any piece) protects from the Wight drain and the Shadow effects (EF-CBT, silver armor). */
    public static boolean wearsSilver(LivingEntity entity) {
        return List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET).stream()
                .map(entity::getItemBySlot).anyMatch(stack -> CombatContent.ARMORS.stream()
                        .anyMatch(a -> a.material() == Material.SILVER && a.item().get() == stack.getItem()));
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (hit && target instanceof Player player && random.nextFloat() < DRAIN_CHANCE) drainXp(player);
        return hit;
    }
}
