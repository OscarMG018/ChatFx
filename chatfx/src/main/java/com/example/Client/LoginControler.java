package com.example.Client;

import com.example.Common.*;
import com.example.Common.MessageDecoder.*;

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
        Message message = client.sendMessage(Command.LOGIN, username.getText() + "," + password.getText());
        if(message.command.equals(Command.ACK)) {
            String[] payload = message.payload.split(",");
            User user = Client.user;
            user.displayName = payload[1];
            user.username = payload[0];
            SceneControler.getInstance().ChangeScene("chat.fxml");
        } else {
            error.setText(message.payload);
        }
    }

    public void goToSignupScene() {
        SceneControler.getInstance().ChangeScene("signup.fxml");
    }
}
