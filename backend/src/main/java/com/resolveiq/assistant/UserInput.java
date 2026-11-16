package com.resolveiq.assistant;

/**
 * What the person sent in one turn: free text they typed (or dictated), the value of an option they
 * tapped, or both (the option's label is sent as text so the transcript reads naturally).
 */
public record UserInput(String text, String choice) {

    public UserInput {
        text = text == null ? "" : text.trim();
        choice = choice == null || choice.isBlank() ? null : choice.trim();
    }

    public static UserInput ofText(String text) {
        return new UserInput(text, null);
    }

    public static UserInput ofChoice(String choice) {
        return new UserInput(null, choice);
    }

    public boolean isEmpty() {
        return text.isEmpty() && choice == null;
    }

    public boolean chose(String... values) {
        if (choice == null) {
            return false;
        }
        for (String v : values) {
            if (v.equalsIgnoreCase(choice)) {
                return true;
            }
        }
        return false;
    }

    /** How this turn appears in the transcript. */
    public String display() {
        return !text.isEmpty() ? text : choice;
    }
}
