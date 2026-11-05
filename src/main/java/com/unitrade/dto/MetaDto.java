package com.unitrade.dto;

import java.util.List;

/**
 * Reference data the frontend uses to build dropdowns, so labels live in one place (the backend).
 */
public record MetaDto(
        List<Option> categories,
        List<Option> conditions,
        List<String> campuses,
        List<Option> reportReasons
) {
    public record Option(String value, String label) {
    }
}
