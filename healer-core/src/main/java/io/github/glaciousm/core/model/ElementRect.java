package io.github.glaciousm.core.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents the bounding rectangle of an element.
 */
public final class ElementRect {
    private final int x;
    private final int y;
    private final int width;
    private final int height;

    @JsonCreator
    public ElementRect(
            @JsonProperty("x") int x,
            @JsonProperty("y") int y,
            @JsonProperty("width") int width,
            @JsonProperty("height") int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    /**
     * Returns the center X coordinate of this rectangle.
     */
    public int getCenterX() {
        return x + width / 2;
    }

    /**
     * Returns the center Y coordinate of this rectangle.
     */
    public int getCenterY() {
        return y + height / 2;
    }

    /**
     * Returns the area of this rectangle.
     */
    public int getArea() {
        return width * height;
    }

    /**
     * Checks if this rectangle contains the given point.
     */
    public boolean contains(int px, int py) {
        return px >= x && px <= x + width && py >= y && py <= y + height;
    }

    /**
     * Checks if this rectangle intersects with another rectangle.
     */
    public boolean intersects(ElementRect other) {
        return x < other.x + other.width &&
               x + width > other.x &&
               y < other.y + other.height &&
               y + height > other.y;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ElementRect that = (ElementRect) o;
        return x == that.x && y == that.y && width == that.width && height == that.height;
    }

    @Override
    public int hashCode() {
        int result = x;
        result = 31 * result + y;
        result = 31 * result + width;
        result = 31 * result + height;
        return result;
    }

    @Override
    public String toString() {
        return "ElementRect{x=" + x + ", y=" + y + ", width=" + width + ", height=" + height + "}";
    }
}
