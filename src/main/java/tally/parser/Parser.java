package tally.parser;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import tally.TallyException;
import tally.task.Deadline;
import tally.task.Event;
import tally.task.Todo;

/**
 * Turns what the user typed into the things Tally acts on.
 *
 * <p>Every method here reports a line it cannot make sense of as a
 * TallyException carrying a message written for the user, so the caller can
 * show it and carry on rather than deciding what went wrong.
 */
public class Parser {
    /**
     * Returns the command named by the first word of a line.
     *
     * @param line the line the user typed, with surrounding spaces removed.
     * @return the command it names.
     * @throws TallyException if the first word names no command.
     */
    public static Command parseCommand(String line) throws TallyException {
        return Command.parse(line.split(" ", 2)[0]);
    }

    /**
     * Returns whatever follows the command word on a line.
     *
     * @param line the line the user typed, with surrounding spaces removed.
     * @return the arguments, or an empty string if the line is only a command word.
     */
    public static String parseArguments(String line) {
        String[] words = line.split(" ", 2);
        return words.length > 1 ? words[1].trim() : "";
    }

    /**
     * Returns the todo described by a "todo" command's arguments.
     *
     * @param arguments what the user typed after the command word.
     * @return the todo described.
     * @throws TallyException if no description was given.
     */
    public static Todo parseTodo(String arguments) throws TallyException {
        if (arguments.isEmpty()) {
            throw new TallyException("A todo needs a description. Try: todo read book");
        }
        return new Todo(arguments);
    }

    /**
     * Returns the deadline described by a "deadline" command's arguments.
     *
     * @param arguments what the user typed after the command word.
     * @return the deadline described.
     * @throws TallyException if the description or the date is missing or unreadable.
     */
    public static Deadline parseDeadline(String arguments) throws TallyException {
        String[] fields = arguments.split(" /by ", 2);
        if (fields.length < 2 || fields[0].isBlank() || fields[1].isBlank()) {
            throw new TallyException(
                    "A deadline needs a description and a /by date."
                            + " Try: deadline return book /by 2019-10-15");
        }
        return new Deadline(fields[0].trim(), parseDate(fields[1].trim()));
    }

    /**
     * Returns the event described by an "event" command's arguments.
     *
     * <p>The arguments are split on /from first, and on /to within what follows.
     * Splitting in that order is what makes the order of the two markers matter:
     * an event written /to first leaves no /to in the remainder, so it is refused
     * rather than recorded with its start and end the wrong way round.
     *
     * @param arguments what the user typed after the command word.
     * @return the event described.
     * @throws TallyException if the description, the start or the end is missing,
     *     or if /to is written before /from.
     */
    public static Event parseEvent(String arguments) throws TallyException {
        // AI found the bug, manually fixed.
        String usage = "An event needs a description, a /from time and a /to time,"
                + " in that order. Try: event project meeting /from Mon 2pm /to 4pm";
        String[] descriptionAndRest = arguments.split(" /from ", 2);
        if (descriptionAndRest.length < 2) {
            throw new TallyException(usage);
        }
        String[] fromAndTo = descriptionAndRest[1].split(" /to ", 2);
        if (fromAndTo.length < 2 || descriptionAndRest[0].isBlank()
                || fromAndTo[0].isBlank() || fromAndTo[1].isBlank()) {
            throw new TallyException(usage);
        }
        return new Event(descriptionAndRest[0].trim(), fromAndTo[0].trim(), fromAndTo[1].trim());
    }

    /**
     * Returns the word a "find" command is searching for.
     *
     * @param arguments what the user typed after the command word.
     * @return the word to look for.
     * @throws TallyException if nothing was given to search for.
     */
    public static String parseSearchWord(String arguments) throws TallyException {
        if (arguments.isEmpty()) {
            throw new TallyException("find needs something to look for. Try: find book");
        }
        return arguments;
    }

    /**
     * Returns the date named by the text the user typed after /by.
     *
     * <p>Dates are read in the yyyy-mm-dd form that LocalDate understands without a
     * formatter, and shown back in a different form, as Level-8 requires.
     *
     * @param text what the user typed as the date.
     * @return the date it names.
     * @throws TallyException if the text is not a date written as yyyy-mm-dd.
     */
    public static LocalDate parseDate(String text) throws TallyException {
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException exception) {
            throw new TallyException(String.format(
                    "I could not read \"%s\" as a date. Write it as yyyy-mm-dd."
                            + " Try: deadline return book /by 2019-10-15", text));
        }
    }

    /**
     * Returns the position a command such as "mark 2" names, counting from 0.
     *
     * <p>The number the user types counts from 1, while the list is indexed from 0.
     * Doing the conversion here keeps it out of every command that names a task.
     *
     * @param arguments what the user typed after the command word.
     * @param size how many tasks the tally holds.
     * @param command the command that named the task, used to word the error.
     * @return the index of the task the command refers to.
     * @throws TallyException if no number was given, it is not a number, or no task
     *     has that position.
     */
    public static int parseTaskIndex(String arguments, int size, Command command)
            throws TallyException {
        int position;
        try {
            position = Integer.parseInt(arguments);
        } catch (NumberFormatException exception) {
            throw new TallyException(String.format(
                    "%s needs the number of a task. Try: %s 2",
                    command.getKeyword(), command.getKeyword()));
        }
        if (position < 1 || position > size) {
            throw new TallyException(String.format(
                    "There is no task %d on your tally. Type list to see what is there.",
                    position));
        }
        return position - 1;
    }
}
