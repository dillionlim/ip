package tally.task;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** A task that has to be finished before a stated date. */
public class Deadline extends Task {
    /**
     * How a due date is shown to the user.
     *
     * <p>Level-8 asks that dates be read in one format and printed in another, so
     * this differs from the yyyy-mm-dd form accepted from the user and kept in the
     * data file. The locale is fixed so the month name does not depend on the
     * machine the chatbot runs on.
     */
    private static final DateTimeFormatter DISPLAY_FORMAT =
            DateTimeFormatter.ofPattern("MMM dd yyyy", Locale.ENGLISH);

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
