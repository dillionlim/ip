/**
 * Keeping the tally on disk between runs.
 *
 * <p>{@link tally.storage.Storage} owns the layout of the data file: one line
 * per task, its fields separated by " | ". A file it cannot read is moved
 * aside rather than written over, so nothing the user typed is lost to a
 * damaged line.
 */
package tally.storage;
