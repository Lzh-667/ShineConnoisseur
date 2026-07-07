USE shineconnoisseur;
CREATE TABLE admin (
                       id BIGINT PRIMARY KEY AUTO_INCREMENT,
                       username VARCHAR(50) NOT NULL UNIQUE,
                       password VARCHAR(255) NOT NULL,
                       real_name VARCHAR(50),
                       email VARCHAR(100),
                       avatar VARCHAR(255),
                       status TINYINT DEFAULT 1,
                       role TINYINT DEFAULT 0,
                       last_login_time DATETIME,
                       create_time DATETIME,
                       update_time DATETIME
);