/**
 * The tasks themselves, and the tally holding them.
 *
 * <p>{@link tally.task.Task} is what every task has in common: a description
 * and whether it is done. {@link tally.task.Todo},
 * {@link tally.task.Deadline} and {@link tally.task.Event} add whatever times
 * their kind carries, each rendering itself for the screen and for the data
 * file. {@link tally.task.TaskList} holds them in the order they were added.
 */
package tally.task;
