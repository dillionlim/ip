package tally.task;

import java.util.ArrayList;
import java.util.List;

/** The tasks on the user's tally, and the things that can be done to them. */
public class TaskList {
    private final List<Task> tasks;

    /** Creates an empty tally. */
    public TaskList() {
        this.tasks = new ArrayList<>();
    }

    /**
     * Creates a tally holding the given tasks.
     *
     * @param tasks the tasks to start with, in the order they should be listed.
     */
    public TaskList(List<Task> tasks) {
        this.tasks = new ArrayList<>(tasks);
    }

    /**
     * Adds a task to the end of the tally.
     *
     * @param task the task to add.
     */
    public void add(Task task) {
        tasks.add(task);
    }

    /**
     * Removes a task from the tally.
     *
     * @param task the task to remove, which must be one this tally holds.
     */
    public void remove(Task task) {
        tasks.remove(task);
    }

    /**
     * Returns the task at the given position, counting from 0.
     *
     * @param index the position of the task.
     * @return the task there.
     */
    public Task get(int index) {
        return tasks.get(index);
    }

    /**
     * Returns how many tasks the tally holds.
     *
     * @return the number of tasks.
     */
    public int size() {
        return tasks.size();
    }

    /**
     * Returns whether the tally holds no tasks at all.
     *
     * @return true when there is nothing on the tally.
     */
    public boolean isEmpty() {
        return tasks.isEmpty();
    }

    /**
     * Returns the tasks as a plain list, for code that only reads them.
     *
     * <p>The list is a copy, so changing it does not change the tally.
     *
     * @return the tasks, in the order they were added.
     */
    public List<Task> asList() {
        return new ArrayList<>(tasks);
    }
}
