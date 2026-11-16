package com.resolveiq.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.util.Objects;

/**
 * How one person wants the interface to look: theme, text size and reading aids.
 *
 * <p><b>Value object</b>: immutable, compared by value, and embedded in the {@code users} row
 * (<b>composition</b> - the preferences have no life of their own without their user). A change
 * replaces the whole object through {@link User#changeDisplayPreferences(DisplayPreferences)}.</p>
 */
@Embeddable
public class DisplayPreferences {

    @Enumerated(EnumType.STRING)
    @Column(name = "display_theme", nullable = false, length = 20)
    private ThemeChoice theme = ThemeChoice.SYSTEM;

    @Enumerated(EnumType.STRING)
    @Column(name = "display_text_size", nullable = false, length = 20)
    private TextSize textSize = TextSize.DEFAULT;

    /** True = always reduce animation; false = follow the device setting. */
    @Column(name = "display_reduce_motion", nullable = false)
    private boolean reduceMotion;

    /** Wider line, letter and word spacing. */
    @Column(name = "display_easier_reading", nullable = false)
    private boolean easierReading;

    @Column(name = "display_underline_links", nullable = false)
    private boolean underlineLinks;

    protected DisplayPreferences() {
        // for JPA; field initialisers are the defaults
    }

    public DisplayPreferences(ThemeChoice theme, TextSize textSize, boolean reduceMotion, boolean easierReading,
                              boolean underlineLinks) {
        this.theme = Objects.requireNonNull(theme, "theme");
        this.textSize = Objects.requireNonNull(textSize, "textSize");
        this.reduceMotion = reduceMotion;
        this.easierReading = easierReading;
        this.underlineLinks = underlineLinks;
    }

    public static DisplayPreferences defaults() {
        return new DisplayPreferences();
    }

    public ThemeChoice getTheme() {
        return theme;
    }

    public TextSize getTextSize() {
        return textSize;
    }

    public boolean isReduceMotion() {
        return reduceMotion;
    }

    public boolean isEasierReading() {
        return easierReading;
    }

    public boolean isUnderlineLinks() {
        return underlineLinks;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof DisplayPreferences p && theme == p.theme && textSize == p.textSize
                && reduceMotion == p.reduceMotion && easierReading == p.easierReading
                && underlineLinks == p.underlineLinks;
    }

    @Override
    public int hashCode() {
        return Objects.hash(theme, textSize, reduceMotion, easierReading, underlineLinks);
    }
}
