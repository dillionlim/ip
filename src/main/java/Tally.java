import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/** Tally is a command-line chatbot that helps the user keep a tally of their tasks. */
public class Tally {
    /** Where the tally is kept, relative to the project root. */
    private static final Path DATA_FILE = Paths.get("data", "tally.txt");

    /**
     * Runs the chatbot by reading back the saved tally, greeting the user, carrying
     * out each command entered, and saying goodbye once the user types "bye".
     *
     * @param args optionally the path of the data file to use, which lets the tests
     *     run against a file of their own rather than the real tally.
     */
    public static void main(String[] args) {
        Ui ui = new Ui();
        Storage storage = new Storage(args.length > 0 ? Paths.get(args[0]) : DATA_FILE);

        List<Task> tasks;
        String loadWarning = null;
        try {
            tasks = storage.load();
        } catch (TallyException exception) {
            tasks = new ArrayList<>();
            loadWarning = exception.getMessage();
        }

        ui.showWelcome();
        if (loadWarning != null) {
            ui.showError(loadWarning);
        }

        boolean isTalking = true;
        while (isTalking && ui.hasNextCommand()) {
            String line = ui.readCommand();
            try {
                isTalking = handleCommand(line, tasks, ui);
                storage.save(tasks);
            } catch (TallyException exception) {
                ui.showError(exception.getMessage());
            }
        }

        ui.showGoodbye();
        ui.close();
    }

    /**
     * Carries out one command from the user.
     *
     * <p>The first word names the command; whatever follows it is that command's
     * arguments. Splitting the two apart is what lets Tally tell an unknown
     * command from a known one that was given nothing to work with.
     *
     * @param line the line the user typed, with surrounding spaces removed.
     * @param tasks the tally to read from and add to.
     * @param ui what Tally replies through.
     * @return whether the conversation should carry on afterwards.
     * @throws TallyException if Tally cannot carry out the command.
     */
    private static boolean handleCommand(String line, List<Task> tasks, Ui ui) throws TallyException {
        String[] words = line.split(" ", 2);
        Command command = Command.parse(words[0]);
        String arguments = words.length > 1 ? words[1].trim() : "";

        // AI suggested switching to a switch statement instead of the if-else chain.
        switch (command) {
        case BYE -> {
            return false;
        }
        case LIST -> {
            if (tasks.isEmpty()) {
                ui.show("Nothing on your tally yet.");
            } else {
                ui.show(formatTasks(tasks));
            }
        }
        case MARK -> {
            Task task = findTask(tasks, arguments, command);
            task.markAsDone();
            ui.show("Nice! I've marked this task as done:", task.toString());
        }
        case UNMARK -> {
            Task task = findTask(tasks, arguments, command);
            task.markAsNotDone();
            ui.show("OK, I've marked this task as not done yet:", task.toString());
        }
        case DELETE -> {
            Task task = findTask(tasks, arguments, command);
            tasks.remove(task);
            ui.show("Noted. I've removed this task:", task.toString(), countSentence(tasks));
        }
        case TODO -> {
            if (arguments.isEmpty()) {
                throw new TallyException("A todo needs a description. Try: todo read book");
            }
            addTask(tasks, ui, new Todo(arguments));
        }
        case DEADLINE -> {
            String[] fields = arguments.split(" /by ", 2);
            if (fields.length < 2 || fields[0].isBlank() || fields[1].isBlank()) {
                throw new TallyException(
                        "A deadline needs a description and a /by date."
                                + " Try: deadline return book /by 2019-10-15");
            }
            addTask(tasks, ui, new Deadline(fields[0].trim(), parseDate(fields[1].trim())));
        }
        case EVENT -> addTask(tasks, ui, parseEvent(arguments));
        }
        return true;
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
    private static LocalDate parseDate(String text) throws TallyException {
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException exception) {
            throw new TallyException(String.format(
                    "I could not read \"%s\" as a date. Write it as yyyy-mm-dd."
                            + " Try: deadline return book /by 2019-10-15", text));
        }
    }

    /**
     * Returns the event described by the arguments of an "event" command.
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
    private static Event parseEvent(String arguments) throws TallyException {
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
     * Returns the task named by a command such as "mark 2".
     *
     * <p>The number the user types counts from 1, while the list is indexed from 0.
     * Doing the conversion here keeps it out of every command that names a task.
     *
     * @param tasks the tasks currently on the tally.
     * @param arguments what the user typed after the command word.
     * @param command the command that named the task, used to word the error.
     * @return the task at the position given.
     * @throws TallyException if no number was given, it is not a number, or no task
     *     has that position.
     */
    private static Task findTask(List<Task> tasks, String arguments, Command command)
            throws TallyException {
        int position;
        try {
            position = Integer.parseInt(arguments);
        } catch (NumberFormatException exception) {
            throw new TallyException(String.format(
                    "%s needs the number of a task. Try: %s 2",
                    command.getKeyword(), command.getKeyword()));
        }
        if (position < 1 || position > tasks.size()) {
            throw new TallyException(String.format(
                    "There is no task %d on your tally. Type list to see what is there.",
                    position));
        }
        return tasks.get(position - 1);
    }

    /**
     * Adds a task to the tally and tells the user what was recorded.
     *
     * @param tasks the tally to add to.
     * @param ui what Tally replies through.
     * @param task the task to add.
     */
    private static void addTask(List<Task> tasks, Ui ui, Task task) {
        tasks.add(task);
        ui.show("Got it. I've added this task:", task.toString(), countSentence(tasks));
    }

    /**
     * Returns the sentence reporting how many tasks the tally now holds.
     *
     * @param tasks the tally to count.
     * @return for example "Now you have 3 tasks in the list."
     */
    private static String countSentence(List<Task> tasks) {
        // AI identified grammatical error, manual fix.
        return String.format("Now you have %d %s in the list.",
                tasks.size(), tasks.size() == 1 ? "task" : "tasks");
    }

    /**
     * Returns the lines Tally prints in reply to "list": a heading, then one line
     * per task, each prefixed with its position counting from 1.
     *
     * @param tasks the tasks to format, in the order they were added.
     * @return the lines of the listing.
     */
    private static String[] formatTasks(List<Task> tasks) {
        String[] lines = new String[tasks.size() + 1];
        lines[0] = "Here are the tasks in your list:";
        for (int i = 0; i < tasks.size(); i++) {
            // AI suggested String.format instead of concatenating strings manually.
            lines[i + 1] = String.format("%d.%s", i + 1, tasks.get(i));
        }
        return lines;
    }
}
