package net.sprocketgames.create_aeronautics_automated_logistics;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.sprocketgames.create_aeronautics_automated_logistics.client.screen.AirshipScheduleScreen;
import net.sprocketgames.create_aeronautics_automated_logistics.client.screen.AirshipStationScreen;
import net.sprocketgames.create_aeronautics_automated_logistics.client.screen.ShipTransponderScreen;
import net.sprocketgames.create_aeronautics_automated_logistics.registry.ModMenus;

@Mod(value = CreateAeronauticsAutomatedLogistics.MOD_ID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = CreateAeronauticsAutomatedLogistics.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class CreateAeronauticsAutomatedLogisticsClient {
    public CreateAeronauticsAutomatedLogisticsClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.AIRSHIP_STATION.get(), AirshipStationScreen::new);
        event.register(ModMenus.AIRSHIP_SCHEDULE.get(), AirshipScheduleScreen::new);
        event.register(ModMenus.SHIP_TRANSPONDER.get(), ShipTransponderScreen::new);
    }
}
