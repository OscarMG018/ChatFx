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
import javafx.event.*;
import javafx.scene.*;
import javafx.geometry.*;

//TODO:SERVER: cahnge user groupIds when accepting invite

public class ChatControler implements Initializable {
    @FXML
    public TextField message;
    @FXML
    public VBox chatBox;
    @FXML
    public ScrollPane scrollPane;
    @FXML
    public VBox GroupList;

    public Client client;
    public User user;
    public ArrayList<Group> groups;
    public int ActiveGroup = 0;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        client = Client.getInstance();
        user = Client.user;
        groups = new ArrayList<Group>();
        LoadGroups();
        LoadInvites();
        Thread t = new Thread(new MessageReciver(chatBox, client, user, this));
        t.start();
    }

    private void LoadInvites() {
        // TODO load invites from server
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
        for (int i = 0; i<msg.content.length;i+=3) {
            final int index = i/3;  
            int id = Integer.parseInt(msg.content[i]);
            String groupName = msg.content[i+1];
            String userIsAdmin = msg.content[i+2];
            Group group = new Group(id, groupName, userIsAdmin.equals("true"));
            groups.add(group);
            Platform.runLater(() ->{  
                //Add group to groupList
                Button button = (Button) group.getGroupNode(index, this);
                GroupList.getChildren().add(button);
            });
        }
    }

    public void addInvite(String groupId, String groupName) {
        //TODO add invite to group
        //like a group in the groupList but with a different color and it does not show the chat it shows a confirm button to accept the invite or decline button to decline the invite
        Button button = new Button(groupName);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setPrefHeight(50);
        button.setStyle("-fx-font-size: 20px; -fx-background-color: #000000; -fx-text-fill: #FFFFFF;");
        button.setOnAction(e -> {
            HBox pane = new HBox();
            pane.setStyle("-fx-background-color: #000000;");
            VBox.setVgrow(pane, Priority.ALWAYS);
            pane.setAlignment(Pos.CENTER);
            pane.setSpacing(100);
            //Add the buttons
            Button acceptButton = new Button("Accept");
            acceptButton.setOnAction(event -> {
                acceptInvite(groupId, button);
            });
            Button declineButton = new Button("Decline");
            declineButton.setOnAction(event -> {
                declineInvite(groupId, button);
            });
            pane.getChildren().addAll(acceptButton, declineButton);
            chatBox.getChildren().add(pane);
        });
        GroupList.getChildren().add(button);
    }

    private void acceptInvite(String groupId, Button button) {
        client.sendMessageWithoutResponse("INVITERESPONSE:" + user.id + "," + groupId + ",ACCEPT");
        int index = GroupList.getChildren().indexOf(button);
        GroupList.getChildren().remove(button);
        Group group =new Group(Integer.parseInt(groupId), button.getText(), false);
        if (groups.size() - 1 <= index)
            groups.add(index, group);
        else {
            groups.add(group);
        }
        Node bt = groups.get(index).getGroupNode(index, this);
        GroupList.getChildren().add(index, bt);
        ActiveGroup = index;
        groups.get(ActiveGroup).ViewMessages(chatBox);
    }

    private void declineInvite(String groupId, Button button) {
        client.sendMessageWithoutResponse("INVITERESPONSE:" + user.id + "," + groupId + ",DECLINE");
        GroupList.getChildren().remove(button);
    }

    public void createGroup(ActionEvent e) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Create Group");
        dialog.setHeaderText("Enter the name of the group");
        dialog.setContentText("Group Name:");
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(groupName -> {
            ServerMessage msg = client.sendMessage("CREATEGROUP:" + user.id + "," + groupName);
            if (msg.code == ServerMessage.Code.OK) {
                int groupId = Integer.parseInt(msg.content[0]);
                Group group = new Group(groupId, groupName, true);
                groups.add(group);
                ActiveGroup = groups.size() - 1;
                Button button = (Button) group.getGroupNode(groups.size() - 1, this);
                GroupList.getChildren().add(button);
            }
        });
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
            case INVITEUSER:
                if(msg.content.length != 2) {
                    System.err.println("Invalid message format for 'INVITEUSER' command: " + string);
                    return;
                }
                groupId = msg.content[0];
                String groupName = msg.content[1];
                Platform.runLater(() -> {
                    chatControler.addInvite(groupId, groupName);
                });
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
    private boolean userIsAdmin;

    public Group(int id,String name, boolean userIsAdmin) {
        this.id = id;
        this.name = name;
        this.messages = new ArrayList<Message>();
        this.userIsAdmin = userIsAdmin;
        loadMessages();
    }

    public Node getGroupNode(int index, ChatControler chatControler) {
        Button button = new Button(name);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setPrefHeight(50);
        button.setStyle("-fx-font-size: 20px;");
        button.setOnAction(e -> {
            chatControler.ActiveGroup = index;
            chatControler.groups.get(chatControler.ActiveGroup).ViewMessages(chatControler.chatBox);
        });
        ContextMenu contextMenu = getContextMenu();
        button.setOnContextMenuRequested(e -> {
            contextMenu.show(button, e.getScreenX(), e.getScreenY());
        });
        return button;
    }

    private ContextMenu getContextMenu() {
        if (!userIsAdmin) {
            ContextMenu contextMenu = new ContextMenu(); 
            MenuItem leaveGroup = new MenuItem("Leave Group");
            leaveGroup.setOnAction(e -> {
                //TODO leave group
            });
            contextMenu.getItems().add(leaveGroup);
            return contextMenu;
        }
        ContextMenu contextMenu = new ContextMenu();
        
        MenuItem deleteGroup = new MenuItem("Delete Group");
        deleteGroup.setOnAction(e -> {
            //TODO delete group
        });
        contextMenu.getItems().add(deleteGroup);

        MenuItem inviteUser = new MenuItem("Invite User");
        inviteUser.setOnAction(e -> {
            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle("Invite User");
            dialog.setHeaderText("Enter the username(not display name) of the user to invite");
            dialog.setContentText("Username:");
            Optional<String> result = dialog.showAndWait();
            result.ifPresent(username -> {
                Client.getInstance().sendMessageWithoutResponse("INVITEUSER:" + id + "," + username);
            });
        });
        contextMenu.getItems().add(inviteUser);
        return contextMenu;
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

