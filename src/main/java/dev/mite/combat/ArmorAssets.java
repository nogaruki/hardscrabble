package dev.mite.combat;

import dev.mite.MiteMod;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import net.minecraft.client.data.models.EquipmentAssetProvider;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

/**
 * Equipment assets ({@code assets/hardscrabble/equipment/*.json}, ENF-QUA-01) of the MITE armors: humanoid layers, plus the horse
 * layer for the silver horse armor. The texture shares the asset name.
 */
public final class ArmorAssets extends EquipmentAssetProvider {
    public ArmorAssets(PackOutput output) {
        super(output);
    }

    @Override
    protected void registerModels(BiConsumer<ResourceKey<EquipmentAsset>, EquipmentClientInfo> output) {
        // Item components are not bound during datagen: the asset names follow CombatContent.armor().
        Set<String> assets = new TreeSet<>();
        CombatContent.ARMORS.stream().filter(a -> CombatContent.NEW_ARMOR.contains(a.item()))
                .forEach(a -> assets.add(CombatContent.prefix(a.material()) + (a.chain() ? "_chainmail" : "")));
        for (String name : assets) {
            Identifier id = MiteMod.id(name);
            EquipmentClientInfo.Layer layer = new EquipmentClientInfo.Layer(id);
            // No humanoid_baby layer (addHumanoidLayers adds one): there is no baby texture.
            EquipmentClientInfo.Builder b = EquipmentClientInfo.builder().addLayers(EquipmentClientInfo.LayerType.HUMANOID, layer)
                    .addLayers(EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS, layer);
            // TreasureContent.SILVER_HORSE_ARMOR wears the silver asset.
            if (name.equals("silver")) b.addLayers(EquipmentClientInfo.LayerType.HORSE_BODY, layer);
            output.accept(ResourceKey.create(EquipmentAssets.ROOT_ID, id), b.build());
        }
    }

    @Override
    public String getName() {
        return "Equipment assets - hardscrabble";
    }
}
