/**
 * Signals that Tally could not carry out what the user asked for.
 *
 * <p>The message of a TallyException is written to be shown to the user as it is,
 * so it should say what went wrong and how to put it right.
 */
public class TallyException extends Exception {
    /**
     * Creates an exception carrying an explanation meant for the user to read.
     *
     * @param message what went wrong, and how to correct it.
     */
    public TallyException(String message) {
        super(message);
    }
}
