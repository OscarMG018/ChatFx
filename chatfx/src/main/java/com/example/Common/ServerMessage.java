package com.example.Common;

import java.util.*;

public class ServerMessage {
    public enum MessageType {
        REQUEST,
        RESPONSE,
        MESSAGE
    }
     
    public enum Command {
        LOGIN,
        SIGNUP,
        CHATMESSAGE,
        GETMESSAGES,
        GETGROUPS,
        GETINVITES,
        CREATEGROUP,
        LEAVEGROUP,
        DELETEGROUP,
        INVITEUSER,
        INVITERESPONSE,
        EXIT
    }
    
    public enum Code {
        OK,
        //login
        USER_NOT_FOUND,
        INVALID_PASSWORD,
        //signup
        USER_ALREADY_EXISTS,
        //other
        SQL_ERROR,
        IO_EXCEPTION,
        INVALID_MESSAGE,
        INVALID_COMMAND_USAGE
    }
    
    public MessageType type; //Request, Response, Message
    public Command command; //Login, Signup, Chat, exit
    public Code code;
    public String[] content; //Username, Password, Message...

    @Override
    public String toString() {
        String s = type.toString() + ":" + (command == null? code.toString():command.toString()) + ":";
        for (int i = 0; i < content.length; i++) {
            String str = content[i];
            s += str;
            if (i < content.length - 1) {
                s += ",";
            }
        }
        return s;
    }

    private static String[] split(String str, char delimiter) {
        System.out.println(str + " " + delimiter);
        // List to hold the parts
        ArrayList<String> parts = new ArrayList<>();
        // StringBuilder to build each part
        StringBuilder currentPart = new StringBuilder();

        // Iterate through each character in the string
        for (int i = 0; i < str.length(); i++) {
            if (currentPart.toString().matches(".*<TEXT!\\d+>")){
                System.out.println(currentPart.toString());
                String numString = currentPart.toString().substring(currentPart.lastIndexOf("<")+6, currentPart.lastIndexOf(">"));
                int n = Integer.parseInt(numString);
                for (int j = 0; j < n; j++) {
                    currentPart.append(str.charAt(i));
                    i++;
                }
                i--;
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

    public static ServerMessage parseMessage(String message) {
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

            if (msg.type == MessageType.RESPONSE)
                msg.code = Code.valueOf(parts[1]);
            else
                msg.command = Command.valueOf(parts[1]);
            
            if (parts.length == 3)
                msg.content = split(parts[2], ',');
            else
                msg.content = new String[0];
            return msg;
            
        } catch (Exception e) {
            return null;
        }
    }
}
