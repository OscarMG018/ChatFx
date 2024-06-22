package com.example.Client;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import com.example.Common.*;
import com.example.Common.ServerMessage.*;


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
        ServerMessage message = client.sendMessage("SIGNUP:" + username.getText() + "," + displayName.getText() + "," + password.getText());
        System.out.println(message);
        if(message.code.equals(Code.OK)) {
            User user = Client.user;
            user.id = Integer.parseInt(message.content[0]);
            user.username = username.getText();
            user.displayName = displayName.getText();
            user.password = password.getText();
            SceneControler.getInstance().ChangeScene("chat.fxml");
        }
        else {
            error.setText(message.code.toString());
        }
    }

    public void goToLoginScene() {
        SceneControler.getInstance().ChangeScene("login.fxml");
    }
}
