package com.example.Client;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import com.example.Common.*;
import com.example.Common.MessageDecoder.*;


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
        Message message = client.sendMessage(Command.SIGNUP, username.getText() + "," + password.getText() + "," + displayName.getText());
        System.out.println(message);
        if(message.command.equals(Command.ACK)) {
            String[] payload = message.payload.split(",");
            User user = Client.user;
            user.username = payload[0];
            user.displayName = payload[1];
            SceneControler.getInstance().ChangeScene("chat.fxml");
        }
        else {
            error.setText(message.payload);
        }
    }

    public void goToLoginScene() {
        SceneControler.getInstance().ChangeScene("login.fxml");
    }
}
