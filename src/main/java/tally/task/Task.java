package tally.task;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** A single entry on the user's tally: what has to be done, and whether it is done yet. */
public class Task {
    /**
     * How a date carried by a task is shown to the user.
     *
     * <p>Level-8 asks that dates be read in one format and printed in another, so this
     * differs from the yyyy-mm-dd form accepted from the user and kept in the data file.
     * It lives here rather than in one subclass because both Deadline and Window show
     * dates, and they should not drift apart. The locale is fixed so the month name does
     * not depend on the machine the chatbot runs on.
     */
    protected static final DateTimeFormatter DISPLAY_FORMAT =
            DateTimeFormatter.ofPattern("MMM dd yyyy", Locale.ENGLISH);

    protected String description;
    protected boolean isDone;

    /**
     * Creates a task that is not done yet.
     *
     * @param description what the user wants to be reminded to do.
     */
    public Task(String description) {
        this.description = description;
        this.isDone = false;
    }

    /**
     * Returns what the task says has to be done.
     *
     * @return the description, without the checkbox or any times.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Returns the mark shown inside the task's checkbox.
     *
     * @return "X" when the task is done, a single space otherwise.
     */
    public String getStatusIcon() {
        return isDone ? "X" : " ";
    }

    /** Records that this task has been done. */
    public void markAsDone() {
        this.isDone = true;
    }

    /** Records that this task is not done after all. */
    public void markAsNotDone() {
        this.isDone = false;
    }

    /**
     * Returns this task as the user sees it: a checkbox followed by the description.
     *
     * @return for example "[X] read book".
     */
    @Override
    public String toString() {
        return String.format("[%s] %s", getStatusIcon(), description);
    }

    /**
     * Returns the part of this task's data-file line that every task shares.
     *
     * <p>Subclasses prefix their type letter and append whatever times they carry,
     * mirroring the way toString is built up.
     *
     * @return for example "1 | read book".
     */
    public String toSaveFormat() {
        return String.format("%d | %s", isDone ? 1 : 0, description);
    }
}
