/** A task that has to be finished before a stated point in time. */
public class Deadline extends Task {
    protected String by;

    /**
     * Creates a deadline that is not done yet.
     *
     * @param description what has to be done.
     * @param by when it has to be done by, kept exactly as the user typed it.
     */
    public Deadline(String description, String by) {
        super(description);
        this.by = by;
    }

    /**
     * Returns this deadline as the user sees it, tagged with its type and due time.
     *
     * @return for example "[D][ ] return book (by: Sunday)".
     */
    @Override
    public String toString() {
        return String.format("[D]%s (by: %s)", super.toString(), by);
    }
}
