package com.dragoncare.item;

import com.dragoncare.DragonCare;
import com.dragoncare.block.ModBlocks;
import com.dragoncare.config.AddonConfig;
import net.minecraft.item.FoodComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;

public class ModItems {

    public static final DeferredRegister<net.minecraft.item.Item> ITEMS =
            DeferredRegister.create(net.minecraftforge.registries.ForgeRegistries.ITEMS, DragonCare.MOD_ID);

    public static final RegistryObject<SyringeItem> DRAGON_BLOOD_SYRINGE = ITEMS.register(
            "dragon_blood_syringe",
            () -> new SyringeItem(new Item.Settings().maxCount(1).maxDamage(240))
    );

    public static final RegistryObject<ExceptionalDragonMealItem> EXCEPTIONAL_DRAGON_MEAL = ITEMS.register(
            "exceptional_dragon_meal",
            () -> new ExceptionalDragonMealItem(new Item.Settings())
    );

    public static final RegistryObject<ScaleShearsItem> SCALE_SHEARS = ITEMS.register(
            "scale_shears",
            () -> new ScaleShearsItem(new Item.Settings().maxCount(1).maxDamage(520))
    );

    public static final RegistryObject<DragonPainkillerItem> DRAGON_PAINKILLER = ITEMS.register(
            "dragon_painkiller",
            () -> new DragonPainkillerItem(new Item.Settings().maxCount(1).maxDamage(getPainkillerDurability()))
    );

    public static final RegistryObject<DragonBrushItem> DRAGON_BRUSH = ITEMS.register(
            "dragon_brush",
            () -> new DragonBrushItem(new Item.Settings().maxCount(1).maxDamage(120))
    );

    public static final RegistryObject<Item> DRACOMANIA_ICON = ITEMS.register(
            "dracomania_icon",
            () -> new Item(new Item.Settings())
    );

    /**
     * Драконий фрукт.
     * Еда для игрока: 6 голода + 3 сытости (saturationModifier = 3 / (2 * 6) = 0.25).
     * Корм для дракона: см. {@link DragonFruitItem#useOnEntity}.
     */
    public static final RegistryObject<DragonFruitItem> DRAGON_FRUIT = ITEMS.register(
            "dragon_fruit",
            () -> new DragonFruitItem(ModBlocks.DRAGON_FRUIT.get(), new Item.Settings()
                    .food(new FoodComponent.Builder()
                            .hunger(6)
                            .saturationModifier(0.25f)
                            .alwaysEdible()
                            .build()))
    );

    /** Семечки драконьего фрукта. Сажаются на вспаханную землю как тыква/арбуз. */
    public static final RegistryObject<DragonFruitSeedsItem> DRAGON_FRUIT_SEEDS = ITEMS.register(
            "dragon_fruit_seeds",
            () -> new DragonFruitSeedsItem(ModBlocks.DRAGON_FRUIT_STEM.get(), new Item.Settings())
    );

    /** Драконий телефон — переключаемый трекер прирученных драконов. */
    public static final RegistryObject<DragonPhoneItem> DRAGON_PHONE = ITEMS.register(
            "dragon_phone",
            () -> new DragonPhoneItem(new Item.Settings().maxCount(1))
    );

    public static final RegistryObject<AshTabletsItem> ASH_TABLETS = ITEMS.register(
            "ash_tablets",
            () -> new AshTabletsItem(new Item.Settings().maxCount(1))
    );

    /** Сгоревший лист — некрафтящийся свиток с заранее сгенерированным текстом. */
    public static final RegistryObject<AshSensorItem> ASH_SENSOR = ITEMS.register(
            "ash_sensor",
            () -> new AshSensorItem(new Item.Settings().maxCount(1))
    );

    public static final RegistryObject<BurntSheetItem> BURNT_SHEET = ITEMS.register(
            "burnt_sheet",
            () -> new BurntSheetItem(new Item.Settings().maxCount(16))
    );

    public static final DeferredRegister<ItemGroup> CREATIVE_TABS =
            DeferredRegister.create(RegistryKeys.ITEM_GROUP, DragonCare.MOD_ID);

    public static final RegistryObject<ItemGroup> ADDON_TAB = CREATIVE_TABS.register(
            "addon_tab",
            () -> ItemGroup.builder()
                    .displayName(Text.translatable("itemGroup.dragoncare"))
                    .icon(() -> new ItemStack(DRAGON_FRUIT.get()))
                    .entries((displayContext, entries) -> {
                        entries.add(DRAGON_BLOOD_SYRINGE.get());
                        entries.add(EXCEPTIONAL_DRAGON_MEAL.get());
                        entries.add(SCALE_SHEARS.get());
                        entries.add(DRAGON_PAINKILLER.get());
                        entries.add(DRAGON_FRUIT.get());
                        entries.add(DRAGON_FRUIT_SEEDS.get());
                        entries.add(DRAGON_PHONE.get());
                        entries.add(ASH_TABLETS.get());
                        entries.add(ASH_SENSOR.get());
                        entries.add(BURNT_SHEET.get());
                        entries.add(DRAGON_BRUSH.get());
                    })
                    .build()
    );

    private static int getPainkillerDurability() {
        try {
            return AddonConfig.PAINKILLER_DURABILITY.get();
        } catch (IllegalStateException e) {
            return AddonConfig.PAINKILLER_DURABILITY.getDefault();
        }
    }
}


