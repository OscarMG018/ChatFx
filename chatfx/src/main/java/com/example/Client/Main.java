package com.example.Client;

import org.sqlite.Function.Window;

import javafx.application.Application;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;


public class Main extends Application {
    private static Client client;
    
    public static void main(String[] args) {launch(args);}

    @Override
    public void start(Stage stage) {
        client = Client.getInstance();
        SceneControler.setStage(stage);
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("login.fxml"));
            Scene scene = new Scene(fxmlLoader.load());
            stage.setScene(scene);
            stage.show();
            stage.setOnCloseRequest(this::close);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void close(WindowEvent e) {
        client.close();
    }
}