package com.unitrade.entity;

/**
 * AVAILABLE - visible and purchasable.
 * SOLD      - visible but marked sold by the seller.
 * HIDDEN    - removed from public view by a moderator; only the owner and admins can see it.
 */
public enum ListingStatus {
    AVAILABLE,
    SOLD,
    HIDDEN
}
