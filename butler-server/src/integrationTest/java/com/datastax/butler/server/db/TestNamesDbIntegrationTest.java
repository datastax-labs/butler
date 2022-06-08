/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.server.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.datastax.butler.commons.jenkins.TestCategory;
import com.datastax.butler.commons.jenkins.TestName;
import com.datastax.butler.server.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class TestNamesDbIntegrationTest extends IntegrationTest {

  @Autowired private TestNamesDb testNamesDb;

  @Test
  void shouldInsertRowOnlyOnceIfTestNameIsSame() {
    // given
    var testCase = randomTestCase();
    var testName1 = new TestName(TestCategory.UNKNOWN, "com.example", "Suite", testCase);
    var testName2 = new TestName(TestCategory.UNKNOWN, "com.example", "Suite", testCase);
    // when
    long id1 = testNamesDb.dbId(testName1);
    long id2 = testNamesDb.dbId(testName2);
    var retrieved = testNamesDb.find(List.of(id1, id2));
    // then
    assertTrue(id1 > 0);
    assertEquals(id1, id2);
    assertEquals(1, retrieved.size());
    assertTrue(retrieved.containsKey(id1));
  }

  @Test
  void shouldInsertRowWithNullCategory() {
    // given
    var testCase = randomTestCase();
    var testName = new TestName(null, "com.example", "Suite", testCase);
    var testNameWithCat =
        new TestName(TestCategory.valueOf("UTEST"), "com.example", "Suite", testCase);
    // when
    long id = testNamesDb.dbId(testName);
    long idWithCat = testNamesDb.dbId(testNameWithCat);
    // then
    assertTrue(id > 0);
    assertEquals(id, idWithCat);
  }
}
