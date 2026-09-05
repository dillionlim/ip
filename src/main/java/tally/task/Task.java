package tally.task;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A single entry on the user's tally: what has to be done, and whether it is done yet.
 *
 * <p>Abstract because the part of a data-file line written here is only the part every
 * kind shares. A task of no particular kind could not be written as a line the reader
 * would take back, so there is no such thing to make.
 */
public abstract class Task {
    /** How the data file separates one part of a task from the next. */
    public static final String FIELD_SEPARATOR = " | ";

    /** What the done field of a data-file line holds for a task that is done. */
    public static final String DONE = "1";

    /** What that same field holds for a task that is not done. */
    public static final String NOT_DONE = "0";

    /**
     * The last day a date may name, being the last one the written form can express.
     *
     * <p>Dates are written as yyyy-mm-dd wherever they appear, so a later day would be
     * shown with five digits of year, which the user could read but could not type back
     * in, leaving them an answer they cannot act on.
     */
    static final LocalDate LAST_DATE = LocalDate.of(9999, 12, 31);

    /**
     * How a date carried by a task is shown to the user.
     *
     * <p>Level-8 asks that dates be read in one format and printed in another, so this
     * differs from the yyyy-mm-dd form accepted from the user and kept in the data file.
     * It lives here rather than in one subclass because both Deadline and Window show
     * dates, and they should not drift apart. The locale is fixed so the month name does
     * not depend on the machine the chatbot runs on. The year is uuuu rather than yyyy,
     * since yyyy counts within an era and would print year zero as the year one.
     */
    private static final DateTimeFormatter DISPLAY_FORMAT =
            DateTimeFormatter.ofPattern("MMM dd uuuu", Locale.ENGLISH);

    /**
     * The one shape a date may be written in, wherever it comes from.
     *
     * <p>LocalDate.parse also reads forms such as "+999999999-12-31", which nobody means
     * to type and which cannot be worked with afterwards without overflowing.
     */
    private static final Pattern DATE_FORM = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    private final String description;
    private boolean isDone;

    /**
     * Creates a task that is not done yet.
     *
     * @param description what the user wants to be reminded to do.
     */
    protected Task(String description) {
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

    /**
     * Returns the date some text names, if it names one at all.
     *
     * @param text the text to read.
     * @return the date, or empty if the text is not a date written as yyyy-mm-dd.
     */
    public static Optional<LocalDate> readDate(String text) {
        if (!DATE_FORM.matcher(text).matches()) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.parse(text));
        } catch (DateTimeParseException exception) {
            return Optional.empty();
        }
    }

    /**
     * Returns a date as the user should see it.
     *
     * @param date the date to show.
     * @return for example "Sep 08 2026".
     */
    public static String formatDate(LocalDate date) {
        return date.format(DISPLAY_FORMAT);
    }

    /**
     * Returns whether this task takes up a given day, which no task does unless it
     * names days at all.
     *
     * <p>Each kind of task answers for itself rather than having a caller ask what kind
     * it is, so a new kind joins the free-day search by overriding this alone. Asking
     * about one day at a time also means a task spanning centuries costs nothing to
     * ask about, where listing its days would not fit in memory.
     *
     * @param day the day being considered.
     * @return true when this task takes up that day.
     */
    public boolean occupies(LocalDate day) {
        return false;
    }

    /**
     * Returns whether this task names days that could not be read as dates.
     *
     * <p>A task naming no days at all is not unreadable; this asks only about days
     * meant to be there, so that a search over days can say when its answer is
     * built on less than the whole tally.
     *
     * @return true when this task means to name days but they could not be read.
     */
    public boolean hasUnreadableDates() {
        return false;
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
     * Returns the line the data file records this task as.
     *
     * <p>Left to the subclasses rather than assembled here, because only they know
     * their type letter and the times they carry. Inheriting a half-written line would
     * let a new kind of task be saved as one the reader then refuses to take back.
     *
     * @return for example "D | 1 | return book | 2019-06-06".
     */
    public abstract String toSaveFormat();

    /**
     * Returns the parts of a data-file line that every task shares.
     *
     * <p>Subclasses prefix their type letter and append whatever times they carry,
     * mirroring the way toString is built up.
     *
     * @return for example "1 | read book".
     */
    protected final String toSharedSaveFields() {
        return (isDone ? DONE : NOT_DONE) + FIELD_SEPARATOR + description;
    }
}
