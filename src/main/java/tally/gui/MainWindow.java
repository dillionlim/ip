package tally.gui;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import tally.Tally;

/** Drives the main window: takes what is typed, and shows what Tally says back. */
public class MainWindow extends AnchorPane {
    @FXML
    private ScrollPane scrollPane;
    @FXML
    private VBox dialogContainer;
    @FXML
    private TextField userInput;
    @FXML
    private Button sendButton;

    private Tally tally;
    private final Image userImage =
            new Image(this.getClass().getResourceAsStream("/images/user.png"));
    private final Image tallyImage =
            new Image(this.getClass().getResourceAsStream("/images/tally.png"));

    /** Scrolls down whenever the conversation grows, so the newest message is in view. */
    @FXML
    public void initialize() {
        dialogContainer.heightProperty().addListener(observed -> scrollPane.setVvalue(1.0));
    }

    /**
     * Attaches the chatbot this window talks to, and shows its greeting.
     *
     * @param tally the chatbot.
     */
    public void setTally(Tally tally) {
        this.tally = tally;
        dialogContainer.getChildren().add(
                DialogBox.getTallyDialog(tally.getGreeting(), tallyImage));
    }

    /**
     * Sends whatever was typed to the chatbot and shows both sides of the exchange.
     *
     * <p>Saying goodbye closes the window once the farewell has been shown, rather
     * than at once, so the user sees the reply they asked for.
     */
    @FXML
    private void handleUserInput() {
        String input = userInput.getText();
        if (input.isBlank()) {
            return;
        }
        String response = tally.getResponse(input);
        dialogContainer.getChildren().addAll(
                DialogBox.getUserDialog(input, userImage),
                DialogBox.getTallyDialog(response, tallyImage));
        userInput.clear();
        if (tally.isExiting()) {
            userInput.setDisable(true);
            sendButton.setDisable(true);
            PauseTransition pause = new PauseTransition(Duration.seconds(1.5));
            pause.setOnFinished(event -> Platform.exit());
            pause.play();
        }
    }
}
