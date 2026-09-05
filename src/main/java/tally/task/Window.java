package tally.task;

import java.time.LocalDate;

/** A task that may be done on any day within a stated period. */
public class Window extends Task {
    /** The first day on which the task may be done. */
    protected LocalDate startDate;

    /** The last day on which the task may be done, never before {@link #startDate}. */
    protected LocalDate endDate;

    /**
     * Creates a window task that is not done yet.
     *
     * @param description what has to be done.
     * @param startDate the first day it may be done.
     * @param endDate the last day it may be done, on or after startDate.
     */
    public Window(String description, LocalDate startDate, LocalDate endDate) {
        super(description);
        this.startDate = startDate;
        this.endDate = endDate;
    }

    /**
     * Returns whether this window takes up a given day.
     *
     * <p>A window claims every day of its period rather than a single day, because the
     * work may fall on any of them and none can be promised free.
     *
     * @param day the day being considered.
     * @return true when the day falls between the two ends, both included.
     */
    @Override
    public boolean occupies(LocalDate day) {
        return !day.isBefore(startDate) && !day.isAfter(endDate);
    }

    /**
     * Returns this window as the user sees it, tagged with its type and its two ends.
     *
     * @return for example "[W][ ] submit form (window: Sep 08 2026 to Sep 12 2026)".
     */
    @Override
    public String toString() {
        return String.format("[W]%s (window: %s to %s)", super.toString(),
                showDate(startDate), showDate(endDate));
    }

    /**
     * Returns the line standing for this window in the data file.
     *
     * <p>Both dates are written the way LocalDate prints itself, which is the same
     * yyyy-mm-dd form LocalDate.parse reads, so they survive a round trip.
     *
     * @return for example "W | 0 | submit form | 2026-09-08 | 2026-09-12".
     */
    @Override
    public String toSaveFormat() {
        return "W | " + super.toSaveFormat() + " | " + startDate + " | " + endDate;
    }
}
