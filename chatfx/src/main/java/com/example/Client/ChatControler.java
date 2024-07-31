package com.example.Client;

import java.util.*;
import com.example.Common.*;
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
 * Store Messages in a database
 * Load Past Messages of a group
 * 
 * See user inforramtion by clicking on a message
 */

 /*KNOWN BUGS
  *Messages from Two different Users with the same display name will appear as selfMessages for each other
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
    public ArrayList<Group> groups;
    public int ActiveGroup = 0;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        client = Client.getInstance();
        user = Client.user;
        groups = new ArrayList<Group>();
        LoadGroups();
        LoadInvites();
        //Load the messages of the first group
        groupName.setText(groups.get(ActiveGroup).getName());
        groups.get(ActiveGroup).ViewMessages(this);
        //Start the message reciver
        Thread t = new Thread(new MessageReciver(chatBox, client, user, this));
        t.start();
    }

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

    public void addMessage(String groupId, String displayName, String message, boolean isSelf) {
        for(Group group : groups) {
            if(group.getId() == Integer.parseInt(groupId)) {
                group.addMessage(displayName, message);
                String time = LocalTime.now().toString();
                if (group.getId() == groups.get(ActiveGroup).getId()) {
                    //Add message to chatBox
                    addMessageToChatBox(displayName, message, time, isSelf);
                }
            }
        }
    }

    public void addMessageToChatBox(String displayName, String message, String time, boolean isSelf) {
        Platform.runLater(() -> {
            VBox messagePane = new VBox();
            messagePane.setStyle(" -fx-background-color: #e0e0e0;\r\n" + //
                                "    -fx-background-radius: 10;\r\n" + //
                                "    -fx-padding: 10;\r\n" + //
                                "    -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 10, 0, 0, 5);");
            messagePane.setAlignment(Pos.TOP_LEFT);
            

            Label nameLabel = new Label(displayName);
            nameLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
            nameLabel.setStyle("-fx-text-fill: #3366cc;\r\n" + //
                                "    -fx-padding: 0 0 5 0;");

            Label timeLabel = new Label(time);
            timeLabel.setFont(Font.font("System", FontWeight.LIGHT, 12));
            timeLabel.setStyle("-fx-text-fill: #3366cc;\r\n" + //
                                "    -fx-padding: 0 0 5 0;");
            FlowPane timeNamePane = new FlowPane();
            timeNamePane.getChildren().addAll(nameLabel, timeLabel);
            timeNamePane.setHgap(10);
            timeNamePane.setAlignment(Pos.TOP_RIGHT);

            Label messageLabel = new Label(message);
            messageLabel.setWrapText(true);
            messageLabel.setStyle("-fx-text-fill: #333333; -fx-text-alignment: left;");

            messagePane.getChildren().addAll(timeNamePane, messageLabel);
            FlowPane flowPane = new FlowPane();
            if (isSelf) {
                flowPane.setAlignment(Pos.TOP_RIGHT);
            }
            else {
                flowPane.setAlignment(Pos.TOP_LEFT);
            }
            flowPane.getChildren().add(messagePane);
            chatBox.getChildren().add(flowPane);
            scrollPane.setVvalue(1.0);
        });
    }

    public void sendMessage() {
        String msg = message.getText();
        if (msg.isEmpty()) {
            message.setText("");
            return;
        }
        message.setText("");
        client.sendMessageWithoutResponse("CHATMESSAGE:" +groups.get(ActiveGroup).getId()+",<TEXT!"+ (msg.length()-1) +">" +msg);
        addMessage(""+groups.get(ActiveGroup).getId(), user.displayName, msg, true);
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
            Button button = (Button) group.getGroupNode(index, this);
            GroupList.getChildren().add(button); 
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
                chatControler.addMessage(groupId, displayName, message, false);
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
            chatControler.groupName.setText(name);
            chatControler.ActiveGroup = index;
            chatControler.groups.get(chatControler.ActiveGroup).ViewMessages(chatControler);
        });
        ContextMenu contextMenu = getContextMenu(chatControler);
        button.setOnContextMenuRequested(e -> {
            contextMenu.show(button, e.getScreenX(), e.getScreenY());
        });
        return button;
    }

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
    }

    public void loadMessages() {
        ServerMessage msg = Client.getInstance().sendMessage("GETMESSAGES:" + id);
        System.out.println("loadMessages: " + msg);
        System.out.println(msg.content.length);
        for (String s : msg.content) {
            System.out.println(s);
        }
        if (msg.code == ServerMessage.Code.OK) {
            for (int i = 0; i < msg.content.length; i+=3) {
                String displayName = msg.content[i];
                String message = msg.content[i+1].substring(msg.content[i+1].indexOf('>') + 1);
                String timeS = msg.content[i+2].substring(msg.content[i+2].indexOf('>') + 1);
                Timestamp time = Timestamp.valueOf(timeS);
                messages.add(new Message(displayName, message, time.toLocalDateTime()));
            }
        }
    }

    public void ViewMessages(ChatControler chatControler) {
        chatControler.chatBox.getChildren().clear();
        for (Message message : messages) {
            chatControler.addMessageToChatBox(message.displayName, message.message, message.time.toString(), message.displayName.equals(Client.user.displayName));
        }
    }

    public void addMessage(String displayName, String message) {
        messages.add(new Message(displayName, message));
    }

    public void addMessage(String displayName, String message, LocalDateTime time) {
        messages.add(new Message(displayName, message, time));
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
    public String displayName;
    public String message;
    public LocalDateTime time;

    public Message(String displayName, String message) {
        this(displayName, message, LocalDateTime.now());
    }

    public Message(String displayName, String message, LocalDateTime time) {
        this.displayName = displayName;
        this.message = message;
        this.time = time;
    }

}

