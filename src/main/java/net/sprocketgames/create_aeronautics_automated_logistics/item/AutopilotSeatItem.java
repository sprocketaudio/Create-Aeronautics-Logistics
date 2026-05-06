package net.sprocketgames.create_aeronautics_automated_logistics.item;

import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.AirshipStationBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.vehicle.SableSubLevelVehicleController;
import net.sprocketgames.create_aeronautics_automated_logistics.vehicle.VehicleControllerRef;
import net.sprocketgames.create_aeronautics_automated_logistics.vehicle.VehicleControllerResolver;

public class AutopilotSeatItem extends BlockItem {
    private static final String LINK_TAG = "create_aeronautics_automated_logistics:station_link";
    private static final String DIMENSION = "dimension";
    private static final String STATION_X = "stationX";
    private static final String STATION_Y = "stationY";
    private static final String STATION_Z = "stationZ";

    public AutopilotSeatItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        ItemStack stack = context.getItemInHand();
        BlockPos clickedPos = context.getClickedPos();
        if (level.getBlockEntity(clickedPos) instanceof AirshipStationBlockEntity) {
            if (!level.isClientSide) {
                writeStationLink(stack, level.dimension(), clickedPos);
                if (context.getPlayer() != null) {
                    context.getPlayer().sendSystemMessage(Component.translatable(
                            "message.create_aeronautics_automated_logistics.link.station_saved",
                            clickedPos.getX(),
                            clickedPos.getY(),
                            clickedPos.getZ()
                    ));
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        Optional<PendingStationLink> pendingLink = readStationLink(stack);
        BlockPos placedPos = new BlockPlaceContext(context).getClickedPos();
        InteractionResult result = super.useOn(context);
        if (!level.isClientSide && result.consumesAction() && pendingLink.isPresent()) {
            applyStationLink((ServerLevel) level, placedPos, pendingLink.get(), context);
        }
        return result;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
        readStationLink(stack).ifPresent(link -> tooltipComponents.add(Component.translatable(
                "tooltip.create_aeronautics_automated_logistics.autopilot_seat.linked_station",
                link.stationPos().getX(),
                link.stationPos().getY(),
                link.stationPos().getZ()
        ).withStyle(ChatFormatting.GRAY)));
    }

    private void applyStationLink(ServerLevel level, BlockPos placedPos, PendingStationLink pendingLink, UseOnContext context) {
        if (!pendingLink.dimension().equals(level.dimension())) {
            if (context.getPlayer() != null) {
                context.getPlayer().sendSystemMessage(Component.translatable("message.create_aeronautics_automated_logistics.link.dimension_mismatch"));
            }
            return;
        }
        if (!(level.getBlockEntity(pendingLink.stationPos()) instanceof AirshipStationBlockEntity station)) {
            if (context.getPlayer() != null) {
                context.getPlayer().sendSystemMessage(Component.translatable("message.create_aeronautics_automated_logistics.link.station_missing"));
            }
            return;
        }
        if (!level.getBlockState(placedPos).is(getBlock())) {
            return;
        }

        VehicleControllerRef placedSeatRef = new VehicleControllerRef(
                VehicleControllerResolver.LINKED_AUTOPILOT_SEAT,
                level.dimension(),
                Optional.empty(),
                Optional.of(placedPos)
        );
        VehicleControllerRef storedRef = SableSubLevelVehicleController.resolve(level, placedSeatRef)
                .map(SableSubLevelVehicleController::ref)
                .orElse(placedSeatRef);
        station.linkController(storedRef);
        CreateAeronauticsAutomatedLogistics.LOGGER.info(
                "Linked Autopilot Seat at {} to station {} using controller ref {}",
                placedPos,
                pendingLink.stationPos(),
                storedRef
        );
        if (context.getPlayer() != null) {
            storedRef.vehicleId()
                    .ifPresentOrElse(
                            vehicleId -> context.getPlayer().sendSystemMessage(Component.translatable(
                                    "message.create_aeronautics_automated_logistics.link.seat_linked_ship",
                                    vehicleId.toString()
                            )),
                            () -> context.getPlayer().sendSystemMessage(Component.translatable(
                                    "message.create_aeronautics_automated_logistics.link.seat_linked",
                                    placedPos.getX(),
                                    placedPos.getY(),
                                    placedPos.getZ()
                            ))
                    );
        }
    }

    private static void writeStationLink(ItemStack stack, ResourceKey<Level> dimension, BlockPos stationPos) {
        CompoundTag root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        CompoundTag link = new CompoundTag();
        link.putString(DIMENSION, dimension.location().toString());
        link.putInt(STATION_X, stationPos.getX());
        link.putInt(STATION_Y, stationPos.getY());
        link.putInt(STATION_Z, stationPos.getZ());
        root.put(LINK_TAG, link);
        CustomData.set(DataComponents.CUSTOM_DATA, stack, root);
    }

    private static Optional<PendingStationLink> readStationLink(ItemStack stack) {
        CompoundTag root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!root.contains(LINK_TAG, TagType.COMPOUND)) {
            return Optional.empty();
        }
        CompoundTag link = root.getCompound(LINK_TAG);
        try {
            ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(link.getString(DIMENSION)));
            BlockPos stationPos = new BlockPos(link.getInt(STATION_X), link.getInt(STATION_Y), link.getInt(STATION_Z));
            return Optional.of(new PendingStationLink(dimension, stationPos));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private record PendingStationLink(ResourceKey<Level> dimension, BlockPos stationPos) {
    }

    private static final class TagType {
        private static final int COMPOUND = 10;
    }
}
