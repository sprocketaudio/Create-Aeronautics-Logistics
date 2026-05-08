package net.sprocketgames.create_aeronautics_automated_logistics.menu;

import java.util.Collections;
import java.util.Comparator;
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
import net.neoforged.neoforge.network.PacketDistributor;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.AirshipStationBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.dock.DockLinkStatus;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.AirshipStationRegistry;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.IdentityNames;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.ShipTransponderRegistry;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.ShipTransponderSnapshot;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.ShipTransponderBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.network.SetFlightPathPreviewPayload;
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
import net.sprocketgames.create_aeronautics_automated_logistics.AutomatedLogisticsConfig;

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
    public static final int ACTION_RECORD_OR_FINISH_SEGMENT = 9;
    public static final int ACTION_FINISH_RECORDING = 10;
    public static final int ACTION_AUTO_SELECT_CLOSEST_SHIP = 11;
    public static final int ACTION_SELECT_SHIP_BASE = 1000;
    public static final int ACTION_PREVIEW_ROUTE_BASE = 2000;
    public static final int ACTION_DELETE_ROUTE_BASE = 3000;
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

        if (id >= ACTION_SELECT_SHIP_BASE) {
            if (id >= ACTION_DELETE_ROUTE_BASE) {
                return deleteRouteByIndex(serverPlayer, station, id - ACTION_DELETE_ROUTE_BASE);
            }
            if (id >= ACTION_PREVIEW_ROUTE_BASE) {
                return previewRouteByIndex(serverPlayer, station, id - ACTION_PREVIEW_ROUTE_BASE);
            }
            return selectShipByIndex(serverPlayer, station, id - ACTION_SELECT_SHIP_BASE);
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
            case ACTION_RECORD_OR_FINISH_SEGMENT -> recordOrFinishSegment(serverPlayer, station);
            case ACTION_FINISH_RECORDING -> finishRecordingSession(serverPlayer, station);
            case ACTION_AUTO_SELECT_CLOSEST_SHIP -> autoSelectClosestShip(serverPlayer, station);
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
        Optional<Component> specific = station.failureReason()
                .map(reason -> Component.translatable(
                        "gui.create_aeronautics_automated_logistics.airship_station.failure",
                        Component.translatable("gui.create_aeronautics_automated_logistics.airship_station.failure_reason." + reason.name().toLowerCase(Locale.ROOT))
                ));
        if (specific.isPresent()) {
            return specific.get();
        }
        if (station.status() == RouteStatus.INVALID_ROUTE) {
            return Component.translatable(
                    "gui.create_aeronautics_automated_logistics.airship_station.failure",
                    Component.translatable("gui.create_aeronautics_automated_logistics.airship_station.failure_reason.invalid_route_data")
            );
        }
        return Component.empty();
    }

    public List<Component> failureTooltipLines(Player player) {
        if (!(player.level().getBlockEntity(stationPos) instanceof AirshipStationBlockEntity station)) {
            return List.of();
        }
        if (station.isRecording() || station.status() == RouteStatus.RECORDING) {
            return List.of();
        }
        Optional<FailureReason> reason = station.failureReason();
        if (reason.isPresent() && reason.get() == FailureReason.INVALID_ROUTE_DATA) {
            return List.of(
                    Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.failure_title")
                            .withStyle(net.minecraft.ChatFormatting.RED),
                    Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.failure_reason.invalid_route")
                            .withStyle(net.minecraft.ChatFormatting.GRAY),
                    Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.failure_hint.invalid_route")
                            .withStyle(net.minecraft.ChatFormatting.GRAY)
            );
        }
        if (station.status() == RouteStatus.INVALID_ROUTE) {
            return List.of(
                    Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.failure_title")
                            .withStyle(net.minecraft.ChatFormatting.RED),
                    Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.failure_reason.invalid_route")
                            .withStyle(net.minecraft.ChatFormatting.GRAY),
                    Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.failure_hint.invalid_route")
                            .withStyle(net.minecraft.ChatFormatting.GRAY)
            );
        }
        Component failure = failureText(player);
        return failure.getString().isBlank()
                ? List.of()
                : List.of(failure.copy().withStyle(net.minecraft.ChatFormatting.RED));
    }

    public boolean isRecording(Player player) {
        if (!(player.level().getBlockEntity(stationPos) instanceof AirshipStationBlockEntity station)) {
            return false;
        }
        Optional<UUID> selected = station.selectedTransponderId();
        if (selected.isPresent() && isSelectedShipRecording(player, selected.get())) {
            return true;
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
                .map(id -> Component.literal(station.selectedShipName()))
                .orElseGet(() -> Component.translatable("gui.create_aeronautics_automated_logistics.airship_station.selected_ship.none"));
    }

    public List<ShipChoice> shipChoices(Player player) {
        if (!(player.level().getBlockEntity(stationPos) instanceof AirshipStationBlockEntity station)) {
            return List.of();
        }
        List<ShipTransponderSnapshot> ships = player instanceof ServerPlayer serverPlayer
                ? sortedShips(serverPlayer, station)
                : ShipTransponderRegistry.knownShips(player.level().dimension()).stream()
                        .sorted(Comparator
                                .comparingDouble((ShipTransponderSnapshot snapshot) -> distanceToStationSqr(station, snapshot))
                                .thenComparing(snapshot -> snapshot.transponderId().toString()))
                        .toList();
        Optional<UUID> selected = station.selectedTransponderId();
        double landingRadius = AutomatedLogisticsConfig.MAX_START_JOIN_DISTANCE.get();
        double landingRadiusSqr = landingRadius * landingRadius;
        return ships.stream().map(snapshot -> {
            boolean selectedShip = selected.map(snapshot.transponderId()::equals).orElse(false);
            boolean available = snapshot.controllerRef().isPresent();
            Vec3 shipPos = snapshot.lastKnownPosition().orElse(Vec3.atCenterOf(snapshot.transponderPos()));
            double distance = Math.sqrt(station.getBlockPos().distToCenterSqr(shipPos.x, shipPos.y, shipPos.z));
            boolean inRange = station.getBlockPos().distToCenterSqr(shipPos.x, shipPos.y, shipPos.z) <= landingRadiusSqr;
            Component statusText = available ? Component.literal((int) distance + "m") : Component.translatable(
                    "gui.create_aeronautics_automated_logistics.airship_station.not_found"
            );
            int statusColor = available ? (inRange ? 0xFF8BE77A : 0xFFFFC66E) : 0xFFFF8C8C;
            return new ShipChoice(
                    snapshot.transponderId(),
                    Component.literal(snapshot.shipName()),
                    statusText,
                    statusColor,
                    selectedShip
            );
        }).toList();
    }

    public Optional<ShipChoice> selectedShipChoice(Player player) {
        return shipChoices(player).stream().filter(ShipChoice::selected).findFirst();
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

    public Component outgoingSegmentsText(Player player) {
        if (!(player.level().getBlockEntity(stationPos) instanceof AirshipStationBlockEntity station)) {
            return Component.empty();
        }
        int outgoing = RouteSegmentResolver.validOutgoingSegments(
                station,
                player.level().dimension(),
                station.selectedTransponderId()
        ).size();
        return Component.translatable(
                "gui.create_aeronautics_automated_logistics.airship_station.routes_from_here",
                outgoing
        );
    }

    public Component panelStatusText(Player player) {
        if (!(player.level().getBlockEntity(stationPos) instanceof AirshipStationBlockEntity station)) {
            return Component.translatable("gui.create_aeronautics_automated_logistics.airship_station.status_missing_station");
        }
        Optional<UUID> selected = station.selectedTransponderId();
        if (selected.isPresent()) {
            if (isSelectedShipRecording(player, selected.get())) {
                return Component.translatable("gui.create_aeronautics_automated_logistics.airship_station.status.recording");
            }
            if (AutomatedLogisticsServices.SCHEDULES.isRunning(selected.get())) {
                return Component.translatable("gui.create_aeronautics_automated_logistics.airship_station.status.running");
            }
        }
        RouteStatus status = station.isRecording() ? RouteStatus.RECORDING : station.status();
        if (status == RouteStatus.RECORDED) {
            status = RouteStatus.IDLE;
        }
        if (status == RouteStatus.INVALID_ROUTE) {
            int outgoing = RouteSegmentResolver.validOutgoingSegments(
                    station,
                    player.level().dimension(),
                    station.selectedTransponderId()
            ).size();
            if (outgoing <= 0) {
                return Component.translatable("gui.create_aeronautics_automated_logistics.airship_station.status.no_route");
            }
        }
        return Component.translatable("gui.create_aeronautics_automated_logistics.airship_station.status." + status.name().toLowerCase(Locale.ROOT));
    }

    public Component routesFromHereText(Player player) {
        if (!(player.level().getBlockEntity(stationPos) instanceof AirshipStationBlockEntity station)) {
            return Component.empty();
        }
        int outgoing = RouteSegmentResolver.validOutgoingSegments(
                station,
                player.level().dimension(),
                station.selectedTransponderId()
        ).size();
        return Component.translatable(
                "gui.create_aeronautics_automated_logistics.airship_station.routes_from_here_compact",
                outgoing
        );
    }

    public int routesFromHereCount(Player player) {
        if (!(player.level().getBlockEntity(stationPos) instanceof AirshipStationBlockEntity station)) {
            return 0;
        }
        return RouteSegmentResolver.validOutgoingSegments(
                station,
                player.level().dimension(),
                station.selectedTransponderId()
        ).size();
    }

    public Component routesToHereText(Player player) {
        if (!(player.level().getBlockEntity(stationPos) instanceof AirshipStationBlockEntity station)) {
            return Component.empty();
        }
        int incoming = RouteSegmentResolver.validLocalSegments(
                        station,
                        player.level().dimension(),
                        station.selectedTransponderId()
                ).stream()
                .filter(segment -> segment.endStationId().equals(station.stationId()))
                .toList()
                .size();
        return Component.translatable(
                "gui.create_aeronautics_automated_logistics.airship_station.routes_to_here_compact",
                incoming
        );
    }

    public int routesToHereCount(Player player) {
        if (!(player.level().getBlockEntity(stationPos) instanceof AirshipStationBlockEntity station)) {
            return 0;
        }
        return (int) RouteSegmentResolver.validLocalSegments(
                        station,
                        player.level().dimension(),
                        station.selectedTransponderId()
                ).stream()
                .filter(segment -> segment.endStationId().equals(station.stationId()))
                .count();
    }

    public Component dockText(Player player) {
        if (!(player.level().getBlockEntity(stationPos) instanceof AirshipStationBlockEntity station)) {
            return Component.empty();
        }
        DockLinkStatus status = station.groundDockStatus() == DockLinkStatus.LINKED && station.groundDockPos().isEmpty()
                ? DockLinkStatus.UNKNOWN
                : station.groundDockStatus();
        Component statusText = Component.translatable(
                "gui.create_aeronautics_automated_logistics.dock.status." + status.name().toLowerCase(Locale.ROOT)
        );
        Component position = station.groundDockPos()
                .map(pos -> Component.literal(pos.getX() + ", " + pos.getY() + ", " + pos.getZ()))
                .orElse(Component.literal("-"));
        Component output = Component.translatable(
                station.dockOutputActive()
                        ? "gui.create_aeronautics_automated_logistics.dock.output.on"
                        : "gui.create_aeronautics_automated_logistics.dock.output.off"
        );
        return Component.translatable(
                "gui.create_aeronautics_automated_logistics.airship_station.dock",
                statusText,
                position,
                output
        );
    }

    public Component dockCompactText(Player player) {
        if (!(player.level().getBlockEntity(stationPos) instanceof AirshipStationBlockEntity station)) {
            return Component.empty();
        }
        return station.groundDockPos()
                .map(pos -> Component.literal(pos.getX() + ", " + pos.getY() + ", " + pos.getZ()))
                .orElseGet(() -> Component.translatable("gui.create_aeronautics_automated_logistics.airship_station.not_found"));
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

    public List<RouteSegment> routeChoices(Player player) {
        if (!(player.level().getBlockEntity(stationPos) instanceof AirshipStationBlockEntity station)) {
            return List.of();
        }
        return localRoutes(station, player);
    }

    private boolean selectNextShip(ServerPlayer player, AirshipStationBlockEntity station) {
        List<ShipTransponderSnapshot> ships = sortedShips(player, station);
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

    private boolean selectShipByIndex(ServerPlayer player, AirshipStationBlockEntity station, int shipIndex) {
        List<ShipTransponderSnapshot> ships = sortedShips(player, station);
        if (shipIndex < 0 || shipIndex >= ships.size()) {
            return false;
        }
        ShipTransponderSnapshot selected = ships.get(shipIndex);
        station.selectShip(selected);
        player.sendSystemMessage(Component.translatable(
                "message.create_aeronautics_automated_logistics.ship_selection.selected",
                selected.shipName(),
                IdentityNames.shortId(selected.transponderId())
        ));
        return true;
    }

    private boolean autoSelectClosestShip(ServerPlayer player, AirshipStationBlockEntity station) {
        List<ShipTransponderSnapshot> ships = sortedShips(player, station);
        if (ships.isEmpty()) {
            return false;
        }
        station.selectShip(ships.getFirst());
        return true;
    }

    private boolean recordOrFinishSegment(ServerPlayer player, AirshipStationBlockEntity station) {
        if (AutomatedLogisticsServices.RECORDING.hasActiveRecording(player)) {
            return finishSegmentRecording(player, station);
        }
        return startSegmentRecording(player, station);
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
                session -> {
                    player.sendSystemMessage(Component.translatable(
                            "message.create_aeronautics_automated_logistics.segment_recording.started",
                            station.stationName(),
                            selectedShip.get().shipName()
                    ));
                    player.sendSystemMessage(Component.translatable("message.create_aeronautics_automated_logistics.segment_recording.hint"));
                },
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

    private boolean finishRecordingSession(ServerPlayer player, AirshipStationBlockEntity station) {
        Optional<RecordingSession> session = AutomatedLogisticsServices.RECORDING.activeRecordingForPlayer(player.getUUID());
        if (session.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.create_aeronautics_automated_logistics.stop_mark.not_recording"));
            return false;
        }
        RouteOperationResult<Route> result = AutomatedLogisticsServices.RECORDING.stopRecording(player, session.get().routeId());
        result.value().ifPresentOrElse(
                route -> player.sendSystemMessage(Component.translatable(
                        "message.create_aeronautics_automated_logistics.recording.saved",
                        route.points().size()
                )),
                () -> result.failure().ifPresent(failure -> player.sendSystemMessage(recordingFailureMessage(failure)))
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

        Optional<UUID> selectedTransponderId = station.selectedTransponderId();
        if (selectedTransponderId.isEmpty()) {
            station.setFailure(FailureReason.MISSING_AUTOPILOT_CONTROLLER);
            player.sendSystemMessage(Component.translatable("message.create_aeronautics_automated_logistics.recording.no_selected_ship"));
            return false;
        }
        Optional<ShipTransponderBlockEntity> transponder = selectedTransponder(player.serverLevel(), selectedTransponderId.get());
        if (transponder.isEmpty()) {
            station.setFailure(FailureReason.VEHICLE_DESTROYED_OR_MISSING);
            player.sendSystemMessage(Component.translatable("message.create_aeronautics_automated_logistics.recording.selected_ship_unavailable"));
            return false;
        }
        if (!AutomatedLogisticsServices.SCHEDULES.canStationStartFor(player.serverLevel(), station, transponder.get())) {
            player.sendSystemMessage(Component.translatable("message.create_aeronautics_automated_logistics.airship_schedule.ship_not_in_landing_area"));
            return false;
        }
        if (!transponder.get().hasInstalledSchedule()) {
            player.sendSystemMessage(Component.translatable("message.create_aeronautics_automated_logistics.airship_schedule.transponder_no_schedule"));
            return false;
        }

        AirshipSchedule schedule = transponder.get().installedSchedule();
        PlaybackOperationResult<?> result = AutomatedLogisticsServices.SCHEDULES.start(
                player,
                station,
                stationPos,
                transponder.get(),
                schedule
        );
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
        Optional<UUID> transponderId = station.selectedTransponderId();
        if (transponderId.isEmpty() || !AutomatedLogisticsServices.SCHEDULES.isRunning(transponderId.get())) {
            return false;
        }
        if (!AutomatedLogisticsServices.SCHEDULES.canStationStopFor(player.serverLevel(), station, transponderId.get())) {
            player.sendSystemMessage(Component.translatable("message.create_aeronautics_automated_logistics.airship_schedule.stop_denied_station_scope"));
            return false;
        }
        AutomatedLogisticsServices.SCHEDULES.stop(player.serverLevel(), transponderId.get());
        player.sendSystemMessage(Component.translatable("message.create_aeronautics_automated_logistics.airship_schedule.stopped"));
        return true;
    }

    private boolean previewRouteByIndex(ServerPlayer player, AirshipStationBlockEntity station, int routeIndex) {
        List<RouteSegment> routes = localRoutes(station, player);
        if (routeIndex < 0 || routeIndex >= routes.size()) {
            return false;
        }
        RouteSegment segment = routes.get(routeIndex);
        PacketDistributor.sendToPlayer(
                player,
                new SetFlightPathPreviewPayload(true, segment.points().stream().map(point -> point.position()).toList())
        );
        return true;
    }

    private boolean deleteRouteByIndex(ServerPlayer player, AirshipStationBlockEntity station, int routeIndex) {
        List<RouteSegment> routes = localRoutes(station, player);
        if (routeIndex < 0 || routeIndex >= routes.size()) {
            return false;
        }
        RouteSegment segment = routes.get(routeIndex);
        if (!canControlSegment(player, segment)) {
            player.sendSystemMessage(Component.translatable("message.create_aeronautics_automated_logistics.station.permission_denied"));
            return false;
        }

        removeRouteFromLoadedStation(player.serverLevel(), segment.startStationId(), segment.id().value());
        removeRouteFromLoadedStation(player.serverLevel(), segment.endStationId(), segment.id().value());
        player.sendSystemMessage(Component.translatable(
                "message.create_aeronautics_automated_logistics.route.deleted",
                segment.startStationName(),
                segment.endStationName()
        ));
        return true;
    }

    private void removeRouteFromLoadedStation(ServerLevel level, UUID stationId, UUID segmentId) {
        AirshipStationRegistry.snapshot(stationId)
                .filter(snapshot -> snapshot.dimension().equals(level.dimension()))
                .map(snapshot -> level.getBlockEntity(snapshot.stationPos()))
                .filter(AirshipStationBlockEntity.class::isInstance)
                .map(AirshipStationBlockEntity.class::cast)
                .ifPresent(station -> station.removeRouteSegment(segmentId));
    }

    private List<RouteSegment> localRoutes(AirshipStationBlockEntity station, Player player) {
        return RouteSegmentResolver.validLocalSegments(
                        station,
                        player.level().dimension(),
                        station.selectedTransponderId()
                ).stream()
                .sorted(Comparator
                        .comparingLong(RouteSegment::createdEpochMillis)
                        .reversed()
                        .thenComparing(segment -> segment.id().value().toString()))
                .toList();
    }

    private Optional<ShipTransponderBlockEntity> selectedTransponder(ServerLevel level, UUID transponderId) {
        return ShipTransponderRegistry.snapshot(transponderId)
                .filter(snapshot -> snapshot.dimension().equals(level.dimension()))
                .map(snapshot -> level.getBlockEntity(snapshot.transponderPos()))
                .filter(ShipTransponderBlockEntity.class::isInstance)
                .map(ShipTransponderBlockEntity.class::cast);
    }

    public record ShipChoice(
            UUID transponderId,
            Component shipName,
            Component statusText,
            int statusColor,
            boolean selected
    ) {
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

    private List<ShipTransponderSnapshot> sortedShips(ServerPlayer player, AirshipStationBlockEntity station) {
        return ShipTransponderRegistry.knownShips(player.serverLevel().dimension()).stream()
                .sorted(Comparator
                        .comparingDouble((ShipTransponderSnapshot snapshot) -> distanceToStationSqr(station, snapshot))
                        .thenComparingInt(snapshot -> activityPriority(player, snapshot))
                        .thenComparing(snapshot -> snapshot.transponderId().toString()))
                .toList();
    }

    private double distanceToStationSqr(AirshipStationBlockEntity station, ShipTransponderSnapshot snapshot) {
        Vec3 shipPos = snapshot.lastKnownPosition().orElse(Vec3.atCenterOf(snapshot.transponderPos()));
        return station.getBlockPos().distToCenterSqr(shipPos.x, shipPos.y, shipPos.z);
    }

    private int activityPriority(ServerPlayer player, ShipTransponderSnapshot snapshot) {
        if (isSelectedShipRecording(player, snapshot.transponderId())) {
            return 0;
        }
        if (AutomatedLogisticsServices.SCHEDULES.isRunning(snapshot.transponderId())) {
            return 1;
        }
        return 2;
    }

    private boolean isSelectedShipRecording(Player player, UUID transponderId) {
        Optional<ShipTransponderSnapshot> snapshot = ShipTransponderRegistry.snapshot(transponderId);
        if (snapshot.isEmpty()) {
            return false;
        }
        Optional<RecordingSession> activeRecording = AutomatedLogisticsServices.RECORDING.activeRecordingForPlayer(player.getUUID());
        if (activeRecording.isEmpty()) {
            return false;
        }

        if (snapshot.get().controllerRef().isPresent() && snapshot.get().controllerRef().get().equals(activeRecording.get().controllerRef())) {
            return true;
        }
        return snapshot.get().runtimeShipId()
                .flatMap(runtimeId -> activeRecording.get().controllerRef().vehicleId().map(runtimeId::equals))
                .orElse(false);
    }

    private boolean canControlRoute(ServerPlayer player, Route route) {
        return route.ownerId()
                .map(ownerId -> ownerId.equals(player.getUUID()))
                .orElse(false)
                || player.server.getProfilePermissions(player.getGameProfile()) >= 2;
    }

    private boolean canControlSegment(ServerPlayer player, RouteSegment segment) {
        return segment.ownerId()
                .map(ownerId -> ownerId.equals(player.getUUID()))
                .orElse(false)
                || player.server.getProfilePermissions(player.getGameProfile()) >= 2;
    }

}
