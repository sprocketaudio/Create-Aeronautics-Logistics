package net.sprocketgames.create_aeronautics_automated_logistics.dock;

import java.util.Optional;
import net.minecraft.core.BlockPos;

public record DockDiscoveryResult(DockLinkStatus status, Optional<BlockPos> dockPos, int candidates) {
    public static DockDiscoveryResult linked(BlockPos dockPos) {
        return new DockDiscoveryResult(DockLinkStatus.LINKED, Optional.of(dockPos), 1);
    }

    public static DockDiscoveryResult missing() {
        return new DockDiscoveryResult(DockLinkStatus.MISSING, Optional.empty(), 0);
    }

    public static DockDiscoveryResult ambiguous(int candidates) {
        return new DockDiscoveryResult(DockLinkStatus.AMBIGUOUS, Optional.empty(), candidates);
    }

    public static DockDiscoveryResult invalid() {
        return new DockDiscoveryResult(DockLinkStatus.INVALID, Optional.empty(), 0);
    }
}
