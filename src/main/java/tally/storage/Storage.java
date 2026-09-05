package tally.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import tally.TallyException;
import tally.parser.Parser;
import tally.task.Deadline;
import tally.task.Event;
import tally.task.Task;
import tally.task.Todo;
import tally.task.Window;

/**
 * Keeps the tally on disk: reads it back when Tally starts, and writes it out
 * again whenever it changes.
 *
 * <p>Each task occupies one line, its fields separated by " | ", with the type
 * letter first and the done flag second:
 *
 * <pre>
 * T | 1 | read book
 * D | 0 | return book | 2019-06-06
 * E | 0 | project meeting | Aug 6th 2pm | 4pm
 * </pre>
 *
 * <p>A description containing " | " would be read back as extra fields and
 * reported as damage. Nothing escapes the separator, because the point of the
 * format is that a person can read and correct the file by hand.
 */
public class Storage {
    /** Where each part of a task sits on its line in the data file. */
    private static final int TYPE_INDEX = 0;
    private static final int DONE_INDEX = 1;
    private static final int DESCRIPTION_INDEX = 2;
    /**
     * Where a task's own two extra parts sit. They are dates for a deadline and a
     * window, and whatever the user typed for an event.
     */
    private static final int FIRST_DETAIL_INDEX = 3;
    private static final int SECOND_DETAIL_INDEX = 4;

    /** How many parts a line of each kind of task has. */
    private static final int TODO_FIELD_COUNT = 3;
    private static final int DEADLINE_FIELD_COUNT = 4;
    private static final int EVENT_FIELD_COUNT = 5;
    private static final int WINDOW_FIELD_COUNT = 5;

    private final Path file;

    /**
     * Whether the file was there but could not be read when it was last loaded.
     *
     * <p>Tally starts with an empty tally when that happens, and saving that over a file
     * whose contents nobody has seen would destroy them. Refusing to write is the only
     * safe answer until the user moves the file or repairs it.
     */
    private boolean isUnreadable;

    /**
     * Creates storage backed by the given file. The file need not exist yet.
     *
     * @param file where the tally is kept.
     */
    public Storage(Path file) {
        this.file = file;
    }

    /**
     * Returns the tasks recorded in the file, or no tasks if it does not exist yet.
     *
     * @return the tasks, in the order they were written.
     * @throws TallyException if the file cannot be read at all. A single line that
     *     cannot be read is skipped and named in the result instead, since losing one
     *     task is a better answer than losing every other task on the tally.
     */
    public LoadResult load() throws TallyException {
        List<Task> tasks = new ArrayList<>();
        if (!Files.exists(file)) {
            return new LoadResult(tasks, Optional.empty());
        }

        List<String> lines = readLines();
        List<Integer> unreadableLines = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) {
                continue;
            }
            Task task = readTask(line);
            if (task == null) {
                unreadableLines.add(i + 1);
            } else {
                tasks.add(task);
            }
        }

        if (unreadableLines.isEmpty()) {
            return new LoadResult(tasks, Optional.empty());
        }
        return new LoadResult(tasks, Optional.of(describeUnreadableLines(unreadableLines)));
    }

    /**
     * Returns the lines the data file holds.
     *
     * @return every line of the file, in order.
     * @throws TallyException if the file is there but cannot be read at all, in which
     *     case there is nothing to keep and nothing to say beyond which file.
     */
    private List<String> readLines() throws TallyException {
        try {
            return Files.readAllLines(file);
        } catch (IOException exception) {
            isUnreadable = true;
            throw new TallyException("I could not read " + file.getFileName()
                    + ", so I am starting with an empty tally." + copyAside()
                    + " I will not write over it until it can be read.");
        }
    }

    /**
     * Returns what to tell the user about the lines that could not be read.
     *
     * <p>The file is copied rather than moved, because the tasks that did load stay on
     * the tally and the next change writes over the original, which would otherwise
     * take the unreadable lines with it.
     *
     * @param unreadableLines the numbers of the lines that held nothing recognizable,
     *     counting from 1.
     * @return a sentence naming them, and where the file was copied to.
     */
    private String describeUnreadableLines(List<Integer> unreadableLines) {
        boolean isSingle = unreadableLines.size() == 1;
        List<String> lineNumbers = unreadableLines.stream().map(String::valueOf).toList();
        String listed = isSingle ? lineNumbers.get(0)
                : String.join(", ", lineNumbers.subList(0, lineNumbers.size() - 1))
                        + " and " + lineNumbers.get(lineNumbers.size() - 1);
        return String.format("I could not read %s %s of %s, so %s not on your tally.%s",
                isSingle ? "line" : "lines", listed, file.getFileName(),
                isSingle ? "that task is" : "those tasks are", copyAside());
    }

    /**
     * Returns the window task a data-file line describes, or null if either date cannot be read.
     *
     * @param description what has to be done.
     * @param startDateText the first date field as it appears in the file.
     * @param endDateText the second date field as it appears in the file.
     * @return the window task, or null if the line cannot be read.
     */
    private static Task readWindow(String description, String startDateText, String endDateText) {
        try {
            LocalDate start = Parser.parseDate(startDateText);
            LocalDate end = Parser.parseDate(endDateText);
            // The parser refuses a backwards window, so a file holding one was edited
            // by hand; letting it through would crash the free-day search later.
            return end.isBefore(start) ? null : new Window(description, start, end);
        } catch (TallyException exception) {
            return null;
        }
    }

    /**
     * Returns the deadline a data-file line describes.
     *
     * <p>A date the file cannot offer as yyyy-mm-dd is damage rather than something
     * to ask the user about, so this reports it the same way as any other malformed
     * line: by returning null.
     *
     * @param description what has to be done.
     * @param dueDateText the date field as it appears in the file.
     * @return the deadline, or null if the date cannot be read.
     */
    private static Task readDeadline(String description, String dueDateText) {
        try {
            return new Deadline(description, Parser.parseDate(dueDateText));
        } catch (TallyException exception) {
            return null;
        }
    }

    /**
     * Keeps a copy of an unusable file, so that a later save cannot write over it.
     *
     * <p>The file is copied rather than moved, because the tasks that did load stay on
     * the tally and the next change writes over the original, which would otherwise take
     * the unreadable lines with it. The original also stays where the user left it.
     *
     * <p>The same damage is copied once. An unrepaired file is read again on every start,
     * and a fresh copy each time would fill the folder without adding anything.
     *
     * @return a sentence saying where the copy was put, that one is already kept, or
     *     that none could be made.
     */
    private String copyAside() {
        try {
            byte[] damaged = Files.readAllBytes(file);
            Path backupFile = file.resolveSibling(file.getFileName() + ".broken");
            for (int attempt = 1; Files.exists(backupFile); attempt++) {
                if (Arrays.equals(Files.readAllBytes(backupFile), damaged)) {
                    return " It is already copied to " + backupFile.getFileName() + ".";
                }
                backupFile = file.resolveSibling(file.getFileName() + ".broken." + attempt);
            }
            Files.copy(file, backupFile);
            return " I copied it to " + backupFile.getFileName() + " so you can repair it.";
        } catch (IOException exception) {
            return " I could not copy it aside.";
        }
    }

    /**
     * Writes the given tasks to the file, replacing whatever it held before.
     *
     * <p>Any missing parent directories are created first, so a fresh checkout
     * needs no setup.
     *
     * @param tasks the tally to record.
     * @throws TallyException if the file cannot be written.
     */
    public void save(List<Task> tasks) throws TallyException {
        Path partial = null;
        try {
            Path target = resolveSaveTarget();
            // A name of its own, so that a file already sitting at a fixed one is not
            // overwritten, and two Tallys saving at once do not write the same place.
            partial = Files.createTempFile(target.getParent(), target.getFileName().toString(),
                    ".part");
            writeReplacement(tasks, target, partial);
            Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            deleteQuietly(partial);
            throw new TallyException(describeSaveFailure());
        }
    }

    /**
     * Returns the file a save should actually write, making its folder if it is missing.
     *
     * <p>A symbolic link is followed to what it points at, so that saving writes through
     * it rather than replacing the link with an ordinary file. A file the user has
     * protected is refused here, because the rename that puts the replacement in place
     * needs permission on the folder rather than on the file, and would otherwise go
     * straight through.
     *
     * @return the file to replace.
     * @throws IOException if the folder cannot be made.
     * @throws TallyException if the file is one the user has protected.
     */
    private Path resolveSaveTarget() throws IOException, TallyException {
        if (isUnreadable) {
            throw new TallyException("I could not read " + file.getFileName()
                    + " when I started, so I will not write over what is in it."
                    + " Move it aside or repair it, then start Tally again.");
        }
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path target = Files.exists(file) ? file.toRealPath() : file;
        if (Files.exists(target) && !Files.isWritable(target)) {
            throw new TallyException(describeSaveFailure());
        }
        return target;
    }

    /**
     * Writes the tally beside the file it will replace, carrying its permissions over.
     *
     * <p>Writing beside it and renaming means a write that fails partway cannot damage
     * what is already saved. The rename replaces the file rather than writing into it,
     * so what the old one carried has to be carried over deliberately.
     *
     * @param tasks the tally to write.
     * @param target the file that will be replaced.
     * @param partial where to write it first.
     * @throws IOException if it cannot be written.
     */
    private static void writeReplacement(List<Task> tasks, Path target, Path partial)
            throws IOException {
        Files.write(partial, tasks.stream().map(Task::toSaveFormat).toList());
        copyPermissions(target, partial);
    }

    /** Returns what to tell the user when the tally could not be written. */
    private String describeSaveFailure() {
        return "I could not save your tally to " + file.getFileName() + ".";
    }

    /**
     * Gives the replacement file the permissions the one it replaces already had.
     *
     * <p>Without this the new file is made under the umask, so a tally the user had kept
     * private would quietly become readable by others on the first save.
     *
     * @param existing the file being replaced, which may not exist yet.
     * @param replacement the file about to take its place.
     * @throws IOException if the permissions can be read but not written.
     */
    private static void copyPermissions(Path existing, Path replacement) throws IOException {
        boolean isPosix = Files.exists(existing)
                && Files.getFileStore(existing).supportsFileAttributeView(PosixFileAttributeView.class);
        if (isPosix) {
            Files.setPosixFilePermissions(replacement, Files.getPosixFilePermissions(existing));
        }
    }

    /**
     * Removes a half-written file, saying nothing if it cannot be removed.
     *
     * <p>This runs while a save is already failing, so a complaint from here would hide
     * the reason the save failed, which is the more useful of the two.
     *
     * @param leftover the file to remove, or null if none was made.
     */
    private static void deleteQuietly(Path leftover) {
        if (leftover == null) {
            return;
        }
        try {
            Files.deleteIfExists(leftover);
        } catch (IOException exception) {
            // The failing save is the more useful complaint; this would hide it.
        }
    }

    /**
     * Returns the task a line of the data file stands for.
     *
     * <p>Reading is a factory rather than a method on Task, because which subclass
     * to build is only known once the type letter has been read.
     *
     * @param line one line of the data file, with surrounding spaces removed.
     * @return the task described, or null if the line is not in the expected format.
     */
    private static Task readTask(String line) {
        String[] fields = line.split(Pattern.quote(Task.FIELD_SEPARATOR));
        boolean hasValidCommonFields = fields.length > DESCRIPTION_INDEX
                && (fields[DONE_INDEX].equals(Task.NOT_DONE) || fields[DONE_INDEX].equals(Task.DONE))
                && !fields[DESCRIPTION_INDEX].isBlank()
                && Arrays.stream(fields).noneMatch(String::isBlank);
        if (!hasValidCommonFields) {
            return null;
        }

        String description = fields[DESCRIPTION_INDEX];
        Task task = switch (fields[TYPE_INDEX]) {
            case Todo.TYPE -> fields.length == TODO_FIELD_COUNT ? new Todo(description) : null;
            case Deadline.TYPE -> fields.length == DEADLINE_FIELD_COUNT
                    ? readDeadline(description, fields[FIRST_DETAIL_INDEX]) : null;
            case Event.TYPE -> fields.length == EVENT_FIELD_COUNT
                    ? new Event(description, fields[FIRST_DETAIL_INDEX], fields[SECOND_DETAIL_INDEX]) : null;
            case Window.TYPE -> fields.length == WINDOW_FIELD_COUNT
                    ? readWindow(description, fields[FIRST_DETAIL_INDEX], fields[SECOND_DETAIL_INDEX]) : null;
            default -> null;
        };

        if (task != null && fields[DONE_INDEX].equals(Task.DONE)) {
            task.markAsDone();
        }
        return task;
    }
}
