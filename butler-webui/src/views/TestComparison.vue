<template>
  <div class="container-fluid vh-100" id="main">
    <h4 v-if="upstreamJobName">
      {{ branchJobName }} vs {{ upstreamJobName }}
      <b-button id="compare-hint" size="sm" variant="link">hint</b-button>
      <b-tooltip target="compare-hint" triggers="hover">
        add <code>/to/{workflow}/{branch}</code> to url to compare vs different
        branch or workflow
      </b-tooltip>
    </h4>
    <h4 v-else>{{ branchJobName }}</h4>
    <div>
      Last build of {{ branchJobName }}:
      <span v-if="lastJob">{{
        format_timestamp(lastJob.start_time.seconds)
      }}</span>
      <span v-else>Not found (please fetch)</span>
    </div>
    <b-button-toolbar class="my-1">
      <b-button-group class="mx-0">
        <b-button
          ref="report_all_button"
          variant="success"
          @click="report_all_failures"
          >Report selected</b-button
        >
        <b-button
          ref="link_all_button"
          variant="secondary"
          @click="link_all_failures"
          >Link selected</b-button
        >
        <b-button variant="outline-secondary" @click="clear_selected"
          >Clear selection</b-button
        >
      </b-button-group>

      <b-button-group class="mx-1">
        <b-button
          variant="outline-success"
          ref="fetch_button"
          @click="load_jobs"
          >Fetch latest jobs</b-button
        >
      </b-button-group>
    </b-button-toolbar>
    <b-collapse :visible="job_loading_started">
      <build-bulk-loader-progress ref="loader_progress" @loaded="onJobsLoaded">
      </build-bulk-loader-progress>
    </b-collapse>
    <b-form-checkbox
      switch
      size="sm"
      v-if="upstreamJobName"
      id="checkbox-hide-upstream"
      v-model="hideUpstreamFailures"
      name="checkbox-hide-upstream"
      @change="switchUpstreamFailures()"
    >
      hide upstream failures
    </b-form-checkbox>
    <b-table
      v-if="summary_data.length > 0"
      small
      bordered
      ref="summary_table"
      :busy="!loaded"
      :items="summary_data"
      :fields="summary_fields"
      tbody-class="text-sm"
      thead-class="d-none"
    >
      <template v-slot:cell(type)="data">
        <b-badge :variant="data.value.variant">{{ data.value.name }}</b-badge>
      </template>
    </b-table>
    <b-table
      small
      hover
      ref="failure_table"
      :busy="!loaded"
      :items="runs"
      :fields="fields"
      sort-by="failures"
      selectable
      @row-selected="row_selected"
      sort-desc
      :filter="hideUpstreamFailures"
      :filter-function="tableFilter"
    >
      <template v-slot:table-busy>
        <div class="text-center my-2">
          <b-spinner class="align-middle"></b-spinner>
        </div>
      </template>
      <template v-slot:cell(type)="data">
        <b-badge :variant="data.value.variant">{{ data.value.name }}</b-badge>
      </template>
      <template v-slot:cell(group)="data">
        {{ data.value }}
      </template>
      <template v-slot:cell(component)="data">
        <b-badge
          v-if="data.value"
          variant="light"
          v-b-tooltip.hover.right
          :title="data.value.path"
        >
          {{ data.value.group }}
        </b-badge>
      </template>
      <template v-slot:cell(test)="data">
        <b-link :to="data.value.url">
          {{ data.value.name }}
        </b-link>
      </template>
      <template v-slot:cell(jira)="data">
        <b-link
          :href="data.value.url"
          target="top"
          :class="data.value.closed ? 'closed' : 'open'"
        >
          {{ data.value.name }}
        </b-link>
      </template>
      <template v-slot:cell(failures)="data">
        {{ data.value }}
      </template>
      <template v-slot:cell()="data">
        <b-link
          v-b-tooltip.hover.topright.html.v-secondary
          :title="data.value.run_at"
          :href="data.value.url"
          target="top"
          >{{ data.value.name }}</b-link
        >
      </template>
    </b-table>
    <create-jira-modal ref="create_jira" @on_success="on_jira_created">
    </create-jira-modal>
  </div>
</template>

<script>
import { parse_error, format_timestamp } from "../plugins/helpers";
import { axios } from "../plugins/network";
import {
  testResultsClassification,
  REGRESSION,
  NEW,
  FLAKY,
  FIXED,
  FAILING
} from "../plugins/classification";
import { isAdmin } from "../plugins/auth";
import CreateJiraModal from "../components/CreateJiraModal";
import BuildBulkLoaderProgress from "../components/BuildBulkLoaderProgress";

export default {
  name: "CiWorkflowFailures",
  components: { CreateJiraModal, BuildBulkLoaderProgress },
  data: function() {
    return {
      hideUpstreamFailures: true,
      loaded: false,
      // main results table
      fields: [
        { key: "type", sortable: true }, // classification: new, flaky, etc.
        { key: "group", sortable: true }, // test group: dtest, upgrade, unit etc
        { key: "component", sortable: true }, // e.g. search or graph
        { key: "test", sortable: true },
        { key: "jira", class: "text-nowrap" },
        { key: "failures", class: "text-center", sortable: true }
      ],
      runs: [],
      branchJobName: "",
      upstreamJobName: "",
      // summary table
      summary_fields: [{ key: "type" }, { key: "count" }, { key: "info" }],
      summary_data: [],
      selected: [],
      lastJob: null,
      job_loading_started: false
    };
  },
  methods: {
    format_timestamp, // So the view can use it
    isAdmin,
    testKey(failure, variant) {
      return (
        failure.test.class_name +
        "." +
        failure.test.test_name +
        " (" +
        variant +
        ")"
      );
    },
    truncateStr(str, num) {
      return str.length > num ? str.slice(0, num) + "..." : str;
    },
    extract(data) {
      let allFailures = new Map();
      let jiraIssues = new Map();
      let byJobFailures = [{}, {}];
      let observedJobs = [new Set(), new Set()]; // 0 - tested branch, 1 - upstream branch
      let perJobAndBuildNumFailed = [new Map(), new Map()]; // 0 - tested branch, 1 - upstream branch

      console.time("prepare data");
      for (let idx in data) {
        if (data[idx].failures != null && data[idx].failures[0] != null) {
          let job = data[idx].failures[0].failure_details.last.id;
          let displayName = job.workflow + "/" + job.job_name;
          if (idx == 0) {
            this.branchJobName = displayName;
          } else {
            this.upstreamJobName = displayName;
          }
        }
        for (let failure of data[idx].failures) {
          for (let variant in failure.failure_details.all_by_variants) {
            let variantFailures =
              failure.failure_details.all_by_variants[variant];
            variantFailures.sort(function(a, b) {
              // most recent first
              return b.timestamp - a.timestamp;
            });
            for (let f of variantFailures) {
              observedJobs[idx].add(f.id.build_number);
            }

            let tKey = this.testKey(failure, variant);
            allFailures.set(tKey, failure.test);
            if (failure.issue_link) {
              console.log("found jira for", tKey, failure.issue_link);
              jiraIssues[tKey] = failure.issue_link;
            }
            byJobFailures[idx][tKey] = variantFailures;
          }
        }
      }
      console.timeEnd("prepare data");

      if (this.branchJobName == this.upstreamJobName) {
        // Same job
        byJobFailures[1] = [];
        observedJobs[1] = [];
        this.upstreamJobName = "";
      }

      let testedBranchJobsCount = observedJobs[0].size;
      console.log("testedBranchJobsCount: " + testedBranchJobsCount);

      // calculate on which workflow we should show the history
      let history_workflow_name = this.upstreamJobName
        ? this.upstreamJobName.split("/")[0]
        : this.branchJobName.split("/")[0];
      console.log("history workflow", history_workflow_name);

      console.time("process data");
      for (let [test, testName] of allFailures) {
        // console.log("Processing", test);
        let failure_url = [
          "/ci",
          "upstream",
          "workflow",
          history_workflow_name,
          "failure"
        ];
        if (testName.path && testName.path.trim() != "") {
          failure_url.push(testName.path);
        }
        failure_url.push(testName.class_name, testName.test_name);
        failure_url = failure_url.join("/");
        let row = {
          _fullName: testName,
          test: {
            url: failure_url,
            name: this.truncateStr(test.replace(" (<default>)", ""), 100)
          },
          jira: jiraIssues[test],
          _cellVariants: {}
        };

        let interesting = false;
        let numFailed = [0, 0];
        let classifications = [[], []]; // [0] - tested branch, [1] - upstream branch

        for (let idx in byJobFailures) {
          if (byJobFailures[idx][test] == null) {
            if (idx == 0) {
              // If we have no results for idx 0 (the non-upstream branch) => just omit the row
              break;
            }
            for (let jobNum of observedJobs[idx]) {
              row[idx + ":" + jobNum] = "";
            }
          }

          let jobRuns = byJobFailures[idx][test];
          if (jobRuns == null) {
            jobRuns = [];
          }

          // here we will collect per build num failures in given job
          let perBuildNumFailed = perJobAndBuildNumFailed[idx];

          // change from flat map to build -> [variants] as
          // every "run" here can include list of builds with different variants
          // this is problematic for upgrades, when we have variants including "methods"
          // e.g. novnode[5.0->6.0]-001-query_upgraded, novnode[5.0->6.0]-000-prepare etc.
          let jobRunsByBuild = jobRuns.reduce((r, a) => {
            r[a.id.build_number] = [...(r[a.id.build_number] || []), a];
            return r;
          }, {});

          // set of builds that were run for this test
          let buildsWithTest = new Set();

          for (let buildNum in jobRunsByBuild) {
            buildsWithTest.add(buildNum);
            let variants = jobRunsByBuild[buildNum];
            let key = idx + ":" + buildNum;

            // calculate build results based on all the variants
            // with failed == true if any of the variants failed
            // and url being url of first failed or first variant (if no failures)
            let url = variants[0].url;
            let runAt = format_timestamp(variants[0].timestamp);
            let failed = variants.some(x => x.failed);
            let skipped = variants.every(x => x.skipped);
            if (failed) {
              url = variants.filter(x => x.failed)[0].url;
              // increment numFailed counter for this test
              numFailed[idx] += 1;
            }
            // update row
            row[key] = {
              url: url,
              name: buildNum,
              run_at: runAt
            };
            // remember number of failures per job and build
            if (failed) {
              if (!perBuildNumFailed.has(buildNum)) {
                perBuildNumFailed.set(buildNum, 0);
              }
              let numFailedSoFar = perBuildNumFailed.get(buildNum);
              perBuildNumFailed.set(buildNum, numFailedSoFar + 1);
            }
            // and classifications, we add them in the begin as we start from oldest runs
            classifications[idx].unshift(!failed);
            let buildFailure =
              variants.length == 1 && numFailed[0] == 1 && !testName.path;
            interesting = interesting || (failed && !buildFailure);
            row["_cellVariants"][key] = failed
              ? "danger"
              : skipped
              ? "warning"
              : "success";
          }

          // set variant to none for runs when given test (row) was not run
          const missingBuilds = [...observedJobs[idx]].filter(
            x => !buildsWithTest.has(x.toString())
          );
          for (let buildNum of missingBuilds) {
            const key = idx + ":" + buildNum;
            row["_cellVariants"][key] = "light";
          }
        }

        if (interesting) {
          let branch = classifications[0];
          let upstream = classifications[1];

          // We expect to have N=testedBranchJobsCount results, but some problems like initialization errors
          // may just stop appearing in the results. Same for test that was removed etc.
          // In such case we will leftpad with nulls
          if (branch.length < testedBranchJobsCount) {
            let n = testedBranchJobsCount;
            branch = new Array(n)
              .fill(null)
              .concat(branch)
              .slice(-n);
          }

          // Set type of test
          row["group"] =
            testName.category == "UPGRADE" ? "UPGR" : testName.category;

          // Set product group e.g. search
          let componentGroup = this.groupComponentFromPath(testName.path);
          if (componentGroup) {
            row["component"] = {
              group: componentGroup,
              path: testName.path
            };
          }

          // Set the number of failures (for comparison only on branch-under-test)
          row["failures"] = numFailed[0];

          // Classify the type of failure
          let type = testResultsClassification(branch, upstream);

          // if classification is REGRESSION but there is an open ticket
          // we remove the classification
          if (type == REGRESSION && row.jira && !row.jira.closed) {
            console.log(
              "Removing REGRESSION for ",
              row.name,
              "because it has open jira ticket"
            );
            type = null;
          }

          let typeVariant = this.type_variant(type);
          // log classification
          if (type != null) {
            console.log(
              row["test"]["name"],
              "is classified as",
              type,
              "based on branch",
              branch,
              "and upstream",
              upstream
            );
          }

          row["type"] = { name: type, variant: typeVariant };
          this.runs.push(row);
        }
      }
      console.timeEnd("process data");

      console.time("finalize");
      for (let idx in observedJobs) {
        let first = true;
        let jobs = Array.from(observedJobs[idx]);
        jobs.sort((a, b) => b - a);
        for (let jobNum of jobs) {
          let f = {
            key: idx + ":" + jobNum,
            label: jobNum.toString(),
            variant: "success"
          };
          if (first) {
            first = false;
            f["class"] = "run_divider";
          }
          // update header if there are failures in the build
          if (perJobAndBuildNumFailed[idx].has(jobNum.toString())) {
            const numFailedTestsInJob = perJobAndBuildNumFailed[idx].get(
              jobNum.toString()
            );
            if (numFailedTestsInJob > 0) {
              f["variant"] = "danger";
              f["label"] = f["label"] + ":" + numFailedTestsInJob;
            }
          }
          this.fields.push(f);
        }
      }
      console.timeEnd("finalize");
      // build summary data
      this.summary_data = this.build_summary(this.runs);
      // and finally mark as loaded
      this.loaded = true;
    },
    groupComponentFromPath(path) {
      return path.split`.`.map((x, y, z) => (z[y + 1] > "[" ? x[0] : x))
        .join`.`;
    },
    //
    // build summary for comparison e.g. number of failed, number of new etc.
    //
    build_summary(runs) {
      let summary = [];
      let n_failed = runs.filter(x => x.failures > 0).length;
      summary.push({
        type: { name: "failed", variant: "light" },
        count: n_failed,
        info:
          "number of distinct tests that failed at least once on the branch in the visible history"
      });
      let n_regression = runs.filter(
        x => x.type.name == REGRESSION || x.type.name == NEW
      ).length;
      if (n_regression > 0) {
        summary.push({
          type: { name: "rgrsn", variant: this.type_variant(REGRESSION) },
          count: n_regression,
          info: "suspicious failures, potentially new regression"
        });
      }
      let n_failing = runs.filter(x => x.type.name == FAILING).length;
      if (n_failing > 0) {
        summary.push({
          type: { name: "failing", variant: this.type_variant(FAILING) },
          count: n_failing,
          info: "tests that are continuously failing... :facepalm:"
        });
      }
      let n_flaky = runs.filter(x => x.type.name == FLAKY).length;
      if (n_flaky > 0) {
        summary.push({
          type: { name: "flaky", variant: this.type_variant(FLAKY) },
          count: n_flaky,
          info: "flaky tests, flickering on branch and upstream"
        });
      }
      return summary;
    },
    //
    // calculate badge variant from classification type
    //
    type_variant(type) {
      switch (type) {
        case REGRESSION:
        case NEW:
          return "danger";
        case FIXED:
          return "success";
        case FLAKY:
          return "info";
        case FAILING:
          return "warning";
        default:
          return null;
      }
    },
    apiUrl() {
      let url =
        "/api/upstream/compare/" +
        this.$route.params.workflowA +
        "/" +
        this.$route.params.jobA;
      if (this.$route.params.workflowB) {
        url =
          url +
          "/to/" +
          this.$route.params.workflowB +
          "/" +
          this.$route.params.jobB;
      }
      if (this.$route.query.numBuilds) {
        url += "?numBuilds=" + this.$route.query.numBuilds;
      }
      console.log("calling api url:", url);
      return url;
    },
    on_jira_created(tests, ticket) {
      this.clear_selected();
      for (const test of tests) {
        this.update_reported_test(test, ticket.value);
      }
    },
    update_reported_test(test, ticket) {
      // what is test, it looks like a TestName dict
      this.runs
        .filter(r => r._fullName == test)
        .forEach(t => (t.jira = ticket));
    },
    row_selected(items) {
      this.selected = items;
      this.set_report_all_state(this.selected.length);
    },
    clear_selected() {
      this.$refs.failure_table.clearSelected();
    },
    set_report_all_state(count) {
      this.$refs.link_all_button.disabled = this.$refs.report_all_button.disabled =
        count < 1;
    },
    report_all_failures() {
      this.$refs.create_jira.display(
        this.workflow(),
        Array.from(new Set(this.selected.map(r => r._fullName)))
      );
    },
    link_all_failures() {
      this.$refs.create_jira.link(
        this.workflow(),
        Array.from(new Set(this.selected.map(r => r._fullName)))
      );
    },
    workflow() {
      if (typeof this.$route.params.workflowB !== "undefined") {
        console.log("workflow is (B): " + this.$route.params.workflowB);
        return this.$route.params.workflowB;
      } else {
        console.log("workflow is (A): " + this.$route.params.workflowA);
        return this.$route.params.workflowA;
      }
    },
    doLoad() {
      this.$refs.report_all_button.disabled = true;
      if (this.$route.query.fetch == "true") {
        this.load_jobs();
        this.$route.query.fetch = "false";
      }

      axios()
        .get(
          "/api/ci/workflow/" +
            this.$route.params.workflowA +
            "/job/" +
            this.$route.params.jobA +
            "/last"
        )
        .then(response => {
          this.lastJob = response.data.value;
        });

      axios()
        .get(this.apiUrl())
        .then(response => {
          this.extract(response.data);
        })
        .catch(error => {
          alert("Error loading failure history: \n" + parse_error(error));
        });
    },
    loadLastJob() {},
    load_jobs() {
      let jobs_list = [
        {
          workflow: this.$route.params.workflowA,
          job_name: this.$route.params.jobA
        }
      ];
      let request = { jobs: jobs_list };
      axios()
        .post("/api/ci/builds/bulkload", request)
        .then(response => {
          console.log("Response submitting bulk load:");
          console.dir(response);
          let taskIds = response.data;
          console.log("loader task ids", taskIds);
          console.log(this.$refs);
          this.$refs["loader_progress"].startMonitoring(taskIds[0]);
          //this.$bvModal.show("statusModal");
          this.job_loading_started = true;
          this.$refs.fetch_button.disabled = true;
        })
        .catch(error => {
          this.loading = false;
          this.$refs.fetch_button.disabled = false;
          alert(parse_error(error));
        });
    },
    onJobsLoaded() {
      console.log("Job loading done");
      this.job_loading_started = false;
      this.$refs.fetch_button.disabled = false;
      this.reload();
    },
    reload() {
      Object.assign(this.$data, this.$options.data());
      this.doLoad();
    },
    tableFilter(row, filter) {
      return filter ? row.failures > 0 : true;
    },
    switchUpstreamFailures() {
      this.$refs.failure_table.refresh();
    }
  },
  watch: {
    $route(to, from) {
      // react to route changes...
      console.log("Watch:", to, from);
      this.reload();
    }
  },
  mounted() {
    this.doLoad();
  }
};
</script>
<style scoped>
.closed {
  text-decoration: line-through;
}
</style>
<style>
/* because we set this in js, it doesn't notice it's scoped, so has to be global */
.run_divider {
  border-left: 2px solid gray;
}
</style>

// this.$refs.table.refresh()
