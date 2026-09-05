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
    TODO("todo"),
    DEADLINE("deadline"),
    EVENT("event"),
    WINDOW("window"),
    LIST("list"),
    MARK("mark"),
    UNMARK("unmark"),
    DELETE("delete"),
    FIND("find"),
    BYE("bye");

    private final String keyword;

    Command(String keyword) {
        this.keyword = keyword;
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
