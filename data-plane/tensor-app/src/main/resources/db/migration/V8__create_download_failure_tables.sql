SET time_zone = '+00:00';

CREATE TABLE `tensor_download_task` (
    `task_id` CHAR(36) NOT NULL,
    `plugin_id` VARCHAR(64) NOT NULL,
    `api_name` VARCHAR(64) NOT NULL,
    `task_params` JSON NOT NULL,
    `created_at` DATETIME(3) NOT NULL,
    `updated_at` DATETIME(3) NOT NULL,
    PRIMARY KEY (`task_id`),
    KEY `idx_download_task_updated` (`updated_at`, `task_id`),
    CONSTRAINT `chk_download_task_params_object`
        CHECK (JSON_TYPE(`task_params`) = 'OBJECT')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `tensor_download_task_item` (
    `task_id` CHAR(36) NOT NULL,
    `target_type` VARCHAR(16) NOT NULL,
    `target_value` VARCHAR(64) NOT NULL,
    `time_type` VARCHAR(16) NOT NULL,
    `time_value` VARCHAR(64) NOT NULL,
    `error_code` VARCHAR(64) NOT NULL,
    `error_message` VARCHAR(512) NOT NULL,
    `updated_at` DATETIME(3) NOT NULL,
    PRIMARY KEY (`task_id`, `target_type`, `target_value`, `time_type`, `time_value`),
    CONSTRAINT `fk_download_task_item_task` FOREIGN KEY (`task_id`)
        REFERENCES `tensor_download_task` (`task_id`)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT `chk_download_task_item_target` CHECK (
        (`target_type` = 'REQUEST' AND `target_value` = '') OR
        (`target_type` = 'STOCK' AND `target_value` <> '')),
    CONSTRAINT `chk_download_task_item_time` CHECK (
        (`time_type` = 'NONE' AND `time_value` = '') OR
        (`time_type` IN ('DATE', 'MONTH', 'RANGE') AND `time_value` <> ''))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
