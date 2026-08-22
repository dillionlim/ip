package tally.task;

/** A task that runs from one stated point in time to another. */
public class Event extends Task {
    protected String from;
    protected String to;

    /**
     * Creates an event that is not done yet.
     *
     * @param description what is happening.
     * @param from when it starts, kept exactly as the user typed it.
     * @param to when it ends, kept exactly as the user typed it.
     */
    public Event(String description, String from, String to) {
        super(description);
        this.from = from;
        this.to = to;
    }

    /**
     * Returns this event as the user sees it, tagged with its type and its span.
     *
     * @return for example "[E][ ] project meeting (from: Mon 2pm to: 4pm)".
     */
    @Override
    public String toString() {
        return String.format("[E]%s (from: %s to: %s)", super.toString(), from, to);
    }

    /**
     * Returns the line standing for this event in the data file.
     *
     * @return for example "E | 0 | project meeting | Mon 2pm | 4pm".
     */
    @Override
    public String toSaveFormat() {
        return "E | " + super.toSaveFormat() + " | " + from + " | " + to;
    }
}
