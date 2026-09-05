package tally.task;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.List;

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
     * Returns the days this event takes up, which is none unless its ends are dates.
     *
     * <p>An event keeps its ends as the user typed them, so "Mon 2pm" names no day
     * this can work out. Ends written as yyyy-mm-dd are read here, which lets an
     * event join the free-day search without changing what is stored for it or
     * making an older data file unreadable.
     *
     * @return the days from the start to the end, or an empty list if either end
     *     is not a date.
     */
    @Override
    public List<LocalDate> occupiedDates() {
        try {
            LocalDate startDate = LocalDate.parse(start);
            LocalDate endDate = LocalDate.parse(end);
            // Written the other way round they still name the same stretch of days.
            LocalDate first = endDate.isBefore(startDate) ? endDate : startDate;
            LocalDate last = endDate.isBefore(startDate) ? startDate : endDate;
            return first.datesUntil(last.plusDays(1)).toList();
        } catch (DateTimeException exception) {
            return List.of();
        }
    }

    /**
     * Returns whether this event's ends were written as something other than dates.
     *
     * @return true when the ends are text such as "Mon 2pm" rather than dates.
     */
    @Override
    public boolean hasUnreadableDates() {
        // Ends the wrong way round still name a stretch of days, so an empty answer from
        // occupiedDates now means only that the ends could not be used as dates at all.
        return occupiedDates().isEmpty();
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
