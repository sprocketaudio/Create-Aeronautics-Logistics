package net.sprocketgames.create_aeronautics_automated_logistics.vehicle;

import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;

public final class VehicleControllerResolver {
    public static final ResourceLocation LINKED_AUTOPILOT_SEAT = ResourceLocation.fromNamespaceAndPath(
            CreateAeronauticsAutomatedLogistics.MOD_ID,
            "autopilot_seat"
    );

    private VehicleControllerResolver() {
    }

    public static Optional<VehicleController> resolve(ServerLevel level, VehicleControllerRef controllerRef) {
        if (controllerRef.controllerType().equals(RiddenEntityVehicleController.TYPE)) {
            return controllerRef.vehicleId()
                    .map(level::getEntity)
                    .map(RiddenEntityVehicleController::new)
                    .map(VehicleController.class::cast);
        }

        if (controllerRef.controllerType().equals(SableSubLevelVehicleController.TYPE)
                || controllerRef.controllerType().equals(LINKED_AUTOPILOT_SEAT)) {
            try {
                return SableSubLevelVehicleController.resolve(level, controllerRef).map(VehicleController.class::cast);
            } catch (RuntimeException exception) {
                CreateAeronauticsAutomatedLogistics.LOGGER.warn(
                        "Failed to resolve Sable vehicle controller {}",
                        controllerRef,
                        exception
                );
                return Optional.empty();
            }
        }

        return Optional.empty();
    }

    public static Optional<VehicleController> resolve(ServerPlayer player, VehicleControllerRef controllerRef) {
        Optional<VehicleController> resolved = resolve(player.serverLevel(), controllerRef);
        if (resolved.isPresent()) {
            return resolved;
        }

        if (controllerRef.controllerType().equals(SableSubLevelVehicleController.TYPE)
                || controllerRef.controllerType().equals(LINKED_AUTOPILOT_SEAT)) {
            try {
                return SableSubLevelVehicleController.resolveTrackedPlayer(player).map(VehicleController.class::cast);
            } catch (RuntimeException exception) {
                CreateAeronauticsAutomatedLogistics.LOGGER.warn(
                        "Failed to resolve Sable tracked-player controller for {}",
                        player.getGameProfile().getName(),
                        exception
                );
            }
        }

        return Optional.empty();
    }
}
