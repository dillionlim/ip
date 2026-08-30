package tally.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tally.TallyException;
import tally.task.Deadline;
import tally.task.Event;
import tally.task.Task;
import tally.task.Todo;

/** Tests that Storage writes a tally it can read back, and refuses one it cannot. */
public class StorageTest {
    @TempDir
    private Path folder;

    @Test
    public void load_noFileYet_returnsNoTasks() throws TallyException {
        assertTrue(new Storage(folder.resolve("tally.txt")).load().isEmpty());
    }

    @Test
    public void saveThenLoad_everyTaskType_survivesUnchanged() throws TallyException {
        Path file = folder.resolve("tally.txt");
        Storage storage = new Storage(file);

        Task done = new Todo("read book");
        done.markAsDone();
        List<Task> written = List.of(done,
                new Deadline("return book", LocalDate.of(2019, 6, 6)),
                new Event("project meeting", "Aug 6th 2pm", "4pm"));
        storage.save(written);

        List<Task> read = storage.load();
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
        assertEquals(1, storage.load().size());
    }

    @Test
    public void load_lineNotInTheSavedFormat_throws() throws IOException {
        Path file = folder.resolve("tally.txt");
        Files.writeString(file, "T | 0 | read book\nthis line is nonsense\n");
        assertThrows(TallyException.class, () -> new Storage(file).load());
    }

    @Test
    public void load_dateNotInTheSavedFormat_throws() throws IOException {
        Path file = folder.resolve("tally.txt");
        Files.writeString(file, "D | 0 | return book | last Tuesday\n");
        assertThrows(TallyException.class, () -> new Storage(file).load());
    }

    @Test
    public void load_wrongNumberOfFields_throws() throws IOException {
        Path file = folder.resolve("tally.txt");
        Files.writeString(file, "T | 0 | read book | extra field\n");
        assertThrows(TallyException.class, () -> new Storage(file).load());
    }

    @Test
    public void load_doneFlagNotZeroOrOne_throws() throws IOException {
        Path file = folder.resolve("tally.txt");
        Files.writeString(file, "T | maybe | read book\n");
        assertThrows(TallyException.class, () -> new Storage(file).load());
    }

    @Test
    public void load_damagedFile_movedAsideWithItsContentsKept() throws IOException {
        Path file = folder.resolve("tally.txt");
        String original = "T | 0 | precious task\nthis line is nonsense\n";
        Files.writeString(file, original);

        assertThrows(TallyException.class, () -> new Storage(file).load());

        // The damaged file must survive: the next save would otherwise write over it.
        Path spoiled = folder.resolve("tally.txt.broken");
        assertTrue(Files.exists(spoiled));
        assertFalse(Files.exists(file));
        assertEquals(original, Files.readString(spoiled));
    }

    @Test
    public void load_blankLines_ignored() throws IOException, TallyException {
        Path file = folder.resolve("tally.txt");
        Files.writeString(file, "\nT | 0 | read book\n\n\nT | 1 | return book\n\n");
        assertEquals(2, new Storage(file).load().size());
    }
}
