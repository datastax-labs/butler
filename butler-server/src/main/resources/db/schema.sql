-- Identifies a given test
CREATE TABLE tests (
    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    category VARCHAR(255) NOT NULL, -- will be one of 'UNIT', 'DTEST' or 'OTHER'
    path VARCHAR(255) COLLATE utf8mb4_bin,   -- the path up to the test class
    class_name VARCHAR(255) COLLATE utf8mb4_bin NOT NULL,
    test_name VARCHAR(4096) COLLATE utf8mb4_bin NOT NULL, -- we have some ungodly long test in for some scala tests !!
    test_name_hash VARCHAR(40) AS (SHA1(test_name)), -- test name is too long to include in the index
    UNIQUE (path, class_name, test_name_hash)
);

-- List jobs for which we have builds
CREATE TABLE jobs (
    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    workflow VARCHAR(255) NOT NULL,
    job_name VARCHAR(255) NOT NULL,
    category VARCHAR(255),  -- one of 'UPSTREAM_DEV', 'UPSTREAM_REL', 'USER_PR'),
    UNIQUE (workflow, job_name)
);

-- Recorded builds, with the summary of their result.
CREATE TABLE builds (
    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    job_id INT UNSIGNED NOT NULL,
    build_number INT NOT NULL,
    status VARCHAR(255) NOT NULL,
    start_time DATETIME NOT NULL,
    duration_ms BIGINT NOT NULL,
    usable BOOLEAN NOT NULL,
    fully_stored BOOLEAN NOT NULL, -- We don't store the whole report of a build as a single transaction (too expensive). This is true
                                   -- when we've stored it all; false before.
    failed_tests INT UNSIGNED NOT NULL,
    ran_tests INT UNSIGNED NOT NULL,
    skipped_tests INT UNSIGNED NOT NULL,
    UNIQUE (job_id, build_number),
    FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE CASCADE
);

-- add column for jenkins build url so that we can build test report url correctly
ALTER TABLE builds ADD build_url varchar(255);

-- Records all test runs.
CREATE TABLE test_runs (
    test_id INT UNSIGNED,
    variant VARCHAR(255), -- For dtests, may be one of: 'vnodes', 'novnode', 'cqlsh'
                          -- For unit tests, can be null or one of: 'compression', 'encryption', 'nio'
    build_id INT UNSIGNED,
    run_blocks VARCHAR(255), -- some jenkins ugliness required to link back to the test page on jenkins
    failed BOOLEAN NOT NULL,
    skipped BOOLEAN NOT NULL,
    duration_ms BIGINT NOT NULL,
    INDEX idx_tr_build_failed (build_id, failed),
    INDEX test_runs_failed_idx (failed),
    PRIMARY KEY (build_id, test_id, variant),
    FOREIGN KEY (test_id) REFERENCES tests(id) ON DELETE CASCADE,
    FOREIGN KEY (build_id) REFERENCES builds(id) ON DELETE CASCADE
);

-- add column for jenkins build url so that we can build test report url correctly
ALTER TABLE test_runs ADD run_url varchar(255);

-- patch for TESTINF-1917 / TESTINF-1991
-- to collect error details like stacktrace or stdout in the butled db
ALTER TABLE test_runs ADD error_details TEXT;
ALTER TABLE test_runs ADD error_stack_trace TEXT;
ALTER TABLE test_runs ADD stdout MEDIUMTEXT;
ALTER TABLE test_runs ADD stderr MEDIUMTEXT;

-- Keeps links between tests and gh/jira issues
CREATE TABLE test_linked_issues (
    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    test_id INT UNSIGNED,
    linked_issue TINYTEXT NOT NULL,
    created_at DATETIME NOT NULL,
    FOREIGN KEY (test_id) REFERENCES tests(id) ON DELETE CASCADE
);

-- migration from UPSTREAM_FAILURES to TEST_LINKED_ISSUES
-- INSERT INTO test_linked_issues(test_id, linked_issue, created_at)
-- SELECT test_id, jira_issue, open_time
-- FROM upstream_failures WHERE jira_issue IS NOT NULL;

-- Simple table that records what versions are considered "maintained".
CREATE TABLE workflow_branches (
    workflow VARCHAR(255),
    branch VARCHAR(255),
    PRIMARY KEY (workflow, branch)
);

CREATE TABLE upstream_workflows (
    workflow VARCHAR(255) PRIMARY KEY
);

CREATE TABLE users (
    username VARCHAR(255) PRIMARY KEY,
    password VARCHAR(255) NOT NULL,
    enabled TINYINT NOT NULL DEFAULT 1
);

CREATE TABLE authorities (
    username VARCHAR(255),
    authority VARCHAR(50),
    FOREIGN KEY (username) REFERENCES users(username) ON DELETE CASCADE,
    PRIMARY KEY (username, authority)
);
