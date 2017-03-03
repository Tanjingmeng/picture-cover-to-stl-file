package com.project.tipta.image2litho;

/**
 * Created by TipTa on 10/11/2016.
 */

public class Setting {

    boolean positive = true;
    float max_size = 101.0f;
    float thickness = 3.0f;
    float border = 0.0f;
    float thinnest_layer = 2.0f;
    int pixel_per_mm = 2;

    public Setting() {
    }

    public boolean isPositive() {
        return positive;
    }

    public void setPositive(boolean positive) {
        this.positive = positive;
    }

    public float getMax_size() {
        return max_size;
    }

    public void setMax_size(float max_size) {
        this.max_size = max_size;
    }

    public float getThickness() {
        return thickness;
    }

    public void setThickness(float thickness) {
        this.thickness = thickness;
    }

    public float getBorder() {
        return border;
    }

    public void setBorder(float border) {
        this.border = border;
    }

    public float getThinnest_layer() {
        return thinnest_layer;
    }

    public void setThinnest_layer(float thinnest_layer) {
        this.thinnest_layer = thinnest_layer;
    }

    public int getPixel_per_mm() {
        return pixel_per_mm;
    }

    public void setPixel_per_mm(int pixel_per_mm) {
        this.pixel_per_mm = pixel_per_mm;
    }
}
