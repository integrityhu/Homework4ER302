package hu.infokristaly.homework4nfcserialreader;

import javafx.application.Application;
import static javafx.application.Application.launch;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

public class MainApp extends Application {

    private Scene scene;
    private Stage mainStage;
    private FXMLController rootController;

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/Scene.fxml"));
        Parent root = loader.load();

        scene = new Scene(root);
        scene.getStylesheets().add("/styles/Styles.css");

        mainStage = stage;
        scene.getStylesheets().add("/styles/Styles.css");

        stage.setTitle("ER302 NFC reader-writer");
        stage.setScene(scene);
        stage.show();

        final FXMLController rootController = loader.<FXMLController>getController();
        mainStage.setOnCloseRequest(new EventHandler<WindowEvent>() {
            public void handle(WindowEvent we) {
                rootController.closeSerialPort();
            }
        });

    }

    /**
     * The main() method is ignored in correctly deployed JavaFX application.
     * main() serves only as fallback in case the application can not be
     * launched through deployment artifacts, e.g., in IDEs with limited FX
     * support. NetBeans ignores main().
     *
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void stop() throws Exception {
        super.stop(); //To change body of generated methods, choose Tools | Templates.
    }

}
