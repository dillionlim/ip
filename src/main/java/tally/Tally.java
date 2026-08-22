package tally;

import java.nio.file.Path;
import java.nio.file.Paths;
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
        this.ui = new Ui();
        this.storage = new Storage(dataFile);
        try {
            this.tasks = new TaskList(storage.load());
        } catch (TallyException exception) {
            this.tasks = new TaskList();
            this.loadWarning = exception.getMessage();
        }
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
     * @param line the line the user typed, with surrounding spaces removed.
     * @return whether the conversation should carry on afterwards.
     * @throws TallyException if Tally cannot carry out the command.
     */
    private boolean isStillTalkingAfter(String line) throws TallyException {
        Command command = Parser.parseCommand(line);
        String arguments = Parser.parseArguments(line);

        // AI suggested switching to a switch statement instead of the if-else chain.
        switch (command) {
        case BYE -> {
            return false;
        }
        case LIST -> showTasks();
        case MARK -> {
            Task task = tasks.get(Parser.parseTaskIndex(arguments, tasks.size(), command));
            task.markAsDone();
            ui.show("Nice! I've marked this task as done:", task.toString());
        }
        case UNMARK -> {
            Task task = tasks.get(Parser.parseTaskIndex(arguments, tasks.size(), command));
            task.markAsNotDone();
            ui.show("OK, I've marked this task as not done yet:", task.toString());
        }
        case DELETE -> {
            Task task = tasks.get(Parser.parseTaskIndex(arguments, tasks.size(), command));
            tasks.remove(task);
            ui.show("Noted. I've removed this task:", task.toString(), countSentence());
        }
        case TODO -> addTask(Parser.parseTodo(arguments));
        case DEADLINE -> addTask(Parser.parseDeadline(arguments));
        case EVENT -> addTask(Parser.parseEvent(arguments));
        }
        return true;
    }

    /** Shows the whole tally, or says so when there is nothing on it. */
    private void showTasks() {
        if (tasks.isEmpty()) {
            ui.show("Nothing on your tally yet.");
            return;
        }
        String[] lines = new String[tasks.size() + 1];
        lines[0] = "Here are the tasks in your list:";
        for (int i = 0; i < tasks.size(); i++) {
            // AI suggested String.format instead of concatenating strings manually.
            lines[i + 1] = String.format("%d.%s", i + 1, tasks.get(i));
        }
        ui.show(lines);
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
