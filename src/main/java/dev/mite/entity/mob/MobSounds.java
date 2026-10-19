package dev.mite.entity.mob;

import dev.mite.MiteMod;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jspecify.annotations.Nullable;

/**
 * Voices of the MITE mobs: one sound event per mob and kind ({@code hardscrabble:entity.<mob>.<kind>}), each a blend of vanilla
 * sound events replayed at another pitch and volume (sounds.json references, no audio file: MobData.Sounds). The mob
 * classes ask {@link #get} and fall back to their vanilla sound.
 */
public final class MobSounds {
    private MobSounds() {}

    public enum Kind { AMBIENT, HURT, DEATH }

    /** A vanilla sound event replayed at this pitch and volume. */
    public record Layer(String event, float pitch, float volume) {}

    /** Subtitle verbs of a family (EN, FR); FR hurt takes the noun's gender. */
    public record Voice(String ambientEn, String ambientFr) {}

    /** Sound of one mob and kind. */
    public record Def(String mob, Kind kind, List<Layer> layers) {}

    static final Voice GROAN = new Voice("groans", "gémit"), WHISPER = new Voice("whispers", "murmure"),
            RUMBLE = new Voice("rumbles", "gronde"), RATTLE = new Voice("rattles", "cliquette"), GROWL = new Voice("growls", "grogne"),
            HISS = new Voice("hisses", "siffle"), CRACKLE = new Voice("crackles", "crépite"), SQUISH = new Voice("squishes", "gargouille"),
            SCREECH = new Voice("screeches", "piaille");

    /** mob -> (family voice, feminine noun in French) for the subtitles. */
    public static final Map<String, Voice> VOICES = new HashMap<>();
    public static final java.util.Set<String> FEMININE = java.util.Set.of("ghoul", "shadow", "black_widow", "demon_spider", "phase_spider",
            "wood_spider", "jelly", "ooze", "vampire_bat", "giant_vampire_bat", "nightwing", "copperspine");
    public static final List<Def> DEFS = new java.util.ArrayList<>();

    private static Layer l(String event, double pitch, double volume) {
        return new Layer(event, (float) pitch, (float) volume);
    }

    private static void mob(String mob, Voice voice, List<Layer> ambient, List<Layer> hurt, List<Layer> death) {
        VOICES.put(mob, voice);
        DEFS.add(new Def(mob, Kind.AMBIENT, ambient));
        DEFS.add(new Def(mob, Kind.HURT, hurt));
        DEFS.add(new Def(mob, Kind.DEATH, death));
    }

    static {
        mob("ghoul", GROAN, List.of(l("entity.zombie.ambient", 0.7, 1), l("entity.husk.ambient", 0.8, 0.8)),
                List.of(l("entity.zombie.hurt", 0.75, 1)), List.of(l("entity.zombie.death", 0.7, 1)));
        mob("wight", WHISPER, List.of(l("entity.vex.ambient", 0.6, 0.9), l("entity.zombie.ambient", 1.35, 0.5)),
                List.of(l("entity.vex.hurt", 0.7, 1)), List.of(l("entity.vex.death", 0.6, 1)));
        mob("revenant", GROAN, List.of(l("entity.zombie_villager.ambient", 0.6, 1), l("entity.zombie.ambient", 0.6, 1)),
                List.of(l("entity.zombie.hurt", 0.6, 1)), List.of(l("entity.zombie.death", 0.55, 1)));
        mob("shadow", WHISPER, List.of(l("entity.phantom.ambient", 0.5, 0.6)),
                List.of(l("entity.phantom.hurt", 0.6, 0.9)), List.of(l("entity.phantom.death", 0.5, 1)));
        mob("invisible_stalker", WHISPER, List.of(l("entity.enderman.ambient", 0.5, 0.4)),
                List.of(l("entity.enderman.hurt", 0.6, 0.8)), List.of(l("entity.enderman.death", 0.6, 0.9)));
        mob("earth_elemental", RUMBLE, List.of(l("block.stone.break", 0.5, 0.8), l("entity.ravager.ambient", 1.4, 0.4)),
                List.of(l("block.stone.break", 0.7, 1), l("entity.iron_golem.hurt", 0.8, 0.8)),
                List.of(l("entity.iron_golem.death", 0.7, 1), l("block.stone.break", 0.4, 1)));
        mob("clay_golem", RUMBLE, List.of(l("block.mud.step", 0.6, 1)),
                List.of(l("block.mud.break", 0.8, 1), l("entity.iron_golem.hurt", 1.2, 0.5)), List.of(l("block.mud.break", 0.6, 1)));
        mob("longdead", RATTLE, List.of(l("entity.skeleton.ambient", 0.8, 1)),
                List.of(l("entity.skeleton.hurt", 0.8, 1)), List.of(l("entity.skeleton.death", 0.75, 1)));
        mob("longdead_guardian", RATTLE, List.of(l("entity.skeleton.ambient", 0.7, 1), l("entity.stray.ambient", 0.8, 0.7)),
                List.of(l("entity.skeleton.hurt", 0.7, 1)), List.of(l("entity.skeleton.death", 0.65, 1)));
        mob("bone_lord", RATTLE, List.of(l("entity.wither_skeleton.ambient", 0.9, 1)),
                List.of(l("entity.wither_skeleton.hurt", 0.9, 1)), List.of(l("entity.wither_skeleton.death", 0.8, 1)));
        mob("ancient_bone_lord", RATTLE, List.of(l("entity.wither_skeleton.ambient", 0.7, 1)),
                List.of(l("entity.wither_skeleton.hurt", 0.7, 1)), List.of(l("entity.wither_skeleton.death", 0.6, 1)));
        mob("dire_wolf", GROWL, List.of(l("entity.wolf_big.growl", 0.75, 1), l("entity.wolf_big.ambient", 0.7, 1)),
                List.of(l("entity.wolf_big.hurt", 0.8, 1)), List.of(l("entity.wolf_big.death", 0.75, 1)));
        mob("hellhound", GROWL, List.of(l("entity.wolf_angry.growl", 0.7, 1), l("entity.blaze.burn", 0.8, 0.3)),
                List.of(l("entity.wolf_angry.hurt", 0.7, 1)), List.of(l("entity.wolf_angry.death", 0.7, 1), l("entity.blaze.death", 1.4, 0.4)));
        mob("phase_spider", HISS, List.of(l("entity.spider.ambient", 1.3, 1)),
                List.of(l("entity.spider.hurt", 1.3, 1)), List.of(l("entity.spider.death", 1.3, 1), l("entity.enderman.teleport", 1.6, 0.5)));
        mob("black_widow", HISS, List.of(l("entity.spider.ambient", 0.85, 1)),
                List.of(l("entity.spider.hurt", 0.85, 1)), List.of(l("entity.spider.death", 0.8, 1)));
        mob("demon_spider", HISS, List.of(l("entity.spider.ambient", 0.6, 1), l("entity.blaze.burn", 0.9, 0.3)),
                List.of(l("entity.spider.hurt", 0.6, 1)), List.of(l("entity.spider.death", 0.55, 1), l("entity.blaze.death", 1.2, 0.4)));
        mob("wood_spider", HISS, List.of(l("entity.spider.ambient", 1.5, 0.8)),
                List.of(l("entity.spider.hurt", 1.5, 0.8)), List.of(l("entity.spider.death", 1.5, 0.8)));
        mob("infernal_creeper", CRACKLE, List.of(l("entity.blaze.burn", 0.8, 0.4)),
                List.of(l("entity.creeper.hurt", 0.7, 1), l("entity.blaze.hurt", 1.3, 0.4)), List.of(l("entity.creeper.death", 0.7, 1)));
        mob("fire_elemental", CRACKLE, List.of(l("entity.blaze.ambient", 0.7, 1), l("block.fire.ambient", 0.8, 0.6)),
                List.of(l("entity.blaze.hurt", 0.7, 1)), List.of(l("entity.blaze.death", 0.6, 1)));
        mob("jelly", SQUISH, List.of(l("entity.slime.squish", 1.2, 1)),
                List.of(l("entity.slime.hurt", 1.2, 1)), List.of(l("entity.slime.death", 1.2, 1)));
        mob("blob", SQUISH, List.of(l("entity.slime.squish", 0.9, 1)),
                List.of(l("entity.slime.hurt", 0.9, 1)), List.of(l("entity.slime.death", 0.9, 1)));
        mob("ooze", SQUISH, List.of(l("entity.slime.squish", 0.7, 1), l("block.mud.step", 0.8, 0.6)),
                List.of(l("entity.slime.hurt", 0.7, 1)), List.of(l("entity.slime.death", 0.7, 1)));
        mob("pudding", SQUISH, List.of(l("entity.magma_cube.squish", 0.6, 1)),
                List.of(l("entity.magma_cube.hurt", 0.6, 1)), List.of(l("entity.magma_cube.death", 0.6, 1)));
        mob("vampire_bat", SCREECH, List.of(l("entity.bat.ambient", 0.8, 1)),
                List.of(l("entity.bat.hurt", 0.8, 1)), List.of(l("entity.bat.death", 0.8, 1)));
        mob("giant_vampire_bat", SCREECH, List.of(l("entity.bat.ambient", 0.55, 1)),
                List.of(l("entity.bat.hurt", 0.55, 1)), List.of(l("entity.bat.death", 0.55, 1)));
        mob("nightwing", SCREECH, List.of(l("entity.phantom.ambient", 1.6, 0.5), l("entity.bat.ambient", 0.7, 0.8)),
                List.of(l("entity.phantom.hurt", 1.5, 0.8)), List.of(l("entity.phantom.death", 1.4, 0.9)));
        mob("copperspine", HISS, List.of(l("entity.silverfish.ambient", 0.8, 1), l("block.copper.step", 1.2, 0.5)),
                List.of(l("entity.silverfish.hurt", 0.8, 1)), List.of(l("entity.silverfish.death", 0.8, 1)));
        mob("netherspawn", HISS, List.of(l("entity.silverfish.ambient", 0.7, 1), l("entity.blaze.burn", 1.0, 0.3)),
                List.of(l("entity.silverfish.hurt", 0.7, 1)), List.of(l("entity.silverfish.death", 0.7, 1)));
        mob("hoary_silverfish", HISS, List.of(l("entity.silverfish.ambient", 1.2, 1), l("block.powder_snow.step", 1.0, 0.5)),
                List.of(l("entity.silverfish.hurt", 1.2, 1)), List.of(l("entity.silverfish.death", 1.2, 1)));
    }

    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, MiteMod.MODID);
    private static final Map<String, DeferredHolder<SoundEvent, SoundEvent>> HOLDERS = new HashMap<>();

    public static String id(String mob, Kind kind) {
        return "entity." + mob + "." + kind.name().toLowerCase(java.util.Locale.ROOT);
    }

    static {
        for (Def d : DEFS) {
            String id = id(d.mob(), d.kind());
            HOLDERS.put(id, SOUNDS.register(id, () -> SoundEvent.createVariableRangeEvent(MiteMod.id(id))));
        }
    }

    public static DeferredHolder<SoundEvent, SoundEvent> holder(String mob, Kind kind) {
        return HOLDERS.get(id(mob, kind));
    }

    /** The MITE sound of this mob type, or {@code fallback} (the vanilla sound) when it has none. */
    public static @Nullable SoundEvent get(EntityType<?> type, Kind kind, @Nullable SoundEvent fallback) {
        var h = HOLDERS.get(id(BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath(), kind));
        return h == null ? fallback : h.get();
    }

    public static void register(IEventBus modBus) {
        SOUNDS.register(modBus);
    }
}
