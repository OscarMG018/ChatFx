package com.example.Server;

import java.net.*;
import java.util.*;


public class Server {
    public static Server instance;
    public static DBConnection db;
    public static ServerSocket serverSocket;
    public static int port = 12345;
    public static ArrayList<ClientHandler> clients = new ArrayList<>();

    public static void main(String[] args) {
        db = DBConnection.getInstance();
        instance = new Server();
    }

    public static Server getInstance() {return instance;}

    public Server() {
        db = DBConnection.getInstance();
        try {
            serverSocket = new ServerSocket(port);
            onOpen();
            while (true) {
                System.out.println("Waiting for client connection...");
                Socket socket = serverSocket.accept();  
                System.out.println("Client connected");
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