package com.example.Server;

import java.sql.*;
import java.net.*;
import java.io.*;
import java.util.*;
import java.util.regex.*;
import com.example.Common.*;
import com.example.Common.MessageDecoder.*;

class ClientHandler implements Runnable {
    private Socket socket;
    private DataInputStream inputStream;
    private DataOutputStream outputStream;
    private static DBConnection db;
    private boolean isRunning;
    User user;

    public ClientHandler(Socket socket) {
        this.socket = socket;
        try {
            this.inputStream = new DataInputStream(socket.getInputStream());
            this.outputStream = new DataOutputStream(socket.getOutputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }
        this.db = DBConnection.getInstance();
    }

    @Override
    public void run() {
        user = null;
        isRunning = true;
        while (isRunning) {
            try {
                if (inputStream.available() > 0) {
                    byte[] messageBytes = inputStream.readAllBytes();
                    Message message = MessageDecoder.decode(messageBytes);
                    System.out.println("Mesage Recived: " + message);
                    onMessage(message);
                }
            } catch (Exception e) {
                onError(e);
            }
        }
        onClose();
    }

    public void onError(Exception e) {
        e.printStackTrace();
    }

    public void onClose() {
        Server.clients.remove(this);
        try {
            if (socket != null) {
                socket.close();
            }
            if (inputStream != null) {
                inputStream.close();
            }
            if (outputStream != null) {
                outputStream.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void onMessage(Message message) {
        switch (message.command) {
            case LOGIN:
                login(message);
                break;
            case SIGNUP:
                signup(message);
                break;
            case CHATMESSAGE:
                chatMessage(message);
                break;
            default:
                sendMessage(MessageDecoder.encode(Command.ERROR, "Invalid command"));
                break;
        }
    }

    public void login(Message message) {
        //username, password
        String[] data = message.payload.split(",");
        if (data.length != 2) {
            sendMessage(MessageDecoder.encode(Command.ERROR, "Invalid login data"));
            return;
        }
        String username = data[0];
        String password = data[1];
        PreparedStatement statement = db.getStatement("SELECT * FROM users WHERE username = ? AND password = ?");
        try {
            statement.setString(1, username);
            statement.setString(2, password);
            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                user = new User();
                user.id = resultSet.getInt("user_id");
                user.username = resultSet.getString("username");
                user.displayName = resultSet.getString("display_name");
                PreparedStatement statement2 = db.getStatement("SELECT * FROM group_members WHERE user_id = ?");
                statement2.setInt(1, user.id);
                ResultSet resultSet2 = statement2.executeQuery();
                while (resultSet2.next()) {
                    user.groupIds.add(resultSet2.getInt("group_id"));
                }
                sendMessage(MessageDecoder.encode(Command.LOGIN, "Login successful" + user.username + "," + user.displayName));
            } else {
                sendMessage(MessageDecoder.encode(Command.ERROR, "Invalid username or password"));
            }
        }
        catch (SQLException e) {
            sendMessage(MessageDecoder.encode(Command.ERROR, "Server error: " + e.getMessage()));
        }
    }

    public void signup(Message message) {
        //username, password, display_name
        String[] data = message.payload.split(",");
        if (data.length != 3) {
            sendMessage(MessageDecoder.encode(Command.ERROR, "Invalid signup data"));
            return;
        }
        String username = data[0];
        String password = data[1];
        String display_name = data[2];
        //check if username is already taken
        PreparedStatement statement = db.getStatement("SELECT * FROM users WHERE username = ?");
        try {
            statement.setString(1, username);
            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                sendMessage(MessageDecoder.encode(Command.ERROR, "Username already taken"));
                return;
            }
        }
        catch (SQLException e) {
            sendMessage(MessageDecoder.encode(Command.ERROR, "Server error: " + e.getMessage()));
            return;
        }
        //create user
        PreparedStatement statement2 = db.getStatement("INSERT INTO users (username, password, display_name) VALUES (?, ?, ?)");
        try {
            statement2.setString(1, username);
            statement2.setString(2, password);
            statement2.setString(3, display_name);
            int id = db.insertAndReturnId(statement2);
            user = new User();
            user.id = id;
            user.username = username;
            user.displayName = display_name;
            //add user to group 1 (global group)
            PreparedStatement statement3 = db.getStatement("INSERT INTO group_members (group_id, user_id) VALUES (?, ?)");
            statement3.setInt(1, 1);
            statement3.setInt(2, user.id);
            statement3.executeUpdate();
            user.groupIds.add(1);
            sendMessage(MessageDecoder.encode(Command.SIGNUP, "Signup successful" + username + "," + display_name));
        }
        catch (SQLException e) {
            sendMessage(MessageDecoder.encode(Command.ERROR, "Server error: " + e.getMessage()));
        }
    }

    public void chatMessage(Message message) {
        //group_id, message
        if (user == null) {
            sendMessage(MessageDecoder.encode(Command.ERROR, "User not logged in"));
            return;
        }

        String[] data = message.payload.split(",");
        if (data.length != 2) {
            sendMessage(MessageDecoder.encode(Command.ERROR, "Invalid chat message data"));
            return;
        }
        int group_id = Integer.parseInt(data[0]);
        if (!user.groupIds.contains(group_id)) {
            sendMessage(MessageDecoder.encode(Command.ERROR, "User not in group"));
            return;
        }
        String messageString = data[1];
        //send message to all clients in the group
        for (ClientHandler client : Server.clients) {
            if (client.user != null && client.user.groupIds.contains(group_id)) {
                client.sendMessage(MessageDecoder.encode(Command.CHATMESSAGE, group_id + "," + messageString));
            }
        }
    }

    public void getMessages(Message message) {
        //group_id
        if (user == null) {
            sendMessage(MessageDecoder.encode(Command.ERROR, "User not logged in"));
            return;
        }
        int group_id = Integer.parseInt(message.payload);
        if (!user.groupIds.contains(group_id)) {
            sendMessage(MessageDecoder.encode(Command.ERROR, "User not in group"));
            return;
        }
        ArrayList<ChatMessage> messages = new ArrayList<>();
        PreparedStatement statement = db.getStatement("SELECT * FROM messages WHERE group_id = ?");
        try {
            statement.setInt(1, group_id);
            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                String messageString = resultSet.getString("message");
                String username = resultSet.getString("username");
                long timestamp = resultSet.getLong("timestamp");
                ChatMessage chatMessage = new ChatMessage(messageString, group_id, username, timestamp);
                messages.add(chatMessage);
            }
            sendMessage(MessageDecoder.encode(Command.GETMESSAGES, "Get messages successful" + messages));
        }
        catch (SQLException e) {
            sendMessage(MessageDecoder.encode(Command.ERROR, "Server error: " + e.getMessage()));
        }
    }
    
    public void getGroups(Message message) {
        if (user == null) {
            sendMessage(MessageDecoder.encode(Command.ERROR, "User not logged in"));
            return;
        }
        if (message.payloadLength != 0) {
            sendMessage(MessageDecoder.encode(Command.ERROR, "Invalid get groups data"));
            return;
        }
        sendMessage(MessageDecoder.encode(Command.GETGROUPS, "Get groups successful" + user.groupIds.toString()));
    }

    public void getInvites(Message message) {
        if (user == null) {
            sendMessage(MessageDecoder.encode(Command.ERROR, "User not logged in"));
            return;
        }
        if (message.payloadLength != 0) {
            sendMessage(MessageDecoder.encode(Command.ERROR, "Invalid get invites data"));
            return;
        }
        PreparedStatement statement = db.getStatement("SELECT * FROM group_invites WHERE user_id = ?");
        try {
            statement.setInt(1, user.id);
            ResultSet resultSet = statement.executeQuery();
            ArrayList<Integer> invites = new ArrayList<>();
            while (resultSet.next()) {
                int group_id = resultSet.getInt("group_id");
                invites.add(group_id);
            }
            sendMessage(MessageDecoder.encode(Command.GETINVITES, "Get invites successful" + invites.toString()));
        }
        catch (SQLException e) {
            sendMessage(MessageDecoder.encode(Command.ERROR, "Server error: " + e.getMessage()));
        }
    }

    public void createGroup(Message message) {
        //group_name
        if (user == null) {
            sendMessage(MessageDecoder.encode(Command.ERROR, "User not logged in"));
            return;
        }
        String[] data = message.payload.split(",");
        if (data.length != 1) {
            sendMessage(MessageDecoder.encode(Command.ERROR, "Invalid create group data"));
            return;
        }
        String group_name = data[0];
        PreparedStatement statement = db.getStatement("INSERT INTO groups (group_name,creator_id,created_at) VALUES (?,?,?)");
        try {
            statement.setString(1, group_name);
            statement.setInt(2, user.id);
            statement.setLong(3, System.currentTimeMillis());
            int groupId = db.insertAndReturnId(statement);
            //add user to group
            PreparedStatement statement2 = db.getStatement("INSERT INTO group_members (group_id, user_id) VALUES (?, ?)");
            statement2.setInt(1, groupId);
            statement2.setInt(2, user.id);
            statement2.executeUpdate();
            user.groupIds.add(groupId);
            sendMessage(MessageDecoder.encode(Command.CREATEGROUP, "Create group successful" + groupId));
        }
        catch (SQLException e) {
            sendMessage(MessageDecoder.encode(Command.ERROR, "Server error: " + e.getMessage()));
        }
    }
    
    public void leaveGroup(Message message) {
        //group_id
        if (user == null) {
            sendMessage(MessageDecoder.encode(Command.ERROR, "User not logged in"));
            return;
        }
        String[] data = message.payload.split(",");
        if (data.length != 1) {
            sendMessage(MessageDecoder.encode(Command.ERROR, "Invalid leave group data"));
            return;
        }
        int group_id = Integer.parseInt(data[0]);
        if (!user.groupIds.contains(group_id)) {
            sendMessage(MessageDecoder.encode(Command.ERROR, "User not in group"));
            return;
        }
        PreparedStatement statement = db.getStatement("DELETE FROM group_members WHERE group_id = ? AND user_id = ?");
        try {
            statement.setInt(1, group_id);
            statement.setInt(2, user.id);
            statement.executeUpdate();
            user.groupIds.remove(Integer.valueOf(group_id));
    
            sendMessage(MessageDecoder.encode(Command.LEAVEGROUP, "Leave group successful"));
        }
        catch (SQLException e) {
            sendMessage(MessageDecoder.encode(Command.ERROR, "Server error: " + e.getMessage()));
        }
    }

    public void deleteGroup(Message message) {
        //group_id
        if (user == null) {
            sendMessage(MessageDecoder.encode(Command.ERROR, "User not logged in"));
            return;
        }
        String[] data = message.payload.split(",");
        if (data.length != 1) {
            sendMessage(MessageDecoder.encode(Command.ERROR, "Invalid delete group data"));
            return;
        }
        int group_id = Integer.parseInt(data[0]);
        if (!user.groupIds.contains(group_id)) {
            sendMessage(MessageDecoder.encode(Command.ERROR, "User not in group"));
            return;
        }
        //see if the user is the creator of the group
        PreparedStatement statement = db.getStatement("SELECT * FROM groups WHERE group_id = ? AND creator_id = ?");
        try {
            statement.setInt(1, group_id);
            statement.setInt(2, user.id);
            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                //delete group
                PreparedStatement statement2 = db.getStatement("DELETE FROM groups WHERE group_id = ?");
                statement2.setInt(1, group_id);
                statement2.executeUpdate();
                user.groupIds.remove(Integer.valueOf(group_id));
                //delete all messages in the group
                PreparedStatement statement3 = db.getStatement("DELETE FROM messages WHERE group_id = ?");
                statement3.setInt(1, group_id);
                statement3.executeUpdate();
                //delete all group members in the group
                PreparedStatement statement4 = db.getStatement("DELETE FROM group_members WHERE group_id = ?");
                statement4.setInt(1, group_id);
                statement4.executeUpdate();
                //delete all group invites in the group
                PreparedStatement statement5 = db.getStatement("DELETE FROM group_invites WHERE group_id = ?");
                statement5.setInt(1, group_id);
                statement5.executeUpdate();
                sendMessage(MessageDecoder.encode(Command.DELETEGROUP, "Delete group successful"));
            }
            else {
                sendMessage(MessageDecoder.encode(Command.ERROR, "User is not the creator of the group"));
            }
        }
        catch (SQLException e) {
            sendMessage(MessageDecoder.encode(Command.ERROR, "Server error: " + e.getMessage()));
        }
    }

    public void inviteUser(Message message) {
        //group_id, username
        if (user == null) {
            sendMessage(MessageDecoder.encode(Command.ERROR, "User not logged in"));
            return;
        }
        String[] data = message.payload.split(",");
        if (data.length != 2) {
            sendMessage(MessageDecoder.encode(Command.ERROR, "Invalid invite user data"));
            return;
        }
        int group_id = Integer.parseInt(data[0]);
        String username = data[1];
        //check if the user is in the group
        if (!user.groupIds.contains(group_id)) {
            sendMessage(MessageDecoder.encode(Command.ERROR, "User not in group"));
            return;
        }
        //check if the user is already in the group
        PreparedStatement statement = db.getStatement("SELECT * FROM group_members WHERE group_id = ? AND user_id = ?");
        try {
            statement.setInt(1, group_id);
            statement.setInt(2, user.id);
            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                sendMessage(MessageDecoder.encode(Command.ERROR, "User already in group"));
                return;
            }
            //check if the user is already invited to the group
            PreparedStatement statement2 = db.getStatement("SELECT * FROM group_invites WHERE group_id = ? AND user_id = ?");
            statement2.setInt(1, group_id);
            statement2.setString(2, username);
            ResultSet resultSet2 = statement2.executeQuery();
            if (resultSet2.next()) {
                sendMessage(MessageDecoder.encode(Command.ERROR, "User already invited to group"));
                return;
            }
            //invite the user
            PreparedStatement statement3 = db.getStatement("INSERT INTO group_invites (group_id, user_id) VALUES (?, ?)");
            statement3.setInt(1, group_id);
            statement3.setString(2, username);
            statement3.executeUpdate();
            sendMessage(MessageDecoder.encode(Command.INVITEUSER, "Invite user successful"));
        }
        catch (SQLException e) {
            sendMessage(MessageDecoder.encode(Command.ERROR, "Server error: " + e.getMessage()));
        }
    }

    public void inviteResponse(Message message) {
        //group_id, response(1 for accept, 0 for decline)
        if (user == null) {
            sendMessage(MessageDecoder.encode(Command.ERROR, "User not logged in"));
            return;
        }
        String[] data = message.payload.split(",");
        if (data.length != 2) {
            sendMessage(MessageDecoder.encode(Command.ERROR, "Invalid invite response data"));
            return;
        }
        int group_id = Integer.parseInt(data[0]);
        int response = Integer.parseInt(data[1]);
        if (response == 1) {
            //add user to group
            PreparedStatement statement = db.getStatement("INSERT INTO group_members (group_id, user_id) VALUES (?, ?)");
            try {
                statement.setInt(1, group_id);
                statement.setInt(2, user.id);
                statement.executeUpdate();
                user.groupIds.add(group_id);
            }
            catch (SQLException e) {
                sendMessage(MessageDecoder.encode(Command.ERROR, "Server error: " + e.getMessage()));
            }
        }
        //delete invite
        PreparedStatement statement2 = db.getStatement("DELETE FROM group_invites WHERE group_id = ? AND user_id = ?");
        try {
            statement2.setInt(1, group_id);
            statement2.setInt(2, user.id);
            statement2.executeUpdate();
            sendMessage(MessageDecoder.encode(Command.INVITERESPONSE, "Invite response successful"));
        }
        catch (SQLException e) {
            sendMessage(MessageDecoder.encode(Command.ERROR, "Server error: " + e.getMessage()));
        }
    }

    public void exit(Message message) {
        //
        if (user == null) {
            sendMessage(MessageDecoder.encode(Command.ERROR, "User not logged in"));
            return;
        }
        if (message.payloadLength != 0) {
            sendMessage(MessageDecoder.encode(Command.ERROR, "Invalid exit data"));
            return;
        }
        isRunning = false;
    }

    public void sendMessage(byte[] message) {
        try {
            outputStream.write(message);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

public class Server {
    public static Server instance;
    public static DBConnection db;
    public static ServerSocket serverSocket;
    public static int port = 12345;
    public static ArrayList<ClientHandler> clients = new ArrayList<>();

    public static void main(String[] args) {
        db = DBConnection.getInstance();
        //part to execute sql test
        instance = new Server();
    }

    public static Server getInstance() {return instance;}

    public Server() {
        db = DBConnection.getInstance();
        try {
            serverSocket = new ServerSocket(port);
            onOpen();
            while (true) {
                Socket socket = serverSocket.accept();  
                ClientHandler clientHandler = new ClientHandler(socket);
                clients.add(clientHandler);
                Thread t = new Thread(clientHandler);
                t.start();
            }
        } catch (Exception e) {
            onError(e);
        }
    }

    public static void onOpen() {
        System.out.println("Server started");
    }

    public static void onClose() {
        System.out.println("Server stopped");
    }

    public static void onError(Throwable throwable) {
        System.out.println("Error: " + throwable.getMessage());
        throwable.printStackTrace();
    }
}