/** A task with nothing attached to it but a description: no date, no time. */
public class Todo extends Task {
    /**
     * Creates a todo that is not done yet.
     *
     * @param description what the user wants to be reminded to do.
     */
    public Todo(String description) {
        super(description);
    }

    /**
     * Returns this todo as the user sees it, tagged with its type.
     *
     * @return for example "[T][ ] borrow book".
     */
    @Override
    public String toString() {
        return "[T]" + super.toString();
    }
}
