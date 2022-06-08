SELECT t.category, t.path, t.class_name, t.test_name, f.jira_issue, r.build_id, r.test_id,
GROUP_CONCAT(IF(r.failed,  CONCAT_WS('|', j.workflow, j.job_name, b.build_number, UNIX_TIMESTAMP(b.start_time),    r.variant,    r.run_blocks,    r.failed,    r.skipped),  null)) AS builds, 
SUM(r.failed) AS failed_count, 
COUNT(*) AS ran_count
FROM test_runs r 
INNER JOIN (builds b, tests t) ON (r.build_id = b.id AND r.test_id = t.id ) 
INNER JOIN upstream_failures f ON (f.test_id = r.test_id AND f.close_time IS NULL)  
INNER JOIN jobs j ON b.job_id = j.id 
WHERE r.skipped = false AND path = 'com.datastax.bdp.advrep.test' AND class_name = 'MapReplicationTest' AND test_name = 'testDeletionMapNotSet' GROUP BY r.test_id;


SELECT t.category, t.path, t.class_name, t.test_name, f.jira_issue, r.build_id, r.test_id, r.variant,
COUNT(*) AS ran_count
FROM test_runs r 
INNER JOIN (builds b, tests t) ON (r.build_id = b.id AND r.test_id = t.id ) 
INNER JOIN upstream_failures f ON (f.test_id = r.test_id AND f.close_time IS NULL)  
INNER JOIN jobs j ON b.job_id = j.id 
WHERE r.skipped = false AND path = 'com.datastax.bdp.advrep.test' AND class_name = 'MapReplicationTest' AND test_name = 'testDeletionMapNotSet' GROUP BY r.test_id;
