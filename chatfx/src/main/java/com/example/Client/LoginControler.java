package com.example.Client;

import com.example.Common.*;
import com.example.Common.ServerMessage.*;

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
        ServerMessage message = client.sendMessage("LOGIN:" + username.getText() + "," + password.getText());
        System.out.println(message);
        if(message.code.equals(Code.OK)) {
            User user = Client.user;
            user.id = Integer.parseInt(message.content[0]);
            user.displayName = message.content[1];
            user.username = username.getText();
            user.password = password.getText();
            SceneControler.getInstance().ChangeScene("chat.fxml");
        } else {
            error.setText(message.code.toString());
        }
    }

    public void goToSignupScene() {
        SceneControler.getInstance().ChangeScene("signup.fxml");
    }
}
