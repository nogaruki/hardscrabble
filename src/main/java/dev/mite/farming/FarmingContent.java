package dev.mite.farming;

import com.mojang.serialization.Codec;
import dev.mite.MiteMod;
import dev.mite.metabolism.Nutrition;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import dev.mite.metabolism.MetabolismContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.NormalCraftingRecipe;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.MushroomBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Farming domain registrations (I01, I02, I03, I05, I06, I07, I13, I16): crops, fruits, manure, MITE dishes. */
public final class FarmingContent {
    private FarmingContent() {}

    static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MiteMod.MODID);
    static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MiteMod.MODID);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, MiteMod.MODID);

    private static BlockBehaviour.Properties crop(BlockBehaviour.Properties p) {
        return p.mapColor(MapColor.PLANT).noCollision().instabreak().sound(SoundType.CROP).pushReaction(PushReaction.POPPED);
    }

    // ---- EF-AGR-01 onion and dead crops ----
    public static final DeferredBlock<OnionBlock> ONIONS = BLOCKS.registerBlock("onions", OnionBlock::new, p -> crop(p).randomTicks());
    public static final DeferredBlock<DeadCropBlock> DEAD_WHEAT = BLOCKS.registerBlock("dead_wheat", DeadCropBlock::new, p -> crop(p).noLootTable());
    public static final DeferredBlock<DeadCropBlock> DEAD_CARROTS = BLOCKS.registerBlock("dead_carrots", DeadCropBlock::new, p -> crop(p).noLootTable());
    public static final DeferredBlock<DeadCropBlock> DEAD_POTATOES = BLOCKS.registerBlock("dead_potatoes", DeadCropBlock::new, p -> crop(p).noLootTable());
    public static final DeferredBlock<DeadCropBlock> DEAD_ONIONS = BLOCKS.registerBlock("dead_onions", DeadCropBlock::new, p -> crop(p).noLootTable());

    /** Every new food with its EF-NUT-08 entry; MetabolismData writes them to {@code hardscrabble:nutrition}. */
    public static final Map<DeferredItem<? extends Item>, Nutrition> FOODS = new LinkedHashMap<>();
    /** EF-AGR-10 bowl dishes (fineDining achievement). */
    public static final List<DeferredItem<Item>> DISHES = new ArrayList<>();

    private static final boolean P = true, V = true, o = false;

    public static final DeferredItem<BlockItem> ONION = food(ITEMS.registerItem("onion", p -> new BlockItem(ONIONS.get(), p),
            p -> p.food(food(1))), 1, 1, 0, o, o);
    // EF-AGR-03 fruits that fall from leaves.
    public static final DeferredItem<Item> ORANGE = simpleFood("orange", 2, 1, 1000, o, V);
    public static final DeferredItem<Item> BANANA = simpleFood("banana", 2, 1, 1000, o, V);
    /** EF-AGR-07: +50 % crop growth on manured farmland, burns 100 ticks. Exposed for the livestock domain. */
    public static final DeferredItem<ManureItem> MANURE = ITEMS.registerItem("manure", ManureItem::new,
            p -> p.cookingFuel(ContextIntProviders.COOKING_TIME_DRY_PLANTS));
    // EF-AGR-10 ingredients and dishes (analysis/02 §8.3), values EF-NUT-08.
    public static final DeferredItem<Item> FLOUR = ITEMS.registerSimpleItem("flour");
    public static final DeferredItem<Item> DOUGH = simpleFood("dough", 6, 2, 0, o, o);
    public static final DeferredItem<Item> CHEESE = simpleFood("cheese", 3, 3, 0, P, o);
    public static final DeferredItem<Item> CHOCOLATE = simpleFood("chocolate", 3, 3, 1000, o, o);
    public static final DeferredItem<Item> BEEF_STEW = dish("beef_stew", 16, 16, 0, P, V);
    public static final DeferredItem<Item> CHICKEN_SOUP = dish("chicken_soup", 10, 10, 0, P, V);
    public static final DeferredItem<Item> VEGETABLE_SOUP = dish("vegetable_soup", 6, 6, 0, o, V);
    public static final DeferredItem<Item> CREAM_OF_MUSHROOM_SOUP = dish("cream_of_mushroom_soup", 3, 5, 0, P, o);
    public static final DeferredItem<Item> CREAM_OF_VEGETABLE_SOUP = dish("cream_of_vegetable_soup", 7, 7, 0, P, V);
    public static final DeferredItem<Item> PUMPKIN_SOUP = dish("pumpkin_soup", 1, 2, 0, o, V);
    public static final DeferredItem<Item> MASHED_POTATOES = dish("mashed_potatoes", 12, 8, 0, P, o);
    public static final DeferredItem<Item> ICE_CREAM = dish("ice_cream", 5, 4, 1000, P, o);
    public static final DeferredItem<Item> SORBET = dish("sorbet", 4, 2, 2000, o, V);
    public static final DeferredItem<Item> PORRIDGE = dish("porridge", 4, 2, 2000, o, V);
    public static final DeferredItem<Item> CEREAL = dish("cereal", 4, 2, 1000, P, o);
    public static final DeferredItem<Item> SALAD = dish("salad", 1, 1, 0, o, V);

    /** EF-AGR-06 / docs/02 §6: crop positions sick with blight, per chunk. */
    static final Supplier<AttachmentType<LongOpenHashSet>> BLIGHT = posSet("blight");
    /** EF-AGR-07: manured farmland positions, per chunk. */
    static final Supplier<AttachmentType<LongOpenHashSet>> MANURED = posSet("manured");
    /** EF-AGR-12 (I08): farmland worn out by a harvest without manure, per chunk. */
    static final Supplier<AttachmentType<LongOpenHashSet>> DEPLETED = posSet("depleted");
    /** EF-AGR-12 (I17): game time the chunk was last saved, set while it loads and removed once fast-forwarded. */
    static final Supplier<AttachmentType<Long>> LAST_SAVED = ATTACHMENTS.register("last_saved",
            () -> AttachmentType.builder(() -> 0L).serialize(dev.mite.attach.Migrations.versioned("last_saved", Codec.LONG.fieldOf("time"))).build());

    private static Supplier<AttachmentType<LongOpenHashSet>> posSet(String name) {
        Codec<LongOpenHashSet> codec = Codec.LONG.listOf().xmap(LongOpenHashSet::new, ArrayList::new);
        return ATTACHMENTS.register(name, () -> AttachmentType.builder(() -> new LongOpenHashSet())
                .serialize(dev.mite.attach.Migrations.versioned("pos_set", codec.fieldOf("positions")), set -> !set.isEmpty()).build());
    }

    /** The food component only drives eating; the MITE values come from {@code hardscrabble:nutrition}. */
    private static FoodProperties food(int nutrition) {
        return new FoodProperties.Builder().nutrition(nutrition).saturationModifier(0).build();
    }

    private static <I extends Item> DeferredItem<I> food(DeferredItem<I> item, int satiety, int nutrition, int sugar, boolean protein, boolean phyto) {
        FOODS.put(item, new Nutrition(satiety, nutrition, sugar, protein, phyto));
        return item;
    }

    private static DeferredItem<Item> simpleFood(String name, int satiety, int nutrition, int sugar, boolean protein, boolean phyto) {
        return food(ITEMS.registerSimpleItem(name, p -> p.food(food(nutrition))), satiety, nutrition, sugar, protein, phyto);
    }

    private static DeferredItem<Item> dish(String name, int satiety, int nutrition, int sugar, boolean protein, boolean phyto) {
        DeferredItem<Item> dish = food(ITEMS.registerSimpleItem(name, p -> p.stacksTo(1).food(food(nutrition)).usingConvertsTo(Items.BOWL)),
                satiety, nutrition, sugar, protein, phyto);
        DISHES.add(dish);
        return dish;
    }

    /** Dead variant left by blight (EF-AGR-06); crops without their own variant wither to dead wheat. */
    static Block deadVariant(Block crop) {
        if (crop == Blocks.CARROTS) return DEAD_CARROTS.get();
        if (crop == Blocks.POTATOES) return DEAD_POTATOES.get();
        if (crop == ONIONS.get()) return DEAD_ONIONS.get();
        return DEAD_WHEAT.get();
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        ATTACHMENTS.register(modBus);
        SERIALIZERS.register(modBus);
        FarmingRules.registerEvents(NeoForge.EVENT_BUS);
        SoilRules.register(NeoForge.EVENT_BUS);
    }

    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, MiteMod.MODID);

    static {
        SERIALIZERS.register("bowl_shapeless", () -> BowlRecipe.SERIALIZER);
    }

    /**
     * EF-NUT-12: a shapeless recipe that hands back the bowl of each water or milk bowl it uses (dough, cheese, cake), as the
     * milk bucket cheese recipes hand back their buckets. Bowl dishes stay plain recipes: their bowl moves into the dish.
     */
    public static final class BowlRecipe extends NormalCraftingRecipe {
        public static final RecipeSerializer<BowlRecipe> SERIALIZER = new RecipeSerializer<>(
                ShapelessRecipe.MAP_CODEC.xmap(BowlRecipe::new, r -> r.recipe), ShapelessRecipe.STREAM_CODEC.map(BowlRecipe::new, r -> r.recipe));
        private final ShapelessRecipe recipe;

        public BowlRecipe(ShapelessRecipe recipe) {
            super(new Recipe.CommonInfo(recipe.showNotification()), new CraftingRecipe.CraftingBookInfo(recipe.category(), recipe.group()));
            this.recipe = recipe;
        }

        @Override
        public boolean matches(CraftingInput input, Level level) {
            return recipe.matches(input, level);
        }

        @Override
        public ItemStack assemble(CraftingInput input) {
            return recipe.assemble(input);
        }

        @Override
        public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
            NonNullList<ItemStack> left = CraftingRecipe.defaultCraftingReminder(input);
            for (int i = 0; i < input.size(); i++) {
                ItemStack in = input.getItem(i);
                if (in.is(MetabolismContent.WATER_BOWL.get()) || in.is(MetabolismContent.MILK_BOWL.get())) left.set(i, new ItemStack(Items.BOWL));
            }
            return left;
        }

        @Override
        protected PlacementInfo createPlacementInfo() {
            return recipe.placementInfo();
        }

        @Override
        public List<RecipeDisplay> display() {
            return recipe.display();
        }

        @Override
        public RecipeSerializer<BowlRecipe> getSerializer() {
            return SERIALIZER;
        }
    }

    public static final class OnionBlock extends CropBlock {
        private static final VoxelShape[] SHAPES = Block.boxes(7, age -> Block.column(16.0, 0.0, 2 + age));

        public OnionBlock(BlockBehaviour.Properties properties) {
            super(properties);
        }

        @Override
        protected ItemLike getBaseSeedId() {
            return ONION.get();
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return SHAPES[getAge(state)];
        }
    }

    /** EF-AGR-01: a withered crop on farmland; drops nothing. */
    public static final class DeadCropBlock extends VegetationBlock {
        private static final VoxelShape SHAPE = Block.column(16.0, 0.0, 6.0);

        public DeadCropBlock(BlockBehaviour.Properties properties) {
            super(properties);
        }

        @Override
        protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
            return state.is(Blocks.FARMLAND);
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return SHAPE;
        }
    }

    /** EF-AGR-07: used on farmland or on the crop above it; supersizes a mushroom (R128). */
    public static final class ManureItem extends Item {
        public ManureItem(Item.Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResult useOn(UseOnContext context) {
            Level level = context.getLevel();
            if (level.getBlockState(context.getClickedPos()).getBlock() instanceof MushroomBlock) {
                if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
                if (!FarmingRules.manureMushroom(server, context.getClickedPos())) return InteractionResult.PASS;
            } else if (!FarmingRules.manure(level, context.getClickedPos())) return InteractionResult.PASS;
            else if (level.isClientSide()) return InteractionResult.SUCCESS; // the manured set is not synced: the server decides and consumes
            else if (context.getPlayer() instanceof net.minecraft.server.level.ServerPlayer p)
                dev.mite.progression.MiteTriggers.EVENT.get().trigger(p, "soil_enrichment"); // changelog l.749 (P-1)
            context.getItemInHand().consume(1, context.getPlayer());
            return InteractionResult.SUCCESS;
        }
    }
}
