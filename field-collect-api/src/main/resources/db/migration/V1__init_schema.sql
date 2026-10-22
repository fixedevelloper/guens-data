CREATE TABLE countries (
  id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  iso_code    VARCHAR(2)       NOT NULL,
  name        VARCHAR(100)  NOT NULL,
  active      TINYINT(1)    NOT NULL DEFAULT 1,
  created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT uq_countries_iso  UNIQUE (iso_code),
  CONSTRAINT uq_countries_name UNIQUE (name)
) ENGINE=InnoDB;

CREATE TABLE cities (
  id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  country_id  BIGINT UNSIGNED NOT NULL,
  name        VARCHAR(100)    NOT NULL,
  active      TINYINT(1)      NOT NULL DEFAULT 1,
  created_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_cities_country FOREIGN KEY (country_id) REFERENCES countries(id),
  CONSTRAINT uq_cities_country_name UNIQUE (country_id, name)
) ENGINE=InnoDB;

CREATE TABLE applications (
  id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  code        VARCHAR(50)   NOT NULL,
  name        VARCHAR(150)  NOT NULL,
  description VARCHAR(500)  NULL,
  active      TINYINT(1)    NOT NULL DEFAULT 1,
  created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT uq_applications_code UNIQUE (code)
) ENGINE=InnoDB;

CREATE TABLE application_cities (
  application_id BIGINT UNSIGNED NOT NULL,
  city_id        BIGINT UNSIGNED NOT NULL,
  PRIMARY KEY (application_id, city_id),
  CONSTRAINT fk_appcity_app  FOREIGN KEY (application_id) REFERENCES applications(id) ON DELETE CASCADE,
  CONSTRAINT fk_appcity_city FOREIGN KEY (city_id)        REFERENCES cities(id)       ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE users (
  id             BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  username       VARCHAR(80)   NOT NULL,
  password_hash  VARCHAR(100)  NOT NULL,
  full_name      VARCHAR(150)  NOT NULL,
  phone          VARCHAR(30)   NULL,
  role           ENUM('ADMIN','AGENT') NOT NULL DEFAULT 'AGENT',
  device_id      VARCHAR(191)  NULL,
  device_model   VARCHAR(150)  NULL,
  device_bound_at TIMESTAMP    NULL,
  active         TINYINT(1)    NOT NULL DEFAULT 1,
  last_login_at  TIMESTAMP     NULL,
  created_by     BIGINT UNSIGNED NULL,
  created_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT uq_users_username  UNIQUE (username),
  CONSTRAINT uq_users_device    UNIQUE (device_id),
  CONSTRAINT fk_users_creator   FOREIGN KEY (created_by) REFERENCES users(id)
) ENGINE=InnoDB;

CREATE TABLE user_assignments (
  user_id        BIGINT UNSIGNED NOT NULL,
  application_id BIGINT UNSIGNED NOT NULL,
  city_id        BIGINT UNSIGNED NOT NULL,
  PRIMARY KEY (user_id, application_id, city_id),
  CONSTRAINT fk_ua_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_ua_appcity FOREIGN KEY (application_id, city_id)
      REFERENCES application_cities(application_id, city_id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE refresh_tokens (
  id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  user_id     BIGINT UNSIGNED NOT NULL,
  token_hash  VARCHAR(64)        NOT NULL,
  device_id   VARCHAR(191)    NULL,
  expires_at  TIMESTAMP       NOT NULL,
  revoked     TINYINT(1)      NOT NULL DEFAULT 0,
  created_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uq_rt_hash UNIQUE (token_hash),
  CONSTRAINT fk_rt_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE persons (
  id                   BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  client_uuid          VARCHAR(36)        NOT NULL,
  application_id       BIGINT UNSIGNED NOT NULL,
  country_id           BIGINT UNSIGNED NOT NULL,
  city_id              BIGINT UNSIGNED NOT NULL,
  agent_id             BIGINT UNSIGNED NOT NULL,
  device_id            VARCHAR(191)    NOT NULL,
  capture_mode         ENUM('SCAN','MANUAL') NOT NULL,
  last_name            VARCHAR(100)    NOT NULL,
  first_name           VARCHAR(100)    NOT NULL,
  gender               ENUM('M','F','OTHER') NULL,
  birth_date           DATE            NULL,
  birth_place          VARCHAR(150)    NULL,
  nationality          VARCHAR(100)    NULL,
  phone                VARCHAR(30)     NULL,
  email                VARCHAR(150)    NULL,
  address              VARCHAR(255)    NULL,
  document_type        ENUM('ID_CARD','PASSPORT','DRIVER_LICENSE','RESIDENCE_PERMIT','OTHER') NULL,
  document_number_enc  VARBINARY(255)  NULL,
  document_number_hash VARCHAR(64)        NULL,
  document_issue_date  DATE            NULL,
  document_expiry_date DATE            NULL,
  document_image_path  VARCHAR(255)    NULL,
  ocr_raw_text         MEDIUMTEXT      NULL,
  ocr_confidence       DECIMAL(5,2)    NULL,
  extra_data           JSON            NULL,
  captured_at          DATETIME        NOT NULL,
  created_at           TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uq_persons_client_uuid UNIQUE (client_uuid),
  CONSTRAINT uq_persons_doc UNIQUE (application_id, document_type, document_number_hash),
  CONSTRAINT fk_persons_app     FOREIGN KEY (application_id) REFERENCES applications(id),
  CONSTRAINT fk_persons_country FOREIGN KEY (country_id)     REFERENCES countries(id),
  CONSTRAINT fk_persons_city    FOREIGN KEY (city_id)        REFERENCES cities(id),
  CONSTRAINT fk_persons_agent   FOREIGN KEY (agent_id)       REFERENCES users(id),
  INDEX idx_persons_stats   (country_id, city_id, application_id, created_at),
  INDEX idx_persons_agent   (agent_id, created_at),
  INDEX idx_persons_name    (last_name, first_name)
) ENGINE=InnoDB;

CREATE TABLE login_attempts (
  id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  username    VARCHAR(80)   NOT NULL,
  user_id     BIGINT UNSIGNED NULL,
  device_id   VARCHAR(191)  NULL,
  success     TINYINT(1)    NOT NULL,
  reason      ENUM('OK','BAD_CREDENTIALS','DEVICE_MISMATCH','INACTIVE','LOCKED') NOT NULL,
  ip_address  VARCHAR(45)   NULL,
  created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_la_user (user_id, created_at),
  INDEX idx_la_reason (reason, created_at)
) ENGINE=InnoDB;

CREATE TABLE audit_logs (
  id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  actor_id    BIGINT UNSIGNED NULL,
  action      VARCHAR(60)   NOT NULL,
  entity_type VARCHAR(60)   NULL,
  entity_id   BIGINT UNSIGNED NULL,
  details     JSON          NULL,
  created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_audit_actor (actor_id, created_at)
) ENGINE=InnoDB;
