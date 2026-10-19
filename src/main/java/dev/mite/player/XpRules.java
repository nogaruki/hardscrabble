package dev.mite.player;

import com.mojang.serialization.Codec;
import dev.mite.MiteMod;
import dev.mite.config.MiteConfig;
import dev.mite.entity.ai.VanillaMobRules;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;

/**
 * XP sources (EF-JOU-07, ET-B-05): passive animals give no XP (hostile animals such as the Hellhound, the Dire Wolf or the
 * hoglin are in the MONSTER category and keep theirs); vanilla mobs get their {@code hardscrabble:xp_multiplier}. Dead players drop only
 * what the death rule says (EF-JOU-05).
 */
@EventBusSubscriber(modid = MiteMod.MODID)
public final class XpRules {
    private XpRules() {}

    /** ET-B-05: XP multiplier per entity type (witch x4, zombified piglin x3, ghast x2, blaze x4: EF-IA-04, EF-IA-08). */
    public static final DataMapType<EntityType<?>, Float> XP_MULTIPLIER =
            DataMapType.builder(MiteMod.id("xp_multiplier"), Registries.ENTITY_TYPE, Codec.FLOAT).build();

    /** Vanilla mob multipliers written to {@link #XP_MULTIPLIER} by AiData. MITE mobs scale their own reward (EF-MOB table). */
    public static final Map<EntityType<?>, Float> VANILLA_MULTIPLIERS = Map.of(EntityTypes.WITCH, (float) VanillaMobRules.WITCH_XP_MULTIPLIER,
            EntityTypes.ZOMBIFIED_PIGLIN, 3f, EntityTypes.GHAST, 2f, EntityTypes.BLAZE, 4f);

    @SubscribeEvent
    static void onRegisterDataMaps(RegisterDataMapTypesEvent event) {
        event.register(XP_MULTIPLIER);
    }

    public static boolean isPassive(LivingEntity entity) {
        return entity instanceof Animal && entity.getType().getCategory() != MobCategory.MONSTER;
    }

    /** Audit 2 moderne m7 (RE-04): the sculk family drops no XP (1.19), since a catalyst grows new sculk from any mob death. */
    public static final Set<Block> NO_XP_BLOCKS = Set.of(Blocks.SCULK, Blocks.SCULK_VEIN, Blocks.SCULK_CATALYST, Blocks.SCULK_SENSOR,
            Blocks.CALIBRATED_SCULK_SENSOR, Blocks.SCULK_SHRIEKER);

    @SubscribeEvent
    static void onBlockDrops(BlockDropsEvent event) {
        if (NO_XP_BLOCKS.contains(event.getState().getBlock())) event.setDroppedExperience(0);
    }

    @SubscribeEvent
    static void onXpDrop(LivingExperienceDropEvent event) {
        LivingEntity entity = event.getEntity();
        if (isPassive(entity)) event.setDroppedExperience(0);
        // Vanilla already drops nothing with keepInventory or for spectators.
        else if (entity instanceof Player p && event.getDroppedExperience() > 0) {
            event.setDroppedExperience(0);
            if (p.level() instanceof ServerLevel level) dropOwnedOrbs(level, p, MiteConfig.DEATH_PENALTY.get().droppedXp(ProgressionService.xp(p)));
        } else {
            Float multiplier = BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(entity.getType()).getData(XP_MULTIPLIER);
            if (multiplier != null) event.setDroppedExperience(Math.round(event.getDroppedExperience() * multiplier));
        }
    }

    /** B-2 (EF-JOU-05 WIKI_THIRD): the dead player's orbs carry their owner, so only that player can pick them up. */
    static void dropOwnedOrbs(ServerLevel level, Player owner, int amount) {
        while (amount > 0) {
            int value = ExperienceOrb.getExperienceValue(amount);
            amount -= value;
            ExperienceOrb orb = new ExperienceOrb(level, owner.getX(), owner.getY(), owner.getZ(), value);
            orb.setData(ProgressionService.XP_OWNER, owner.getUUID());
            level.addFreshEntity(orb);
        }
    }

    /** Owner of a dead player's orb, null for an ordinary orb. */
    public static @Nullable UUID owner(ExperienceOrb orb) {
        return orb.hasData(ProgressionService.XP_OWNER) ? orb.getData(ProgressionService.XP_OWNER) : null;
    }

    /** True when {@code orb} belongs to a dead player other than {@code player}. */
    public static boolean ownedByOther(ExperienceOrb orb, Player player) {
        UUID owner = owner(orb);
        return owner != null && !owner.equals(player.getUUID());
    }

    @SubscribeEvent
    static void onPickup(PlayerXpEvent.PickupXp event) {
        if (ownedByOther(event.getOrb(), event.getEntity())) event.setCanceled(true);
    }
}
