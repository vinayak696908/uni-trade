package com.unitrade.service;

import com.unitrade.entity.Category;
import com.unitrade.entity.ItemCondition;
import com.unitrade.entity.ListingStatus;
import com.unitrade.entity.Product;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Search filters for listings, translated into a JPA Criteria query (parameterised, so safe from SQL injection).
 *
 * @param statuses which listing statuses to include (public search uses AVAILABLE only)
 */
public record ProductSearchCriteria(
        String query,
        Category category,
        String campus,
        ItemCondition condition,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        List<ListingStatus> statuses
) {

    private static final int MAX_TERMS = 5;

    public Specification<Product> toSpecification() {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (statuses != null && !statuses.isEmpty()) {
                predicates.add(root.get("status").in(statuses));
            }
            if (category != null) {
                predicates.add(cb.equal(root.get("category"), category));
            }
            if (condition != null) {
                predicates.add(cb.equal(root.get("condition"), condition));
            }
            if (campus != null && !campus.isBlank()) {
                predicates.add(cb.equal(root.get("campus"), campus));
            }
            if (minPrice != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<BigDecimal>get("price"), minPrice));
            }
            if (maxPrice != null) {
                predicates.add(cb.lessThanOrEqualTo(root.<BigDecimal>get("price"), maxPrice));
            }
            if (query != null && !query.isBlank()) {
                Expression<String> title = cb.lower(root.<String>get("title"));
                Expression<String> description = cb.lower(root.<String>get("description"));
                // Every search term must appear in the title or the description.
                Arrays.stream(query.trim().toLowerCase(Locale.ROOT).split("\\s+"))
                        .filter(t -> !t.isBlank())
                        .limit(MAX_TERMS)
                        .forEach(term -> {
                            String pattern = "%" + escapeLike(term) + "%";
                            predicates.add(cb.or(
                                    cb.like(title, pattern, '\\'),
                                    cb.like(description, pattern, '\\')));
                        });
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static String escapeLike(String term) {
        return term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /** Maps the frontend's sort keys to a Sort. Unknown keys fall back to newest first. */
    public static Sort sortFor(String key) {
        if (key == null) {
            return Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"));
        }
        return switch (key) {
            case "price_asc" -> Sort.by(Sort.Direction.ASC, "price").and(Sort.by(Sort.Direction.DESC, "id"));
            case "price_desc" -> Sort.by(Sort.Direction.DESC, "price").and(Sort.by(Sort.Direction.DESC, "id"));
            case "oldest" -> Sort.by(Sort.Direction.ASC, "createdAt").and(Sort.by(Sort.Direction.ASC, "id"));
            default -> Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"));
        };
    }
}
