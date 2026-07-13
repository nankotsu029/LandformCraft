package com.github.nankotsu029.landformcraft.model;

public record GenerationOptions(int candidates, long baseSeed) {
    public GenerationOptions {
        if (candidates < 1 || candidates > 16) {
            throw new IllegalArgumentException("candidates must be between 1 and 16");
        }
    }
}
