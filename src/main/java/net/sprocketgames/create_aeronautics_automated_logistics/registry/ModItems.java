package net.sprocketgames.create_aeronautics_automated_logistics.registry;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.item.AirshipScheduleItem;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CreateAeronauticsAutomatedLogistics.MOD_ID);

    public static final DeferredItem<BlockItem> AIRSHIP_STATION = ITEMS.registerSimpleBlockItem(
            "airship_station",
            ModBlocks.AIRSHIP_STATION,
            new Item.Properties()
    );

    public static final DeferredItem<BlockItem> SHIP_TRANSPONDER = ITEMS.registerSimpleBlockItem(
            "ship_transponder",
            ModBlocks.SHIP_TRANSPONDER,
            new Item.Properties()
    );

    public static final DeferredItem<Item> AIRSHIP_SCHEDULE = ITEMS.register(
            "airship_schedule",
            () -> new AirshipScheduleItem(new Item.Properties().stacksTo(1))
    );

    private ModItems() {
    }
}
