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
