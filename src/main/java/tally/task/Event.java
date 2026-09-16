package tally.task;

import java.time.LocalDate;

/**
 * A task that runs from one moment to another.
 *
 * <p>Both ends are days, either of which may carry an hour. An event and a window cover
 * the same span of days between them; what differs is what the span means. An event is
 * happening on every one of those days, so none of them can be offered as free, while a
 * window is a period the work may be done within.
 */
public class Event extends Task {
    /** The letter standing for this kind of task in the data file. */
    public static final String TYPE = "E";

    /** When the event starts. */
    private final Moment start;

    /** When it ends, never before {@link #start}. */
    private final Moment end;

    /**
     * Creates an event that is not done yet.
     *
     * @param description what is happening.
     * @param start when it starts.
     * @param end when it ends, at or after the start.
     */
    public Event(String description, Moment start, Moment end) {
        super(description);
        assert !hasBackwardsEnds(start, end)
                : "Parser.parseEvent and TaskLine.readEvent both refuse an event that ends"
                + " before it starts, so one reaching here came from neither: "
                + start + " to " + end;
        this.start = start;
        this.end = end;
    }

    /**
     * Returns whether an event running between two moments would end before it started.
     *
     * <p>An end given without an hour runs to the close of its day, so "the 12th at four
     * to the 12th" is a half-day event rather than a contradiction. The comparison lives
     * here so that the parser, the data file's reader and this class all answer it the
     * same way.
     *
     * @param start when it would start.
     * @param end when it would end.
     * @return true when the end falls before the start.
     */
    public static boolean hasBackwardsEnds(Moment start, Moment end) {
        return end.asEnd().isBefore(start.asStart());
    }

    /**
     * Returns whether this event takes up a given day.
     *
     * <p>A day an event touches at all is a day it takes up: an hour of it is enough to
     * make the day something other than free.
     *
     * @param day the day being considered.
     * @return true when the day falls between the two ends, both included.
     */
    @Override
    public boolean occupies(LocalDate day) {
        return !day.isBefore(start.date()) && !day.isAfter(end.date());
    }

    /**
     * Returns this event as the user sees it, tagged with its type and its span.
     *
     * @return for example "[E][ ] lecture (from: Sep 12 2026 4:00 pm to: Sep 12 2026
     *     6:00 pm)".
     */
    @Override
    public String toString() {
        return String.format("[%s]%s (from: %s to: %s)", TYPE, super.toString(), start, end);
    }

    /**
     * Returns the line standing for this event in the data file.
     *
     * @return for example "E | 0 | lecture | 2026-09-12 16:00 | 2026-09-12 18:00".
     */
    @Override
    public String toSaveFormat() {
        return TYPE + FIELD_SEPARATOR + toSharedSaveFields()
                + FIELD_SEPARATOR + start.toSaveFormat() + FIELD_SEPARATOR + end.toSaveFormat();
    }
}
