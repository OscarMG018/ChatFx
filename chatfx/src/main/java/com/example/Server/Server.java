package com.example.Server;

import java.sql.*;
import java.net.*;
import java.io.*;
import java.util.*;
import java.util.regex.*;
import com.example.Common.*;
import com.example.Common.ServerMessage.*;

class ClientHandler implements Runnable {
    private Socket socket;
    private BufferedReader inputStream;
    private PrintWriter outputStream;
    private static DBConnection db;
    User user;

    public ClientHandler(Socket socket) {
        this.socket = socket;
        try {
            this.inputStream = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.outputStream = new PrintWriter(socket.getOutputStream(), true);
        } catch (IOException e) {
            e.printStackTrace();
        }
        this.db = DBConnection.getInstance();
    }

    @Override
    public void run() {
        user = new User();
        while (true) {
            String message = "";
            try {
                message = inputStream.readLine();
                System.out.println("Mesage Recived: " + message);
                onMessage(message);
            } catch (IOException e) {
                System.err.println("Error: " + e.getMessage());
                break;
            }
        }
    }

    public void onClose() {
        System.out.println("Closing connection");
        outputStream.println("EXIT");
        try {
            if (socket!= null) {
                socket.close();
            }
            if (inputStream!= null) {
                inputStream.close();
            }
            if (outputStream!= null) {
                outputStream.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void onError(Throwable throwable) {
        Server.onError(throwable);
    }

    public void onMessage(String message) {//login:username,password or signup:username,password,displayName        
        ServerMessage msg = ServerMessage.parseMessage(message);
        if (msg == null) {
            outputStream.println("RESPONSE:" + Code.INVALID_MESSAGE.toString());
            return;
        }
        if (msg.type.equals(MessageType.REQUEST)) {
            switch (msg.command) {
                case LOGIN:
                    if (msg.content.length != 2) {
                        outputStream.println("RESPONSE:" + Code.INVALID_COMMAND_USAGE.toString());
                        return;
                    }
                    String username = msg.content[0];
                    String password = msg.content[1];
                    Code code = logIn(username, password);
                    if (code.equals(Code.OK)) {
                        outputStream.println("RESPONSE:" + code.toString()+":"+user.id+","+user.displayName);
                    } 
                    else {
                        outputStream.println("RESPONSE:" + code.toString());
                    }
                    break;
                case SIGNUP:
                    if (msg.content.length != 3) {
                        outputStream.println("RESPONSE:" + Code.INVALID_COMMAND_USAGE.toString());
                        return;
                    }
                    String _username = msg.content[0];
                    String _displayName = msg.content[1];
                    String _password = msg.content[2];
                    Code _code = signUp(_username, _password, _displayName);
                    if (_code.equals(Code.OK)) {
                        outputStream.println("RESPONSE:" + _code.toString()+":"+user.id);
                    } 
                    else {
                        outputStream.println("RESPONSE:" + _code.toString());
                    }
                    break;
                case CHATMESSAGE:
                    if (msg.content.length != 2) {
                        System.out.println(Code.INVALID_COMMAND_USAGE.toString() + "for CHATMESSAGE: " + msg);
                        return;
                    }
                    broadCastMesage(msg);
                    break;
                case GETGROUPS:
                    if (msg.content.length != 1) {
                        outputStream.println("RESPONSE:" + Code.INVALID_COMMAND_USAGE.toString());
                        return;
                    }
                    getGroups();
                    break;
                case CREATEGROUP:
                    if (msg.content.length != 2) {
                        outputStream.println("RESPONSE:" + Code.INVALID_COMMAND_USAGE.toString());
                        return;
                    }
                    createGroup(msg);
                    break;
                case INVITEUSER:
                    if (msg.content.length != 2) {
                        outputStream.println("RESPONSE:" + Code.INVALID_COMMAND_USAGE.toString());
                        return;
                    }
                    inviteUser(msg);
                    break;
                case INVITERESPONSE:
                    if (msg.content.length != 3) {
                        outputStream.println("RESPONSE:" + Code.INVALID_COMMAND_USAGE.toString());
                        return;
                    }
                    inviteResponse(msg);
                    break;
                case EXIT:
                    onClose();
                    return;
            }
        }
    }
    
    private void inviteResponse(ServerMessage msg) {
        String userId = msg.content[0];
        String groupId = msg.content[1];
        String response = msg.content[2];
        String sql = "DELETE FROM group_invites WHERE group_id = ? AND user_id = ?";
        try {
            PreparedStatement stmt = db.getStatement(sql);
            stmt.setInt(1, Integer.parseInt(groupId));
            stmt.setInt(2, Integer.parseInt(userId));
            stmt.executeUpdate();
            if (response.equals("ACCEPT")) {
                acceptInvite(groupId, userId);
                broadCastMesage(Integer.parseInt(groupId), "User " + Server.getUsername(Integer.parseInt(userId)) + " accepted the invite");
            }
            else {
                broadCastMesage(Integer.parseInt(groupId), "User " + Server.getUsername(Integer.parseInt(userId)) + " declined the invite");
            }
        }
        catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void acceptInvite(String groupId, String userId) {
        try {
            String sql = "INSERT INTO group_members (group_id, user_id) VALUES (?,?)";
            PreparedStatement stmt = db.getStatement(sql);
            stmt.setInt(1, Integer.parseInt(groupId));
            stmt.setInt(2, Integer.parseInt(userId));
            stmt.executeUpdate();

        }
        catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void inviteUser(ServerMessage msg) {
        int groupId = Integer.parseInt(msg.content[0]);
        String username = msg.content[1];
        int userId = 0;
        try {
            String sql = "SELECT user_id FROM users WHERE username = ?";
            PreparedStatement stmt = db.getStatement(sql);
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                userId = rs.getInt("user_id");
            }
            sql = "INSERT INTO group_invites (group_id, user_id) VALUES (?,?)";
            stmt = db.getStatement(sql);
            stmt.setInt(1, groupId);
            stmt.setInt(2, userId);
            stmt.executeUpdate();

            sql = "SELECT group_name FROM groups WHERE group_id = ?";
            stmt = db.getStatement(sql);
            stmt.setInt(1, groupId);
            rs = stmt.executeQuery();
            String groupName = "";
            if (rs.next()) {
                groupName = rs.getString("group_name");
            }
            ClientHandler client = Server.getClientHandler(userId);
            System.out.println("Client: " + client);
            if (client != null) {
                System.out.println("Sending invite to " + userId + " for group " + groupName);
                client.outputStream.println("MESSAGE:" + ServerMessage.Command.INVITEUSER + ":" + groupId + "," + groupName);
            }
        }
        catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void createGroup(ServerMessage msg) {
        String creatorId = msg.content[0];
        String groupName = msg.content[1];
        int groupId = 0;
        try {
            String sql = "INSERT INTO groups (group_name, creator_id) VALUES (?,?)";
            PreparedStatement stmt = db.getStatement(sql);
            stmt.setString(1, groupName);
            stmt.setString(2, creatorId);
            groupId = db.insertAndReturnId(stmt);
            sql = "INSERT INTO group_members (group_id, user_id) VALUES (?,?)";
            stmt = db.getStatement(sql);
            stmt.setInt(1, groupId);
            stmt.setInt(2, Integer.parseInt(creatorId));
            stmt.executeUpdate();
        }
        catch (SQLException e) {
            e.printStackTrace();
            outputStream.println("RESPONSE:" + Code.SQL_ERROR.toString());
        }
        outputStream.println("RESPONSE:" + Code.OK.toString() + ":" + groupId);
    }

    private void getGroups() {
        String sql = "SELECT \r\n" + //
                        "    g.group_id,\r\n" + //
                        "    g.group_name,\r\n" + //
                        "    CASE \r\n" + //
                        "        WHEN g.creator_id = gm.user_id THEN 'true'\r\n" + //
                        "        ELSE 'false'\r\n" + //
                        "    END AS is_owner\r\n" + //
                        "FROM \r\n" + //
                        "    groups g\r\n" + //
                        "JOIN \r\n" + //
                        "    group_members gm ON g.group_id = gm.group_id\r\n" + //
                        "WHERE \r\n" + //
                        "    gm.user_id = ?;";
        try {
            PreparedStatement stmt = db.getStatement(sql);
            stmt.setInt(1, user.id);
            ResultSet rs = stmt.executeQuery();
            String response = "RESPONSE:" + Code.OK + ":";
            while (rs.next()) {
                int group_id = rs.getInt("group_id");
                String group_name = rs.getString("group_name");
                String is_owner = rs.getString("is_owner");
                response += group_id + "," + group_name + "," + is_owner + ",";
            }
            response = response.substring(0, response.length() - 1);
            outputStream.println(response);
        } catch (SQLException e) {
            e.printStackTrace();
            outputStream.println("RESPONSE:" + Code.SQL_ERROR.toString());
        }
    }
    
    public Code signUp(String username, String password, String displayName) {
        int user_id = 0;
        try {
            //check if user already exists
            String sql = "SELECT * FROM users WHERE username = ?";
            PreparedStatement stmt = db.getStatement(sql);
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return Code.USER_ALREADY_EXISTS;
            }
            //insert
            sql = "INSERT INTO users (username, password, display_name) VALUES (?, ?, ?)";
            stmt = db.getStatement(sql);
            stmt.setString(1, username);
            stmt.setString(2, password);
            stmt.setString(3, displayName);
            user_id = db.insertAndReturnId(stmt);
            sql = "INSERT INTO group_members (group_id,user_id) VALUES (?,?)";
            stmt = db.getStatement(sql);
            stmt.setInt(1, 1);
            stmt.setInt(2, user_id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            return Code.SQL_ERROR;
        }
        user.id = user_id;
        user.username = username;
        user.displayName = displayName;
        user.password = password;
        user.groupIds.add(1);
        return Code.OK;
    }

    public Code logIn(String username, String password) {
        try {
            String sql = "SELECT * FROM users WHERE username = ?";
            PreparedStatement stmt = db.getStatement(sql);
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                if (rs.getString("password").equals(password)) {
                    user.id = rs.getInt("user_id");
                    user.username = username;
                    user.displayName = rs.getString("display_name");
                    user.password = password;
                    //Get the Ids of the groups where the user is
                    sql = "SELECT group_id FROM group_members WHERE user_id = ?";
                    stmt = db.getStatement(sql);
                    stmt.setInt(1, user.id);
                    rs = stmt.executeQuery();
                    while (rs.next()) {
                        user.groupIds.add(rs.getInt("group_id"));
                    }
                    return Code.OK;
                }
                return Code.INVALID_PASSWORD;
            }
            return Code.USER_NOT_FOUND;
        } catch (SQLException e) {
            e.printStackTrace();
            return Code.SQL_ERROR;
        }
    }

    public void broadCastMesage(ServerMessage msg) {
        //get all the ClientHandlers of the same group that are logged in
        //send a MESSAGE to the clients with msg
        int group_id = Integer.parseInt(msg.content[0]);
        ArrayList<ClientHandler> clients = Server.clients;
        msg.type = MessageType.MESSAGE;
        msg.content = new String[]{msg.content[0],user.displayName, msg.content[1]};
        for (ClientHandler client : clients) {
            if (client == this) {
                continue;
            }
            if (client.user.groupIds.contains(group_id)) {
                client.outputStream.println(msg.toString());
            }
        }
    }

    public void broadCastMesage(int group_id, String message) {
        //get all the ClientHandlers of the same group that are logged in
        //send a MESSAGE to the clients with msg
        ArrayList<ClientHandler> clients = Server.clients;
        for (ClientHandler client : clients) {
            ServerMessage msg = new ServerMessage();
            msg.type = MessageType.MESSAGE;
            msg.command = ServerMessage.Command.CHATMESSAGE;
            msg.content = new String[]{String.valueOf(group_id), "Server", message};
            if (client == this) {
                continue;
            }
            if (client.user.groupIds.contains(group_id)) {
                client.outputStream.println(msg.toString());
            }
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

    public static ClientHandler getClientHandler(int userId) {
        for (ClientHandler client : clients) {
            System.out.println(client.user.id);
            if (client.user.id == userId) {
                return client;
            }
        }
        return null;
    }

    public static String getUsername(int userId) {
        try {
            String sql = "SELECT username FROM users WHERE user_id = ?";
            PreparedStatement stmt = db.getStatement(sql);
            stmt.setInt(1, userId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString("username");
            }
            return "";
        }
        catch (SQLException e) {
            e.printStackTrace();
            return "";
        }
    }
}