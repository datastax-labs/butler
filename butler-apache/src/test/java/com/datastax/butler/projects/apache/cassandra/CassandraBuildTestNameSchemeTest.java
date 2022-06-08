/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.projects.apache.cassandra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.datastax.butler.commons.jenkins.TestVariant;
import org.junit.jupiter.api.Test;

class CassandraBuildTestNameSchemeTest {

  @Test
  void cqlshTestCallMethodIsNotRecognizedAsTest() {
    var scheme = Cassandra.cqlshTestScheme();
    var className = "cqlshlib.python2.jdk8.no_cython.test.test_cqlsh_output";
    var name = "testcall_cqlsh"; // this is some helper method not a test
    var id = scheme.createTestId(className, name);
    assertFalse(id.isPresent());
  }

  @Test
  void unitTestsSchemeMatchingSuite() {
    var scheme = Cassandra.unitTestScheme();
    assertTrue(scheme.suiteMatchesPattern("org.apache.cassandra.streaming.SessionInfoTest"));
    assertFalse(scheme.suiteMatchesPattern("vnodes-dtests.consistency_test.TestAccuracyNtsEq"));
  }

  @Test
  void dseUnitTestsCreateTestIdNoVariant() {
    var scheme = Cassandra.unitTestScheme();
    var id = scheme.createTestId("org.apache.cassandra.streaming.SessionInfoTest", "testTotals");
    assertTrue(id.isPresent());
    assertEquals("UNIT", id.get().name().category().toString());
    assertEquals("org.apache.cassandra.streaming", id.get().name().path());
    assertEquals("SessionInfoTest", id.get().name().className());
    assertEquals("testTotals", id.get().name().testName());
    assertEquals(TestVariant.DEFAULT, id.get().variant());
  }

  @Test
  void dseUnitTestsCreateTestWithVariant() {
    var scheme = Cassandra.unitTestScheme();
    var id =
        scheme.createTestId("org.apache.cassandra.streaming.SessionInfoTest", "testTotals-cdc");
    assertTrue(id.isPresent());
    assertEquals("UNIT", id.get().name().category().toString());
    assertEquals("org.apache.cassandra.streaming", id.get().name().path());
    assertEquals("SessionInfoTest", id.get().name().className());
    assertEquals("testTotals", id.get().name().testName());
    assertEquals("cdc", id.get().variant().toString());
  }

  @Test
  void dseDtestSchemeMatchingSuite() {
    var scheme = Cassandra.dtestTestScheme();
    assertTrue(
        scheme.suiteMatchesPattern(
            "dtest-large-offheap-bti.replace_address_test.TestReplaceAddress"));
    assertTrue(scheme.suiteMatchesPattern("dtest-offheap.write_failures_test.TestWriteFailures"));
    assertTrue(scheme.suiteMatchesPattern("dtest.write_failures_test.TestWriteFailures"));
  }

  @Test
  void dtestCreateTestIdWithVariant() {
    var scheme = Cassandra.dtestTestScheme();
    var id =
        scheme.createTestId(
            "dtest-large-offheap-bti.replace_address_test.TestReplaceAddress", "test_case");
    assertTrue(id.isPresent());
    assertEquals("DTEST", id.get().name().category().toString());
    assertEquals("replace_address_test", id.get().name().path());
    assertEquals("TestReplaceAddress", id.get().name().className());
    assertEquals("test_case", id.get().name().testName());
    assertEquals("large-offheap-bti", id.get().variant().toString());
  }

  @Test
  void dtestTestIdNoVariant() {
    var scheme = Cassandra.dtestTestScheme();
    var id = scheme.createTestId("dtest.replace_address_test.TestReplaceAddress", "test_case");
    assertTrue(id.isPresent());
    assertEquals("DTEST", id.get().name().category().toString());
    assertEquals("replace_address_test", id.get().name().path());
    assertEquals("TestReplaceAddress", id.get().name().className());
    assertEquals("test_case", id.get().name().testName());
    assertEquals(TestVariant.DEFAULT, id.get().variant());
  }

  @Test
  void cqlshTestWithVariant() {
    var className = "cqlshlib.python2.7-no-cython.jdk8.test.test_unicode.TestCqlshUnicode";
    var name = "test_unicode_desc";
    var scheme = Cassandra.cqlshTestScheme();
    var id = scheme.createTestId(className, name);
    assertTrue(id.isPresent());
    assertEquals("CQLSH", id.get().name().category().toString());
    assertEquals("test_unicode", id.get().name().path());
    assertEquals("TestCqlshUnicode", id.get().name().className());
    assertEquals("test_unicode_desc", id.get().name().testName());
    assertEquals("python2.7-no-cython.jdk8", id.get().variant().toString());
  }

  @Test
  void dtestUpgradeTestWithVariant() {
    var className = "dtest-upgrade-novnode-bti.upgrade_tests.bootstrap_upgrade_test.TestBootstrap";
    var name = "testCase";
    var scheme = Cassandra.dtestUpgradeTestScheme();
    var id = scheme.createTestId(className, name);
    assertTrue(id.isPresent());
    assertEquals("DT-UPGR", id.get().name().category().toString());
    assertEquals("upgrade_tests.bootstrap_upgrade_test", id.get().name().path());
    assertEquals("TestBootstrap", id.get().name().className());
    assertEquals("testCase", id.get().name().testName());
    assertEquals("novnode-bti", id.get().variant().toString());
  }

  @Test
  void dtestUpgradeInternalTestWithVariant() {
    var className = "dtest-upgrade-novnode.upgrade_internal_auth_test.TestAuthUpgrade";
    var name = "test_case";
    var scheme = Cassandra.dtestUpgradeTestScheme();
    var id = scheme.createTestId(className, name);
    assertTrue(id.isPresent());
    assertEquals("DT-UPGR", id.get().name().category().toString());
    assertEquals("upgrade_internal_auth_test", id.get().name().path());
    assertEquals("TestAuthUpgrade", id.get().name().className());
    assertEquals("test_case", id.get().name().testName());
    assertEquals("novnode", id.get().variant().toString());
  }

  @Test
  void jvmUpgradeNoVariant() {
    var className = "org.apache.cassandra.distributed.upgrade.MixedModeConsistencyV22Test";
    var name = "testConsistencyV22ToV30";
    var scheme = Cassandra.jvmUpgradeTestScheme();
    var id = scheme.createTestId(className, name);
    assertTrue(id.isPresent());
    assertEquals("JVM-UPGR", id.get().name().category().toString());
    assertEquals("org.apache.cassandra.distributed.upgrade", id.get().name().path());
    assertEquals("MixedModeConsistencyV22Test", id.get().name().className());
    assertEquals("testConsistencyV22ToV30", id.get().name().testName());
    assertEquals(TestVariant.DEFAULT, id.get().variant());
  }
}
