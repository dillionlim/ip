package tally.parser;

import java.util.Arrays;
import java.util.stream.Collectors;

import tally.TallyException;

/**
 * The commands Tally understands, each paired with the word the user types for it.
 *
 * <p>Holding the words here rather than spelling them out at each branch of the
 * dispatch means the list Tally offers when it does not recognize a command is
 * built from the same source, so the two cannot fall out of step.
 */
public enum Command {
    TODO("todo", true),
    DEADLINE("deadline", true),
    EVENT("event", true),
    WINDOW("window", true),
    LIST("list", false),
    MARK("mark", true),
    UNMARK("unmark", true),
    DELETE("delete", true),
    FIND("find", false),
    FREE("free", false),
    BYE("bye", false);

    private final String keyword;

    /** Whether carrying this command out can leave the tally different from before. */
    private final boolean changesTally;

    Command(String keyword, boolean changesTally) {
        this.keyword = keyword;
        this.changesTally = changesTally;
    }

    /**
     * Returns whether this command can change what is on the tally.
     *
     * <p>A command that only reads the tally needs no save afterwards, and saving
     * anyway would rewrite the file for a command that changed nothing.
     *
     * @return true for the commands that add, remove or alter a task.
     */
    public boolean changesTally() {
        return changesTally;
    }

    /**
     * Returns the word the user types to invoke this command.
     *
     * @return the command word, such as "mark".
     */
    public String getKeyword() {
        return keyword;
    }

    /**
     * Returns the command named by the given word.
     *
     * @param word the first word of the line the user typed.
     * @return the command that word names.
     * @throws TallyException if no command uses that word.
     */
    public static Command parse(String word) throws TallyException {
        return Arrays.stream(values())
                .filter(command -> command.keyword.equals(word))
                .findFirst()
                .orElseThrow(() -> new TallyException(
                        "I don't know that one. I understand: " + listKeywords() + "."));
    }

    /**
     * Returns every command word, in the order the commands are declared here.
     *
     * @return the words separated by commas, such as "todo, deadline, event".
     */
    public static String listKeywords() {
        return Arrays.stream(values())
                .map(command -> command.keyword)
                .collect(Collectors.joining(", "));
    }
}
