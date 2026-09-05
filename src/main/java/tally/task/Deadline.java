package tally.task;

import java.time.LocalDate;

/** A task that has to be finished before a stated date. */
public class Deadline extends Task {
    protected LocalDate dueDate;

    /**
     * Creates a deadline that is not done yet.
     *
     * @param description what has to be done.
     * @param dueDate the date it has to be done by.
     */
    public Deadline(String description, LocalDate dueDate) {
        super(description);
        this.dueDate = dueDate;
    }

    /**
     * Returns this deadline as the user sees it, tagged with its type and due date.
     *
     * @return for example "[D][ ] return book (by: Oct 15 2019)".
     */
    @Override
    public String toString() {
        return String.format("[D]%s (by: %s)", super.toString(), dueDate.format(DISPLAY_FORMAT));
    }

    /**
     * Returns the line standing for this deadline in the data file.
     *
     * <p>The date is written the way LocalDate prints itself, which is the same
     * yyyy-mm-dd form that LocalDate.parse reads, so it survives a round trip.
     *
     * @return for example "D | 0 | return book | 2019-10-15".
     */
    @Override
    public String toSaveFormat() {
        return "D | " + super.toSaveFormat() + " | " + dueDate;
    }
}
