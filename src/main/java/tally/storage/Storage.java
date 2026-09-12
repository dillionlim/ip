package tally.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
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
    private static final int INDEX_TYPE = 0;
    private static final int INDEX_DONE = 1;
    private static final int INDEX_DESCRIPTION = 2;

    /**
     * Where a task's own two extra parts sit. They are dates for a deadline and a
     * window, and whatever the user typed for an event.
     */
    private static final int INDEX_FIRST_DETAIL = 3;
    private static final int INDEX_SECOND_DETAIL = 4;

    /** How many parts a line of each kind of task has. */
    private static final int FIELD_COUNT_TODO = 3;
    private static final int FIELD_COUNT_DEADLINE = 4;
    private static final int FIELD_COUNT_EVENT = 5;
    private static final int FIELD_COUNT_WINDOW = 5;

    /**
     * The mark some editors write at the start of a UTF-8 file.
     *
     * <p>It is invisible in the editor, so a user who opened the file to correct one
     * line saves it back with this stuck to the front of the first task, and that task
     * is then the one line Tally cannot read.
     */
    private static final String BYTE_ORDER_MARK = "\ufeff";

    /** How many symbolic links may be followed before the chain is called a loop. */
    private static final int MAX_LINKS_FOLLOWED = 8;

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
            Task task = readTask(line);
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
        boolean hasValidCommonFields = fields.length > INDEX_DESCRIPTION
                && (fields[INDEX_DONE].equals(Task.FLAG_NOT_DONE) || fields[INDEX_DONE].equals(Task.FLAG_DONE))
                && Arrays.stream(fields).noneMatch(String::isBlank);
        if (!hasValidCommonFields) {
            return null;
        }

        String description = fields[INDEX_DESCRIPTION];
        Task task = switch (fields[INDEX_TYPE]) {
            case Todo.TYPE -> fields.length == FIELD_COUNT_TODO ? new Todo(description) : null;
            case Deadline.TYPE -> fields.length == FIELD_COUNT_DEADLINE
                    ? readDeadline(description, fields[INDEX_FIRST_DETAIL]) : null;
            case Event.TYPE -> fields.length == FIELD_COUNT_EVENT
                    ? readEvent(description, fields[INDEX_FIRST_DETAIL],
                            fields[INDEX_SECOND_DETAIL]) : null;
            case Window.TYPE -> fields.length == FIELD_COUNT_WINDOW
                    ? readWindow(description, fields[INDEX_FIRST_DETAIL], fields[INDEX_SECOND_DETAIL]) : null;
            default -> null;
        };

        if (task != null && fields[INDEX_DONE].equals(Task.FLAG_DONE)) {
            task.markAsDone();
        }
        return task;
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
        return Task.readDate(dueDateText)
                .<Task>map(dueDate -> new Deadline(description, dueDate))
                .orElse(null);
    }

    /**
     * Returns the event a data-file line describes, or null if its dated ends run backwards.
     *
     * <p>An event's ends are whatever the user wrote, spacing aside, so most pairs
     * cannot be compared at all. A pair that can be, and runs the wrong way, was edited
     * by hand into something the parser would have refused.
     *
     * @param description what is happening.
     * @param startText the first time field as it appears in the file.
     * @param endText the second time field as it appears in the file.
     * @return the event, or null if the line cannot be read.
     */
    private static Task readEvent(String description, String startText, String endText) {
        if (Event.hasBackwardsDates(startText, endText)) {
            return null;
        }
        return new Event(description, startText, endText);
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
        Optional<LocalDate> start = Task.readDate(startDateText);
        Optional<LocalDate> end = Task.readDate(endDateText);
        if (start.isEmpty() || end.isEmpty()) {
            return null;
        }
        // The parser refuses a backwards window, so a file holding one was edited
        // by hand; letting it through would crash the free-day search later.
        if (end.get().isBefore(start.get())) {
            return null;
        }
        return new Window(description, start.get(), end.get());
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
        Path partial = null;
        try {
            Path target = resolveSaveTarget();
            // A name of its own, so that a file already sitting at a fixed one is not
            // overwritten, and two Tallys saving at once do not write the same place.
            // It goes in the target's own folder, because the rename that puts it in
            // place is only atomic within one folder.
            partial = Files.createTempFile(getFolderOf(target), target.getFileName().toString(),
                    ".part");
            writeReplacement(tasks, target, partial);
            moveIntoPlace(partial, target);
        } catch (IOException exception) {
            deleteQuietly(partial);
            throw new TallyException(describeSaveFailure());
        }
    }

    /**
     * Puts the written replacement in place of the file it replaces.
     *
     * <p>Asked for as one indivisible step, so that a crash midway leaves either the old
     * tally or the new one and never a mixture. Not every file system can promise that,
     * and replacing without the promise is still better than writing into the file where
     * it lies, which a crash could leave half rewritten.
     *
     * @param partial the replacement that has been written.
     * @param target the file it replaces.
     * @throws IOException if it cannot be put in place.
     */
    private static void moveIntoPlace(Path partial, Path target) throws IOException {
        try {
            Files.move(partial, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
            // Whether an atomic move may replace a file that is already there is left
            // to the file system, and one that will not say so need not use the named
            // exception for it. Replacing without the promise is the fallback either
            // way, and if that fails too its own complaint is the one that gets out.
            Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING);
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
        Path target = followLinks(file);
        Files.createDirectories(getFolderOf(target));
        if (!Files.notExists(target) && !Files.isWritable(target)) {
            throw new TallyException(describeSaveFailure());
        }
        return target;
    }

    /**
     * Returns the folder a file sits in.
     *
     * <p>Taken from the absolute form of the path, because a path written as a bare
     * name, such as "tally.txt", has no parent of its own even though it plainly sits
     * somewhere.
     *
     * @param path the file whose folder is wanted.
     * @return the folder holding it.
     */
    private static Path getFolderOf(Path path) {
        return path.toAbsolutePath().getParent();
    }

    /**
     * Returns the file a path finally names, following symbolic links.
     *
     * <p>toRealPath covers a link pointing at a file that is there, but a link pointing
     * at one that is not resolves to nothing at all, and the save would then replace the
     * link itself with an ordinary file instead of writing through it.
     *
     * @param start the path to resolve.
     * @return what the last link in the chain names, which need not exist yet.
     * @throws IOException if a link cannot be read, or the chain does not end.
     */
    private static Path followLinks(Path start) throws IOException {
        Path here = start;
        for (int followed = 0; Files.isSymbolicLink(here); followed++) {
            if (followed == MAX_LINKS_FOLLOWED) {
                throw new IOException("Too many symbolic links to follow from " + start);
            }
            Path pointee = Files.readSymbolicLink(here);
            here = pointee.isAbsolute() ? pointee : here.resolveSibling(pointee);
        }
        return here;
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
        return "The record could not be saved to " + file.getFileName() + ".";
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
}
