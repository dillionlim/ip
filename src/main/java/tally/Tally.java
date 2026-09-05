package tally;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import tally.parser.Command;
import tally.parser.FreeQuery;
import tally.parser.Parser;
import tally.storage.LoadResult;
import tally.storage.Storage;
import tally.task.Task;
import tally.task.TaskList;
import tally.ui.Ui;

/**
 * Tally is a chatbot that helps the user keep a tally of their tasks, in a window
 * or in a terminal.
 *
 * <p>This class holds the parts together and does nothing itself: Ui talks to the
 * user, Parser makes sense of what they typed, TaskList holds the tasks, and
 * Storage keeps them on disk between runs. The window calls getResponse for each
 * line; the terminal calls run, which asks for lines until the user leaves.
 */
public class Tally {
    /** Where the tally is kept, relative to the project root. */
    private static final Path DATA_FILE = Paths.get("data", "tally.txt");

    private final Ui ui;
    private final Storage storage;
    private final TaskList tasks;

    /** What went wrong while reading the data file, if anything did. */
    private final Optional<String> loadWarning;

    /** Whether the user has said goodbye. */
    private boolean isExiting;

    /**
     * Creates a chatbot working on the given data file, reading back whatever it
     * already holds.
     *
     * <p>A file that cannot be read is reported once the user has been greeted, and
     * the chatbot starts with an empty tally rather than refusing to run.
     *
     * @param dataFile where the tally is kept.
     */
    public Tally(Path dataFile) {
        this(dataFile, true);
    }

    /**
     * Creates a chatbot working on the tally's usual data file.
     *
     * <p>Where that file lives is Tally's own business, so a front end that wants the
     * usual one asks for it this way rather than naming the place itself.
     *
     * @param isConsole whether replies are printed and commands read from standard input.
     */
    public Tally(boolean isConsole) {
        this(DATA_FILE, isConsole);
    }

    /**
     * Creates a chatbot working on the given data file, replying either on the
     * console or as text for a caller to display.
     *
     * @param dataFile where the tally is kept.
     * @param isConsole whether replies are printed and commands read from standard input.
     */
    public Tally(Path dataFile, boolean isConsole) {
        this.ui = new Ui(isConsole);
        this.storage = new Storage(dataFile);
        TaskList loaded;
        Optional<String> warning;
        try {
            LoadResult result = storage.load();
            loaded = new TaskList(result.tasks());
            warning = result.note();
        } catch (TallyException exception) {
            loaded = new TaskList();
            warning = Optional.of(exception.getMessage());
        }
        this.tasks = loaded;
        this.loadWarning = warning;
    }

    /**
     * Returns what Tally says in reply to one command, for a caller that shows the
     * reply itself rather than having it printed.
     *
     * @param input the line the user typed.
     * @return everything Tally says in reply, which is never empty.
     */
    public String getResponse(String input) {
        String line = input.trim();
        try {
            isExiting = !runCommand(line);
            if (isExiting) {
                ui.showGoodbye();
            }
        } catch (TallyException exception) {
            ui.showError(exception.getMessage());
        }
        return ui.takePendingResponse();
    }

    /**
     * Returns whether the last command asked to end the conversation.
     *
     * @return true once the user has said goodbye.
     */
    public boolean isExiting() {
        return isExiting;
    }

    /**
     * Returns the greeting shown when the chatbot starts, together with any
     * complaint about the data file.
     *
     * @return the opening message.
     */
    public String getGreeting() {
        ui.showWelcome();
        loadWarning.ifPresent(ui::showError);
        return ui.takePendingResponse();
    }

    /** Greets the user, carries out commands until they leave, then says goodbye. */
    public void run() {
        getGreeting();

        boolean isTalking = true;
        while (isTalking && ui.hasNextCommand()) {
            String line = ui.readCommand();
            try {
                isTalking = runCommand(line);
            } catch (TallyException exception) {
                ui.showError(exception.getMessage());
            }
        }

        ui.showGoodbye();
        ui.close();
    }

    /**
     * Carries out one command from the user, and says whether the conversation
     * carries on afterwards.
     *
     * @param line the line the user typed, with surrounding spaces removed.
     * @return whether the conversation should carry on afterwards.
     * @throws TallyException if Tally cannot carry out the command.
     */
    private boolean runCommand(String line) throws TallyException {
        Command command = Parser.parseCommand(line);
        assert command != null
                : "Parser.parseCommand returns a constant or throws, so it never yields null";
        if (command == Command.BYE) {
            return false;
        }

        carryOut(command, Parser.parseArguments(line));

        // Saving after a command that only read the tally would rewrite the file for
        // nothing, and every rewrite is a chance to lose what is already there.
        if (command.changesTally()) {
            saveOrPutBack();
        }
        return true;
    }

    /**
     * Writes the tally, and puts it back as it was if it cannot be written.
     *
     * <p>A command changes the tally before it can be saved, so a save that fails would
     * otherwise leave the user looking at a change that a restart would take away, having
     * been told it worked. Reading the file back is what says which of the two is real.
     *
     * @throws TallyException naming what went wrong, and whether the change was kept.
     */
    private void saveOrPutBack() throws TallyException {
        try {
            storage.save(tasks.asList());
            return;
        } catch (TallyException failure) {
            // The message is worked out first: throwing from inside the inner try would
            // be caught by its own catch, and report the wrong one of the two.
            String outcome;
            try {
                tasks.replaceAll(storage.load().tasks());
                outcome = " I have put your tally back the way the file has it.";
            } catch (TallyException unreadable) {
                outcome = " Your tally is as you left it here,"
                        + " but a restart will not show it.";
            }
            throw new TallyException(failure.getMessage() + outcome);
        }
    }

    /**
     * Carries out one command, which by now is not the one that ends the conversation.
     *
     * <p>Commands are dispatched by a switch over the Command enum, rather than by
     * a class per command carrying an execute method. The latter is how
     * AddressBook-Level3 is built, and how several classmates built theirs, such as
     * <a href="https://github.com/NUS-CS2103-AY2627-S1/ip/pull/535">this one</a>,
     * which has an abstract Command with an execute(TaskList, Ui, Storage) method and
     * a subclass for each command.
     *
     * <p>With ten commands of a few lines each, keeping them together shows the whole
     * conversation at once, so the switch stays. The trade reverses once a command
     * needs state of its own. A switch over an enum is not checked for exhaustiveness,
     * so a new constant would otherwise compile with nothing to carry it out; the
     * default clause turns that into a failure that is at least loud.
     *
     * @param command what the user asked for.
     * @param arguments the rest of the line they typed, with surrounding spaces removed.
     * @throws TallyException if Tally cannot carry the command out.
     */
    private void carryOut(Command command, String arguments) throws TallyException {
        // AI suggested switching to a switch statement instead of the if-else chain.
        // Arrow labels keep each branch self-contained.
        // Command.parse has already rejected any word that is not a command, and the
        // caller has already dealt with bye, so reaching default means an enum constant
        // nobody wired up here: a programming error rather than anything the user typed,
        // hence IllegalStateException over TallyException.
        switch (command) {
            case LIST -> showTasks();
            case FIND -> showMatchingTasks(Parser.parseSearchText(arguments));
            case FREE -> showFreeDays(Parser.parseFreeQuery(arguments, LocalDate.now()));
            case MARK -> markTask(Parser.parseTaskIndex(arguments, tasks.size(), command));
            case UNMARK -> unmarkTask(Parser.parseTaskIndex(arguments, tasks.size(), command));
            case DELETE -> deleteTask(Parser.parseTaskIndex(arguments, tasks.size(), command));
            case TODO -> addTask(Parser.parseTodo(arguments));
            case DEADLINE -> addTask(Parser.parseDeadline(arguments));
            case EVENT -> addTask(Parser.parseEvent(arguments));
            case WINDOW -> addTask(Parser.parseWindow(arguments));
            default -> throw new IllegalStateException("No handling for command: " + command);
        }
    }

    /** Marks the task at the given place done, and shows it as it now reads. */
    private void markTask(int position) {
        Task task = tasks.get(position);
        task.markAsDone();
        ui.show("Nice! I've marked this task as done:", task.toString());
    }

    /** Marks the task at the given place not done after all, and shows it as it now reads. */
    private void unmarkTask(int position) {
        Task task = tasks.get(position);
        task.markAsNotDone();
        ui.show("OK, I've marked this task as not done yet:", task.toString());
    }

    /** Takes the task at the given place off the tally, and says how many are left. */
    private void deleteTask(int position) {
        Task task = tasks.get(position);
        tasks.remove(task);
        ui.show("Noted. I've removed this task:", task.toString(), formatCountSentence());
    }

    /**
     * Shows when the user is next free for as long as they asked, or says there is no
     * such stretch.
     *
     * <p>The reply says so when the tally holds a task whose days could not be read,
     * because the answer is then drawn from less than everything on it.
     *
     * @param query the run of days wanted, and the day to start looking from.
     */
    private void showFreeDays(FreeQuery query) {
        int days = query.days();
        Optional<LocalDate> found = tasks.findFreeRun(days, query.earliestDate());
        String answer = found.isPresent()
                ? describeRunFound(found.get(), days)
                : describeNoRun(query.earliestDate(), days);

        if (tasks.hasUnreadableDates()) {
            ui.show(answer, "Events whose times are not dates were not counted.");
            return;
        }
        ui.show(answer);
    }

    /**
     * Returns the reply naming when the user is next free for as long as they asked.
     *
     * @param start the first day of the run found.
     * @param days how many days in a row were wanted.
     * @return a sentence naming the day, reading for one day or for several.
     */
    private static String describeRunFound(LocalDate start, int days) {
        if (days == 1) {
            return String.format("The next free day is %s.", Task.formatDate(start));
        }
        return String.format("The next %d free days in a row start %s.", days,
                Task.formatDate(start));
    }

    /**
     * Returns the reply for when no such run of days exists within the year searched.
     *
     * @param earliestDate the day the search started from.
     * @param days how many days in a row were wanted.
     * @return a sentence saying so, reading for one day or for several.
     */
    private static String describeNoRun(LocalDate earliestDate, int days) {
        if (days == 1) {
            return String.format("Every day in the year from %s has something on it.",
                    Task.formatDate(earliestDate));
        }
        return String.format("There is no run of %d free days in the year from %s.", days,
                Task.formatDate(earliestDate));
    }

    /** Shows the whole tally, or says so when there is nothing on it. */
    private void showTasks() {
        if (tasks.isEmpty()) {
            ui.show("Nothing on your tally yet.");
            return;
        }
        List<Integer> allPositions = IntStream.range(0, tasks.size()).boxed().toList();
        ui.show(formatNumberedTasks("Here are the tasks in your list:", allPositions));
    }

    /**
     * Shows the tasks whose description contains the given text.
     *
     * <p>Each is shown against its place on the whole tally rather than its place
     * among the matches, so the number beside it still names that task if the user
     * goes on to mark or delete it.
     *
     * @param searchText the text to look for.
     */
    private void showMatchingTasks(String searchText) {
        List<Integer> positions = tasks.findPositions(searchText);
        if (positions.isEmpty()) {
            ui.show("Nothing on your tally matches that.");
            return;
        }
        ui.show(formatNumberedTasks("Here are the matching tasks in your list:", positions));
    }

    /**
     * Returns a heading followed by one line per task, each numbered by its place on
     * the tally counting from 1.
     *
     * @param heading the line introducing the list.
     * @param positions the places of the tasks to show, counting from 0.
     * @return the lines to show, ready to hand to the user interface.
     */
    private String[] formatNumberedTasks(String heading, List<Integer> positions) {
        assert positions.stream().allMatch(position -> position >= 0 && position < tasks.size())
                : "positions come from findPositions or from a walk over the whole tally, and"
                + " both yield only places that hold a task, unlike one of: " + positions;
        // AI suggested String.format instead of concatenating strings manually.
        Stream<String> numberedTasks = positions.stream()
                .map(position -> String.format("%d.%s", position + 1, tasks.get(position)));
        return Stream.concat(Stream.of(heading), numberedTasks).toArray(String[]::new);
    }

    /**
     * Adds a task to the tally and tells the user what was recorded.
     *
     * @param task the task to add.
     */
    private void addTask(Task task) {
        tasks.add(task);
        ui.show("Got it. I've added this task:", task.toString(), formatCountSentence());
    }

    /**
     * Returns the sentence reporting how many tasks the tally now holds.
     *
     * @return for example "Now you have 3 tasks in the list."
     */
    private String formatCountSentence() {
        // AI identified grammatical error, manual fix.
        return String.format("Now you have %d %s in the list.",
                tasks.size(), tasks.size() == 1 ? "task" : "tasks");
    }

    /**
     * Starts the chatbot.
     *
     * @param args optionally the path of the data file to use, which lets the tests
     *     run against a file of their own rather than the real tally.
     */
    public static void main(String[] args) {
        Path dataFile = args.length > 0 ? Paths.get(args[0]) : DATA_FILE;
        new Tally(dataFile).run();
    }
}
