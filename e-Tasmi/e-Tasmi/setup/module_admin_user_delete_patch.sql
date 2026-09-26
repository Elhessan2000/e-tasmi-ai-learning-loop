ALTER TABLE `user`
  MODIFY `status` ENUM('ACTIVE','INACTIVE','DELETED') NOT NULL DEFAULT 'ACTIVE';

UPDATE `user` u
JOIN audit_log a
  ON a.action = 'USER_DELETED'
 AND a.entity_type = 'USER'
 AND CAST(a.entity_id AS UNSIGNED) = u.user_id
SET u.is_active = 0,
    u.status = 'DELETED',
    u.email = CONCAT('deleted+', u.user_id, '+', UNIX_TIMESTAMP(), '@deleted.etasmi.local'),
    u.email_verified = 0,
    u.email_verified_at = NULL,
    u.email_verification_token_hash = NULL,
    u.email_verification_token_expires_at = NULL
WHERE u.status <> 'DELETED';
