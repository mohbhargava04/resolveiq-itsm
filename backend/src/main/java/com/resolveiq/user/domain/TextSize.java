package com.resolveiq.user.domain;

/** Interface text size. Each step scales the root font size, and with it the whole layout. */
public enum TextSize {
    SMALL(87.5), DEFAULT(100), LARGE(112.5), EXTRA_LARGE(125);

    private final double percent;

    TextSize(double percent) {
        this.percent = percent;
    }

    /** Root font size as a percentage of the browser default. */
    public double percent() {
        return percent;
    }
}
