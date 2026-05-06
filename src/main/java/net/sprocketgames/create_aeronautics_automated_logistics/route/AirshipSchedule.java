package net.sprocketgames.create_aeronautics_automated_logistics.route;

import java.util.List;
import java.util.Objects;

public record AirshipSchedule(
        String title,
        boolean loop,
        List<AirshipScheduleEntry> entries
) {
    public AirshipSchedule {
        title = title == null || title.isBlank() ? "Airship Schedule" : title;
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
    }

    public static AirshipSchedule empty() {
        return new AirshipSchedule("Airship Schedule", true, List.of());
    }

    public AirshipSchedule withEntries(List<AirshipScheduleEntry> entries) {
        return new AirshipSchedule(title, loop, entries);
    }

    public AirshipSchedule withLoop(boolean loop) {
        return new AirshipSchedule(title, loop, entries);
    }

    public AirshipSchedule withTitle(String title) {
        return new AirshipSchedule(title, loop, entries);
    }
}
