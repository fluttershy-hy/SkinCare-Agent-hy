CREATE TABLE IF NOT EXISTS user_profile (
    user_id VARCHAR(40) PRIMARY KEY,
    skin_type VARCHAR(80) NOT NULL
);

CREATE TABLE IF NOT EXISTS user_allergen (
    user_id VARCHAR(40) NOT NULL,
    allergen VARCHAR(80) NOT NULL,
    PRIMARY KEY (user_id, allergen),
    FOREIGN KEY (user_id) REFERENCES user_profile(user_id)
);

CREATE TABLE IF NOT EXISTS user_product_ingredients (
    user_id VARCHAR(40) NOT NULL,
    product_key VARCHAR(120) NOT NULL,
    product_name VARCHAR(120) NOT NULL,
    ingredients_text VARCHAR(6000) NOT NULL,
    source_label VARCHAR(40) NOT NULL,
    saved_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY (user_id, product_key)
);
