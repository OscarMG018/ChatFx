package com.example.Common;

import java.util.*;
import java.nio.ByteBuffer;
import java.io.DataInputStream;
public class MessageDecoder {
    public enum Command {
        LOGIN,
        SIGNUP,
        CHATMESSAGE,
        GETMESSAGES,
        GETGROUPS,
        GETGROUPINFO,
        GETINVITES, 
        CREATEGROUP, 
        LEAVEGROUP, 
        DELETEGROUP, 
        INVITEUSER, 
        INVITERESPONSE, 
        EXIT,
        ACK,
        ERROR
    }
    
    public static class Message {
        public Command command;
        public int payloadLength;
        public long timestamp;
        public String payload;

        public Message(Command command, int payloadLength, long timestamp, String payload) {
            this.command = command;
            this.payloadLength = payloadLength;
            this.timestamp = timestamp;
            this.payload = payload;
        }
    }

    public static Message decode(byte[] messageBytes) {
        ByteBuffer buffer = ByteBuffer.wrap(messageBytes);
        //read header
        Command command = Command.values()[buffer.get()];
        int payloadLength = buffer.getInt();
        long timestamp = buffer.getLong();

        //read message
        byte[] payload = new byte[payloadLength];
        buffer.get(payload);
        String payloadString = new String(payload);

        int checksum = buffer.getInt();
        int calculatedChecksum = calculateChecksum(messageBytes);
        if (checksum != calculatedChecksum) {
            throw new IllegalArgumentException("Checksum mismatch");
        }

        return new Message(command, payloadLength, timestamp, payloadString);
    }

    public static byte[] encode(Command command, String payload) {
        long timestamp = System.currentTimeMillis();

        byte[] payloadBytes = payload.getBytes();
        int payloadLength = payloadBytes.length;
        int checksum = calculateChecksum(payloadBytes);

        ByteBuffer buffer = ByteBuffer.allocate(1 + 4 + 8 + payloadLength + 4);
        buffer.put((byte) command.ordinal());
        buffer.putInt(payloadLength);
        buffer.putLong(timestamp);
        buffer.put(payloadBytes);
        buffer.putInt(checksum);
        return buffer.array();
    }

    public static Message readMessage(DataInputStream inputStream) {
        try {
            //read header
            Command command = Command.values()[inputStream.readByte()];
            int payloadLength = inputStream.readInt();
            long timestamp = inputStream.readLong();

            //read message
            byte[] payload = new byte[payloadLength];
            inputStream.readFully(payload);
            String payloadString = new String(payload);
            int checksum = inputStream.readInt();
            int calculatedChecksum = calculateChecksum(payload);
            if (checksum != calculatedChecksum) {
                throw new IllegalArgumentException("Checksum mismatch");
            }

            return new Message(command, payloadLength, timestamp, payloadString);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static String[] splitPayload(String payload) {
        ArrayList<String> result = new ArrayList<String>();
        String temp = "";
        int i = 0;
        payload = payload.substring(1, payload.length() - 1);
        for (char c : payload.toCharArray()) {
            if (c == '{') {
                i++;
            }
            else if (c == '}') {
                i--;
                if (i == 0) {
                    result.add(temp);
                    temp = "";
                }
            }
            else if (c == ',') {
                if (i == 0)
                    continue;
                else {
                    temp += c;
                }
            }
            else {
                temp += c;
            }
        }
        return result.toArray(new String[0]);
    }

    private static int calculateChecksum(byte[] data) {
        int checksum = 0;
        for (byte b : data) {
            checksum = (checksum * 31 + (b & 0xFF)) & 0xFFFFFFFF;
        }
        return checksum;
    }
}
