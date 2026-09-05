package tally;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import tally.parser.Command;
import tally.parser.Parser;
import tally.storage.Storage;
import tally.task.Task;
import tally.task.TaskList;
import tally.ui.Ui;

/**
 * Tally is a command-line chatbot that helps the user keep a tally of their tasks.
 *
 * <p>This class holds the parts together and does nothing itself: Ui talks to the
 * user, Parser makes sense of what they typed, TaskList holds the tasks, and
 * Storage keeps them on disk between runs.
 */
public class Tally {
    /** Where the tally is kept, relative to the project root. */
    private static final Path DATA_FILE = Paths.get("data", "tally.txt");

    private final Ui ui;
    private final Storage storage;
    private TaskList tasks;

    /** What went wrong while reading the data file, or null if nothing did. */
    private String loadWarning;

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
     * Creates a chatbot working on the given data file, replying either on the
     * console or as text for a caller to display.
     *
     * @param dataFile where the tally is kept.
     * @param isConsole whether replies are printed and commands read from standard input.
     */
    public Tally(Path dataFile, boolean isConsole) {
        this.ui = new Ui(isConsole);
        this.storage = new Storage(dataFile);
        try {
            this.tasks = new TaskList(storage.load());
        } catch (TallyException exception) {
            this.tasks = new TaskList();
            this.loadWarning = exception.getMessage();
        }
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
            isExiting = !isStillTalkingAfter(line);
            if (isExiting) {
                ui.showGoodbye();
            } else {
                storage.save(tasks.asList());
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
        if (loadWarning != null) {
            ui.showError(loadWarning);
        }
        return ui.takePendingResponse();
    }

    /** Greets the user, carries out commands until they leave, then says goodbye. */
    public void run() {
        ui.showWelcome();
        if (loadWarning != null) {
            ui.showError(loadWarning);
        }

        boolean isTalking = true;
        while (isTalking && ui.hasNextCommand()) {
            String line = ui.readCommand();
            try {
                isTalking = isStillTalkingAfter(line);
                storage.save(tasks.asList());
            } catch (TallyException exception) {
                ui.showError(exception.getMessage());
            }
        }

        ui.showGoodbye();
        ui.close();
    }

    /**
     * Carries out one command from the user, and says whether to keep going.
     *
     * <p>Commands are dispatched by a switch over the Command enum, rather than by
     * a class per command carrying an execute method. The latter is how
     * AddressBook-Level3 is built, and how several classmates built theirs, such as
     * <a href="https://github.com/NUS-CS2103-AY2627-S1/ip/pull/535">this one</a>,
     * which has an abstract Command with an execute(TaskList, Ui, Storage) method and
     * a subclass for each command.
     *
     * <p>With nine commands of a few lines each, keeping them together shows the whole
     * conversation at once, so the switch stays. The trade reverses once a command
     * needs state of its own. A switch over an enum is not checked for exhaustiveness,
     * so a new constant would otherwise compile with nothing to carry it out; the
     * default clause turns that into a failure that is at least loud.
     *
     * @param line the line the user typed, with surrounding spaces removed.
     * @return whether the conversation should carry on afterwards.
     * @throws TallyException if Tally cannot carry out the command.
     */
    private boolean isStillTalkingAfter(String line) throws TallyException {
        Command command = Parser.parseCommand(line);
        String arguments = Parser.parseArguments(line);

        // AI suggested switching to a switch statement instead of the if-else chain.
        // Arrow labels keep each branch self-contained.
        // Command.parse has already rejected any word that is not a command, so reaching
        // default means an enum constant nobody wired up here: a programming error rather
        // than anything the user typed, hence IllegalStateException over TallyException.
        switch (command) {
            case BYE -> {
                return false;
            }
            case LIST -> showTasks();
            case FIND -> showMatchingTasks(Parser.parseSearchWord(arguments));
            case MARK -> markTask(arguments, command);
            case UNMARK -> unmarkTask(arguments, command);
            case DELETE -> deleteTask(arguments, command);
            case TODO -> addTask(Parser.parseTodo(arguments));
            case DEADLINE -> addTask(Parser.parseDeadline(arguments));
            case EVENT -> addTask(Parser.parseEvent(arguments));
            default -> throw new IllegalStateException("No handling for command: " + command);
        }
        return true;
    }

    /** Returns the task the user named by its number, counting from 1. */
    private Task taskNamedIn(String arguments, Command command) throws TallyException {
        return tasks.get(Parser.parseTaskIndex(arguments, tasks.size(), command));
    }

    /** Marks the named task done, and shows it as it now reads. */
    private void markTask(String arguments, Command command) throws TallyException {
        Task task = taskNamedIn(arguments, command);
        task.markAsDone();
        ui.show("Nice! I've marked this task as done:", task.toString());
    }

    /** Marks the named task not done after all, and shows it as it now reads. */
    private void unmarkTask(String arguments, Command command) throws TallyException {
        Task task = taskNamedIn(arguments, command);
        task.markAsNotDone();
        ui.show("OK, I've marked this task as not done yet:", task.toString());
    }

    /** Takes the named task off the tally, and says how many are left. */
    private void deleteTask(String arguments, Command command) throws TallyException {
        Task task = taskNamedIn(arguments, command);
        tasks.remove(task);
        ui.show("Noted. I've removed this task:", task.toString(), countSentence());
    }

    /** Shows the whole tally, or says so when there is nothing on it. */
    private void showTasks() {
        if (tasks.isEmpty()) {
            ui.show("Nothing on your tally yet.");
            return;
        }
        List<Integer> allPositions = new ArrayList<>();
        for (int i = 0; i < tasks.size(); i++) {
            allPositions.add(i);
        }
        ui.show(formatNumberedTasks("Here are the tasks in your list:", allPositions));
    }

    /**
     * Shows the tasks whose description contains the given word.
     *
     * <p>Each is shown against its place on the whole tally rather than its place
     * among the matches, so the number beside it still names that task if the user
     * goes on to mark or delete it.
     *
     * @param word the text to look for.
     */
    private void showMatchingTasks(String word) {
        List<Integer> positions = tasks.findPositions(word);
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
        List<String> lines = new ArrayList<>();
        lines.add(heading);
        for (int position : positions) {
            // AI suggested String.format instead of concatenating strings manually.
            lines.add(String.format("%d.%s", position + 1, tasks.get(position)));
        }
        return lines.toArray(new String[0]);
    }

    /**
     * Adds a task to the tally and tells the user what was recorded.
     *
     * @param task the task to add.
     */
    private void addTask(Task task) {
        tasks.add(task);
        ui.show("Got it. I've added this task:", task.toString(), countSentence());
    }

    /**
     * Returns the sentence reporting how many tasks the tally now holds.
     *
     * @return for example "Now you have 3 tasks in the list."
     */
    private String countSentence() {
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
