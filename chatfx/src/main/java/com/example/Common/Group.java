package com.example.Common;

import java.util.ArrayList;

import com.example.Common.MessageDecoder.Message;

public class Group {
    public int id;
    public String name;
    public String creatorUsername;
    public String creatorDisplayName;
    public long createdAt;
    
    public Group(int id,String name, String creatorUsername, String creatorDisplayName, long createdAt) {
        this.id = id;
        this.name = name;
        this.creatorUsername = creatorUsername;
        this.creatorDisplayName = creatorDisplayName;
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return id + "," + name + "," + creatorUsername + "," + creatorDisplayName + "," + createdAt;
    }
}

