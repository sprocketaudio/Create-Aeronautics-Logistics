package net.sprocketgames.create_aeronautics_automated_logistics.menu;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.AirshipStationBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.IdentityNames;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.ShipTransponderRegistry;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.ShipTransponderSnapshot;
import net.sprocketgames.create_aeronautics_automated_logistics.item.AirshipScheduleItem;
import net.sprocketgames.create_aeronautics_automated_logistics.registry.ModItems;
import net.sprocketgames.create_aeronautics_automated_logistics.route.AirshipSchedule;
import net.sprocketgames.create_aeronautics_automated_logistics.route.FailureReason;
import net.sprocketgames.create_aeronautics_automated_logistics.route.Route;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteSegment;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteSegmentResolver;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteStatus;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteStop;
import net.sprocketgames.create_aeronautics_automated_logistics.route.WaitCondition;
import net.sprocketgames.create_aeronautics_automated_logistics.route.WaitConditionType;
import net.sprocketgames.create_aeronautics_automated_logistics.service.AutomatedLogisticsServices;
import net.sprocketgames.create_aeronautics_automated_logistics.service.PlaybackOperationResult;
import net.sprocketgames.create_aeronautics_automated_logistics.service.RecordingFailure;
import net.sprocketgames.create_aeronautics_automated_logistics.service.RecordingSession;
import net.sprocketgames.create_aeronautics_automated_logistics.service.RouteOperationResult;
import net.sprocketgames.create_aeronautics_automated_logistics.registry.ModMenus;
import net.sprocketgames.create_aeronautics_automated_logistics.vehicle.VehicleController;
import net.sprocketgames.create_aeronautics_automated_logistics.vehicle.VehicleControllerResolver;

public class AirshipStationMenu extends AbstractContainerMenu {
    public static final int ACTION_SELECT_SHIP = 0;
    public static final int ACTION_START_SEGMENT_RECORDING = 1;
    public static final int ACTION_FINISH_SEGMENT_RECORDING = 2;
    public static final int ACTION_RUN_SCHEDULE = 3;
    public static final int ACTION_STOP_SCHEDULE = 4;
    public static final int ACTION_MARK_STOP = 5;
    public static final int ACTION_CYCLE_LAST_STOP_WAIT = 6;
    public static final int ACTION_DECREASE_LAST_STOP_WAIT = 7;
    public static final int ACTION_INCREASE_LAST_STOP_WAIT = 8;
    private static final int WAIT_ADJUST_TICKS = 20 * 5;

    private final BlockPos stationPos;

    public AirshipStationMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buffer) {
        this(containerId, playerInventory, buffer.readBlockPos());
    }

    public AirshipStationMenu(int containerId, Inventory playerInventory, BlockPos stationPos) {
        super(ModMenus.AIRSHIP_STATION.get(), containerId);
        this.stationPos = stationPos;
    }

    public BlockPos stationPos() {
        return stationPos;
    }

    public String stationName(Player player) {
        if (!(player.level().getBlockEntity(stationPos) instanceof AirshipStationBlockEntity station)) {
            return "";
        }
        return station.stationName();
    }

    public Component stationIdText(Player player) {
        if (!(player.level().getBlockEntity(stationPos) instanceof AirshipStationBlockEntity station)) {
            return Component.literal("-");
        }
        return Component.literal(IdentityNames.shortId(station.stationId()));
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        if (!(serverPlayer.serverLevel().getBlockEntity(stationPos) instanceof AirshipStationBlockEntity station)) {
            return false;
        }

        return switch (id) {
            case ACTION_SELECT_SHIP -> selectNextShip(serverPlayer, station);
            case ACTION_START_SEGMENT_RECORDING -> startSegmentRecording(serverPlayer, station);
            case ACTION_FINISH_SEGMENT_RECORDING -> finishSegmentRecording(serverPlayer, station);
            case ACTION_RUN_SCHEDULE -> runSchedule(serverPlayer, station);
            case ACTION_STOP_SCHEDULE -> stopSchedule(serverPlayer, station);
            case ACTION_MARK_STOP -> markStop(serverPlayer, station);
            case ACTION_CYCLE_LAST_STOP_WAIT -> cycleLastStopWait(serverPlayer, station);
            case ACTION_DECREASE_LAST_STOP_WAIT -> adjustLastStopWait(serverPlayer, station, -WAIT_ADJUST_TICKS);
            case ACTION_INCREASE_LAST_STOP_WAIT -> adjustLastStopWait(serverPlayer, station, WAIT_ADJUST_TICKS);
            default -> false;
        };
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        if (!(player.level().getBlockEntity(stationPos) instanceof AirshipStationBlockEntity)) {
            return false;
        }
        return player.distanceToSqr(
                stationPos.getX() + 0.5D,
                stationPos.getY() + 0.5D,
                stationPos.getZ() + 0.5D
        ) <= 64.0D;
    }

    public Component statusText(Player player) {
        if (!(player.level().getBlockEntity(stationPos) instanceof AirshipStationBlockEntity station)) {
            return Component.translatable("gui.create_aeronautics_automated_logistics.airship_station.status_missing_station");
        }
        RouteStatus status = station.isRecording() ? RouteStatus.RECORDING : station.status();
        return Component.translatable("gui.create_aeronautics_automated_logistics.airship_station.status." + status.name().toLowerCase(Locale.ROOT));
    }

    public Component failureText(Player player) {
        if (!(player.level().getBlockEntity(stationPos) instanceof AirshipStationBlockEntity station)) {
            return Component.empty();
        }
        if (station.isRecording() || station.status() == RouteStatus.RECORDING) {
            return Component.empty();
        }
        return station.failureReason()
                .map(reason -> Component.translatable(
                        "gui.create_aeronautics_automated_logistics.airship_station.failure",
                        Component.translatable("gui.create_aeronautics_automated_logistics.airship_station.failure_reason." + reason.name().toLowerCase(Locale.ROOT))
                ))
                .orElse(Component.empty());
    }

    public boolean isRecording(Player player) {
        if (!(player.level().getBlockEntity(stationPos) instanceof AirshipStationBlockEntity station)) {
            return false;
        }
        return station.isRecording() || station.status() == RouteStatus.RECORDING;
    }

    public List<Vec3> previewPoints(Player player) {
        if (!(player.level().getBlockEntity(stationPos) instanceof AirshipStationBlockEntity station)) {
            return Collections.emptyList();
        }
        return station.recordedRoute()
                .map(route -> route.points().stream().map(point -> point.position()).toList())
                .orElse(Collections.emptyList());
    }

    public Component stopSummary(Player player) {
        if (!(player.level().getBlockEntity(stationPos) instanceof AirshipStationBlockEntity station)) {
            return Component.empty();
        }
        if (station.isRecording()) {
            return Component.translatable(
                    "gui.create_aeronautics_automated_logistics.airship_station.stops_recording",
                    station.recordingStops().size()
            );
        }
        return station.recordedRoute()
                .map(route -> Component.translatable(
                        "gui.create_aeronautics_automated_logistics.airship_station.stops_recorded",
                        route.stops().size()
                ))
                .orElse(Component.empty());
    }

    public Component selectedShipText(Player player) {
        if (!(player.level().getBlockEntity(stationPos) instanceof AirshipStationBlockEntity station)) {
            return Component.translatable("gui.create_aeronautics_automated_logistics.airship_station.selected_ship.none");
        }
        return station.selectedTransponderId()
                .map(id -> Component.literal(station.selectedShipName() + " [" + IdentityNames.shortId(id) + "]"))
                .orElseGet(() -> Component.translatable("gui.create_aeronautics_automated_logistics.airship_station.selected_ship.none"));
    }

    public Component segmentSummary(Player player) {
        if (!(player.level().getBlockEntity(stationPos) instanceof AirshipStationBlockEntity station)) {
            return Component.empty();
        }
        int outgoing = RouteSegmentResolver.validOutgoingSegments(
                station,
                player.level().dimension(),
                station.selectedTransponderId()
        ).size();
        return Component.translatable(
                "gui.create_aeronautics_automated_logistics.airship_station.segments",
                outgoing,
                station.routeSegments().size()
        );
    }

    public List<Component> segmentLines(Player player) {
        if (!(player.level().getBlockEntity(stationPos) instanceof AirshipStationBlockEntity station)) {
            return List.of();
        }
        return RouteSegmentResolver.validLocalSegments(
                        station,
                        player.level().dimension(),
                        station.selectedTransponderId()
                ).stream()
                .limit(4)
                .<Component>map(segment -> Component.translatable(
                        segment.startStationId().equals(station.stationId())
                                ? "gui.create_aeronautics_automated_logistics.airship_station.segment_line.outgoing"
                                : "gui.create_aeronautics_automated_logistics.airship_station.segment_line.incoming",
                        segment.startStationName(),
                        segment.endStationName(),
                        segment.shipName(),
                        segment.points().size()
                ))
                .toList();
    }

    private boolean selectNextShip(ServerPlayer player, AirshipStationBlockEntity station) {
        List<ShipTransponderSnapshot> ships = ShipTransponderRegistry.knownShips(player.serverLevel().dimension());
        if (ships.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.create_aeronautics_automated_logistics.ship_selection.none_found"));
            return false;
        }

        int currentIndex = -1;
        Optional<UUID> current = station.selectedTransponderId();
        if (current.isPresent()) {
            for (int i = 0; i < ships.size(); i++) {
                if (ships.get(i).transponderId().equals(current.get())) {
                    currentIndex = i;
                    break;
                }
            }
        }

        ShipTransponderSnapshot selected = ships.get((currentIndex + 1) % ships.size());
        station.selectShip(selected);
        player.sendSystemMessage(Component.translatable(
                "message.create_aeronautics_automated_logistics.ship_selection.selected",
                selected.shipName(),
                IdentityNames.shortId(selected.transponderId())
        ));
        return true;
    }

    private boolean startSegmentRecording(ServerPlayer player, AirshipStationBlockEntity station) {
        if (station.isRecording()) {
            player.sendSystemMessage(Component.translatable("message.create_aeronautics_automated_logistics.recording.busy"));
            return false;
        }
        if (station.isPlaybackRunning()) {
            player.sendSystemMessage(Component.translatable("message.create_aeronautics_automated_logistics.playback.running"));
            return false;
        }

        Optional<UUID> selectedTransponderId = station.selectedTransponderId();
        if (selectedTransponderId.isEmpty()) {
            station.setFailure(FailureReason.MISSING_AUTOPILOT_CONTROLLER);
            player.sendSystemMessage(Component.translatable("message.create_aeronautics_automated_logistics.recording.no_selected_ship"));
            return false;
        }

        Optional<ShipTransponderSnapshot> selectedShip = ShipTransponderRegistry.snapshot(selectedTransponderId.get())
                .filter(ship -> ship.dimension().equals(player.serverLevel().dimension()));
        if (selectedShip.isEmpty() || selectedShip.get().controllerRef().isEmpty()) {
            station.setFailure(FailureReason.VEHICLE_DESTROYED_OR_MISSING);
            player.sendSystemMessage(Component.translatable("message.create_aeronautics_automated_logistics.recording.selected_ship_unavailable"));
            return false;
        }

        Optional<VehicleController> controller = selectedShip.get().controllerRef()
                .flatMap(controllerRef -> VehicleControllerResolver.resolve(player.serverLevel(), controllerRef));
        if (controller.isEmpty()) {
            station.setFailure(FailureReason.VEHICLE_DESTROYED_OR_MISSING);
            player.sendSystemMessage(Component.translatable("message.create_aeronautics_automated_logistics.recording.selected_ship_unavailable"));
            return false;
        }

        RouteOperationResult<RecordingSession> result = AutomatedLogisticsServices.RECORDING.startRecording(
                player,
                stationPos,
                controller.get()
        );
        result.value().ifPresentOrElse(
                session -> player.sendSystemMessage(Component.translatable(
                        "message.create_aeronautics_automated_logistics.segment_recording.started",
                        station.stationName(),
                        selectedShip.get().shipName()
                )),
                () -> result.failure().ifPresent(failure -> {
                    station.setFailure(failure.failureReason());
                    player.sendSystemMessage(recordingFailureMessage(failure));
                })
        );
        return result.value().isPresent();
    }

    private boolean finishSegmentRecording(ServerPlayer player, AirshipStationBlockEntity station) {
        RouteOperationResult<RouteSegment> result = AutomatedLogisticsServices.RECORDING.finishSegmentRecording(player, stationPos);
        result.value().ifPresentOrElse(
                segment -> player.sendSystemMessage(Component.translatable(
                        "message.create_aeronautics_automated_logistics.segment_recording.saved",
                        segment.startStationName(),
                        segment.endStationName(),
                        segment.shipName(),
                        segment.points().size()
                )),
                () -> result.failure().ifPresent(failure -> {
                    station.setFailure(failure.failureReason());
                    player.sendSystemMessage(recordingFailureMessage(failure));
                })
        );
        return result.value().isPresent();
    }

    private boolean runSchedule(ServerPlayer player, AirshipStationBlockEntity station) {
        if (station.isRecording()) {
            player.sendSystemMessage(Component.translatable("message.create_aeronautics_automated_logistics.recording.busy"));
            return false;
        }
        if (station.isPlaybackRunning()) {
            player.sendSystemMessage(Component.translatable("message.create_aeronautics_automated_logistics.playback.running"));
            return false;
        }

        Optional<ItemStack> scheduleStack = heldSchedule(player);
        if (scheduleStack.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.create_aeronautics_automated_logistics.airship_schedule.hold_schedule"));
            return false;
        }

        AirshipSchedule schedule = AirshipScheduleItem.readSchedule(scheduleStack.get());
        PlaybackOperationResult<?> result = AutomatedLogisticsServices.SCHEDULES.start(player, station, stationPos, schedule);
        result.value().ifPresentOrElse(
                routeId -> player.sendSystemMessage(Component.translatable("message.create_aeronautics_automated_logistics.airship_schedule.started")),
                () -> result.failure().ifPresent(failure -> {
                    station.setFailure(failure.failureReason());
                    player.sendSystemMessage(Component.translatable(
                            "message.create_aeronautics_automated_logistics.playback.failed",
                            Component.translatable("failure.create_aeronautics_automated_logistics.playback." + failure.name().toLowerCase(Locale.ROOT))
                    ));
                })
        );
        return result.value().isPresent();
    }

    private boolean stopSchedule(ServerPlayer player, AirshipStationBlockEntity station) {
        if (!AutomatedLogisticsServices.SCHEDULES.isRunning(stationPos) && !station.isPlaybackRunning()) {
            return false;
        }
        AutomatedLogisticsServices.SCHEDULES.stop(player.serverLevel(), stationPos);
        player.sendSystemMessage(Component.translatable("message.create_aeronautics_automated_logistics.airship_schedule.stopped"));
        return true;
    }

    private Optional<ItemStack> heldSchedule(Player player) {
        if (player.getMainHandItem().is(ModItems.AIRSHIP_SCHEDULE.get())) {
            return Optional.of(player.getMainHandItem());
        }
        if (player.getOffhandItem().is(ModItems.AIRSHIP_SCHEDULE.get())) {
            return Optional.of(player.getOffhandItem());
        }
        return Optional.empty();
    }

    private boolean markStop(ServerPlayer player, AirshipStationBlockEntity station) {
        if (!station.isRecording()) {
            player.sendSystemMessage(Component.translatable("message.create_aeronautics_automated_logistics.stop_mark.not_recording"));
            return false;
        }
        return station.activeRecording()
                .filter(session -> session.playerId().equals(player.getUUID()))
                .map(session -> {
                    RouteOperationResult<RouteStop> result = AutomatedLogisticsServices.RECORDING.markStop(
                            player,
                            session.routeId(),
                            WaitCondition.timed(WaitCondition.DEFAULT_TIMED_WAIT_TICKS)
                    );
                    result.value().ifPresentOrElse(
                            stop -> player.sendSystemMessage(Component.translatable(
                                    "message.create_aeronautics_automated_logistics.stop_mark.added",
                                    stop.name(),
                                    stop.pointIndex(),
                                    waitText(stop.waitCondition())
                            )),
                            () -> result.failure().ifPresent(failure -> player.sendSystemMessage(recordingFailureMessage(failure)))
                    );
                    return result.value().isPresent();
                })
                .orElse(false);
    }

    private boolean cycleLastStopWait(ServerPlayer player, AirshipStationBlockEntity station) {
        Optional<RouteStop> lastStop = lastEditableStop(station);
        if (lastStop.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.create_aeronautics_automated_logistics.stop_mark.no_stop"));
            return false;
        }

        WaitCondition next = lastStop.get().waitCondition().type() == WaitConditionType.NONE
                ? WaitCondition.timed(WaitCondition.DEFAULT_TIMED_WAIT_TICKS)
                : WaitCondition.none();
        return updateLastStopWait(player, station, next);
    }

    private boolean adjustLastStopWait(ServerPlayer player, AirshipStationBlockEntity station, int deltaTicks) {
        Optional<RouteStop> lastStop = lastEditableStop(station);
        if (lastStop.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.create_aeronautics_automated_logistics.stop_mark.no_stop"));
            return false;
        }

        int currentTicks = lastStop.get().waitCondition().type() == WaitConditionType.TIMED
                ? lastStop.get().waitCondition().durationTicks()
                : 0;
        int nextTicks = Math.max(0, currentTicks + deltaTicks);
        WaitCondition next = nextTicks <= 0 ? WaitCondition.none() : WaitCondition.timed(nextTicks);
        return updateLastStopWait(player, station, next);
    }

    private Optional<RouteStop> lastEditableStop(AirshipStationBlockEntity station) {
        if (station.isRecording()) {
            List<RouteStop> stops = station.recordingStops();
            return stops.isEmpty() ? Optional.empty() : Optional.of(stops.getLast());
        }
        return station.recordedRoute().flatMap(route -> route.stops().isEmpty()
                ? Optional.empty()
                : Optional.of(route.stops().getLast()));
    }

    private boolean updateLastStopWait(ServerPlayer player, AirshipStationBlockEntity station, WaitCondition waitCondition) {
        if (station.isRecording()) {
            return station.activeRecording()
                    .filter(session -> session.playerId().equals(player.getUUID()))
                    .map(session -> {
                        RouteOperationResult<RouteStop> result = AutomatedLogisticsServices.RECORDING.updateLastStopWait(
                                player,
                                session.routeId(),
                                waitCondition
                        );
                        result.value().ifPresentOrElse(
                                stop -> player.sendSystemMessage(Component.translatable(
                                        "message.create_aeronautics_automated_logistics.stop_mark.wait_updated",
                                        stop.name(),
                                        waitText(stop.waitCondition())
                                )),
                                () -> result.failure().ifPresent(failure -> player.sendSystemMessage(recordingFailureMessage(failure)))
                        );
                        return result.value().isPresent();
                    })
                    .orElse(false);
        }

        return station.recordedRoute().map(route -> {
            if (!canControlRoute(player, route)) {
                player.sendSystemMessage(Component.translatable("message.create_aeronautics_automated_logistics.station.permission_denied"));
                return false;
            }
            if (route.stops().isEmpty()) {
                player.sendSystemMessage(Component.translatable("message.create_aeronautics_automated_logistics.stop_mark.no_stop"));
                return false;
            }

            RouteStop updated = route.stops().getLast().withWaitCondition(waitCondition);
            station.replaceLastRouteStop(updated);
            player.sendSystemMessage(Component.translatable(
                    "message.create_aeronautics_automated_logistics.stop_mark.wait_updated",
                    updated.name(),
                    waitText(updated.waitCondition())
            ));
            return true;
        }).orElse(false);
    }

    private Component waitText(WaitCondition waitCondition) {
        return switch (waitCondition.type()) {
            case NONE -> Component.translatable("gui.create_aeronautics_automated_logistics.airship_station.wait.none");
            case TIMED -> Component.translatable(
                    "gui.create_aeronautics_automated_logistics.airship_station.wait.timed",
                    waitCondition.durationTicks() / 20
            );
            default -> Component.translatable("gui.create_aeronautics_automated_logistics.airship_station.wait.unsupported");
        };
    }

    private Component recordingFailureMessage(RecordingFailure failure) {
        return Component.translatable(
                "message.create_aeronautics_automated_logistics.recording.failed",
                Component.translatable("failure.create_aeronautics_automated_logistics." + failure.name().toLowerCase(Locale.ROOT))
        );
    }

    private boolean canControlRoute(ServerPlayer player, Route route) {
        return route.ownerId()
                .map(ownerId -> ownerId.equals(player.getUUID()))
                .orElse(false)
                || player.server.getProfilePermissions(player.getGameProfile()) >= 2;
    }
}
