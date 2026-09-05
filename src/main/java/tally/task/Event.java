package tally.task;

import java.time.LocalDate;
import java.util.Optional;

/** A task that runs from one stated point in time to another. */
public class Event extends Task {
    /** When the event starts, kept as the user typed it rather than parsed. */
    protected String start;

    /** When the event ends, in the same raw form as {@link #start}. */
    protected String end;

    /**
     * Creates an event that is not done yet.
     *
     * @param description what is happening.
     * @param start when it starts, kept exactly as the user typed it.
     * @param end when it ends, kept exactly as the user typed it.
     */
    public Event(String description, String start, String end) {
        super(description);
        this.start = start;
        this.end = end;
    }

    /**
     * Returns whether this event takes up a given day, which none of them are unless
     * its ends were written as dates.
     *
     * <p>An event keeps its ends as the user typed them, so "Mon 2pm" names no day this
     * can work out. Ends written as yyyy-mm-dd are read here, which lets an event join
     * the free-day search without changing what is stored for it. A pair written the
     * other way round still names the same stretch of days.
     *
     * @param day the day being considered.
     * @return true when the day falls within the two ends, both included.
     */
    @Override
    public boolean occupies(LocalDate day) {
        Optional<LocalDate> startDate = readDate(start);
        Optional<LocalDate> endDate = readDate(end);
        if (startDate.isEmpty() || endDate.isEmpty()) {
            return false;
        }
        LocalDate first = startDate.get().isAfter(endDate.get()) ? endDate.get() : startDate.get();
        LocalDate last = startDate.get().isAfter(endDate.get()) ? startDate.get() : endDate.get();
        return !day.isBefore(first) && !day.isAfter(last);
    }

    /**
     * Returns whether this event's ends were written as something other than dates.
     *
     * @return true when either end is text such as "Mon 2pm" rather than a date.
     */
    @Override
    public boolean hasUnreadableDates() {
        return readDate(start).isEmpty() || readDate(end).isEmpty();
    }

    /**
     * Returns this event as the user sees it, tagged with its type and its span.
     *
     * @return for example "[E][ ] project meeting (from: Mon 2pm to: 4pm)".
     */
    @Override
    public String toString() {
        return String.format("[E]%s (from: %s to: %s)", super.toString(), start, end);
    }

    /**
     * Returns the line standing for this event in the data file.
     *
     * @return for example "E | 0 | project meeting | Mon 2pm | 4pm".
     */
    @Override
    public String toSaveFormat() {
        return "E | " + super.toSaveFormat() + " | " + start + " | " + end;
    }
}
