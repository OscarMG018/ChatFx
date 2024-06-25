package com.example.Server;

import java.sql.*;

public class ResetDataBase {
    
    public static DBConnection db = DBConnection.getInstance();
    
    public static void main(String[] args) {
        db.connect();
        reset();
        db.close();
    }

    public static void reset() {
        //Drop the table if it exists
        try {
            
            db.getStatement("DROP TABLE IF EXISTS users").executeUpdate();
            db.getStatement("DROP TABLE IF EXISTS groups").executeUpdate();
            db.getStatement("DROP TABLE IF EXISTS group_members").executeUpdate();
            db.getStatement("DROP TABLE IF EXISTS messages").executeUpdate();
            db.getStatement("DROP TABLE IF EXISTS group_invites").executeUpdate();
            //Create them again
            db.getStatement("CREATE TABLE IF NOT EXISTS users (user_id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT UNIQUE, password TEXT, display_name TEXT, created_at DATETIME DEFAULT CURRENT_TIMESTAMP)").executeUpdate();
            db.getStatement("CREATE TABLE IF NOT EXISTS groups (group_id INTEGER PRIMARY KEY AUTOINCREMENT, group_name TEXT, creator_id INTEGER, created_at DATETIME DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY (creator_id) REFERENCES users(user_id))").executeUpdate();
            db.getStatement("CREATE TABLE IF NOT EXISTS group_members (group_id INTEGER, user_id INTEGER, PRIMARY KEY (group_id, user_id), FOREIGN KEY (group_id) REFERENCES groups(group_id), FOREIGN KEY (user_id) REFERENCES users(user_id))").executeUpdate();
            db.getStatement("CREATE TABLE IF NOT EXISTS messages (message_id INTEGER PRIMARY KEY AUTOINCREMENT, group_id INTEGER, username TEXT, message TEXT, created_at DATETIME DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY (group_id) REFERENCES groups(group_id), FOREIGN KEY (username) REFERENCES users(username))").executeUpdate();
            db.getStatement("CREATE TABLE IF NOT EXISTS group_invites (group_id INTEGER, user_id INTEGER, PRIMARY KEY (group_id, user_id), FOREIGN KEY (group_id) REFERENCES groups(group_id), FOREIGN KEY (user_id) REFERENCES users(user_id))").executeUpdate();
            
            db.getStatement("INSERT INTO users (username, password, display_name) VALUES ('admin', '789admin123', 'Admin')").executeUpdate();
            db.getStatement("INSERT INTO groups (group_name, creator_id) VALUES ('Global Chat', 1)").executeUpdate();
            db.getStatement("INSERT INTO group_members (group_id, user_id) VALUES (1, 1)").executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
