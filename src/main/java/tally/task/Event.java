package tally.task;

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
