package com.unitrade.entity;

public enum Category {
    BOOKS("Books"),
    ELECTRONICS("Electronics"),
    CALCULATORS("Calculators"),
    LAB_EQUIPMENT("Lab Equipment"),
    STATIONERY("Stationery"),
    PROJECT_COMPONENTS("Project Components"),
    OTHER("Other");

    private final String label;

    Category(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
