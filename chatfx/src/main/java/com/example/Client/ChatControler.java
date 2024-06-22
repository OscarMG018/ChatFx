package com.example.Client;

import java.util.*;
import com.example.Common.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.io.*;
import javafx.application.Platform;
import javafx.fxml.*;
import java.net.*;
import java.time.*;

public class ChatControler implements Initializable {
    @FXML
    private TextField message;
    @FXML
    private VBox chatBox;
    @FXML
    private ScrollPane scrollPane;
    @FXML
    private VBox GroupList;

    private Client client;
    private User user;
    private ArrayList<Group> groups;
    private int ActiveGroup = 0;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        client = Client.getInstance();
        user = Client.user;
        groups = new ArrayList<Group>();
        LoadGroups();
        //groups.add(new Group(1,"Global"));
        Thread t = new Thread(new MessageReciver(chatBox, client, user, this));
        t.start();
    }

    public void addMessage(String groupId, String displayName, String message) {
        for(Group group : groups) {
            if(group.getId() == Integer.parseInt(groupId)) {
                group.addMessage(displayName, message);
                if (group.getId() == groups.get(ActiveGroup).getId()) {
                    //Add message to chatBox
                    Platform.runLater(() ->{
                        Label label = new Label(displayName + ": " + message);
                        label.setWrapText(true);
                        chatBox.getChildren().add(label);
                        scrollPane.setVvalue(1.0);
                    });
                }
            }
        }
    }

    public void sendMessage() {
        String msg = message.getText();
        if (msg.isEmpty()) {
            message.setText("");
            return;
        }
        message.setText("");
        client.sendMessageWithoutResponse("CHATMESSAGE:" +groups.get(ActiveGroup).getId()+",<TEXT!"+ msg.length() +">" +msg);
        addMessage(""+groups.get(ActiveGroup).getId(), user.displayName, msg);
    }

    public void LoadGroups() {
        ServerMessage msg = client.sendMessage("GETGROUPS:" + user.id);
        System.out.println(msg);
        for (int i = 0; i<msg.content.length;i+=2) {
            final int index = i/2;  
            int id = Integer.parseInt(msg.content[i]);
            String groupName = msg.content[i+1];
            groups.add(new Group(id, groupName));
            Platform.runLater(() ->{  
                //Add group to groupList
                Button button = new Button(groupName);
                button.setMaxWidth(Double.MAX_VALUE);
                button.setPrefHeight(50);
                button.setStyle("-fx-font-size: 20px;");
                button.setOnAction(e -> {
                    ActiveGroup = index;
                    groups.get(ActiveGroup).ViewMessages(chatBox);
                });
                GroupList.getChildren().add(button);
            });
        }
    }
}

class MessageReciver implements Runnable {

    private VBox chatBox;
    private Client client;
    private User user;
    private ChatControler chatControler;

    public MessageReciver(VBox chatBox, Client client, User user, ChatControler chatControler) {
        this.chatBox = chatBox;
        this.client = client;
        this.user = user;
        this.chatControler = chatControler;
    }

    public void run() {
        System.out.println("Message Reciver started");
        BufferedReader reader = client.getReader();
        while (true) {
            try {
                BufferedReader messageStream = client.getReader();
                String line = "";
                StringBuilder message = new StringBuilder();
                while((line = messageStream.readLine()) != null) {
                    if (line.startsWith("MESSAGE:")) {
                        message.append(line);
                        break;
                    }
                }
                onMessageRecived(message.toString());
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void onMessageRecived(String string) {
        //Chat:groupid,username,message
        System.out.println("Message Recived: " + string);
        ServerMessage msg = ServerMessage.parseMessage(string);
        switch (msg.command) {
            case CHATMESSAGE:
                if(msg.content.length != 3) {
                    System.err.println("Invalid message format for 'CHATMESSAGE' command: " + string);
                    return;
                }
                String groupId = msg.content[0];
                String displayName = msg.content[1];
                String message = msg.content[2].substring(1+msg.content[2].indexOf('>'));
                chatControler.addMessage(groupId, displayName, message);
                break;
            default:
                break;
        }
    }
}

class Group {
    private int id;
    private String name;
    private ArrayList<Message> messages;

    public Group(int id,String name) {
        this.id = id;
        this.messages = new ArrayList<Message>();
        loadMessages();
    }

    public void loadMessages() {
        // TODO load messages from server
    }

    public void ViewMessages(VBox chatBox) {
        chatBox.getChildren().clear();
        for (Message message : messages) {
            Label label = new Label(message.username + ": " + message.message);
            label.setWrapText(true);
            chatBox.getChildren().add(label);
        }
    }

    public void addMessage(String username, String message) {
        messages.add(new Message(username, message));
    }

    public void addMessage(String username, String message, LocalTime time) {
        messages.add(new Message(username, message, time));
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }
    public ArrayList<Message> getMessages() {
        return messages;
    }   

    public void setId(int id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setMessages(ArrayList<Message> messages) {
        this.messages = messages;
    }
}

class Message {
    public String username;
    public String message;
    public LocalTime time;

    public Message(String username, String message) {
        this(username, message, LocalTime.now());
    }

    public Message(String username, String message, LocalTime time) {
        this.username = username;
        this.message = message;
        this.time = time;
    }

}

