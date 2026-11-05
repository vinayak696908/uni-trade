-- =====================================================================
-- UniTrade schema (MySQL 8.0+). Applied automatically by Flyway on startup.
-- Never edit this file after it has been applied; add V2__..., V3__... instead.
-- =====================================================================

CREATE TABLE users (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    full_name     VARCHAR(100) NOT NULL,
    email         VARCHAR(191) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    campus        VARCHAR(100) NOT NULL,
    phone         VARCHAR(20)  NULL,
    role          VARCHAR(20)  NOT NULL DEFAULT 'STUDENT',
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT chk_users_role CHECK (role IN ('STUDENT', 'ADMIN'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE products (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    seller_id      BIGINT        NOT NULL,
    title          VARCHAR(150)  NOT NULL,
    description    TEXT          NOT NULL,
    category       VARCHAR(30)   NOT NULL,
    item_condition VARCHAR(20)   NOT NULL,
    price          DECIMAL(10,2) NOT NULL,
    campus         VARCHAR(100)  NOT NULL,
    status         VARCHAR(20)   NOT NULL DEFAULT 'AVAILABLE',
    created_at     DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at     DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_products_seller FOREIGN KEY (seller_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_products_price CHECK (price > 0),
    CONSTRAINT chk_products_status CHECK (status IN ('AVAILABLE', 'SOLD', 'HIDDEN')),
    CONSTRAINT chk_products_condition CHECK (item_condition IN ('NEW', 'LIKE_NEW', 'GOOD', 'FAIR', 'POOR')),
    CONSTRAINT chk_products_category CHECK (category IN
        ('BOOKS', 'ELECTRONICS', 'CALCULATORS', 'LAB_EQUIPMENT', 'STATIONERY', 'PROJECT_COMPONENTS', 'OTHER')),
    INDEX idx_products_seller (seller_id),
    INDEX idx_products_status_created (status, created_at),
    INDEX idx_products_status_price (status, price),
    INDEX idx_products_category_status (category, status),
    INDEX idx_products_campus_status (campus, status),
    INDEX idx_products_condition_status (item_condition, status),
    FULLTEXT INDEX ft_products_title_description (title, description)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE product_images (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    product_id        BIGINT       NOT NULL,
    stored_filename   VARCHAR(80)  NOT NULL,
    original_filename VARCHAR(255) NULL,
    content_type      VARCHAR(50)  NOT NULL,
    size_bytes        BIGINT       NOT NULL,
    sort_order        INT          NOT NULL DEFAULT 0,
    created_at        DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_product_images_filename UNIQUE (stored_filename),
    CONSTRAINT fk_product_images_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
    CONSTRAINT chk_product_images_type CHECK (content_type IN ('image/jpeg', 'image/png', 'image/webp')),
    INDEX idx_product_images_product (product_id, sort_order)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE wishlist (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    product_id BIGINT      NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_wishlist_user_product UNIQUE (user_id, product_id),
    CONSTRAINT fk_wishlist_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_wishlist_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
    INDEX idx_wishlist_product (product_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE inquiries (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    product_id      BIGINT      NOT NULL,
    buyer_id        BIGINT      NOT NULL,
    seller_id       BIGINT      NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    last_message_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_inquiries_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
    CONSTRAINT fk_inquiries_buyer FOREIGN KEY (buyer_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_inquiries_seller FOREIGN KEY (seller_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_inquiries_status CHECK (status IN ('OPEN', 'CLOSED')),
    INDEX idx_inquiries_buyer (buyer_id, last_message_at),
    INDEX idx_inquiries_seller (seller_id, last_message_at),
    INDEX idx_inquiries_product_buyer (product_id, buyer_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Messages inside an inquiry thread (first message = the buyer's original inquiry).
CREATE TABLE inquiry_messages (
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    inquiry_id BIGINT        NOT NULL,
    sender_id  BIGINT        NOT NULL,
    body       VARCHAR(2000) NOT NULL,
    created_at DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_inquiry_messages_inquiry FOREIGN KEY (inquiry_id) REFERENCES inquiries (id) ON DELETE CASCADE,
    CONSTRAINT fk_inquiry_messages_sender FOREIGN KEY (sender_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_inquiry_messages_inquiry (inquiry_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE listing_reports (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    product_id  BIGINT        NOT NULL,
    reporter_id BIGINT        NOT NULL,
    reason      VARCHAR(30)   NOT NULL,
    details     VARCHAR(1000) NULL,
    status      VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    admin_note  VARCHAR(500)  NULL,
    resolved_by BIGINT        NULL,
    resolved_at DATETIME(6)   NULL,
    created_at  DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_report_product_reporter UNIQUE (product_id, reporter_id),
    CONSTRAINT fk_reports_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
    CONSTRAINT fk_reports_reporter FOREIGN KEY (reporter_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_reports_resolved_by FOREIGN KEY (resolved_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT chk_reports_status CHECK (status IN ('PENDING', 'ACTION_TAKEN', 'DISMISSED')),
    CONSTRAINT chk_reports_reason CHECK (reason IN
        ('SPAM', 'PROHIBITED_ITEM', 'MISLEADING', 'SCAM', 'OFFENSIVE', 'OTHER')),
    INDEX idx_reports_status_created (status, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
