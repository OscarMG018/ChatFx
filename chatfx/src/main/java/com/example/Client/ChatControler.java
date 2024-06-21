package com.example.Client;

import java.util.ArrayList;
import java.util.ResourceBundle;

import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.io.*;

import javafx.application.Platform;
import javafx.fxml.*;
import java.net.*;
import java.time.LocalDate;
import java.time.LocalTime;

public class ChatControler implements Initializable {
    @FXML
    private TextField message;
    @FXML
    private VBox chatBox;

    private Client client;
    private User user;
    private ArrayList<Group> groups;
    private int ActiveGroup = 0;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        client = Client.getInstance();
        user = User.getCurrentUser();
        groups = new ArrayList<Group>();
        // TODO load all groups of the user from server
        groups.add(new Group(1));
        Thread t = new Thread(new MessageReciver(chatBox, client, user, this));
        t.start();
    }

    public void addMessage(String groupId, String displayName, String message) {
        System.out.println("Add message to group " + groupId);
        for(Group group : groups) {
            if(group.getId() == Integer.parseInt(groupId)) {
                group.addMessage(displayName, message);
                if (group.getId() == groups.get(ActiveGroup).getId()) {
                    //Add message to chatBox
                    Platform.runLater(() ->{
                        Label label = new Label(displayName + ": " + message);
                        label.setWrapText(true);
                        chatBox.getChildren().add(label);
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
        client.sendMessage("CHATMESSAGE:" +groups.get(ActiveGroup).getId()+",<TEXT!"+ msg.length() +">" +msg);
        addMessage(""+groups.get(ActiveGroup).getId(), user.displayName, msg);
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

    public static String[] split(String str, char delimiter) {
        // List to hold the parts
        ArrayList<String> parts = new ArrayList<>();
        // StringBuilder to build each part
        StringBuilder currentPart = new StringBuilder();

        // Iterate through each character in the string
        for (int i = 0; i < str.length(); i++) {
            if (currentPart.toString().matches(".*<TEXT!\\d+>")){
                String numString = currentPart.toString().substring(currentPart.indexOf("<")+6, currentPart.length()-1);
                int n = Integer.parseInt(numString);
                for (int j = 0; j < n; j++) {
                    currentPart.append(str.charAt(i));
                    i++;
                }
            }
            else {
                char currentChar = str.charAt(i);

                // If the current character is the delimiter, add the current part to the list
                if (currentChar == delimiter) {
                    parts.add(currentPart.toString());
                    // Reset the StringBuilder for the next part
                    currentPart = new StringBuilder();
                } else {
                    // Otherwise, append the character to the current part
                    currentPart.append(currentChar);
                }
            }
        }

        // Add the last part to the list
        parts.add(currentPart.toString());

        // Convert the list to an array and return
        return parts.toArray(new String[0]);
    }

    public ServerMessage parseMessage(String message) {
        if (message == null) {
            return null;
        }
        ServerMessage msg = new ServerMessage();
        String[] parts = split(message, ':');
        if (parts.length < 2) {
            return null;
        }
        try {
            msg.type = MessageType.valueOf(parts[0]);
            msg.command = Command.valueOf(parts[1]);
            if (parts.length == 3) {
                msg.content = split(parts[2], ',');
            }
            else {
                msg.content = new String[0];
            }
            return msg;
        } catch (Exception e) {
            return null;
        }
    }

    private void onMessageRecived(String string) {
        //Chat:groupid,username,message
        System.out.println("Message Recived: " + string);
        ServerMessage msg = parseMessage(string);
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

enum Command {
    LOGIN,
    SIGNUP,
    CHATMESSAGE,
    EXIT
}

enum MessageType {
    REQUEST,
    RESPONSE,
    MESSAGE
}

class ServerMessage {
    public MessageType type; //Request, Response, Message
    public Command command; //Login, Signup, Chat, exit
    public String[] content; //Username, Password, Message...

    @Override
    public String toString() {
        String s = type.toString() + ":" + command.toString() + ":";
        for (int i = 0; i < content.length; i++) {
            String str = content[i];
            s += str;
            if (i < content.length - 1) {
                s += ",";
            }
        }
        return s;
    }
}

class Group {
    private int id;
    private String name;
    private ArrayList<Message> messages;

    public Group(int id) {
        this.id = id;
        this.messages = new ArrayList<Message>();
        loadMessages();
    }

    public void loadMessages() {
        // TODO load messages from server
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

