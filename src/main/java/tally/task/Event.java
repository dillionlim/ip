package tally.task;

import java.time.LocalDate;

/**
 * A task that runs from one day to another.
 *
 * <p>Both ends are dates, as a deadline's day is. An event and a window cover the same
 * span of days between them; what differs is what the span means. An event is happening
 * on every one of those days, so none of them can be offered as free, while a window is
 * a period the work may be done within.
 */
public class Event extends Task {
    /** The letter standing for this kind of task in the data file. */
    public static final String TYPE = "E";

    /** The day the event starts. */
    private final LocalDate startDate;

    /** The day it ends, never before {@link #startDate}. */
    private final LocalDate endDate;

    /**
     * Creates an event that is not done yet.
     *
     * @param description what is happening.
     * @param startDate the day it starts.
     * @param endDate the day it ends, on or after startDate.
     */
    public Event(String description, LocalDate startDate, LocalDate endDate) {
        super(description);
        assert !endDate.isBefore(startDate)
                : "Parser.parseEvent and TaskLine.readEvent both refuse an event that ends"
                + " before it starts, so one reaching here came from neither: "
                + startDate + " to " + endDate;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    /**
     * Returns whether this event takes up a given day.
     *
     * @param day the day being considered.
     * @return true when the day falls between the two ends, both included.
     */
    @Override
    public boolean occupies(LocalDate day) {
        return !day.isBefore(startDate) && !day.isAfter(endDate);
    }

    /**
     * Returns this event as the user sees it, tagged with its type and its span.
     *
     * @return for example "[E][ ] project meeting (from: Aug 06 2026 to: Aug 06 2026)".
     */
    @Override
    public String toString() {
        return String.format("[%s]%s (from: %s to: %s)", TYPE, super.toString(),
                formatDate(startDate), formatDate(endDate));
    }

    /**
     * Returns the line standing for this event in the data file.
     *
     * <p>Both dates are written the way LocalDate prints itself, which is the same
     * yyyy-mm-dd form LocalDate.parse reads, so they survive a round trip.
     *
     * @return for example "E | 0 | project meeting | 2026-08-06 | 2026-08-06".
     */
    @Override
    public String toSaveFormat() {
        return TYPE + FIELD_SEPARATOR + toSharedSaveFields() + FIELD_SEPARATOR + startDate + FIELD_SEPARATOR + endDate;
    }
}
