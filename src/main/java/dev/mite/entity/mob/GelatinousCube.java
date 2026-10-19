package dev.mite.entity.mob;

import net.minecraft.sounds.SoundEvent;
import dev.mite.config.MiteConfig;
import dev.mite.core.Material;
import dev.mite.crafting.CraftQuality;
import dev.mite.crafting.CraftingContent;
import dev.mite.equipment.EquipmentContent;
import java.util.List;
import net.minecraft.tags.BlockItemTags;
import net.minecraft.tags.TagKey;
import dev.mite.farming.FarmingContent;
import dev.mite.harvest.ToolStats;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import dev.mite.core.Formulas;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.cubemob.Slime;
import java.util.Set;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.EventHooks;

/**
 * EF-MOB-16 gelatinous cubes (Jelly, Blob, Ooze, Pudding), as in the R134 changelog: slimes whose health, damage and XP follow
 * their size. Jelly and Blob secrete pepsin that digests dropped flesh and protein (meat, cheese, leather); the Blob paralyses
 * on touch. Ooze and Pudding are acidic: they dissolve dropped wood and metal (gold, mithril and adamantium resist) and the small
 * blocks they touch (buttons, levers, doors, ladders, wooden fences, rails...). The Ooze is hurt only by lava and enchanted
 * weapons, the Pudding also by fire; the Ooze does not jump, crawls silently and climbs walls. Item dissolving follows the
 * config {@code mobs.gelatinous_dissolve_items}. Tiny cubes drop their coloured gelatinous ball. The Jelly hits harder than a slime
 * and the Blob harder still (l.481-482); without a target cubes jump less often (l.463, R136). The acid also eats the wood or
 * metal item the victim holds (l.483); the cube-wide rules of {@link CubeRules} (prey, arrows, weapons, wool, torches) apply too.
 */
public class GelatinousCube extends Slime {
    public static final int DISSOLVE_INTERVAL = 10;
    private static final Set<Item> LEATHER_ARMOR = Set.of(Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS);

    /** [INFÉRÉ] Wear of an item eaten by the acid or the pepsin, in attacks of its tool type (EF-OUT-06 scale x100). */
    public static final int CORROSION_ATTACKS = 2;

    /** Damage and XP per size point, x1 for the slime [INFÉRÉ: l.481-482 only order them, slime < Jelly < Blob; l.472 adjusts the XP]. */
    public enum Kind {
        JELLY(1.5F, 2), BLOB(2, 3), OOZE(1, 3), PUDDING(1, 4);

        public final float damage;
        public final int xp;

        Kind(float damage, int xp) {
            this.damage = damage;
            this.xp = xp;
        }

        public boolean acidic() {
            return this == OOZE || this == PUDDING;
        }

        public boolean hurtBy(DamageSource source) {
            if (!acidic() || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.is(DamageTypes.LAVA)) return true;
            if (source.isDirect() && source.getWeaponItem() != null && source.getWeaponItem().isEnchanted()) return true;
            return this == PUDDING && source.is(DamageTypeTags.IS_FIRE);
        }
    }

    public final Kind kind;

    public GelatinousCube(EntityType<? extends GelatinousCube> type, Level level, Kind kind) {
        super(type, level);
        this.kind = kind;
    }

    /** R134: the small blocks an acidic cube dissolves. */
    public static boolean dissolves(BlockState state) {
        return state.is(BlockTags.BUTTONS) || state.is(BlockTags.DOORS) || state.is(BlockTags.TRAPDOORS) || state.is(BlockTags.WOODEN_FENCES)
                || state.is(BlockTags.FENCE_GATES) || state.is(BlockTags.RAILS) || state.is(BlockTags.WOODEN_PRESSURE_PLATES)
                || state.is(Blocks.LADDER) || state.is(Blocks.LEVER);
    }

    /** R134: pepsin digests meat, cheese and leather; acid dissolves wood and metal except gold, mithril and adamantium. */
    public boolean dissolves(ItemStack stack) {
        return dissolves(kind.acidic(), stack);
    }

    /** The pepsin rule (slime, Jelly, Blob) or the acid rule (Ooze, Pudding). */
    public static boolean dissolves(boolean acidic, ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (!acidic) return stack.is(ItemTags.MEAT) || stack.is(FarmingContent.CHEESE.get()) || stack.is(Tags.Items.LEATHERS)
                || LEATHER_ARMOR.contains(stack.getItem());
        Material m = CraftQuality.materialOf(stack);
        if (m == null) m = EquipmentContent.metalOf(stack.getItem());
        if (m == null) m = stack.getData(CraftingContent.MATERIAL);
        if (m != null) return m == Material.WOOD || m.isMetal() && m != Material.GOLD && m != Material.MITHRIL && m != Material.ADAMANTIUM;
        return WOOD_AND_METAL.stream().anyMatch(stack::is) || WOOD_AND_METAL_ITEMS.contains(stack.getItem());
    }

    /**
     * l.483: unlisted items made of wood or metal, the dropped forms of the blocks of l.484 first. Metal doors and trapdoors are
     * iron or copper here (the gold, mithril and adamantium doors have a material above). ponytail: tags and a short list, not
     * every recipe; add an item here when playtest finds one the acid should eat.
     */
    private static final List<TagKey<Item>> WOOD_AND_METAL = List.of(ItemTags.LOGS, ItemTags.PLANKS, ItemTags.WOODEN_SLABS, ItemTags.WOODEN_STAIRS,
            ItemTags.WOODEN_FENCES, ItemTags.FENCE_GATES, ItemTags.WOODEN_BUTTONS, ItemTags.WOODEN_PRESSURE_PLATES, ItemTags.SIGNS, ItemTags.BOATS,
            ItemTags.CHEST_BOATS, ItemTags.RAILS, BlockItemTags.DOORS.item(), BlockItemTags.TRAPDOORS.item(), Tags.Items.RODS_WOODEN);
    private static final Set<Item> WOOD_AND_METAL_ITEMS = Set.of(Items.LADDER, Items.LEVER, Items.BOWL, Items.CHEST, Items.BARREL, Items.CRAFTING_TABLE,
            Items.IRON_BARS, Items.MINECART);

    @Override
    public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
        return super.isInvulnerableTo(level, source) || !kind.hurtBy(source);
    }

    /** l.481-482 / l.472: the slime's size-based damage and XP, scaled by the kind. The super constructor calls it with kind still null. */
    @Override
    public void setSize(int size, boolean updateHealth) {
        super.setSize(size, updateHealth);
        if (kind == null) return;
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(getSize() * kind.damage);
        xpReward = getSize() * kind.xp;
    }

    /** R134: the Blob paralyses on touch (Slowness VI 50 ticks, as the Ghoul); acid eats the held wood or metal item (l.483). */
    @Override
    protected void dealDamage(LivingEntity target) {
        float health = target.getHealth();
        super.dealDamage(target);
        if (target.getHealth() >= health) return;
        if (kind == Kind.BLOB) target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, Ghoul.PARALYSIS_TICKS, Ghoul.PARALYSIS_AMPLIFIER), this);
        if (kind.acidic() && dissolves(target.getMainHandItem())) corrode(target.getMainHandItem(), target, EquipmentSlot.MAINHAND);
    }

    /** Wears a damageable item by {@link #CORROSION_ATTACKS} attacks, or eats one item of any other stack [INFÉRÉ]. */
    public static void corrode(ItemStack stack, LivingEntity holder, EquipmentSlot slot) {
        if (stack.isDamageableItem())
            stack.hurtAndBreak(CORROSION_ATTACKS * ToolStats.of(stack).map(t -> Formulas.attackWear(t.type())).orElse(100), holder, slot);
        else stack.shrink(1);
        holder.playSound(SoundEvents.GENERIC_EXTINGUISH_FIRE, 0.5F, 1.5F);
    }

    /** R134: the Ooze crawls without jumping (a move every tick, no vertical push), silently, and climbs walls. l.463: idle cubes jump half as often. */
    @Override
    protected int getJumpDelay() {
        if (kind == Kind.OOZE) return 0;
        return getTarget() == null ? 2 * super.getJumpDelay() : super.getJumpDelay();
    }

    @Override
    public void jumpFromGround() {
        if (kind != Kind.OOZE) super.jumpFromGround();
    }

    @Override
    protected boolean doPlayJumpSound() {
        return kind != Kind.OOZE && super.doPlayJumpSound();
    }

    @Override
    public boolean onClimbable() {
        return kind == Kind.OOZE ? horizontalCollision : super.onClimbable();
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (tickCount % DISSOLVE_INTERVAL == 0) dissolve(level);
    }

    /** Dissolves every touched dropped item it can digest and, when acidic, one touched block. */
    public void dissolve(ServerLevel level) {
        if (MiteConfig.GELATINOUS_DISSOLVE_ITEMS.get())
            level.getEntitiesOfClass(ItemEntity.class, getBoundingBox(), item -> dissolves(item.getItem())).forEach(item -> {
                item.discard();
                playSound(SoundEvents.GENERIC_EXTINGUISH_FIRE, 0.5F, 1.5F);
            });
        if (!kind.acidic() || !EventHooks.canEntityGrief(level, this)) return;
        BlockPos.betweenClosedStream(getBoundingBox().inflate(0.2, 0, 0.2)).filter(p -> dissolves(level.getBlockState(p))).findFirst()
                .ifPresent(p -> level.destroyBlock(p.immutable(), false, this));
    }

    @Override
    protected SoundEvent getSquishSound() {
        return MobSounds.get(getType(), MobSounds.Kind.AMBIENT, super.getSquishSound());
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return MobSounds.get(getType(), MobSounds.Kind.HURT, super.getHurtSound(source));
    }

    @Override
    protected SoundEvent getDeathSound() {
        return MobSounds.get(getType(), MobSounds.Kind.DEATH, super.getDeathSound());
    }
}
