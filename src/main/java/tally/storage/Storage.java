package tally.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import tally.TallyException;
import tally.task.Task;

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
    /**
     * The mark some editors write at the start of a UTF-8 file.
     *
     * <p>It is invisible in the editor, so a user who opened the file to correct one
     * line saves it back with this stuck to the front of the first task, and that task
     * is then the one line Tally cannot read.
     */
    private static final String BYTE_ORDER_MARK = "\ufeff";

    private final Path file;

    /**
     * Why the tally must not be written, if it must not.
     *
     * <p>Two things put the file beyond writing. It could not be read at all, so Tally
     * started empty and saving would destroy contents nobody has seen. Or some of its
     * lines could not be read and no copy of them could be kept, so saving would drop
     * those lines for good. Either way the user is told what to do about it rather than
     * being quietly written over.
     */
    private Optional<String> saveRefusal = Optional.empty();

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
        // notExists rather than !exists: exists answers false both for a file that is
        // not there and for one it could not find out about, and reading the second as
        // the first would let a later save write over a file nobody has seen.
        if (Files.notExists(file)) {
            return new LoadResult(new ArrayList<>(), Optional.empty());
        }

        List<String> lines;
        try {
            lines = Files.readAllLines(file);
        } catch (IOException exception) {
            refuseToSave(file.getFileName() + " could not be read at startup,"
                    + " so it will not be written over."
                    + " Move it aside or repair it, then start Tally again.");
            throw new TallyException(file.getFileName() + " could not be read."
                    + " Starting with nothing on record." + copyAside()
                    + " It will not be written over until it can be read.");
        }
        Reading reading = readTally(stripByteOrderMark(lines));
        if (reading.unreadableLines().isEmpty() && reading.repeatedLines().isEmpty()) {
            return new LoadResult(reading.tasks(), Optional.empty());
        }
        // Damage is copied aside here, before anything can write over it. A repeat is a
        // readable line rather than damage, so there is nothing to quarantine for it.
        String aside = reading.unreadableLines().isEmpty() ? "" : copyAside();
        return new LoadResult(reading.tasks(), Optional.of(describe(reading, aside)));
    }

    /**
     * Returns the lines with any byte-order mark taken off the first of them.
     *
     * @param lines the lines of the data file, in order.
     * @return the same lines, the first no longer carrying an invisible mark.
     */
    private static List<String> stripByteOrderMark(List<String> lines) {
        if (lines.isEmpty() || !lines.get(0).startsWith(BYTE_ORDER_MARK)) {
            return lines;
        }
        List<String> stripped = new ArrayList<>(lines);
        stripped.set(0, stripped.get(0).substring(BYTE_ORDER_MARK.length()));
        return stripped;
    }

    /**
     * Returns what to tell the user about the lines that did not become tasks.
     *
     * <p>Wording only. Nothing here reads a file, writes one, or decides anything: the
     * caller has already done whatever the damage called for and says so through
     * {@code aside}.
     *
     * @param reading the tasks found, and the lines that did not become one.
     * @param aside what was done with the damaged file, or empty if there was none.
     * @return the sentences to show the user.
     */
    private String describe(Reading reading, String aside) {
        List<String> sentences = new ArrayList<>();
        if (!reading.unreadableLines().isEmpty()) {
            sentences.add(describeUnreadableLines(reading.unreadableLines()) + aside);
            if (saveRefusal.isPresent()) {
                sentences.add("Nothing will be written over it until it is repaired.");
            }
        }
        if (!reading.repeatedLines().isEmpty()) {
            sentences.add(describeRepeatedLines(reading.repeatedLines()));
        }
        return String.join(" ", sentences);
    }

    /**
     * What one pass over the data file's lines found.
     *
     * @param tasks the tasks read, in the order they appear.
     * @param unreadableLines the numbers of the lines that held nothing recognizable,
     *     counting from 1.
     * @param repeatedLines the numbers of the lines that named a task an earlier line
     *     had already named, counting from 1.
     */
    private record Reading(List<Task> tasks, List<Integer> unreadableLines,
            List<Integer> repeatedLines) {
    }

    /**
     * Returns what the given lines hold, reading no files and changing nothing.
     *
     * @param lines the lines of the data file, in order.
     * @return the tasks they describe, which lines could not be read, and which named
     *     a task an earlier line had already named.
     */
    private static Reading readTally(List<String> lines) {
        List<Task> tasks = new ArrayList<>();
        List<Integer> unreadableLines = new ArrayList<>();
        List<Integer> repeatedLines = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) {
                continue;
            }
            Task task = TaskLine.read(line);
            if (task == null) {
                unreadableLines.add(i + 1);
                continue;
            }
            Optional<Task> alreadyRead = tasks.stream().filter(task::isSameAs).findFirst();
            if (alreadyRead.isEmpty()) {
                tasks.add(task);
                continue;
            }
            // Tally refuses to add a task it already holds, so a file naming one twice
            // would otherwise put the tally in a state no command can reach. The one
            // kept takes the done flag of any copy carrying it, since a task recorded
            // as done anywhere in the file has been done.
            if (task.isDone()) {
                alreadyRead.get().markAsDone();
            }
            repeatedLines.add(i + 1);
        }
        return new Reading(tasks, unreadableLines, repeatedLines);
    }

    /**
     * Returns what to tell the user about the lines that could not be read.
     *
     * @param unreadableLines the numbers of the lines that held nothing recognizable,
     *     counting from 1.
     * @return a sentence naming them.
     */
    private String describeUnreadableLines(List<Integer> unreadableLines) {
        boolean isSingle = unreadableLines.size() == 1;
        return String.format("%s %s of %s could not be read, so %s not on record.",
                isSingle ? "Line" : "Lines", listNumbers(unreadableLines), file.getFileName(),
                isSingle ? "that task is" : "those tasks are");
    }

    /**
     * Returns what to tell the user about lines naming a task an earlier line named.
     *
     * @param repeatedLines the numbers of those lines, counting from 1.
     * @return a sentence naming them.
     */
    private String describeRepeatedLines(List<Integer> repeatedLines) {
        boolean isSingle = repeatedLines.size() == 1;
        return String.format("%s %s of %s %s a task already on record, so %s kept once.",
                isSingle ? "Line" : "Lines", listNumbers(repeatedLines), file.getFileName(),
                isSingle ? "repeats" : "repeat", isSingle ? "it is" : "they are");
    }

    /**
     * Returns line numbers written out for a reader, such as "1, 2 and 5".
     *
     * @param numbers the line numbers, in order.
     * @return them joined by commas, with "and" before the last.
     */
    private static String listNumbers(List<Integer> numbers) {
        List<String> written = numbers.stream().map(String::valueOf).toList();
        if (written.size() == 1) {
            return written.get(0);
        }
        return String.join(", ", written.subList(0, written.size() - 1))
                + " and " + written.get(written.size() - 1);
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
     * <p>Failing to make the copy is what stops the save rather than merely being
     * mentioned: the damaged lines then exist nowhere else, and the first save would be
     * the last time anyone could have read them.
     *
     * @return a sentence saying where the copy was put, that one is already kept, or
     *     that none could be made and so nothing will be written.
     */
    private String copyAside() {
        try {
            byte[] damagedBytes = Files.readAllBytes(file);
            Path backupFile = file.resolveSibling(file.getFileName() + ".broken");
            for (int attempt = 1; isNameTaken(backupFile); attempt++) {
                if (isKeptCopyOf(backupFile, damagedBytes)) {
                    return " Already copied to " + backupFile.getFileName() + ".";
                }
                backupFile = file.resolveSibling(file.getFileName() + ".broken." + attempt);
            }
            Files.copy(file, backupFile);
            return " Copied to " + backupFile.getFileName() + " for repair.";
        } catch (IOException exception) {
            refuseToSave("What could not be read in " + file.getFileName()
                    + " could not be copied aside either, so it will not be written over."
                    + " Move it aside or repair it, then start Tally again.");
            return " It could not be copied aside.";
        }
    }

    /**
     * Returns whether anything at all sits at a name, a symbolic link included.
     *
     * <p>Links are not followed, because a name holding one is taken whether or not
     * there is anything at the end of it, and copying onto it would write through the
     * link rather than make the copy this is looking for a place for.
     *
     * @param candidate the name being considered for the copy.
     * @return true when the name is not free.
     */
    private static boolean isNameTaken(Path candidate) {
        return Files.exists(candidate, LinkOption.NOFOLLOW_LINKS);
    }

    /**
     * Returns whether a file already holds exactly the damage about to be copied.
     *
     * <p>It has to be a file of its own to count. A symbolic link back to the data file
     * holds the same bytes and so looks like a copy, while keeping nothing: the next save
     * writes through it and the damaged lines are gone from both names at once.
     *
     * @param candidate the file being considered as an existing copy.
     * @param damagedBytes what the data file holds.
     * @return true when the damage is already kept there.
     * @throws IOException if the file is there but cannot be read.
     */
    private static boolean isKeptCopyOf(Path candidate, byte[] damagedBytes) throws IOException {
        return Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)
                && Arrays.equals(Files.readAllBytes(candidate), damagedBytes);
    }

    /**
     * Records why the tally must not be written, keeping the first reason found.
     *
     * <p>The first is kept because the later ones follow from it: a file that could not
     * be read is also one whose damage could not be copied, and the reading is what the
     * user has to put right.
     *
     * @param reason what to tell the user when they next change the tally.
     */
    private void refuseToSave(String reason) {
        if (saveRefusal.isEmpty()) {
            saveRefusal = Optional.of(reason);
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
            List<String> lines = tasks.stream().map(Task::toSaveFormat).toList();
            FileReplacer.replace(resolveSaveTarget(), lines);
        } catch (IOException exception) {
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
     * @throws IOException if the folder cannot be made, or a link cannot be followed.
     * @throws TallyException if the file is one the user has protected, or one this
     *     storage has already refused to write over.
     */
    private Path resolveSaveTarget() throws IOException, TallyException {
        if (saveRefusal.isPresent()) {
            throw new TallyException(saveRefusal.get());
        }
        Path target = FileReplacer.followLinks(file);
        Files.createDirectories(FileReplacer.getFolderOf(target));
        if (!Files.notExists(target) && !Files.isWritable(target)) {
            throw new TallyException(describeSaveFailure());
        }
        return target;
    }

    /** Returns what to tell the user when the tally could not be written. */
    private String describeSaveFailure() {
        return "The record could not be saved to " + file.getFileName() + ".";
    }
}
