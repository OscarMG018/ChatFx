package com.example.Common;

public class ChatMessage {
    public String message;
    public int group_id;
    public String displayName;
    public long createdAt;

    public ChatMessage(String message, int group_id, String displayName, long createdAt) {
        this.message = message;
        this.group_id = group_id;
        this.displayName = displayName;
        this.createdAt = createdAt;
    }

    public String toString() {
        return "{" + message + "," + group_id + "," + displayName + "," + createdAt + "}";
    }
}
