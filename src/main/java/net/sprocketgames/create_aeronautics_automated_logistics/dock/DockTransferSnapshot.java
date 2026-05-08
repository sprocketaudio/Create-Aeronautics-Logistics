package net.sprocketgames.create_aeronautics_automated_logistics.dock;

import com.simibubi.create.content.logistics.filter.FilterItemStack;
import dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorBlockEntity;
import dev.simulated_team.simulated.multiloader.tanks.CFluidType;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Tuple;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;

public record DockTransferSnapshot(
        List<String> items,
        List<String> fluids,
        List<ItemStack> itemStacks,
        List<FluidStack> fluidStacks,
        int itemCount,
        long fluidAmount
) {
    public static DockTransferSnapshot capture(
            DockingConnectorBlockEntity stationDock,
            DockingConnectorBlockEntity shipDock
    ) {
        List<String> itemState = new ArrayList<>();
        List<String> fluidState = new ArrayList<>();
        List<ItemStack> itemStacks = new ArrayList<>();
        List<FluidStack> fluidStacks = new ArrayList<>();
        ConnectorTotals stationTotals = captureConnector(stationDock, itemState, fluidState, itemStacks, fluidStacks);
        ConnectorTotals shipTotals = captureConnector(shipDock, itemState, fluidState, itemStacks, fluidStacks);
        return new DockTransferSnapshot(
                List.copyOf(itemState),
                List.copyOf(fluidState),
                itemStacks.stream().map(ItemStack::copy).toList(),
                fluidStacks.stream().map(FluidStack::copy).toList(),
                stationTotals.itemCount() + shipTotals.itemCount(),
                stationTotals.fluidAmount() + shipTotals.fluidAmount()
        );
    }

    private static ConnectorTotals captureConnector(
            DockingConnectorBlockEntity connector,
            List<String> itemState,
            List<String> fluidState,
            List<ItemStack> itemStacks,
            List<FluidStack> fluidStacks
    ) {
        ItemStack stack = connector.inventory.getItem(0).copy();
        itemStacks.add(stack.copy());
        itemState.add(itemKey(stack));
        Tuple<CFluidType, Long> tankState = connector.tank.createSnapshot();
        CFluidType fluidType = tankState.getA();
        long amount = Math.max(0L, tankState.getB());
        fluidStacks.add(new FluidStack(fluidType.fluid, (int) Math.min(Integer.MAX_VALUE, amount)));
        fluidState.add(BuiltInRegistries.FLUID.getKey(fluidType.fluid)
                + "|"
                + amount
                + "|"
                + fluidType.write());
        return new ConnectorTotals(stack.getCount(), amount);
    }

    public int itemAmount(Level level, ItemStack filter, boolean stacks) {
        FilterItemStack filterStack = FilterItemStack.of(filter);
        int amount = 0;
        for (ItemStack stack : itemStacks) {
            if (!filterStack.test(level, stack)) {
                continue;
            }
            amount += stacks
                    ? stack.getCount() == stack.getMaxStackSize() ? 1 : 0
                    : stack.getCount();
        }
        return amount;
    }

    public int fluidBuckets(Level level, ItemStack filter) {
        FilterItemStack filterStack = FilterItemStack.of(filter);
        long amount = 0L;
        for (FluidStack fluidStack : fluidStacks) {
            if (!filterStack.test(level, fluidStack)) {
                continue;
            }
            amount += fluidStack.getAmount();
        }
        return (int) Math.min(Integer.MAX_VALUE, amount / 1000L);
    }

    private static String itemKey(ItemStack stack) {
        if (stack.isEmpty()) {
            return "empty";
        }
        return BuiltInRegistries.ITEM.getKey(stack.getItem())
                + "|"
                + stack.getCount()
                + "|"
                + stack.getComponents();
    }

    private record ConnectorTotals(int itemCount, long fluidAmount) {
    }
}
