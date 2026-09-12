package tally.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tally.TallyException;
import tally.task.Deadline;
import tally.task.Event;
import tally.task.Task;
import tally.task.Todo;
import tally.task.Window;

/** Tests that Storage writes a tally it can read back, and refuses one it cannot. */
public class StorageTest {
    @TempDir
    private Path folder;

    /**
     * Makes a symbolic link, skipping the test where the file system has no such thing.
     *
     * @param link where the link goes.
     * @param target what it points at, which need not exist.
     */
    private static void linkOrSkip(Path link, Path target) {
        try {
            Files.createSymbolicLink(link, target);
        } catch (IOException | UnsupportedOperationException exception) {
            assumeTrue(false, "this file system does not allow symbolic links");
        }
    }

    @Test
    public void load_backwardsWindow_isSkipped() throws TallyException, IOException {
        Path file = folder.resolve("tally.txt");
        // The parser refuses one, so a file holding it was edited by hand. Letting it
        // through used to crash the free-day search.
        Files.writeString(file, "W | 0 | submit form | 2026-09-12 | 2026-09-08\n");

        LoadResult loaded = new Storage(file).load();
        assertTrue(loaded.tasks().isEmpty());
        assertTrue(loaded.note().orElseThrow().contains("Line 1"));
    }

    @Test
    public void save_fileTheUserProtected_isRefusedRatherThanReplaced() throws IOException {
        Path file = folder.resolve("tally.txt");
        Files.writeString(file, "T | 0 | protected\n");
        assumeTrue(file.toFile().setReadOnly(), "this file system has no read-only bit");

        Storage storage = new Storage(file);
        // Renaming over a file needs write permission on the directory rather than the
        // file, so without a check of its own the rename would go straight through.
        assertThrows(TallyException.class, () -> storage.save(List.of(new Todo("sneaky"))));
        assertEquals("T | 0 | protected", Files.readString(file).strip());
    }

    @Test
    public void save_fileWithItsOwnPermissions_keepsThem() throws TallyException, IOException {
        Path file = folder.resolve("tally.txt");
        Files.writeString(file, "T | 0 | private\n");
        assumeTrue(Files.getFileStore(file).supportsFileAttributeView(PosixFileAttributeView.class));
        Set<PosixFilePermission> ownerOnlyPermissions = PosixFilePermissions.fromString("rw-------");
        Files.setPosixFilePermissions(file, ownerOnlyPermissions);

        new Storage(file).save(List.of(new Todo("another")));
        assertEquals(ownerOnlyPermissions, Files.getPosixFilePermissions(file));
    }

    @Test
    public void save_dataFileIsASymbolicLink_writesThroughIt() throws TallyException, IOException {
        Path real = folder.resolve("actual.txt");
        Files.writeString(real, "T | 0 | alpha\n");
        Path link = folder.resolve("tally.txt");
        linkOrSkip(link, real);

        new Storage(link).save(List.of(new Todo("alpha"), new Todo("beta")));
        assertTrue(Files.isSymbolicLink(link), "the link was replaced by a regular file");
        assertTrue(Files.readString(real).contains("beta"), "the link was not written through");
    }

    @Test
    public void save_danglingSymbolicLink_writesThroughItRatherThanReplacingIt()
            throws TallyException, IOException {
        // The link points at a file that is not there yet, which is what separates this
        // from the case above: there is nothing for toRealPath to resolve the link to.
        Path real = folder.resolve("actual.txt");
        Path link = folder.resolve("tally.txt");
        linkOrSkip(link, real);

        new Storage(link).save(List.of(new Todo("alpha")));
        assertTrue(Files.isSymbolicLink(link), "the link was replaced by a regular file");
        assertEquals(List.of("T | 0 | alpha"), Files.readAllLines(real));
    }

    @Test
    public void save_damageThatCouldNotBeCopiedAside_isRefused() throws TallyException, IOException {
        Path file = folder.resolve("tally.txt");
        Files.writeString(file, "T | 0 | read book\nBAD LINE\n");
        // A rescue copy of some earlier damage, which cannot be read to tell the two
        // apart, so this damage cannot be copied aside anywhere.
        Path occupied = folder.resolve("tally.txt.broken");
        Files.writeString(occupied, "older damage\n");
        assumeTrue(Files.getFileStore(file).supportsFileAttributeView(PosixFileAttributeView.class),
                "this file system does not carry POSIX permissions");
        Files.setPosixFilePermissions(occupied, PosixFilePermissions.fromString("---------"));
        assumeTrue(!Files.isReadable(occupied), "these tests are running as a user nothing stops");

        Storage storage = new Storage(file);
        LoadResult loaded = storage.load();
        assertEquals(1, loaded.tasks().size());
        assertTrue(loaded.note().orElseThrow().contains("could not be copied aside"));

        // Saving the one task that loaded would drop the damaged line for good.
        List<Task> readableTasks = loaded.tasks();
        TallyException refused = assertThrows(
                TallyException.class, () -> storage.save(readableTasks));
        assertTrue(refused.getMessage().contains("will not be written over"),
                refused.getMessage());
        assertTrue(Files.readString(file).contains("BAD LINE"), "the damaged line was lost");
    }

    @Test
    public void load_backupNameHoldsALinkToTheFile_keepsARealCopyElsewhere()
            throws TallyException, IOException {
        Path file = folder.resolve("tally.txt");
        Files.writeString(file, "T | 0 | good\nBAD LINE\n");
        Path decoy = folder.resolve("tally.txt.broken");
        linkOrSkip(decoy, file);

        Storage storage = new Storage(file);
        LoadResult loaded = storage.load();
        // A link back to the data file holds the same bytes, so it looked like a copy
        // while keeping nothing: the save below wrote through it and took the damage.
        assertTrue(loaded.note().orElseThrow().contains("tally.txt.broken.1"),
                loaded.note().orElseThrow());

        storage.save(loaded.tasks());
        assertTrue(Files.readString(folder.resolve("tally.txt.broken.1")).contains("BAD LINE"),
                "the damaged line was kept nowhere");
        assertFalse(Files.readString(file).contains("BAD LINE"));
    }

    @Test
    public void load_theSameDamageTwice_isCopiedAsideOnce() throws TallyException, IOException {
        Path file = folder.resolve("tally.txt");
        Files.writeString(file, "T | 0 | good\nBAD LINE\n");

        new Storage(file).load();
        String second = new Storage(file).load().note().orElseThrow();

        // Reading does not rewrite the file, so an unrepaired one would otherwise be
        // copied again on every start, without limit.
        try (Stream<Path> keptFiles = Files.list(folder)) {
            assertEquals(1, (int) keptFiles.filter(each ->
                    each.getFileName().toString().contains(".broken")).count());
        }
        assertTrue(second.contains("Already copied"));
    }

    @Test
    public void save_writeFails_leavesTheFileAsItWas() throws IOException {
        Path lockedFolder = Files.createDirectory(folder.resolve("locked"));
        Path file = lockedFolder.resolve("tally.txt");
        Files.writeString(file, "T | 0 | keep me\n");
        // A folder that will take no new file is what a full disk looks like from here:
        // the replacement cannot be written, and what is already saved has to survive.
        assumeTrue(lockedFolder.toFile().setWritable(false), "no read-only bit here");
        assumeTrue(!Files.isWritable(lockedFolder), "the bit does not bind for this user");

        try {
            Storage storage = new Storage(file);
            assertThrows(TallyException.class, () -> storage.save(List.of(new Todo("new task"))));
            assertEquals("T | 0 | keep me", Files.readString(file).strip());
        } finally {
            lockedFolder.toFile().setWritable(true);
        }
    }

    @Test
    public void load_damagedTwice_keepsEveryRescueCopy() throws TallyException, IOException {
        Path file = folder.resolve("tally.txt");
        Files.writeString(file, "T | 0 | first attempt\nBAD LINE\n");
        assertTrue(new Storage(file).load().note().isPresent());

        Files.writeString(file, "T | 0 | second attempt\nWORSE LINE\n");
        assertTrue(new Storage(file).load().note().isPresent());

        // The second damaged file must not overwrite what the first one rescued.
        assertTrue(Files.readString(folder.resolve("tally.txt.broken")).contains("first attempt"));
        assertTrue(Files.readString(folder.resolve("tally.txt.broken.1")).contains("second attempt"));
    }

    @Test
    public void load_noFileYet_returnsNoTasks() throws TallyException {
        assertTrue(new Storage(folder.resolve("tally.txt")).load().tasks().isEmpty());
    }

    @Test
    public void saveThenLoad_everyTaskType_survivesUnchanged() throws TallyException {
        Path file = folder.resolve("tally.txt");
        Storage storage = new Storage(file);

        Task done = new Todo("read book");
        done.markAsDone();
        List<Task> savedTasks = List.of(done,
                new Deadline("return book", LocalDate.of(2019, 6, 6)),
                new Event("project meeting", "Aug 6th 2pm", "4pm"),
                new Window("submit form", LocalDate.of(2026, 9, 8), LocalDate.of(2026, 9, 12)));
        storage.save(savedTasks);

        List<Task> loadedTasks = storage.load().tasks();
        assertEquals(savedTasks.size(), loadedTasks.size());
        for (int i = 0; i < savedTasks.size(); i++) {
            assertEquals(savedTasks.get(i).toString(), loadedTasks.get(i).toString());
        }
    }

    @Test
    public void save_missingDirectory_createsIt() throws TallyException {
        Path file = folder.resolve("nested").resolve("deeper").resolve("tally.txt");
        new Storage(file).save(List.of(new Todo("read book")));
        assertTrue(Files.exists(file));
    }

    @Test
    public void save_calledAgain_replacesWhatWasThereBefore() throws TallyException {
        Path file = folder.resolve("tally.txt");
        Storage storage = new Storage(file);
        storage.save(List.of(new Todo("first"), new Todo("second")));
        storage.save(List.of(new Todo("only")));
        assertEquals(1, storage.load().tasks().size());
    }

    @Test
    public void load_lineNotInTheSavedFormat_isSkippedAndReported() throws TallyException, IOException {
        Path file = folder.resolve("tally.txt");
        Files.writeString(file, "T | 0 | read book\nthis line is nonsense\n");

        LoadResult loaded = new Storage(file).load();
        assertEquals(1, loaded.tasks().size());
        assertEquals("[T][ ] read book", loaded.tasks().get(0).toString());
        assertTrue(loaded.note().orElseThrow().contains("Line 2"));
    }

    @Test
    public void load_unreadableDateOrFieldsOrFlag_isSkippedAndReported() throws TallyException, IOException {
        Path file = folder.resolve("tally.txt");
        Files.writeString(file, "D | 0 | return book | last Tuesday\n"
                + "T | 0 | read book | extra field\n"
                + "T | maybe | read book\n"
                + "T | 0 | the only good one\n");

        LoadResult loaded = new Storage(file).load();
        assertEquals(1, loaded.tasks().size());
        assertEquals("[T][ ] the only good one", loaded.tasks().get(0).toString());
        assertTrue(loaded.note().orElseThrow().contains("Lines 1, 2 and 3"));
    }

    @Test
    public void load_everyLineReadable_reportsNothing() throws TallyException, IOException {
        Path file = folder.resolve("tally.txt");
        Files.writeString(file, "T | 0 | read book\nT | 1 | return book\n");

        LoadResult loaded = new Storage(file).load();
        assertEquals(2, loaded.tasks().size());
        assertTrue(loaded.note().isEmpty());
    }

    @Test
    public void load_damagedFile_copiedAsideWithItsContentsKept() throws TallyException, IOException {
        Path file = folder.resolve("tally.txt");
        String original = "T | 0 | precious task\nthis line is nonsense\n";
        Files.writeString(file, original);

        new Storage(file).load();

        // Copied, not moved: the tasks that loaded stay on the tally, so the next save
        // writes over the original and would otherwise take the bad line with it.
        Path spoiled = folder.resolve("tally.txt.broken");
        assertTrue(Files.exists(spoiled));
        assertTrue(Files.exists(file));
        assertEquals(original, Files.readString(spoiled));
    }

    @Test
    public void load_blankLines_ignored() throws IOException, TallyException {
        Path file = folder.resolve("tally.txt");
        Files.writeString(file, "\nT | 0 | read book\n\n\nT | 1 | return book\n\n");
        assertEquals(2, new Storage(file).load().tasks().size());
    }

    @Test
    public void load_aLineOfEachKind_readsThemBackAsThemselves()
            throws TallyException, IOException {
        Path file = folder.resolve("tally.txt");
        Files.writeString(file, String.join("\n",
                "T | 0 | read book",
                "D | 1 | return book | 2019-10-15",
                "E | 0 | project meeting | Mon 2pm | 4pm",
                "W | 0 | submit form | 2026-09-08 | 2026-09-12") + "\n");

        List<Task> loaded = new Storage(file).load().tasks();
        assertEquals(List.of("[T][ ] read book",
                "[D][X] return book (by: Oct 15 2019)",
                "[E][ ] project meeting (from: Mon 2pm to: 4pm)",
                "[W][ ] submit form (window: Sep 08 2026 to Sep 12 2026)"),
                loaded.stream().map(Task::toString).toList());
    }

    @Test
    public void load_aTypeLetterNobodyWrites_isSkippedLikeAnyOtherDamage()
            throws TallyException, IOException {
        Path file = folder.resolve("tally.txt");
        Files.writeString(file, "T | 0 | read book\nQ | 0 | whatever this is\n");

        LoadResult loaded = new Storage(file).load();
        assertEquals(1, loaded.tasks().size());
        assertTrue(loaded.note().orElseThrow().contains("Line 2"), loaded.note().orElseThrow());
    }

    @Test
    public void save_symbolicLinksPointingAtEachOther_isRefusedRatherThanFollowedForever()
            throws IOException {
        Path first = folder.resolve("tally.txt");
        Path second = folder.resolve("other.txt");
        linkOrSkip(first, second);
        linkOrSkip(second, first);

        // Following them one after another never reaches a file, so the chain is given
        // a limit rather than being walked until the program stops responding.
        List<Task> tasks = List.of(new Todo("read book"));
        Storage storage = new Storage(first);
        assertThrows(TallyException.class, () -> storage.save(tasks));
    }

    @Test
    public void load_aLineWithTheWrongNumberOfFields_isSkippedAndReported()
            throws TallyException, IOException {
        Path file = folder.resolve("tally.txt");
        // Each of these names a kind of task, then gives it the wrong number of parts.
        Files.writeString(file, String.join("\n",
                "T | 0 | read book | extra",
                "D | 0 | return book",
                "E | 0 | meeting | 2pm",
                "W | 0 | form | 2026-09-08",
                "T | 0 | the only good line") + "\n");

        LoadResult loaded = new Storage(file).load();
        assertEquals(1, loaded.tasks().size());
        assertEquals("[T][ ] the only good line", loaded.tasks().get(0).toString());
        assertTrue(loaded.note().orElseThrow().contains("1, 2, 3 and 4"),
                loaded.note().orElseThrow());
    }

    @Test
    public void load_aWindowWhoseDatesCannotBeRead_isSkipped() throws TallyException, IOException {
        Path file = folder.resolve("tally.txt");
        Files.writeString(file, "W | 0 | submit form | last Tuesday | 2026-09-12\n");

        LoadResult loaded = new Storage(file).load();
        assertTrue(loaded.tasks().isEmpty());
        assertTrue(loaded.note().orElseThrow().contains("Line 1"));
    }

    @Test
    public void load_aBlankFieldWhereOneIsRequired_isSkipped() throws TallyException, IOException {
        Path file = folder.resolve("tally.txt");
        // A blank done flag, and a blank description: neither is a task anyone wrote.
        Files.writeString(file, "T |   | read book\nT | 0 |   \nT | 0 | good\n");

        LoadResult loaded = new Storage(file).load();
        assertEquals(1, loaded.tasks().size());
        assertTrue(loaded.note().orElseThrow().contains("1 and 2"), loaded.note().orElseThrow());
    }

    @Test
    public void save_aLinkNamedRelativeToItsOwnFolder_isFollowedToWhereItPoints()
            throws TallyException, IOException {
        Path link = folder.resolve("tally.txt");
        // Named without a folder, so it resolves against the link's own folder.
        linkOrSkip(link, Path.of("actual.txt"));

        new Storage(link).save(List.of(new Todo("read book")));
        assertEquals(List.of("T | 0 | read book"),
                Files.readAllLines(folder.resolve("actual.txt")));
        assertTrue(Files.isSymbolicLink(link), "the link was replaced by a regular file");
    }

    @Test
    public void load_aFileAnEditorMarkedAsUtf8_readsItsFirstTaskAnyway()
            throws TallyException, IOException {
        Path file = folder.resolve("tally.txt");
        // Editors on Windows write this at the start and do not show it, so a user who
        // opened the file to correct a line saves it back with the mark on task one.
        Files.writeString(file, "\ufeffT | 0 | read book\nT | 1 | buy bread\n");

        LoadResult loaded = new Storage(file).load();
        assertEquals(2, loaded.tasks().size(), loaded.note().orElse("no complaint"));
        assertEquals("[T][ ] read book", loaded.tasks().get(0).toString());
        assertTrue(loaded.note().isEmpty(), loaded.note().orElse(""));
    }

    @Test
    public void load_unreadableFileThatCannotBeCopied_saysEachThingOnce()
            throws IOException {
        Path file = folder.resolve("tally.txt");
        Files.writeString(file, "T | 0 | read book\n");
        Path occupied = folder.resolve("tally.txt.broken");
        Files.writeString(occupied, "older damage\n");
        assumeTrue(Files.getFileStore(file).supportsFileAttributeView(PosixFileAttributeView.class),
                "this file system does not carry POSIX permissions");
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("---------"));
        Files.setPosixFilePermissions(occupied, PosixFilePermissions.fromString("---------"));
        assumeTrue(!Files.isReadable(file), "these tests are running as a user nothing stops");

        TallyException thrown = assertThrows(TallyException.class, () -> new Storage(file).load());
        String message = thrown.getMessage();
        // Both the read and the copy failing used to append the same refusal, so the
        // user was told twice, in two different wordings, in one breath.
        assertEquals(1, message.split("will not be written over", -1).length - 1, message);
    }

    @Test
    public void load_eventWithDatedEndsRunningBackwards_isSkipped()
            throws TallyException, IOException {
        Path file = folder.resolve("tally.txt");
        // The parser refuses this, so a file holding it was edited by hand.
        Files.writeString(file, "E | 0 | trip | 2026-09-12 | 2026-09-08\nT | 0 | good\n");

        LoadResult loaded = new Storage(file).load();
        assertEquals(1, loaded.tasks().size());
        assertTrue(loaded.note().orElseThrow().contains("Line 1"), loaded.note().orElseThrow());
    }

    @Test
    public void load_aTaskNamedTwice_isKeptOnceAndReported() throws TallyException, IOException {
        Path file = folder.resolve("tally.txt");
        // Tally refuses to add a task it already holds, so a file naming one twice
        // would otherwise put the tally in a state no command could have reached.
        // The two copies disagree about being done, and dropping one must not throw
        // that away: a task recorded as done anywhere in the file has been done.
        Files.writeString(file, "T | 0 | read book\nT | 1 | read book\nT | 0 | buy bread\n");

        LoadResult loaded = new Storage(file).load();
        assertEquals(List.of("[T][X] read book", "[T][ ] buy bread"),
                loaded.tasks().stream().map(Task::toString).toList());
        String note = loaded.note().orElseThrow();
        assertTrue(note.contains("Line 2"), note);
        assertTrue(note.contains("repeats a task already on record"), note);
    }

    @Test
    public void load_repeatsButNoDamage_keepsNoRescueCopy() throws TallyException, IOException {
        Path file = folder.resolve("tally.txt");
        Files.writeString(file, "T | 0 | read book\nT | 0 | read book\n");

        new Storage(file).load();
        // A repeat is a readable line, not damage, so there is nothing to quarantine.
        try (Stream<Path> left = Files.list(folder)) {
            assertEquals(0, (int) left.filter(each ->
                    each.getFileName().toString().contains(".broken")).count());
        }
    }

    @Test
    public void load_repeatsThatAgreeOnBeingDone_leaveTheFlagAlone()
            throws TallyException, IOException {
        Path file = folder.resolve("tally.txt");
        Files.writeString(file, "T | 0 | read book\nT | 0 | read book\n");

        LoadResult loaded = new Storage(file).load();
        assertEquals(List.of("[T][ ] read book"),
                loaded.tasks().stream().map(Task::toString).toList());
    }
}
