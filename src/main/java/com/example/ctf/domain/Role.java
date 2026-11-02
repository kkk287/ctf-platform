package com.example.ctf.domain;

public enum Role {
    USER, ADMIN;

    public String authority() {
        return "ROLE_" + name();
    }
}
