ALTER TABLE `user`
  ADD COLUMN profile_image_url VARCHAR(500) NULL AFTER phone,
  ADD COLUMN profile_image_updated_at TIMESTAMP NULL DEFAULT NULL AFTER profile_image_url;
