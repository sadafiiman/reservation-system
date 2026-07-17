-- ============================================================================
-- Generates >1,000,000 available_slots rows to match the task's stated scale
--  so the index/SKIP LOCKED
-- strategy can be verified against realistic data volume, not a toy dataset.
--
-- Run against the mysql container:
--   docker exec -i azki-mysql mysql -uazki -pazki_password azki_reservations < scripts/generate_mock_data.sql
-- ============================================================================

SET @row_count = 1200000;
SET @start_base = NOW();

DROP PROCEDURE IF EXISTS generate_slots;

DELIMITER $$
CREATE PROCEDURE generate_slots(IN total INT)
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE batch_size INT DEFAULT 5000;

    WHILE i < total DO
        START TRANSACTION;

        INSERT INTO available_slots (start_time, end_time, is_reserved, version)
        SELECT
            DATE_ADD(@start_base, INTERVAL (i + n) HOUR),
            DATE_ADD(@start_base, INTERVAL (i + n + 1) HOUR),
            -- ~70% pre-reserved so a query for "nearest free slot" has to actually skip rows,
            -- mirroring a realistically-loaded production table rather than an all-empty one.
            (RAND() < 0.7),
            0
        FROM (
            SELECT a.N + b.N * 100 + c.N * 10000 AS n
            FROM
                (SELECT 0 AS N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
                 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) a,
                (SELECT 0 AS N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
                 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) b,
                (SELECT 0 AS N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4) c
        ) numbers
        WHERE n < batch_size AND (i + n) < total;

        COMMIT;
        SET i = i + batch_size;
    END WHILE;
END$$
DELIMITER ;

CALL generate_slots(@row_count);
DROP PROCEDURE generate_slots;

SELECT COUNT(*) AS total_slots FROM available_slots;
SELECT COUNT(*) AS free_slots FROM available_slots WHERE is_reserved = FALSE;
