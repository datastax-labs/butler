/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.server.db;

import com.datastax.butler.commons.jenkins.TestCategory;
import com.datastax.butler.commons.jenkins.TestName;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.agrona.collections.Object2LongHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles accesses to the test names known of the database (so mostly maintenance of the {@link
 * TestNamesDb#TABLE} table).
 */
@Repository
@CacheConfig(cacheNames = {"test_names"})
public class TestNamesDb extends DbTableService {

  public static final String TABLE = "tests";

  /**
   * Caches TEST_NAMES::ID for the stored test names.
   *
   * <p>We mostly do this 1) to save ourselves from having to query {@link #TABLE} for almost every
   * test run when saving a build test report (which can contain more than 50k runs) and 2) have a
   * cheap way to even know if the test name exists in the database in the first place or not.
   *
   * <p>This is probably not an essential optimization (because database still has to enforce
   * integrity constraints, we don't necessarily save "that" much), but it's easy enough to
   * implement and probably simplify things overall.
   */
  private final Object2LongHashMap<TestName> testIdCache = new Object2LongHashMap<>(-1L);

  private final Mapper<TestName> testNameMapper =
      Mapper.create(TestName.class, "path", "class_name", "test_name");
  private final TableMapper<TestNameDto, Long> testsMapper;

  /** Creates the repository (Auto-wired by Spring). */
  @Autowired
  public TestNamesDb(NamedParameterJdbcTemplate template) {
    super(template, TABLE);
    this.testsMapper = tableMapper(TestNameDto.class, Long.class);
  }

  Mapper<TestName> testNameMapper() {
    return testNameMapper;
  }

  /**
   * Populates the internal cache of test names to their database id.
   *
   * <p>This is called automatically post-construction by Spring.
   */
  @PostConstruct
  public void populateIdCache() {
    List<TestNameDto> knowTests = testsMapper.getAll();
    logger.info("Loading {} names into the tests cache", knowTests.size());
    knowTests.forEach(withId -> testIdCache.put(withId.value(), withId.id()));
  }

  /**
   * Gets the database ID of the provided test name if it is stored, or store it if it is unknown
   * and return the newly assigned ID.
   *
   * @param name the test name for which to get the database id (storing it if not already stored).
   * @return the database ID of {@code name}, which will be stored once this method return (either
   *     because it already was, or because if was inserted).
   */
  @Transactional
  public long dbId(TestName name) {
    long id = testIdCache.getValue(name);
    if (id >= 0) {
      return id;
    }

    id = getId(name).orElseGet(() -> insertTest(name));
    testIdCache.put(name, id);
    return id;
  }

  private Optional<Long> getId(TestName name) {
    return testsMapper.getUnique(name, testNameMapper).map(TestNameDto::id);
  }

  private long insertTest(TestName testName) {
    return testsMapper.insert(new TestNameDto(-1L, testName));
  }

  /**
   * Find test names from a class name and test name.
   *
   * @param className the class name to search for.
   * @param testName the test name to search for.
   * @return the (possibly empty) list of test names in the database whose {@code class_name} is
   *     {@code className} <b>and</b> {@code test_name} is {@code testName}. Please not that in
   *     theory we would need the path to ensure uniqueness of the result, which is why this return
   *     a list. However, in practice, this will almost surely return either no result, or only one.
   */
  public List<TestName> find(String className, String testName) {
    String whereClause = "class_name=:class_name and test_name=:test_name";
    return testsMapper
        .getWhere(whereClause, Map.of("class_name", className, "test_name", testName), "id DESC")
        .stream()
        .map(TestNameDto::value)
        .collect(Collectors.toUnmodifiableList());
  }

  /**
   * Find test names from a path(package) and class name and test name.
   *
   * @param path path of the test (usually package)
   * @param className the class name to search for.
   * @param testName the test name to search for.
   * @return the (possibly empty) list of test names in the database
   */
  public List<TestName> find(String path, String className, String testName) {
    String whereClause = "path=:path AND class_name=:class_name and test_name=:test_name";
    return testsMapper
        .getWhere(
            whereClause,
            Map.of("path", path, "class_name", className, "test_name", testName),
            "id DESC")
        .stream()
        .map(TestNameDto::value)
        .collect(Collectors.toUnmodifiableList());
  }

  /**
   * Load a test name by it's TEST_NAMES::ID.
   *
   * @param id The db id of the test
   * @return a optional test name
   */
  @Cacheable("test_names")
  public Optional<TestName> find(long id) {
    return template
        .query(
            q("SELECT * FROM %s WHERE id=:id", TABLE),
            Map.of("id", id),
            testsMapper.mapper().rowMapper())
        .stream()
        .map(TestNameDto::value)
        .findFirst();
  }

  /**
   * Read map of testId -> TestName by reading from the database.
   *
   * @param ids list of IDs to retrieve.
   * @return immutable map
   */
  public Map<Long, TestName> find(List<Long> ids) {
    var names =
        new ArrayList<>(
            template.query(
                q("SELECT * FROM %s WHERE id IN (%s)", TABLE, idsToInClause(ids)),
                testsMapper.mapper().rowMapper()));
    return names.stream().collect(Collectors.toMap(Id::id, Id::value));
  }

  /**
   * Simple class to represent a row in the {@link #TABLE} table, which is essentially just a {@link
   * TestName} plus the database id, but SimpleFlatMapper needs this to work properly (and needs the
   * additional ctor and getter added here).
   *
   * <p>Also note that this is public because SimplFlatMapper is unhappy otherwise, but this class
   * is not meant to be used outside this class.
   *
   * <p>Also also note that the testNameHash is a "VIRTUAL GENERATED" field in the db, to assist
   * with the unique index, and doesn't need to be persisted or utilized, it's just here to make the
   * ORM happy.
   */
  @Value
  @EqualsAndHashCode(callSuper = true)
  @SuppressWarnings("MissingOverride")
  public static class TestNameDto extends Id<TestName> {
    public TestNameDto(long id, TestName value) {
      super(id, value);
    }

    public TestNameDto(
        long id,
        TestCategory category,
        String path,
        String className,
        String testName,
        String testNameHash) {
      super(id, new TestName(category, path, className, testName));
    }

    public TestCategory category() {
      return value().category();
    }

    public String path() {
      return value().path();
    }

    public String className() {
      return value().className();
    }

    public String testName() {
      return value().testName();
    }

    public String testNameHash() {
      return null;
    }
  }
}
