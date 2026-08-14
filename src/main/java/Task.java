/** A single entry on the user's tally: what has to be done, and whether it is done yet. */
public class Task {
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

    /**
     * Returns this task as the user sees it: a checkbox followed by the description.
     *
     * @return for example "[X] read book".
     */
    @Override
    public String toString() {
        return String.format("[%s] %s", getStatusIcon(), description);
    }
}
