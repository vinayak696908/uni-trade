package com.unitrade.entity;

public enum ReportReason {
    SPAM("Spam or duplicate"),
    PROHIBITED_ITEM("Prohibited item"),
    MISLEADING("Misleading description or price"),
    SCAM("Suspected scam"),
    OFFENSIVE("Offensive content"),
    OTHER("Other");

    private final String label;

    ReportReason(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
