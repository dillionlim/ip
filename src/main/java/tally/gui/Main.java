package tally.gui;

import java.io.IOException;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;
import tally.Tally;

/** The window Tally runs in. */
public class Main extends Application {
    /** Where the tally is kept, relative to the folder the app is started from. */

    private final Tally tally = new Tally(false);

    /**
     * Builds the window and shows it.
     *
     * @param stage the window supplied by JavaFX.
     */
    @Override
    public void start(Stage stage) {
        try {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("/view/MainWindow.fxml"));
            AnchorPane root = loader.load();
            Scene scene = new Scene(root);
            stage.setScene(scene);
            stage.setTitle("Tally");
            stage.setMinHeight(420.0);
            stage.setMinWidth(460.0);
            loader.<MainWindow>getController().setTally(tally);
            stage.show();
        } catch (IOException exception) {
            throw new IllegalStateException("The window could not be built.", exception);
        }
    }
}
