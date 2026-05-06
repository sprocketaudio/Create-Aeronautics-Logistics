package net.sprocketgames.create_aeronautics_automated_logistics.route;

public enum WaitDurationUnit {
    TICKS(1),
    SECONDS(20),
    MINUTES(20 * 60);

    private final int ticksPerStep;

    WaitDurationUnit(int ticksPerStep) {
        this.ticksPerStep = ticksPerStep;
    }

    public int ticksPerStep() {
        return ticksPerStep;
    }

    public WaitDurationUnit next() {
        WaitDurationUnit[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
