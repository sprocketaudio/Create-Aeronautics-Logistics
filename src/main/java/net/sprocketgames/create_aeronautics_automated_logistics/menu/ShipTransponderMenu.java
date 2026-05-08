package net.sprocketgames.create_aeronautics_automated_logistics.menu;

import java.util.Optional;
import java.util.UUID;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.ShipTransponderBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.AirshipStationRegistry;
import net.sprocketgames.create_aeronautics_automated_logistics.dock.DockLinkStatus;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.IdentityNames;
import net.sprocketgames.create_aeronautics_automated_logistics.item.AirshipScheduleItem;
import net.sprocketgames.create_aeronautics_automated_logistics.network.SetFlightPathPreviewPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import net.sprocketgames.create_aeronautics_automated_logistics.registry.ModMenus;
import net.sprocketgames.create_aeronautics_automated_logistics.registry.ModItems;
import net.sprocketgames.create_aeronautics_automated_logistics.route.AirshipSchedule;
import net.sprocketgames.create_aeronautics_automated_logistics.route.AirshipScheduleEntry;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteSegment;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteSegmentResolver;
import net.sprocketgames.create_aeronautics_automated_logistics.service.AutomatedLogisticsServices;
import java.util.Locale;
import java.util.ArrayList;
import net.sprocketgames.create_aeronautics_automated_logistics.service.PlaybackFailure;

public class ShipTransponderMenu extends AbstractContainerMenu {
    public static final int ACTION_START_INSTALLED_SCHEDULE = 0;
    public static final int ACTION_STOP_SCHEDULE = 1;
    public static final int ACTION_TOGGLE_PREVIEW = 2;
    private static final int SCHEDULE_SLOT_INDEX = 0;
    private static final int PLAYER_INVENTORY_START = 1;
    private static final int PLAYER_INVENTORY_END = 28;
    private static final int HOTBAR_START = 28;
    private static final int HOTBAR_END = 37;

    private final BlockPos transponderPos;

    public ShipTransponderMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buffer) {
        this(containerId, playerInventory, buffer.readBlockPos());
    }

    public ShipTransponderMenu(int containerId, Inventory playerInventory, BlockPos transponderPos) {
        super(ModMenus.SHIP_TRANSPONDER.get(), containerId);
        this.transponderPos = transponderPos;
        if (playerInventory.player.level().getBlockEntity(transponderPos) instanceof ShipTransponderBlockEntity transponder) {
        addSlot(new Slot(transponder, ShipTransponderBlockEntity.INTERNAL_SCHEDULE_SLOT, 44, 64) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return stack.is(ModItems.AIRSHIP_SCHEDULE.get());
                }
            });
        }
        addPlayerInventorySlots(playerInventory, 17, 198);
    }

    public BlockPos transponderPos() {
        return transponderPos;
    }

    public String shipName(Player player) {
        if (player.level().getBlockEntity(transponderPos) instanceof ShipTransponderBlockEntity transponder) {
            return transponder.shipName();
        }
        return "";
    }

    public Component shipIdText(Player player) {
        if (player.level().getBlockEntity(transponderPos) instanceof ShipTransponderBlockEntity transponder) {
            return Component.literal(IdentityNames.shortId(transponder.transponderId()));
        }
        return Component.literal("-");
    }

    public Component runtimeShipText(Player player) {
        if (player.level().getBlockEntity(transponderPos) instanceof ShipTransponderBlockEntity transponder) {
            return transponder.runtimeShipId()
                    .map(id -> Component.literal(IdentityNames.shortId(id)))
                    .orElseGet(() -> Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.unavailable"));
        }
        return Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.missing");
    }

    public Component lastKnownPositionText(Player player) {
        if (player.level().getBlockEntity(transponderPos) instanceof ShipTransponderBlockEntity transponder) {
            Optional<Vec3> position = transponder.lastKnownPosition();
            if (position.isPresent()) {
                Vec3 pos = position.get();
                return Component.literal((int) pos.x + ", " + (int) pos.y + ", " + (int) pos.z);
            }
        }
        return Component.literal("-");
    }

    public Component dockText(Player player) {
        if (!(player.level().getBlockEntity(transponderPos) instanceof ShipTransponderBlockEntity transponder)) {
            return Component.empty();
        }
        DockLinkStatus status = transponder.shipDockStatus() == DockLinkStatus.LINKED && transponder.shipDockPos().isEmpty()
                ? DockLinkStatus.UNKNOWN
                : transponder.shipDockStatus();
        Component output = Component.translatable(
                transponder.dockOutputActive()
                        ? "gui.create_aeronautics_automated_logistics.dock.output.on"
                        : "gui.create_aeronautics_automated_logistics.dock.output.off"
        );
        return dockTooltip(player).isEmpty() ? Component.empty() : dockTooltip(player).getFirst();
    }

    public List<Component> dockTooltip(Player player) {
        if (!(player.level().getBlockEntity(transponderPos) instanceof ShipTransponderBlockEntity transponder)) {
            return List.of();
        }
        DockLinkStatus status = transponder.shipDockStatus() == DockLinkStatus.LINKED && transponder.shipDockPos().isEmpty()
                ? DockLinkStatus.UNKNOWN
                : transponder.shipDockStatus();
        Component output = Component.translatable(
                transponder.dockOutputActive()
                        ? "gui.create_aeronautics_automated_logistics.dock.output.on"
                        : "gui.create_aeronautics_automated_logistics.dock.output.off"
        );
        Component statusLine = Component.translatable(
                "gui.create_aeronautics_automated_logistics.ship_transponder.dock.hover.status",
                Component.translatable("gui.create_aeronautics_automated_logistics.dock.status." + status.name().toLowerCase(Locale.ROOT))
        );
        Component outputLine = Component.translatable(
                "gui.create_aeronautics_automated_logistics.ship_transponder.dock.hover.output",
                output
        );
        Component distanceLine = transponder.shipDockPos()
                .map(dockPos -> {
                    double distance = Math.sqrt(transponderPos.distSqr(dockPos));
                    int meters = (int) Math.round(distance);
                    return Component.translatable(
                            "gui.create_aeronautics_automated_logistics.ship_transponder.dock.hover.distance",
                            meters
                    );
                })
                .orElseGet(() -> Component.translatable(
                        "gui.create_aeronautics_automated_logistics.ship_transponder.dock.hover.distance_unknown"
                ));
        return List.of(statusLine, outputLine, distanceLine);
    }

    public Component dockCompactText(Player player) {
        if (!(player.level().getBlockEntity(transponderPos) instanceof ShipTransponderBlockEntity transponder)) {
            return Component.literal("-");
        }
        DockLinkStatus status = transponder.shipDockStatus();
        return Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.dock.compact." + status.name().toLowerCase(Locale.ROOT));
    }

    public int dockStatusColor(Player player) {
        return 0xFFAFC7DE;
    }

    public Component installedScheduleText(Player player) {
        if (!(player.level().getBlockEntity(transponderPos) instanceof ShipTransponderBlockEntity transponder)) {
            return Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.no_schedule");
        }
        return transponder.installedScheduleTitle();
    }

    public boolean hasInstalledSchedule(Player player) {
        return player.level().getBlockEntity(transponderPos) instanceof ShipTransponderBlockEntity transponder
                && transponder.hasInstalledSchedule();
    }

    public boolean isScheduleRunning(Player player) {
        if (!(player.level().getBlockEntity(transponderPos) instanceof ShipTransponderBlockEntity transponder)) {
            return false;
        }
        return AutomatedLogisticsServices.SCHEDULES.isRunning(transponder.transponderId());
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {
            return false;
        }
        if (!(player.level().getBlockEntity(transponderPos) instanceof ShipTransponderBlockEntity transponder)) {
            return false;
        }

        return switch (id) {
            case ACTION_START_INSTALLED_SCHEDULE -> startInstalledSchedule(serverPlayer, transponder);
            case ACTION_STOP_SCHEDULE -> stopSchedule(serverPlayer, transponder);
            case ACTION_TOGGLE_PREVIEW -> togglePreview(serverPlayer, transponder);
            default -> false;
        };
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (!(player.level().getBlockEntity(transponderPos) instanceof ShipTransponderBlockEntity)) {
            return ItemStack.EMPTY;
        }
        Slot sourceSlot = this.slots.get(index);
        if (sourceSlot == null || !sourceSlot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack sourceStack = sourceSlot.getItem();
        ItemStack stackCopy = sourceStack.copy();

        if (index == SCHEDULE_SLOT_INDEX) {
            if (!moveItemStackTo(sourceStack, PLAYER_INVENTORY_START, HOTBAR_END, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (!sourceStack.is(ModItems.AIRSHIP_SCHEDULE.get())) {
                return ItemStack.EMPTY;
            }
            if (!moveItemStackTo(sourceStack, SCHEDULE_SLOT_INDEX, SCHEDULE_SLOT_INDEX + 1, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (sourceStack.isEmpty()) {
            sourceSlot.set(ItemStack.EMPTY);
        } else {
            sourceSlot.setChanged();
        }

        sourceSlot.onTake(player, sourceStack);
        return stackCopy;
    }

    @Override
    public boolean stillValid(Player player) {
        if (!(player.level().getBlockEntity(transponderPos) instanceof ShipTransponderBlockEntity)) {
            return false;
        }
        return player.distanceToSqr(
                transponderPos.getX() + 0.5D,
                transponderPos.getY() + 0.5D,
                transponderPos.getZ() + 0.5D
        ) <= 64.0D;
    }

    private boolean startInstalledSchedule(net.minecraft.server.level.ServerPlayer player, ShipTransponderBlockEntity transponder) {
        if (!transponder.hasInstalledSchedule()) {
            player.sendSystemMessage(Component.translatable("message.create_aeronautics_automated_logistics.airship_schedule.transponder_no_schedule"));
            return false;
        }
        var result = AutomatedLogisticsServices.SCHEDULES
                .startFromTransponder(player, transponder, AirshipScheduleItem.readSchedule(transponder.installedScheduleStack()));
        result.failure().ifPresent(failure -> player.sendSystemMessage(Component.translatable(
                "message.create_aeronautics_automated_logistics.playback.failed",
                Component.translatable("failure.create_aeronautics_automated_logistics.playback." + failure.name().toLowerCase(Locale.ROOT))
        )));
        return result.value().isPresent();
    }

    private boolean stopSchedule(net.minecraft.server.level.ServerPlayer player, ShipTransponderBlockEntity transponder) {
        UUID transponderId = transponder.transponderId();
        if (!AutomatedLogisticsServices.SCHEDULES.isRunning(transponderId)) {
            return false;
        }
        AutomatedLogisticsServices.SCHEDULES.stop(player.serverLevel(), transponderId);
        return true;
    }

    private boolean togglePreview(net.minecraft.server.level.ServerPlayer player, ShipTransponderBlockEntity transponder) {
        if (!transponder.hasInstalledSchedule()) {
            PacketDistributor.sendToPlayer(player, new SetFlightPathPreviewPayload(false, List.of()));
            return false;
        }
        List<Vec3> points = previewPathPoints(player.serverLevel(), transponder, transponder.installedSchedule());
        boolean enabled = !points.isEmpty();
        PacketDistributor.sendToPlayer(player, new SetFlightPathPreviewPayload(enabled, points));
        return enabled;
    }

    private List<Vec3> previewPathPoints(ServerLevel level, ShipTransponderBlockEntity transponder, AirshipSchedule schedule) {
        if (schedule.entries().isEmpty()) {
            return List.of();
        }
        Optional<UUID> currentStationId = resolveStartStationId(level, transponder);
        if (currentStationId.isEmpty()) {
            return List.of();
        }
        List<Vec3> points = new ArrayList<>();
        UUID stationId = currentStationId.get();
        for (AirshipScheduleEntry entry : schedule.entries()) {
            if (entry.targetStationId().isEmpty()) {
                break;
            }
            UUID fromStationId = stationId;
            Optional<RouteSegment> segment = entry.pinnedSegmentId()
                    .flatMap(net.sprocketgames.create_aeronautics_automated_logistics.route.RouteSegmentRegistry::byId)
                    .filter(candidate -> candidate.startStationId().equals(fromStationId))
                    .filter(candidate -> candidate.endStationId().equals(entry.targetStationId().get()))
                    .filter(candidate -> candidate.dimension().equals(level.dimension()))
                    .filter(candidate -> candidate.transponderId().equals(transponder.transponderId()))
                    .or(() -> RouteSegmentResolver.newestFor(
                            fromStationId,
                            entry.targetStationId().get(),
                            level.dimension(),
                            Optional.of(transponder.transponderId())
                    ));
            if (segment.isEmpty()) {
                break;
            }
            segment.get().points().forEach(routePoint -> points.add(routePoint.position()));
            stationId = entry.targetStationId().get();
        }
        return points;
    }

    private Optional<UUID> resolveStartStationId(ServerLevel level, ShipTransponderBlockEntity transponder) {
        if (AutomatedLogisticsServices.SCHEDULES.isRunning(transponder.transponderId())) {
            Optional<UUID> runningStation = AutomatedLogisticsServices.SCHEDULES.currentStationId(transponder.transponderId());
            if (runningStation.isPresent()) {
                return runningStation;
            }
        }
        Vec3 shipPos = transponder.lastKnownPosition().orElse(Vec3.atCenterOf(transponder.getBlockPos()));
        double radius = net.sprocketgames.create_aeronautics_automated_logistics.AutomatedLogisticsConfig.MAX_START_JOIN_DISTANCE.get();
        double radiusSqr = radius * radius;
        return AirshipStationRegistry.knownStations(level.dimension()).stream()
                .filter(snapshot -> snapshot.stationPos().distToCenterSqr(shipPos.x, shipPos.y, shipPos.z) <= radiusSqr)
                .sorted((a, b) -> Double.compare(
                        a.stationPos().distToCenterSqr(shipPos.x, shipPos.y, shipPos.z),
                        b.stationPos().distToCenterSqr(shipPos.x, shipPos.y, shipPos.z)
                ))
                .map(net.sprocketgames.create_aeronautics_automated_logistics.identity.AirshipStationSnapshot::stationId)
                .findFirst();
    }

    public Component runtimeStateText(Player player) {
        if (!(player.level().getBlockEntity(transponderPos) instanceof ShipTransponderBlockEntity transponder)) {
            return Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.idle");
        }
        if (AutomatedLogisticsServices.SCHEDULES.isRunning(transponder.transponderId())) {
            return Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.running");
        }
        Optional<PlaybackFailure> failure = AutomatedLogisticsServices.SCHEDULES.lastFailure(transponder.transponderId());
        if (failure.isPresent()) {
            if (failure.get() == PlaybackFailure.INVALID_ROUTE) {
                return Component.translatable("gui.create_aeronautics_automated_logistics.airship_station.status.no_route");
            }
            return Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.failed");
        }
        return Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.idle");
    }

    public int runtimeStateColor(Player player) {
        if (!(player.level().getBlockEntity(transponderPos) instanceof ShipTransponderBlockEntity transponder)) {
            return 0xAFC7DE;
        }
        if (AutomatedLogisticsServices.SCHEDULES.isRunning(transponder.transponderId())) {
            return 0xFFE7C46E;
        }
        Optional<PlaybackFailure> failure = AutomatedLogisticsServices.SCHEDULES.lastFailure(transponder.transponderId());
        if (failure.isPresent()) {
            return 0xFFFFB4B4;
        }
        return 0xAFC7DE;
    }

    public List<Component> runtimeFailureTooltip(Player player) {
        if (!(player.level().getBlockEntity(transponderPos) instanceof ShipTransponderBlockEntity transponder)) {
            return List.of();
        }
        Optional<PlaybackFailure> failure = AutomatedLogisticsServices.SCHEDULES.lastFailure(transponder.transponderId());
        if (failure.isEmpty()) {
            return List.of();
        }
        PlaybackFailure value = failure.get();
        String suffix = value.name().toLowerCase(Locale.ROOT);
        return List.of(
                Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.failure_title")
                        .withStyle(net.minecraft.ChatFormatting.RED),
                Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.failure_reason." + suffix)
                        .withStyle(net.minecraft.ChatFormatting.GRAY),
                Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.failure_hint." + suffix)
                        .withStyle(net.minecraft.ChatFormatting.DARK_GRAY)
        );
    }

    private void addPlayerInventorySlots(Inventory inventory, int leftX, int topY) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, leftX + col * 18, topY + row * 18));
            }
        }
        int hotbarY = topY + 58;
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, leftX + col * 18, hotbarY));
        }
    }
}
