package tally.task;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** The tasks on the user's tally, and the things that can be done to them. */
public class TaskList {
    /** How far ahead a free-day search looks before giving up, in days. */
    private static final int SEARCH_HORIZON_DAYS = 366;

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
     * Adds tasks to the end of the tally, in the order given.
     *
     * <p>Takes any number of them so that adding one reads no differently from
     * adding several, which is what the tests and the file loader both want.
     *
     * @param tasksToAdd the tasks to add.
     */
    public void add(Task... tasksToAdd) {
        for (Task task : tasksToAdd) {
            assert task != null
                    : "Storage.load throws rather than yield a null task, and the parser builds"
                    + " every other one, so a null would surface only later as a failed save";
        }
        Collections.addAll(tasks, tasksToAdd);
    }

    /**
     * Returns the first day of the earliest run of free days of the length asked for.
     *
     * <p>A day is free when no task on the tally takes it up. The search runs a year
     * ahead and no further, so that a tally with something on every day answers
     * rather than running on.
     *
     * @param days how many free days in a row are wanted, at least one.
     * @param from the first day that may be offered.
     * @return the first day of the earliest such run, or empty if there is none.
     */
    public Optional<LocalDate> findFreeRun(int days, LocalDate from) {
        assert days >= 1 : "Parser.parseFreeQuery refuses a run shorter than a day: " + days;
        Set<LocalDate> takenDays = tasks.stream()
                .flatMap(task -> task.occupiedDates().stream())
                .collect(Collectors.toSet());
        int freeInARow = 0;
        for (LocalDate day = from; day.isBefore(from.plusDays(SEARCH_HORIZON_DAYS)); day = day.plusDays(1)) {
            freeInARow = takenDays.contains(day) ? 0 : freeInARow + 1;
            if (freeInARow == days) {
                return Optional.of(day.minusDays(days - 1L));
            }
        }
        return Optional.empty();
    }

    /**
     * Returns whether any task names days that could not be read as dates.
     *
     * @return true when the free-day search saw less than the whole tally.
     */
    public boolean hasUnreadableDates() {
        return tasks.stream().anyMatch(Task::hasUnreadableDates);
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
        assert index >= 0 && index < tasks.size()
                : "Parser.parseTaskIndex turns what the user typed into a position only after"
                + " rejecting out-of-range numbers, so an invalid index here means a caller"
                + " reached past it: " + index;
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
     * Returns the positions of the tasks whose description contains the given word.
     *
     * <p>Positions count from 0, and are the tasks' places on the whole tally
     * rather than places among the matches, so a number shown to the user still
     * names the same task in a later command.
     *
     * @param word the text to look for, matched without regard to case.
     * @return the positions of the matching tasks, in the order they were added.
     */
    public List<Integer> findPositions(String word) {
        String lowercaseWord = word.toLowerCase();
        return IntStream.range(0, tasks.size())
                .filter(i -> tasks.get(i).getDescription().toLowerCase().contains(lowercaseWord))
                .boxed()
                .toList();
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
