package com.unitrade.controller;

import com.unitrade.dto.MetaDto;
import com.unitrade.dto.MetaDto.Option;
import com.unitrade.entity.Campuses;
import com.unitrade.entity.Category;
import com.unitrade.entity.ItemCondition;
import com.unitrade.entity.ReportReason;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;

@RestController
public class MetaController {

    @GetMapping("/api/meta")
    public MetaDto meta() {
        return new MetaDto(
                Arrays.stream(Category.values()).map(c -> new Option(c.name(), c.getLabel())).toList(),
                Arrays.stream(ItemCondition.values()).map(c -> new Option(c.name(), c.getLabel())).toList(),
                Campuses.ALL,
                Arrays.stream(ReportReason.values()).map(r -> new Option(r.name(), r.getLabel())).toList());
    }
}
