SELECT test_id, T.category, T.class_name, T.test_name, AVG(duration_ms) / 1000 as avg_duration_sec
FROM test_runs TR LEFT JOIN tests T on TR.test_id = T.id
WHERE TR.failed = 0
GROUP BY test_id
HAVING avg_duration_sec > 60 AND T.category NOT IN ('UPGRADE') AND T.class_name NOT LIKE 'utests%'
ORDER BY avg_duration_sec DESC
LIMIT 100;
