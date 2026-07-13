USE shineconnoisseur;
CREATE TABLE `admin` (
                         `id` bigint NOT NULL AUTO_INCREMENT,
                         `username` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
                         `password` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
                         `real_name` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
                         `email` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
                         `avatar` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
                         `status` tinyint DEFAULT '1',
                         `role` tinyint DEFAULT '0',
                         `last_login_time` datetime DEFAULT NULL,
                         `create_time` datetime DEFAULT NULL,
                         `update_time` datetime DEFAULT NULL,
                         PRIMARY KEY (`id`),
                         UNIQUE KEY `username` (`username`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci