package dev.mite.entity.mob;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings.SpawnerData;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;

/**
 * {@code hardscrabble:spawn_weight}: sets the weight of an existing spawn entry where a biome has it, keeping its group size (EF-IA-06,
 * spider 80 instead of 100). NeoForge's stock modifiers remove spawns after adding them, so they cannot swap an entry.
 */
public record SpawnWeightModifier(HolderSet<Biome> biomes, EntityType<?> entityType, int weight) implements BiomeModifier {
    public static final MapCodec<SpawnWeightModifier> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Biome.LIST_CODEC.fieldOf("biomes").forGetter(SpawnWeightModifier::biomes),
            BuiltInRegistries.ENTITY_TYPE.byNameCodec().fieldOf("entity_type").forGetter(SpawnWeightModifier::entityType),
            Codec.intRange(1, 1000).fieldOf("weight").forGetter(SpawnWeightModifier::weight)).apply(i, SpawnWeightModifier::new));

    @Override
    public void modify(RegistryAccess registries, Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        if (phase != Phase.MODIFY || !biomes.contains(biome)) return;
        for (MobCategory category : MobCategory.values()) {
            var spawner = builder.getMobSpawnSettings().getSpawner(category);
            if (spawner == null) continue;
            List<Weighted<SpawnerData>> entries = spawner.getList().stream().filter(w -> w.value().type() == entityType).toList();
            spawner.removeIf(w -> w.value().type() == entityType);
            entries.forEach(w -> spawner.add(w.value(), weight));
        }
    }

    @Override
    public MapCodec<? extends BiomeModifier> codec() {
        return MobContent.SPAWN_WEIGHT.get();
    }
}
