package com.example.Client;

public class User {
    public static User currentUser;
    public int id;
    public String username;
    public String displayName;
    public String password;

    public static User getCurrentUser() {
        if (currentUser == null) {
            currentUser = new User();
        }
        return currentUser;
    }
}
