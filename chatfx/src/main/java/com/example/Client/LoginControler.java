package com.example.Client;

import javafx.fxml.FXML;
import javafx.scene.control.*;

public class LoginControler {
    @FXML
    private TextField username;
    @FXML
    private TextField password;
    @FXML
    private Label error;

    public void login() {
        Client client = Client.getInstance();
        String message = client.sendMessage("LOGIN:" + username.getText() + "," + password.getText());
        System.out.println(message);
        if(message.substring(0, 2).equals("OK")) {
            String[] parts = message.split(",");
            User user = User.getCurrentUser();
            user.id = Integer.parseInt(parts[1]);
            user.displayName = parts[2];
            user.username = username.getText();
            user.password = password.getText();
            SceneControler.getInstance().ChangeScene("chat.fxml");
        } else {
            error.setText(message);
        }
    }

    public void goToSignupScene() {
        SceneControler.getInstance().ChangeScene("signup.fxml");
    }
}
