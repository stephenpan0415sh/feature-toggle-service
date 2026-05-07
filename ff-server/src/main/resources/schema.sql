CREATE DATABASE IF NOT EXISTS feature_toggle DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE feature_toggle;

CREATE TABLE app (
    id bigint NOT NULL AUTO_INCREMENT,
    app_key varchar(64) NOT NULL,
    name varchar(128) NOT NULL,
    created_at datetime DEFAULT CURRENT_TIMESTAMP,
    updated_at datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_app_key (app_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE feature_flag (
    id bigint NOT NULL AUTO_INCREMENT,
    app_id bigint NOT NULL,
    flag_key varchar(128) NOT NULL,
    name varchar(128) NOT NULL,
    environment varchar(32) NOT NULL DEFAULT 'prod',
    status tinyint DEFAULT 1,
    default_value varchar(255) DEFAULT 'false',
    version bigint DEFAULT 0,
    release_version varchar(64) DEFAULT NULL COMMENT 'Associated release version (e.g., v1.2.3)',
    created_at datetime DEFAULT CURRENT_TIMESTAMP,
    updated_at datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_app_env_key (app_id, environment, flag_key),
    KEY idx_app_id (app_id),
    KEY idx_environment (environment)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE flag_rule (
    id bigint NOT NULL AUTO_INCREMENT,
    flag_id bigint NOT NULL,
    priority int NOT NULL,
    conditions json NOT NULL,
    rule_default_enabled tinyint(1) NOT NULL DEFAULT 0 COMMENT 'Default enabled state when rule matches',
    description varchar(512) DEFAULT '',
    PRIMARY KEY (id),
    KEY idx_flag_priority (flag_id, priority)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
