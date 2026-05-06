package net.sprocketgames.create_aeronautics_automated_logistics.route;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record AirshipScheduleEntry(
        AirshipScheduleEntryType type,
        Optional<UUID> targetStationId,
        String targetStationName,
        WaitCondition waitCondition,
        WaitDurationUnit waitUnit,
        Optional<RouteSegmentId> pinnedSegmentId,
        List<List<AirshipScheduleCondition>> conditionGroups
) {
    public AirshipScheduleEntry {
        Objects.requireNonNull(type, "type");
        targetStationId = Objects.requireNonNull(targetStationId, "targetStationId");
        targetStationName = Objects.requireNonNull(targetStationName, "targetStationName");
        Objects.requireNonNull(waitCondition, "waitCondition");
        Objects.requireNonNull(waitUnit, "waitUnit");
        pinnedSegmentId = Objects.requireNonNull(pinnedSegmentId, "pinnedSegmentId");
        conditionGroups = copyConditionGroups(conditionGroups);
    }

    public static AirshipScheduleEntry blankTravel() {
        return new AirshipScheduleEntry(
                AirshipScheduleEntryType.TRAVEL_TO_STATION,
                Optional.empty(),
                "",
                WaitCondition.timed(WaitCondition.DEFAULT_TIMED_WAIT_TICKS),
                WaitDurationUnit.SECONDS,
                Optional.empty(),
                List.of(List.of(AirshipScheduleCondition.scheduledDelay(WaitCondition.timed(WaitCondition.DEFAULT_TIMED_WAIT_TICKS))))
        );
    }

    public String displayStationName() {
        return targetStationName == null || targetStationName.isBlank() ? "Unset Station" : targetStationName;
    }

    public AirshipScheduleEntry withWaitCondition(WaitCondition waitCondition) {
        List<List<AirshipScheduleCondition>> groups = conditionGroups.isEmpty()
                ? List.of(List.of(AirshipScheduleCondition.scheduledDelay(waitCondition)))
                : replaceFirstScheduledDelay(waitCondition);
        return new AirshipScheduleEntry(type, targetStationId, targetStationName, waitCondition, waitUnit, pinnedSegmentId, groups);
    }

    public AirshipScheduleEntry withTargetStation(UUID stationId, String stationName) {
        return new AirshipScheduleEntry(type, Optional.of(stationId), stationName, waitCondition, waitUnit, Optional.empty(), conditionGroups);
    }

    public AirshipScheduleEntry withWaitUnit(WaitDurationUnit waitUnit) {
        return new AirshipScheduleEntry(type, targetStationId, targetStationName, waitCondition, waitUnit, pinnedSegmentId, conditionGroups);
    }

    public AirshipScheduleEntry withPinnedSegment(Optional<RouteSegmentId> pinnedSegmentId) {
        return new AirshipScheduleEntry(type, targetStationId, targetStationName, waitCondition, waitUnit, pinnedSegmentId, conditionGroups);
    }

    public AirshipScheduleEntry withConditionGroups(List<List<AirshipScheduleCondition>> conditionGroups) {
        return new AirshipScheduleEntry(type, targetStationId, targetStationName, waitCondition, waitUnit, pinnedSegmentId, conditionGroups);
    }

    public AirshipScheduleEntry withAddedCondition() {
        List<List<AirshipScheduleCondition>> groups = mutableConditionGroups();
        if (groups.isEmpty()) {
            groups.add(new ArrayList<>());
        }
        groups.get(0).add(AirshipScheduleCondition.scheduledDelay(waitCondition));
        return withConditionGroups(groups);
    }

    public AirshipScheduleEntry withAddedAlternativeConditionGroup() {
        List<List<AirshipScheduleCondition>> groups = mutableConditionGroups();
        groups.add(new ArrayList<>(List.of(AirshipScheduleCondition.scheduledDelay(waitCondition))));
        return withConditionGroups(groups);
    }

    private List<List<AirshipScheduleCondition>> replaceFirstScheduledDelay(WaitCondition waitCondition) {
        List<List<AirshipScheduleCondition>> groups = mutableConditionGroups();
        for (List<AirshipScheduleCondition> group : groups) {
            for (int i = 0; i < group.size(); i++) {
                if (group.get(i).type() == AirshipScheduleConditionType.SCHEDULED_DELAY) {
                    group.set(i, AirshipScheduleCondition.scheduledDelay(waitCondition));
                    return groups;
                }
            }
        }
        groups.get(0).add(AirshipScheduleCondition.scheduledDelay(waitCondition));
        return groups;
    }

    private List<List<AirshipScheduleCondition>> mutableConditionGroups() {
        List<List<AirshipScheduleCondition>> groups = new ArrayList<>();
        for (List<AirshipScheduleCondition> group : conditionGroups) {
            groups.add(new ArrayList<>(group));
        }
        return groups;
    }

    private static List<List<AirshipScheduleCondition>> copyConditionGroups(List<List<AirshipScheduleCondition>> conditionGroups) {
        Objects.requireNonNull(conditionGroups, "conditionGroups");
        List<List<AirshipScheduleCondition>> copied = new ArrayList<>();
        for (List<AirshipScheduleCondition> group : conditionGroups) {
            copied.add(List.copyOf(Objects.requireNonNull(group, "group")));
        }
        return List.copyOf(copied);
    }
}
