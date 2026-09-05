package tally.parser;

import java.time.LocalDate;

import tally.TallyException;
import tally.task.Deadline;
import tally.task.Event;
import tally.task.Task;
import tally.task.Todo;
import tally.task.Window;

/**
 * Turns what the user typed into the things Tally acts on.
 *
 * <p>Every method here reports a line it cannot make sense of as a
 * TallyException carrying a message written for the user, so the caller can
 * show it and carry on rather than deciding what went wrong.
 */
public class Parser {
    /**
     * How the data file separates one part of a task from the next.
     *
     * <p>The file has no way to escape it, so text holding it would be written out and
     * read back as a different number of parts. The whole character is refused rather
     * than the separator as written, because the file joins parts with a space either
     * side: a description merely ending in a bar builds the separator on being saved.
     */
    private static final String FIELD_SEPARATOR = "|";

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
        rejectSeparator(arguments);
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
        rejectSeparator(fields[0]);
        return new Deadline(fields[0].trim(), parseDate(fields[1].trim()));
    }

    /**
     * Returns the three parts of a command written with two markers, in marker order.
     *
     * <p>Splitting on the first marker before looking for the second is what makes the
     * order matter: a line writing them the other way round leaves no second marker
     * behind to find, and is refused rather than quietly recorded back to front.
     *
     * @param arguments everything the user typed after the command word.
     * @param firstMarker the marker introducing the second part, such as " /from ".
     * @param secondMarker the marker introducing the third part, such as " /to ".
     * @param usage what to tell the user when a part or a marker is missing.
     * @return the description and the two values the markers introduce, trimmed.
     * @throws TallyException if either marker is missing or any of the three is blank.
     */
    private static String[] splitOnTwoMarkers(String arguments, String firstMarker,
            String secondMarker, String usage) throws TallyException {
        String[] descriptionAndRest = arguments.split(firstMarker, 2);
        if (descriptionAndRest.length < 2) {
            throw new TallyException(usage);
        }
        String[] secondAndThird = descriptionAndRest[1].split(secondMarker, 2);
        if (secondAndThird.length < 2 || descriptionAndRest[0].isBlank()
                || secondAndThird[0].isBlank() || secondAndThird[1].isBlank()) {
            throw new TallyException(usage);
        }
        return new String[] {descriptionAndRest[0].trim(), secondAndThird[0].trim(),
                secondAndThird[1].trim()};
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
        String[] parts = splitOnTwoMarkers(arguments, " /from ", " /to ", usage);
        for (String part : parts) {
            rejectSeparator(part);
        }
        return new Event(parts[0], parts[1], parts[2]);
    }

    /**
     * Returns the word a "find" command is searching for.
     *
     * @param arguments what the user typed after the command word.
     * @return the word to look for.
     * @throws TallyException if nothing was given to search for.
     */
    public static String parseSearchText(String arguments) throws TallyException {
        if (arguments.isEmpty()) {
            throw new TallyException("find needs something to look for. Try: find book");
        }
        return arguments;
    }

    /**
     * Returns what a free command asked for: a run of days, and where to start looking.
     *
     * <p>Both parts are optional. "free" asks for the next single free day from today,
     * "/for 3" asks for three in a row, and "/from 2026-09-08" moves the search off
     * today, which is what lets the answer be checked against a fixed expectation.
     *
     * @param arguments everything the user typed after the command word.
     * @param today the day to search from when no /from date is given.
     * @return the run wanted and the day to start from.
     * @throws TallyException if a marker is malformed, the count is not a positive
     *     number, or the date cannot be read.
     */
    public static FreeQuery parseFreeQuery(String arguments, LocalDate today) throws TallyException {
        String usage = "free takes an optional /for count and an optional /from date."
                + " Try: free /for 3 /from 2026-09-08";
        String rest = arguments.trim();

        LocalDate earliestDate = today;
        int fromMarkerIndex = rest.indexOf("/from");
        if (fromMarkerIndex >= 0) {
            String dateText = rest.substring(fromMarkerIndex + "/from".length()).trim();
            if (dateText.isEmpty()) {
                throw new TallyException(usage);
            }
            earliestDate = parseDate(dateText);
            rest = rest.substring(0, fromMarkerIndex).trim();
        }

        int days = 1;
        if (!rest.isEmpty()) {
            if (!rest.startsWith("/for")) {
                throw new TallyException(usage);
            }
            days = parseDayCount(rest.substring("/for".length()).trim(), usage);
        }
        return new FreeQuery(days, earliestDate);
    }

    /**
     * Returns the number of days a /for marker named.
     *
     * @param text what followed the marker.
     * @param usage what to tell the user when it makes no sense.
     * @return the count, always at least one.
     * @throws TallyException if the text is not a number, or names fewer than one day.
     */
    private static int parseDayCount(String text, String usage) throws TallyException {
        int days;
        try {
            days = Integer.parseInt(text);
        } catch (NumberFormatException exception) {
            throw new TallyException(usage);
        }
        if (days < 1) {
            throw new TallyException("A free stretch has to be at least one day long.");
        }
        return days;
    }

    /**
     * Returns the window task described by "submit form /between 2026-09-08 /and 2026-09-12".
     *
     * <p>Both ends are read as dates rather than kept as text, so that a period running
     * backwards can be refused here instead of reaching the tally.
     *
     * @param arguments everything the user typed after the command word.
     * @return the window task described.
     * @throws TallyException if either marker or either part is missing, if a date cannot
     *     be read, or if the period ends before it starts.
     */
    public static Window parseWindow(String arguments) throws TallyException {
        String usage = "A window needs a description, a /between date and an /and date,"
                + " in that order. Try: window submit form /between 2026-09-08 /and 2026-09-12";
        String[] parts = splitOnTwoMarkers(arguments, " /between ", " /and ", usage);
        rejectSeparator(parts[0]);

        LocalDate startDate = parseDate(parts[1]);
        LocalDate endDate = parseDate(parts[2]);
        if (endDate.isBefore(startDate)) {
            throw new TallyException(String.format(
                    "A window cannot end before it starts, and this one ends %s"
                            + " but starts %s.", endDate, startDate));
        }
        return new Window(parts[0], startDate, endDate);
    }

    /**
     * Refuses text that the data file could not carry back unchanged.
     *
     * @param text a part of a task as the user typed it.
     * @throws TallyException if it holds the separator the data file uses.
     */
    private static void rejectSeparator(String text) throws TallyException {
        if (text.contains(FIELD_SEPARATOR)) {
            throw new TallyException("A task cannot contain \"|\", because that is how the"
                    + " file Tally keeps your tally in separates one part from the next.");
        }
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
        return Task.readDate(text).orElseThrow(() -> new TallyException(String.format(
                "I could not read \"%s\" as a date."
                        + " Write it as yyyy-mm-dd, for example 2019-10-15.", text)));
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
