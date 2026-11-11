-- 64: 提供商支持多接口地址与按延迟自动选路
-- backupBaseUrls：备用接口地址，换行分隔；autoSelectEndpoint：是否按实测延迟自动选择。
ALTER TABLE `ai_providers` ADD COLUMN `backupBaseUrls` TEXT NOT NULL DEFAULT '';
ALTER TABLE `ai_providers` ADD COLUMN `autoSelectEndpoint` INTEGER NOT NULL DEFAULT 0;