package com.example.Client;

import javafx.fxml.FXML;
import javafx.scene.control.*;


public class SignUpControler {
    @FXML
    private TextField username;
    @FXML
    private TextField displayName;
    @FXML
    private TextField password;
    @FXML
    private TextField confirmPassword;
    @FXML
    private Label error;

    public void signup() {
        if (!password.getText().equals(confirmPassword.getText())) {
            error.setText("Passwords do not match");
            return;
        }
        Client client = Client.getInstance();
        String message = client.sendMessage("SIGNUP:" + username.getText() + "," + displayName.getText() + "," + password.getText());
        System.out.println(message);
        if(message.substring(0, 2).equals("OK")) {
            User user = User.getCurrentUser();
            user.id = Integer.parseInt(message.split(",")[1]);
            user.username = username.getText();
            user.displayName = displayName.getText();
            user.password = password.getText();
            SceneControler.getInstance().ChangeScene("chat.fxml");
        }
        else {
            error.setText(message);
        }
    }

    public void goToLoginScene() {
        SceneControler.getInstance().ChangeScene("login.fxml");
    }
}
