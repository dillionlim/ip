package tally.task;

import java.time.LocalDate;
import java.util.Optional;

/**
 * A task that runs from one stated point in time to another.
 *
 * <p>Its two ends are kept as the user wrote them, give or take the spacing, where a
 * deadline insists on a date.
 * The requirements ask that dates be handled but do not say every time must be one, and
 * an event is as often written "Mon 2pm" as a date, so insisting would refuse input the
 * user has every reason to expect to work. The cost is that such an event names no days
 * and so cannot be counted by the free-day search, which says so when it happens.
 */
public class Event extends Task {
    /** The letter standing for this kind of task in the data file. */
    public static final String TYPE = "E";

    /** When the event starts, as written rather than parsed, apart from spacing. */
    private final String start;

    /** When the event ends, in the same form as {@link #start}. */
    private final String end;

    /** The start read as a date, or empty when it was not written as one. */
    private final Optional<LocalDate> startDay;

    /** The end read as a date, or empty when it was not written as one. */
    private final Optional<LocalDate> endDay;

    /**
     * Creates an event that is not done yet.
     *
     * <p>Ends that both read as dates must be in order. Ends written as anything else
     * are taken as they come, since nothing here can compare them.
     *
     * @param description what is happening.
     * @param start when it starts, kept as written apart from its spacing.
     * @param end when it ends, kept as written apart from its spacing.
     */
    public Event(String description, String start, String end) {
        super(description);
        this.start = tidySpacing(start);
        this.end = tidySpacing(end);
        // Read once here rather than each time a day is asked about, since the free-day
        // search asks every task about every day of a year.
        this.startDay = readDate(this.start);
        this.endDay = readDate(this.end);
        assert !hasBackwardsDates(start, end)
                : "Parser.parseEvent and Storage refuse an event whose dated ends run"
                + " backwards, so one arriving here came from neither: " + start + " to " + end;
    }

    /**
     * Returns whether two ends are both dates, the later one written first.
     *
     * <p>Only a pair that both read as dates can be compared at all. "Mon 2pm" to "4pm"
     * may well be back to front, and nothing here can tell.
     *
     * <p>Takes the ends as written, since reading a date tidies the text first. That
     * is what lets everyone asking the question get the same answer as the event
     * would: asking it of the untidied text while the event read the tidied text once
     * let a padded pair past every guard and into an assertion that stopped the
     * program.
     *
     * @param start when it starts, as written.
     * @param end when it ends, as written.
     * @return true when both read as dates and the end falls before the start.
     */
    public static boolean hasBackwardsDates(String start, String end) {
        Optional<LocalDate> from = readDate(start);
        Optional<LocalDate> to = readDate(end);
        return from.isPresent() && to.isPresent() && to.get().isBefore(from.get());
    }

    /**
     * Returns whether this event takes up a given day, which none of them are unless
     * its ends were written as dates.
     *
     * <p>An event keeps its ends as they were written, apart from their spacing, so
     * "Mon 2pm" names no day this can work out. Ends written as yyyy-mm-dd were read
     * when the event was made, which lets an event join the free-day search without
     * changing what is stored for it.
     *
     * @param day the day being considered.
     * @return true when the day falls within the two ends, both included.
     */
    @Override
    public boolean occupies(LocalDate day) {
        if (hasUnreadableDates()) {
            return false;
        }
        return !day.isBefore(startDay.get()) && !day.isAfter(endDay.get());
    }

    /**
     * Returns whether this event's ends were written as something other than dates.
     *
     * @return true when either end is text such as "Mon 2pm" rather than a date.
     */
    @Override
    public boolean hasUnreadableDates() {
        return startDay.isEmpty() || endDay.isEmpty();
    }

    /**
     * Returns this event as the user sees it, tagged with its type and its span.
     *
     * @return for example "[E][ ] project meeting (from: Mon 2pm to: 4pm)".
     */
    @Override
    public String toString() {
        return String.format("[%s]%s (from: %s to: %s)", TYPE, super.toString(), start, end);
    }

    /**
     * Returns the line standing for this event in the data file.
     *
     * @return for example "E | 0 | project meeting | Mon 2pm | 4pm".
     */
    @Override
    public String toSaveFormat() {
        return TYPE + FIELD_SEPARATOR + toSharedSaveFields() + FIELD_SEPARATOR + start + FIELD_SEPARATOR + end;
    }
}
