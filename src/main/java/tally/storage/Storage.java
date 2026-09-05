package tally.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import tally.TallyException;
import tally.task.Deadline;
import tally.task.Event;
import tally.task.Task;
import tally.task.Todo;

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
    public List<Task> load() throws TallyException {
        List<Task> tasks = new ArrayList<>();
        if (!Files.exists(file)) {
            return tasks;
        }

        List<String> lines;
        try {
            lines = Files.readAllLines(file);
        } catch (IOException exception) {
            throw new TallyException("I could not read " + file.getFileName()
                    + ", so I am starting with an empty tally." + setAside());
        }

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) {
                continue;
            }
            Task task = parseTask(line);
            if (task == null) {
                throw new TallyException(String.format(
                        "Line %d of %s is not in a format I recognize, so I am starting"
                                + " with an empty tally.%s",
                        i + 1, file.getFileName(), setAside()));
            }
            tasks.add(task);
        }
        return tasks;
    }

    /**
     * Returns the deadline a data-file line describes.
     *
     * <p>A date the file cannot offer as yyyy-mm-dd is damage rather than something
     * to ask the user about, so this reports it the same way as any other malformed
     * line: by returning null.
     *
     * @param description what has to be done.
     * @param dueDate the date field as it appears in the file.
     * @return the deadline, or null if the date cannot be read.
     */
    private static Task parseDeadline(String description, String dueDate) {
        try {
            return new Deadline(description, LocalDate.parse(dueDate));
        } catch (DateTimeParseException exception) {
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
    private String setAside() {
        Path spoiled = file.resolveSibling(file.getFileName() + ".broken");
        try {
            Files.move(file, spoiled, StandardCopyOption.REPLACE_EXISTING);
            return " I moved it to " + spoiled.getFileName() + " so you can repair it.";
        } catch (IOException exception) {
            return "";
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
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            List<String> lines = new ArrayList<>();
            for (Task task : tasks) {
                lines.add(task.toSaveFormat());
            }
            Files.write(file, lines);
        } catch (IOException exception) {
            throw new TallyException("I could not save your tally to " + file.getFileName() + ".");
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
            case "D" -> fields.length == 4 ? parseDeadline(fields[2], fields[3]) : null;
            case "E" -> fields.length == 5 ? new Event(fields[2], fields[3], fields[4]) : null;
            default -> null;
        };

        if (task != null && fields[1].equals("1")) {
            task.markAsDone();
        }
        return task;
    }
}
