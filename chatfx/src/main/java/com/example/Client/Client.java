package com.example.Client;

import com.example.Common.*;
import com.example.Common.ServerMessage.*;

import java.net.*;
import java.io.*;


public class Client {
    static Socket socket;
    static User user;
    static BufferedReader messageStream;
    static BufferedReader responseStream;
    static PrintWriter outputStream;
    static Client client;
    static int port = 12345;
    static Thread messageReciver;
    static Thread duplicator;

    
    private Client() {
        try {
            user = new User();
            socket = new Socket("192.168.3.163", port);
            InputStream socketInputStream = socket.getInputStream();

            PipedInputStream pos1 = new PipedInputStream();
            PipedOutputStream pis1 = new PipedOutputStream(pos1);

            PipedInputStream pos2 = new PipedInputStream();
            PipedOutputStream pis2 = new PipedOutputStream(pos2);
            
            duplicator = new Thread(new StreamDuplicator(socketInputStream, pis1, pis2));
            duplicator.start();

            messageStream = new BufferedReader(new InputStreamReader(pos2));
            responseStream = new BufferedReader(new InputStreamReader(pos1));

            outputStream = new PrintWriter(socket.getOutputStream(), true);
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

    public ServerMessage sendMessage(String message) {
        try {
            outputStream.println("REQUEST:" + message);
            String line = "";
            StringBuilder response = new StringBuilder();
            while ((line = responseStream.readLine()) != null) {
                if (line.startsWith("RESPONSE:")) {
                    response.append(line);
                    break;
                }
            }
            return ServerMessage.parseMessage(response.toString());
        } catch (IOException e) {
            e.printStackTrace();
            ServerMessage returnMessage = new ServerMessage();
            returnMessage.code = Code.IO_EXCEPTION;
            returnMessage.type = MessageType.RESPONSE;
            return returnMessage;
        }
    }

    public void sendMessageWithoutResponse(String message) {
        outputStream.println("REQUEST:" + message);
    }

    public void close() {
        sendMessage("EXIT");
    }

    public BufferedReader getReader() {
        return messageStream;
    }

    static class StreamDuplicator implements Runnable {
        private InputStream original;
        private OutputStream out1;
        private OutputStream out2;

        public StreamDuplicator(InputStream original, OutputStream out1, OutputStream out2) {
            this.original = original;
            this.out1 = out1;
            this.out2 = out2;
        }

        @Override
        public void run() {
            try {
                int data;
                while (true) {
                    while ((data = original.read()) != -1) {
                        out1.write(data);
                        out2.write(data);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            finally {
                try {
                    out1.close();
                    out2.close();
                    original.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}