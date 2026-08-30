package tally.gui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;

/** One turn of the conversation: a picture of whoever spoke, and what they said. */
public class DialogBox extends HBox {
    @FXML
    private Label dialog;
    @FXML
    private ImageView displayPicture;

    private DialogBox(String text, Image picture) {
        try {
            FXMLLoader loader = new FXMLLoader(DialogBox.class.getResource("/view/DialogBox.fxml"));
            loader.setController(this);
            loader.setRoot(this);
            loader.load();
        } catch (IOException exception) {
            throw new IllegalStateException("A dialog box could not be built.", exception);
        }
        dialog.setText(text);
        displayPicture.setImage(picture);
    }

    /**
     * Returns a box showing what the user said, with their picture on the right.
     *
     * @param text what the user typed.
     * @param picture the user's picture.
     * @return the box to add to the conversation.
     */
    public static DialogBox getUserDialog(String text, Image picture) {
        return new DialogBox(text, picture);
    }

    /**
     * Returns a box showing what Tally said, with its picture on the left.
     *
     * <p>Tally's boxes are mirrored so the two speakers face each other, which is
     * what lets a reader tell them apart without reading a word.
     *
     * @param text what Tally replied.
     * @param picture Tally's picture.
     * @return the box to add to the conversation.
     */
    public static DialogBox getTallyDialog(String text, Image picture) {
        DialogBox box = new DialogBox(text, picture);
        box.flip();
        return box;
    }

    /** Puts this box's picture on the left of its text rather than the right. */
    private void flip() {
        List<Node> children = new ArrayList<>(getChildren());
        Collections.reverse(children);
        getChildren().setAll(children);
        setAlignment(Pos.TOP_LEFT);
    }
}
