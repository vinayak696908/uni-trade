package com.unitrade.entity;

import java.util.List;

/**
 * Fictional sample campuses. Listings and users must belong to one of these.
 * Edit this list (and restart) to support your own institutions.
 */
public final class Campuses {

    public static final List<String> ALL = List.of(
            "Northfield Institute of Technology",
            "Riverside College of Engineering",
            "Lakeview University",
            "Greenvale Science College",
            "Hillcrest Polytechnic"
    );

    private Campuses() {
    }

    public static boolean isValid(String campus) {
        return campus != null && ALL.contains(campus);
    }
}
