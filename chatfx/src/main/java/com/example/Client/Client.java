package com.example.Client;

import com.example.Common.*;
import com.example.Common.MessageDecoder.*;

import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

class ClientReader implements Runnable {

    DataInputStream inputStream;

    public ClientReader(DataInputStream inputStream) {
        this.inputStream = inputStream;
    }

    public void run() {
        try {
            while(true) {
                if (inputStream.available() > 0) {
                    Message message = MessageDecoder.readMessage(inputStream);//reads only one message from the stream
                    System.out.println("Message Recived: " + message.command + " " + message.payload);
                    //add message to pendingMessages
                    synchronized (Client.pendingMessages) {
                        Client.pendingMessages.add(message);
                        System.out.println("Pending Messages: " + Client.pendingMessages.size());
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}


public class Client {
    public static Client client;

    public static Socket socket;
    public static int port = 12345;
    public static String host = "localhost";//192.168.3.163
    
    public static User user;
    public static DataInputStream inputStream;
    public static DataOutputStream outputStream;
    public static CopyOnWriteArrayList<Message> pendingMessages;

    Thread readerThread;
    
    private Client() {
        try {
            socket = new Socket(host, port);
            inputStream = new DataInputStream(socket.getInputStream());
            outputStream = new DataOutputStream(socket.getOutputStream());
            pendingMessages = new CopyOnWriteArrayList<>();
            readerThread = new Thread(new ClientReader(inputStream));
            readerThread.start();
            user = new User();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static Client getInstance() {
        if(client == null) {
            client = new Client();
        }
        return client;
    }

    public Message sendMessage(Command command, String payload) {
        System.out.println("Sending message: " + command + " " + payload);
        try {
            outputStream.write(MessageDecoder.encode(command, payload));
            outputStream.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
        //wait for response on pendingMessages
        System.out.println("Waiting for response");
        Message message = null;
        while (message == null) {
            synchronized (pendingMessages) {
                for (Message msg : pendingMessages) {
                    if (msg.command == Command.ACK || msg.command == Command.ERROR) {
                        System.out.println("Response Recived: " + msg.command + " " + msg.payload);
                        message = msg;
                        pendingMessages.remove(msg);
                        break;
                    }
                }
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        System.out.println("Returning Response");
        return message;
    }

    public void close() {
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
            if (readerThread != null) {
                readerThread.interrupt();
            }
        } catch (Exception e) {
            System.out.println("Error closing client: ");
            e.printStackTrace();
        }
    }
}