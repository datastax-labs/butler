/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.server.service.issues;

import com.datastax.butler.commons.issues.IssueId;
import com.datastax.butler.commons.issues.IssueLink;
import com.datastax.butler.commons.issues.IssueTrackingProject;
import com.datastax.butler.commons.issues.jira.JiraProject;
import com.datastax.butler.commons.jenkins.TestName;
import com.datastax.butler.server.db.TestLinkedIssuesDb;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import freemarker.cache.ClassTemplateLoader;
import freemarker.cache.MultiTemplateLoader;
import freemarker.cache.TemplateLoader;
import freemarker.template.Configuration;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Service for all the issue-tracking systems related operations such as create issue, search etc.
 *
 * <p>Projects are identified by its names, which depends on the project type. For jira projects it
 * is project key e.g. CASSANDRA or XYZ. For github issues projects it is repository name e.g.
 * datastax/butler.
 *
 * <p>This service provides registry of all issue tracking projects so that other services can find
 * them.
 */
@Service
public class IssueTrackersService {
  private static final Logger logger = LogManager.getLogger();
  private final TestLinkedIssuesDb testLinkedIssuesDb;

  private final Map<String, IssueTrackingProject> projects = new HashMap<>();

  private final Cache<String, IssueLink> issueLinkCache;
  private final Configuration templateConfiguration;

  @Autowired
  IssueTrackersService(TestLinkedIssuesDb testLinkedIssuesDb) {
    this.testLinkedIssuesDb = testLinkedIssuesDb;
    issueLinkCache = CacheBuilder.newBuilder().expireAfterWrite(Duration.ofMinutes(15)).build();
    templateConfiguration = prepareTemplateEngine();
  }

  private Configuration prepareTemplateEngine() {
    var config = new Configuration(Configuration.VERSION_2_3_31);
    var templateLoader =
        new MultiTemplateLoader(
            new TemplateLoader[] {
              new ClassTemplateLoader(getClass(), "/templates"),
              new ClassTemplateLoader(JiraProject.class, "/templates")
            });
    config.setTemplateLoader(templateLoader);
    return config;
  }

  /** Registers a project (jira, gh) and configures whatever is required. */
  public void registerProject(IssueTrackingProject project) {
    var key = project.projectName();
    logger.info("registering issue tracking project {}", key);
    if (project instanceof JiraProject) {
      var jiraProject = (JiraProject) project;
      jiraProject.withTemplateConfiguration(templateConfiguration);
      projects.put(key, jiraProject);
    } else {
      projects.put(key, project);
    }
  }

  /** Return project definition for given key or throw if not registered. */
  public IssueTrackingProject getProject(String projectKey) {
    var p = projects.getOrDefault(projectKey, null);
    if (p == null) {
      logger.error("Unable to find jira project definition for {}", projectKey);
      throw new IllegalArgumentException(
          "Unable to find jira project definition for " + projectKey);
    }
    return p;
  }

  /** Return project definition for given issue or throw if not registered. */
  public IssueTrackingProject getProject(IssueId issueId) {
    return getProject(issueId.projectName());
  }

  /** Return all registered projects. */
  public Collection<IssueTrackingProject> allProjects() {
    return this.projects.values();
  }

  /** Return link to the issue. */
  public IssueLink issueLink(IssueId issueId) {
    try {
      var project = getProject(issueId);
      return issueLinkCache.get(issueId.toString(), () -> project.getLink(issueId));
    } catch (ExecutionException e) {
      logger.warn("Problem loading issue link for {}", issueId, e);
      return null;
    }
  }

  /**
   * Search for issues related to the test name in selected (registered) projects.
   *
   * @param projects list of issue tracking projects
   * @param testName test name
   * @return set of issue ids found
   */
  public Set<IssueLink> searchOpenIssuesForTest(List<String> projects, TestName testName) {
    boolean onlyOpen = true;
    return projects.stream()
        .map(this::getProject)
        .map(p -> p.searchIssuesForTest(testName, onlyOpen))
        .flatMap(Collection::stream)
        .collect(Collectors.toSet());
  }

  /** Periodically refresh status for all known linked issues so that UI reacts faster. */
  @SuppressWarnings("unused")
  @Scheduled(fixedDelay = 1000 * 60 * 10) // every 10 minutes
  void refreshIssueLinks() {
    if (testLinkedIssuesDb != null) {
      final Set<IssueId> knownIssues = testLinkedIssuesDb.allKnownIssues();
      logger.info("Refreshing status of {} known linked issues started", knownIssues.size());
      knownIssues.forEach(this::issueLink);
      logger.info("Refreshing status of {} known linked issues finished", knownIssues.size());
    } else {
      logger.warn("Configuration error, cannot refresh issue links");
    }
  }
}
