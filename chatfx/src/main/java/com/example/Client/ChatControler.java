package com.example.Client;

import com.example.Common.*;
import com.example.Common.MessageDecoder.*;

import java.util.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.io.*;
import javafx.application.Platform;
import javafx.fxml.*;
import java.net.*;
import java.sql.Timestamp;
import java.time.*;
import javafx.event.*;
import javafx.scene.*;
import javafx.geometry.*;
import java.time.format.*;

/*TODO LIST
 * Update the controller to use the new message format
 * Update group as groupUI to extend the group class
 * Use css to style the chat instead of code
 * make a ChatMessageUI class that extends ChatMessage that defindes the ui of a message
 */


public class ChatControler implements Initializable {
    @FXML
    public TextField message;
    @FXML
    public VBox chatBox;
    @FXML
    public ScrollPane scrollPane;
    @FXML
    public VBox GroupList;
    @FXML
    public Label groupName;

    public Client client;
    public User user;
    public ArrayList<GroupUI> groups;
    public int ActiveGroup = 0;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        client = Client.getInstance();
        user = Client.user;
        groups = new ArrayList<GroupUI>();
        LoadGroups();
        //LoadInvites();
        //Load the messages of the first group
        DisplayGroup(groups.get(ActiveGroup));
        //Start the message reciver
        Thread t = new Thread(new MessageReciver(this));
        t.start();
    }


    public void LoadGroups() {
        Message msg = client.sendMessage(Command.GETGROUPS,"");
        //[id1,id2...]
        String[] ids = msg.payload.substring(1, msg.payload.length() - 1).split(",");
        for (String id : ids) {
            Message msg2 = client.sendMessage(Command.GETGROUPINFO, id);
            //id,name,creatorUsername,creatorDisplayName,createdAt
            String[] groupInfo = msg2.payload.split(",");
            GroupUI group = new GroupUI(Integer.parseInt(groupInfo[0]), groupInfo[1], groupInfo[2], groupInfo[3], Long.parseLong(groupInfo[4]), Client.user.username.equals(groupInfo[2]));
            groups.add(group);
        }
    }

    public void DisplayGroup(GroupUI group) {
        groupName.setText(group.name);
        for (ChatMessageUI message : group.messages) {
            addMessageToChatBox(message);
        }
    }

    public void addMessageToChatBox(ChatMessageUI message) {
        Platform.runLater(() -> {
            chatBox.getChildren().add(message.getMessageNode());
            scrollPane.setVvalue(1.0);
        });
    }

    public void addNewMessage(ChatMessageUI message) {
        //add the message to the group
        System.out.println("adding new message");
        GroupUI group = null;
        for (GroupUI g : groups) {
            if (g.id == message.group_id) {
                group = g;
                break;
            }
        }
        if (group != null) {
            System.out.println("group found");
            group.messages.add(message);
            addMessageToChatBox(message);
        }
    }

    public void sendMessage() {
        String msgString = message.getText();
        message.setText("");
        if (msgString.isEmpty()) {
            return;
        }
        client.sendMessage(Command.CHATMESSAGE, groups.get(ActiveGroup).id + "," + msgString);
        addNewMessage(new ChatMessageUI(msgString, groups.get(ActiveGroup).id, user.displayName, System.currentTimeMillis()));
    }

    /*
    private void LoadInvites() {
        ServerMessage msg = client.sendMessage(ServerMessage.Command.GETINVITES.toString() + ":" + user.id);
        System.out.println(msg);
        for (int i = 0; i<msg.content.length;i+=2) {
            final int index = i/2;  
            int id = Integer.parseInt(msg.content[i]);
            String groupName = msg.content[i+1];
            addInvite(String.valueOf(id), groupName);
        } 
    }

    public void addInvite(String groupId, String groupName) {
        //like a group in the groupList but with a different color and it does not show the chat it shows a confirm button to accept the invite or decline button to decline the invite
        Button button = new Button(groupName);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setPrefHeight(50);
        button.setStyle("-fx-font-size: 20px; -fx-background-color: #000000; -fx-text-fill: #FFFFFF;");
        button.setOnAction(e -> {
            chatBox.getChildren().clear();
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
        groups.get(ActiveGroup).ViewMessages(this);
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

    public void deleteGroup(int id) {
        int index = -1;
        for (int i = 0; i < groups.size(); i++) {
            if (groups.get(i).getId() == id) {
                index = i;
                break;
            }
        }
        groups.remove(index);
        GroupList.getChildren().remove(index);
        if (ActiveGroup >= groups.size()) {
            ActiveGroup = groups.size() - 1;
        }
        groups.get(ActiveGroup).ViewMessages(this);
    }*/
}

class ChatMessageUI extends ChatMessage {

    public ChatMessageUI(String message, int group_id, String displayName, long createdAt) {
        super(message, group_id, displayName, createdAt);
    }

    public Node getMessageNode() {
        VBox messagePane = new VBox();
        messagePane.getStyleClass().add("message-pane");
        messagePane.setAlignment(Pos.TOP_LEFT);
    
        Label nameLabel = new Label(displayName);
        nameLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
        nameLabel.getStyleClass().add("message-name");


        LocalDateTime dateTime = LocalDateTime.ofEpochSecond(createdAt, 0, ZoneOffset.UTC);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
        String formattedDateTime = dateTime.format(formatter);
        Label timeLabel = new Label(formattedDateTime);
        timeLabel.setFont(Font.font("System", FontWeight.LIGHT, 12));
        timeLabel.getStyleClass().add("message-time");

        FlowPane timeNamePane = new FlowPane();
        timeNamePane.getChildren().addAll(nameLabel, timeLabel);
        timeNamePane.setHgap(10);
        timeNamePane.setAlignment(Pos.TOP_RIGHT);

        Label messageLabel = new Label(message);
        messageLabel.setWrapText(true);
        messageLabel.getStyleClass().add("message-text");

        messagePane.getChildren().addAll(timeNamePane, messageLabel);
        FlowPane flowPane = new FlowPane();
        if (displayName.equals(Client.user.displayName)) {
            flowPane.setAlignment(Pos.TOP_RIGHT);
        }
        else {
            flowPane.setAlignment(Pos.TOP_LEFT);
        }
        flowPane.getChildren().add(messagePane);
        return flowPane;
    }
}

class GroupUI extends com.example.Common.Group {

    boolean userIsAdmin;
    ArrayList<ChatMessageUI> messages;

    public GroupUI(int id,String name, String creatorUsername, String creatorDisplayName, long createdAt, boolean userIsAdmin) {
        super(id, name, creatorUsername, creatorDisplayName, createdAt);
        messages = new ArrayList<ChatMessageUI>();
        loadMessages();
    }

    public void loadMessages() {
        Message msg = Client.getInstance().sendMessage(Command.GETMESSAGES, String.valueOf(id));
        //[{,,,},{,,,},{,,,}]
        if (msg.payload.equals("[]")) {
            return;
        }
        System.out.println(msg.payload);
        String[] messages = MessageDecoder.splitPayload(msg.payload);
        for (String message : messages) {
            System.out.println(message);
            String[] messageParts = message.split(",");
            for (String part : messageParts) {
                System.out.println(part);
            }
            ChatMessageUI chatMessage = new ChatMessageUI(messageParts[0], Integer.parseInt(messageParts[1]), messageParts[2], Long.parseLong(messageParts[3]));
            this.messages.add(chatMessage);
        }
    }

    public Node getGroupNode(int index, ChatControler chatControler) {
        Button button = new Button(name);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setPrefHeight(50);
        button.setStyle("-fx-font-size: 20px;");
        button.setOnAction(e -> {
            chatControler.groupName.setText(name);
            chatControler.ActiveGroup = index;
            chatControler.DisplayGroup(this);
        });
        /*ContextMenu contextMenu = getContextMenu(chatControler);
        button.setOnContextMenuRequested(e -> {
            contextMenu.show(button, e.getScreenX(), e.getScreenY());
        });*/
        return button;
    }

    /*
    private ContextMenu getContextMenu(ChatControler chatControler) {
        if (!userIsAdmin) {
            ContextMenu contextMenu = new ContextMenu(); 
            MenuItem leaveGroup = new MenuItem("Leave Group");
            leaveGroup.setOnAction(e -> {
                Client.getInstance().sendMessageWithoutResponse("LEAVEGROUP:" + id + "," + Client.user.id);
                chatControler.deleteGroup(id);
            });
            contextMenu.getItems().add(leaveGroup);
            return contextMenu;
        }
        ContextMenu contextMenu = new ContextMenu();
        
        MenuItem deleteGroup = new MenuItem("Delete Group");
        deleteGroup.setOnAction(e -> {
            Client.getInstance().sendMessageWithoutResponse("DELETEGROUP:" + id);
            chatControler.deleteGroup(id);
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
    }*/
}

class MessageReciver implements Runnable {

    ChatControler chatControler;
    Client client;

    public MessageReciver(ChatControler chatControler) {
        this.chatControler = chatControler;
        this.client = Client.getInstance();
    }

    public void run() {
        while (true) {
            synchronized (Client.pendingMessages) {
                for (Message msg : Client.pendingMessages) {
                    if (msg.command.equals(Command.CHATMESSAGE)) {
                        System.out.println("Chat message recived");
                        String[] messageParts = msg.payload.substring(1, msg.payload.length() - 1).split(",");
                        ChatMessageUI chatMessage = new ChatMessageUI(messageParts[0], Integer.parseInt(messageParts[1]), messageParts[2], Long.parseLong(messageParts[3]));
                        chatControler.addNewMessage(chatMessage);
                        Client.pendingMessages.remove(msg);
                    }
                }
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
