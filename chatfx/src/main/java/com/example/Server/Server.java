package com.example.Server;

import java.sql.*;
import java.net.*;
import java.io.*;
import java.util.*;
import java.util.regex.*;

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

enum Code {
    OK,
    //login
    USER_NOT_FOUND,
    INVALID_PASSWORD,
    //signup
    USER_ALREADY_EXISTS,
    //other
    SQL_ERROR,
    INVALID_MESSAGE,
    INVALID_COMMAND_USAGE
}

class Message {
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

class ClientHandler implements Runnable {
    private Socket socket;
    private BufferedReader inputStream;
    private PrintWriter outputStream;
    private static DBConnection db;
    private User user;

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
        Message msg = parseMessage(message);
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
                        outputStream.println("RESPONSE:" + code.toString()+","+user.id+","+user.displayName);
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
                        outputStream.println("RESPONSE:" + _code.toString()+","+user.id);
                    } 
                    else {
                        outputStream.println("RESPONSE:" + _code.toString());
                    }
                    break;
                case CHATMESSAGE:
                    if (msg.content.length != 2) {
                        outputStream.println("RESPONSE:" + Code.INVALID_COMMAND_USAGE.toString());
                        return;
                    }
                    code = broadCastMesage(msg);
                    outputStream.println("RESPONSE:" + code.toString());
                    break;
                case EXIT:
                    onClose();
                    return;
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

    public Message parseMessage(String message) {
        if (message == null) {
            return null;
        }
        Message msg = new Message();
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

    public Code broadCastMesage(Message msg) {
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
        return Code.OK;
    }

}

public class Server {
    public static Server instance;
    public static DBConnection db;
    public static ServerSocket serverSocket;
    public static int port = 12345;
    public static ArrayList<ClientHandler> clients = new ArrayList<>();

    public static void main(String[] args) {instance = new Server();}

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