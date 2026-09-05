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
 * D | 0 | return book | June 6th
 * E | 0 | project meeting | Aug 6th 2pm | 4pm
 * </pre>
 *
 * <p>A description containing " | " would be read back as extra fields and
 * reported as damage. Nothing escapes the separator, because the point of the
 * format is that a person can read and correct the file by hand.
 */
public class Storage {
    private final Path file;

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
     * @throws TallyException if the file cannot be read, or holds a line that is not
     *     in the expected format.
     */
    public LoadResult load() throws TallyException {
        List<Task> tasks = new ArrayList<>();
        if (!Files.exists(file)) {
            return new LoadResult(tasks, Optional.empty());
        }

        List<String> lines;
        try {
            lines = Files.readAllLines(file);
        } catch (IOException exception) {
            throw new TallyException("I could not read " + file.getFileName()
                    + ", so I am starting with an empty tally." + copyAside());
        }

        List<Integer> unreadable = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) {
                continue;
            }
            Task task = parseTask(line);
            if (task == null) {
                unreadable.add(i + 1);
            } else {
                tasks.add(task);
            }
        }

        if (unreadable.isEmpty()) {
            return new LoadResult(tasks, Optional.empty());
        }
        return new LoadResult(tasks, Optional.of(describeUnreadableLines(unreadable)));
    }

    /**
     * Returns what to tell the user about the lines that could not be read.
     *
     * <p>The file is copied rather than moved, because the tasks that did load stay on
     * the tally and the next change writes over the original, which would otherwise
     * take the unreadable lines with it.
     *
     * @param unreadable the line numbers that held nothing recognizable, counting from 1.
     * @return a sentence naming them, and where the file was copied to.
     */
    private String describeUnreadableLines(List<Integer> unreadable) {
        boolean isSingle = unreadable.size() == 1;
        List<String> numbers = unreadable.stream().map(String::valueOf).toList();
        String which = numbers.size() == 1 ? numbers.get(0)
                : String.join(", ", numbers.subList(0, numbers.size() - 1))
                        + " and " + numbers.get(numbers.size() - 1);
        return String.format("I could not read %s %s of %s, so %s not on your tally.%s",
                isSingle ? "line" : "lines", which, file.getFileName(),
                isSingle ? "that task is" : "those tasks are", copyAside());
    }

    /**
     * Returns the window task a data-file line describes, or null if either date is unreadable.
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
     * Moves an unusable file out of the way, so that the next save cannot write over it.
     *
     * <p>Without this, Tally would refuse to load a damaged file and then destroy it
     * anyway, because the first command the user types saves the empty tally over it.
     *
     * <p>Renaming needs write access to the directory rather than to the file, so it
     * usually succeeds even when the file itself could not be read.
     *
     * @return a sentence saying where the file was put, or an empty string if it
     *     could not be moved, in which case nothing is promised about it.
     */
    private String copyAside() {
        try {
            byte[] damaged = Files.readAllBytes(file);
            Path spoiledFile = file.resolveSibling(file.getFileName() + ".broken");
            for (int attempt = 1; Files.exists(spoiledFile); attempt++) {
                if (Arrays.equals(Files.readAllBytes(spoiledFile), damaged)) {
                    return " It is already copied to " + spoiledFile.getFileName() + ".";
                }
                spoiledFile = file.resolveSibling(file.getFileName() + ".broken." + attempt);
            }
            Files.copy(file, spoiledFile);
            return " I copied it to " + spoiledFile.getFileName() + " so you can repair it.";
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
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            // Writing beside the file and renaming means a failed write cannot damage what
            // is already saved. The rename replaces the file rather than writing into it,
            // so anything the old file carried has to be carried over deliberately: follow
            // a symbolic link to what it points at, refuse a file the user protected, and
            // put the old permissions on the replacement.
            Path target = Files.exists(file) ? file.toRealPath() : file;
            if (Files.exists(target) && !Files.isWritable(target)) {
                throw new TallyException(describeSaveFailure());
            }

            partial = target.resolveSibling(target.getFileName() + ".part");
            Files.write(partial, tasks.stream().map(Task::toSaveFormat).toList());
            copyPermissions(target, partial);
            Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            deleteQuietly(partial);
            throw new TallyException(describeSaveFailure());
        }
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
            return;
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
    private static Task parseTask(String line) {
        String[] fields = line.split(" \\| ");
        boolean isWellFormed = fields.length >= 3
                && (fields[1].equals("0") || fields[1].equals("1"));
        if (!isWellFormed) {
            return null;
        }

        Task task = switch (fields[0]) {
            case "T" -> fields.length == 3 ? new Todo(fields[2]) : null;
            case "D" -> fields.length == 4 ? readDeadline(fields[2], fields[3]) : null;
            case "E" -> fields.length == 5 ? new Event(fields[2], fields[3], fields[4]) : null;
            case "W" -> fields.length == 5 ? readWindow(fields[2], fields[3], fields[4]) : null;
            default -> null;
        };

        if (task != null && fields[1].equals("1")) {
            task.markAsDone();
        }
        return task;
    }
}
