package com.example.ctf.domain;

public enum Category {
    WEB("Web"), PWN("Pwn"), CRYPTO("Crypto"), MISC("Misc"), REVERSE("Reverse");

    private final String label;

    Category(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
