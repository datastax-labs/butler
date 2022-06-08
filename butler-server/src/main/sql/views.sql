-- bdp daily fast CI jobs
create view bdp_fastci_jobs as select id from jobs where workflow='bdp-daily-fast-ci';

-- bdp daily fast CI builds
drop view bdp_fastci_builds; 
create view bdp_fastci_builds as 
select B.*, J.workflow, J.job_name as branch from builds B 
join jobs J on B.job_id=J.id 
where J.workflow = 'bdp-daily-fast-ci';


-- top failures by package
select T.category, T.path, count(*) as nfail from test_runs R
join bdp_fastci_builds B on build_id=B.id and R.failed=1
join tests T on R.test_id = T.id
where T.category is not null AND T.class_name not like 'utest%'
group by 1,2
having nfail >= 4
order by nfail desc;

select T.category, T.path, count(*) as nfail from test_runs R
join bdp_fastci_builds B on build_id=B.id and R.failed=1
join tests T on R.test_id = T.id
where T.category is not null AND T.class_name not like 'utest%'
AND T.category != 'UPGRADE'
group by 1,2
having nfail >= 4
order by nfail desc;


-- top failures by suite
select T.category, T.path, T.class_name, count(*) as nfail from test_runs R
join bdp_fastci_builds B on build_id=B.id and R.failed=1
join tests T on R.test_id = T.id
where T.category is not null AND T.class_name not like 'utest%'
AND T.category != 'UPGRADE'
group by 1,2,3
having nfail >= 4
order by nfail desc;

-- top failures by test
select T.category, T.path, T.class_name, T.test_name, count(*) as nfail from test_runs R
join bdp_fastci_builds B on build_id=B.id and R.failed=1
join tests T on R.test_id = T.id
where T.category is not null AND T.class_name not like 'utest%'
group by 1,2,3,4
having nfail >= 4
order by nfail desc;

select T.category, T.path, T.class_name, T.test_name, count(*) as nfail from test_runs R
join bdp_fastci_builds B on build_id=B.id and R.failed=1
join tests T on R.test_id = T.id
where T.category is not null AND T.class_name not like 'utest%'
AND B.start_time >= NOW() - INTERVAL 30 DAY
AND T.category != 'UPGRADE'
AND B.branch='6.8-dev'
group by 1,2,3,4
having nfail >= 4
order by nfail desc;


select T.category, T.path, T.class_name, T.test_name, count(*) as nfail from test_runs R
join bdp_fastci_builds B on build_id=B.id and R.failed=1
join tests T on R.test_id = T.id
where T.category is not null AND T.class_name not like 'utest%'
AND B.start_time >= NOW() - INTERVAL 30 DAY
AND T.category != 'UPGRADE'
group by 1,2,3,4
having nfail >= 4
order by nfail desc;


+----------+----------------------------------------------------------------+-------+
| category | path                                                           | nfail |
+----------+----------------------------------------------------------------+-------+
| UPGRADE  | upgrade_tests.cql_tests                                        | 14121 |
| UNIT     | com.datastax.bdp.cassandra.auth.functional                     |   539 |
| DTEST    | thrift_tests.thrift_upgrade_test                               |   280 |
| DTEST    | dtests.ttl_test                                                |   271 |
| DTEST    | dtests.replica_side_filtering_test                             |   216 |
| UNIT     | com.datastax.bdp.cassandra.encryption.functional               |   182 |
| UPGRADE  | upgrade_tests.upgrade_materialized_view_test                   |   181 |
| DTEST    | dtests.paging_test                                             |   181 |
| UNIT     |                                                                |   159 |
| DTEST    | dtests.consistency_test                                        |   146 |
| UPGRADE  | upgrade_tests.storage_engine_upgrade_test                      |   142 |
| DTEST    | repair_tests.nodesync_test                                     |   138 |
| DTEST    | dtests.compaction_test                                         |   122 |
| UNIT     | com.datastax.bdp.cassandra.audit.functional                    |   118 |
| UNIT     | org.apache.cassandra.cql3                                      |   116 |
| DTEST    | dtests.materialized_views_test                                 |   113 |
| UNIT     | com.datastax.bdp.search.solr.functional                        |   109 |
| DTEST    | dtests.backups_test                                            |   105 |
| UNIT     | com.datastax.bdp.db.audit                                      |   102 |
| UPGRADE  | upgrade_tests.nodesync_upgrade_test                            |   101 |
| DTEST    | dtests.jmx_test                                                |    92 |
| UNIT     | com.datastax.bdp.cassandra.cache                               |    80 |
| UNIT     | org.apache.cassandra.auth                                      |    78 |
| UNIT     | org.apache.cassandra.tools                                     |    74 |
| DTEST    | repair_tests.repair_test                                       |    71 |
| UPGRADE  | upgrade_tests.compatibility_flag_test                          |    65 |
| UNIT     | com.datastax.bdp.advrep.test                                   |    64 |
| UNIT     | org.apache.cassandra.index.sai.disk                            |    59 |
| UNIT     | org.apache.cassandra.db.compaction                             |    55 |
| UNIT     | com.datastax.bdp.search.solr.metrics.unit                      |    50 |
| UNIT     | com.datastax.bdp.fs.client                                     |    46 |
| UNIT     | com.datastax.bdp.cassandra.cql3.inject                         |    45 |
| UNIT     | com.datastax.bdp.config                                        |    44 |
| DTEST    | dtests.read_repair_test                                        |    44 |
| DTEST    | dtests.json_test                                               |    43 |
| UNIT     | org.apache.cassandra.service.reads.range                       |    43 |
| UNIT     | org.apache.cassandra.concurrent                                |    35 |
| UPGRADE  | upgrade_tests.repair_test                                      |    34 |
| UNIT     | com.datastax.bdp.util                                          |    33 |
| DTEST    | dtests.upgrade_internal_auth_test                              |    32 |
| DTEST    | dtests.cql_tests                                               |    32 |
| UNIT     | org.apache.cassandra.index.sai.cql                             |    31 |
| UNIT     | org.apache.spark.deploy.rm                                     |    30 |
| DTEST    | dtests.offline_tools_test                                      |    29 |
| UNIT     | org.apache.spark.sql.cassandra                                 |    29 |
| UNIT     | com.datastax.bdp.graph.impl                                    |    27 |
| UNIT     | com.datastax.bdp.auth                                          |    27 |
| UNIT     | com.datastax.bdp.insights                                      |    26 |
| UNIT     | com.datastax.bdp.graph.index.functional                        |    25 |
| UNIT     | com.datastax.bdp.db.backups                                    |    25 |
| DTEST    | <plugins                                                       |    25 |
| UNIT     | com.datastax.bdp.spark.rm                                      |    24 |
| DTEST    | dtests.paxos_tests                                             |    23 |
| UNIT     | com.datastax.bdp.graphv2.engine                                |    22 |
| UNIT     | org.apache.cassandra.db.lifecycle                              |    22 |
| UNIT     | org.apache.cassandra.metrics                                   |    21 |
| UNIT     | com.datastax.bdp.db.guardrails                                 |    21 |
| DTEST    | cqlsh_tests.cqlsh_tests                                        |    21 |
| DTEST    | dtests.cql_tracing_test                                        |    19 |
| UNIT     | com.datastax.bdp.cassandra.crypto                              |    19 |
| UNIT     | com.datastax.bdp.concurrent.metrics.unit                       |    18 |
| DTEST    | dtests.bootstrap_test                                          |    18 |
| UNIT     | org.apache.cassandra.service                                   |    18 |
| UNIT     | org.apache.cassandra.utils.memory                              |    17 |
| UNIT     | org.apache.tinkerpop.gremlin.process.computer.search.path      |    17 |
| UNIT     | com.datastax.bdp.cassandra.auth                                |    17 |
| UNIT     | com.datastax.bdp.fs.server.tx                                  |    16 |
| UNIT     | org.apache.tinkerpop.gremlin.process.computer.ranking.pagerank |    16 |
| DTEST    | dtests.topology_test                                           |    15 |
| UNIT     | org.apache.cassandra.cql3.validation.entities                  |    15 |
| UNIT     | com.datastax.bdp.db.nodesync                                   |    15 |
| UNIT     | org.apache.cassandra.cql3.continuous.paging                    |    14 |
| UNIT     | org.apache.cassandra.db.view                                   |    14 |
| UNIT     | com.datastax.spark.connector.rdd                               |    14 |
| UNIT     | com.datastax.bdp.plugin.unit                                   |    13 |
| DTEST    | dtests.stress_tool_test                                        |    13 |
| UNIT     | org.apache.cassandra.net.async                                 |    13 |
| UNIT     | com.datastax.bdp.fs.server                                     |    12 |
| UNIT     | org.apache.cassandra.index.sai.functional                      |    12 |
| DTEST    | dtests.rebuild_test                                            |    12 |
| UNIT     | org.apache.cassandra.db                                        |    10 |
| UNIT     | org.apache.cassandra.index.sasi                                |    10 |
| UNIT     | org.apache.cassandra.cql3.validation.miscellaneous             |     8 |
| UNIT     | org.apache.cassandra.index.sai.cql.types.collections.maps      |     8 |
| DTEST    | dtests.hintedhandoff_test                                      |     8 |
| UNIT     | com.datastax.bdp.graphv2.dsedb.query                           |     8 |
| DTEST    | dtests.seed_test                                               |     7 |
| UNIT     | com.datastax.bdp.hadoop.hive.metastore                         |     7 |
| DTEST    | dtests.sstablesplit_test                                       |     7 |
| UNIT     | org.apache.cassandra.index.sai.cql.types                       |     7 |
| UNIT     | com.datastax.bdp.analytics.rm.plan                             |     7 |
| UNIT     | org.apache.cassandra.db.compaction.unified                     |     6 |
| UNIT     | com.datastax.bdp.analytics.rm                                  |     6 |
| DTEST    | dtests.replication_test                                        |     6 |
| UNIT     | com.datastax.bdp.db.backups.executor                           |     6 |
| UNIT     | com.datastax.bdp.leasemanager                                  |     6 |
| UPGRADE  | upgrade_tests.feature_upgrade_test                             |     5 |
| UNIT     | org.apache.cassandra.index                                     |     5 |
| UNIT     | com.datastax.bdp.reporting.snapshots.spark                     |     5 |
| UNIT     | com.datastax.bdp.gcore.shareddata                              |     5 |
| UNIT     | org.apache.cassandra.cache                                     |     5 |
| DTEST    | dtests.tpc_cores_test                                          |     5 |
| UNIT     | org.apache.cassandra.cql3.validation.operations                |     5 |
| UNIT     | org.apache.cassandra.index.sai.cql.types.collections.lists     |     5 |
| DTEST    |                                                                |     5 |
| UNIT     | com.datastax.bdp.node.transport.functional                     |     5 |
| UNIT     | com.datastax.bdp.graph.index                                   |     5 |
| DTEST    | dtests.replace_address_test                                    |     5 |
| UNIT     | com.datastax.bdp.rm.spark                                      |     5 |
| DTEST    | dtests.scrub_test                                              |     4 |
| DTEST    | thrift_tests.thrift_hsha_test                                  |     4 |
| UNIT     | com.datastax.bdp.test.parallel                                 |     4 |
| UNIT     | org.apache.cassandra.io.sstable                                |     4 |
| UNIT     | com.datastax.bdp.cassandra.metrics.unit                        |     4 |
| UNIT     | com.datastax.bdp.gcore.config                                  |     4 |
| UNIT     | com.datastax.bdp.spark.ha.alwaysonsql                          |     4 |
| UNIT     | org.apache.cassandra.batchlog                                  |     4 |
| UNIT     | com.datastax.bdp.graph.id.allocator                            |     4 |
| UNIT     | org.apache.cassandra.utils                                     |     4 |
+----------+----------------------------------------------------------------+-------+
119 rows in set (0.147 sec)

