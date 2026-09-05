package tally.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    public void save_keepsThePermissionsTheFileAlreadyHad() throws TallyException, IOException {
        Path file = folder.resolve("tally.txt");
        Files.writeString(file, "T | 0 | private\n");
        assumeTrue(Files.getFileStore(file).supportsFileAttributeView(PosixFileAttributeView.class));
        Set<PosixFilePermission> onlyMine = PosixFilePermissions.fromString("rw-------");
        Files.setPosixFilePermissions(file, onlyMine);

        new Storage(file).save(List.of(new Todo("another")));
        assertEquals(onlyMine, Files.getPosixFilePermissions(file));
    }

    @Test
    public void save_dataFileIsASymbolicLink_writesThroughIt() throws TallyException, IOException {
        Path real = folder.resolve("actual.txt");
        Files.writeString(real, "T | 0 | alpha\n");
        Path link = folder.resolve("tally.txt");
        try {
            Files.createSymbolicLink(link, real);
        } catch (IOException | UnsupportedOperationException exception) {
            assumeTrue(false, "this file system does not allow symbolic links");
        }

        new Storage(link).save(List.of(new Todo("alpha"), new Todo("beta")));
        assertTrue(Files.isSymbolicLink(link), "the link was replaced by a regular file");
        assertTrue(Files.readString(real).contains("beta"), "the link was not written through");
    }

    @Test
    public void load_theSameDamageTwice_isCopiedAsideOnce() throws TallyException, IOException {
        Path file = folder.resolve("tally.txt");
        Files.writeString(file, "T | 0 | good\nBAD LINE\n");

        new Storage(file).load();
        String second = new Storage(file).load().note().orElseThrow();

        // Reading does not rewrite the file, so an unrepaired one would otherwise be
        // copied again on every start, without limit.
        assertEquals(1, (int) Files.list(folder).filter(each ->
                each.getFileName().toString().contains(".broken")).count());
        assertTrue(second.contains("already copied"));
    }

    @Test
    public void save_writeFails_leavesTheFileAsItWas() throws IOException {
        Path file = folder.resolve("tally.txt");
        Files.writeString(file, "T | 0 | keep me\n");
        // A directory where the half-written copy must go makes the write fail, which is
        // what a full disk would do. The tally already saved has to survive that.
        Files.createDirectory(folder.resolve("tally.txt.part"));

        Storage storage = new Storage(file);
        assertThrows(TallyException.class, () -> storage.save(List.of(new Todo("new task"))));
        assertEquals("T | 0 | keep me", Files.readString(file).strip());
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
        List<Task> written = List.of(done,
                new Deadline("return book", LocalDate.of(2019, 6, 6)),
                new Event("project meeting", "Aug 6th 2pm", "4pm"),
                new Window("submit form", LocalDate.of(2026, 9, 8), LocalDate.of(2026, 9, 12)));
        storage.save(written);

        List<Task> read = storage.load().tasks();
        assertEquals(written.size(), read.size());
        for (int i = 0; i < written.size(); i++) {
            assertEquals(written.get(i).toString(), read.get(i).toString());
        }
    }

    @Test
    public void save_missingDirectory_createsIt() throws TallyException {
        Path file = folder.resolve("nested").resolve("deeper").resolve("tally.txt");
        new Storage(file).save(List.of(new Todo("read book")));
        assertTrue(Files.exists(file));
    }

    @Test
    public void save_replacesWhatWasThereBefore() throws TallyException {
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
        assertTrue(loaded.note().orElseThrow().contains("line 2"));
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
        assertTrue(loaded.note().orElseThrow().contains("lines 1, 2 and 3"));
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
}
