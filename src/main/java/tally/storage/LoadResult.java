package tally.storage;

import java.util.List;
import java.util.Optional;

import tally.task.Task;

/**
 * What reading the data file produced.
 *
 * <p>A line the file cannot offer as a task is skipped rather than costing the user
 * every other task on the tally, so a read can both succeed and have something to
 * report. The note carries that report when there is one.
 *
 * @param tasks the tasks the file held, in the order they were written.
 * @param note what could not be read, or empty when every line was understood.
 */
public record LoadResult(List<Task> tasks, Optional<String> note) {
}
