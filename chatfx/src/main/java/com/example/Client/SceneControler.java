package com.example.Client;

import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import java.io.IOException;

public class SceneControler {
    static SceneControler instance;
    Stage stage;

    public static SceneControler getInstance() {
        if(instance == null) {
            instance = new SceneControler();
        }
        return instance;
    }	

    public static void setStage(Stage stage) {
        SceneControler.getInstance().stage = stage;
    }

    public void ChangeScene(String sceneName) {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(sceneName));
        try {
            Scene scene = new Scene(loader.load());
            stage.setScene(scene);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}