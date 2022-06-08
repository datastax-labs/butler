<template>
  <div class="container-fluid vh-100" id="main" v-if="loaded">
    <h4>
      <b-badge>{{ failure.test.category }}</b-badge>
      {{ failure.test.path }}.{{ failure.test.class_name }}.{{
        failure.test.test_name
      }}
      <small v-if="$route.params.workflow_name"
        >( {{ $route.params.workflow_name }} )</small
      >
    </h4>
    <hr />

    <p>
      Jira Issue:
      <b-link
        v-if="issue_link.name"
        :href="issue_link.url"
        target="_blank"
        rel="noopener noreferrer"
        :class="issue_link.closed ? 'closed' : 'open'"
        >{{ issue_link.name }}
      </b-link>
      <b-button-group size="sm">
        <b-button
          variant="primary"
          @click="
            $refs.create_jira.display($route.params.workflow_name, failure.test)
          "
          >Create</b-button
        >
        <b-button
          variant="secondary"
          @click="
            $refs.create_jira.link($route.params.workflow_name, failure.test)
          "
          >Link</b-button
        >
      </b-button-group>
    </p>

    <b-tabs content-class="mt-1">
      <b-tab title="Recent Failures" active>
        <div v-for="(runs, variant) in grouped_runs" :key="variant">
          <b-card :title="variant" class="mt-1 p-0">
            <b-card-body class="p-0 m-0">
              <b-table-lite
                small
                striped
                :busy="!loaded"
                :items="runs"
                :fields="fields"
                primary-key="version"
              >
                <template v-slot:cell(version)="data">
                  <span
                    v-b-tooltip.hover.topright.html.v-secondary
                    :title="'Last run: ' + data.value.last_run"
                    >{{ data.value.value }}</span
                  >
                </template>
                <template v-slot:cell()="data">
                  <b-link
                    v-b-tooltip.hover.topright.html.v-secondary
                    :title="data.value.run_at"
                    :href="data.value.url"
                  >
                    {{ data.value.name }}
                  </b-link>
                </template>
              </b-table-lite>
            </b-card-body>
          </b-card>
        </div>
      </b-tab>
      <b-tab title="Recent Output">
        <div v-if="last_failure.output.error_details">
          <h4>error message</h4>
          <pre>{{ last_failure.output.error_details }}</pre>
        </div>
        <div v-if="last_failure.output.error_stack_trace">
          <h4>stack trace</h4>
          <pre>{{ last_failure.output.error_stack_trace }}</pre>
        </div>
        <div v-if="last_failure.output.stdout">
          <h4>stdout</h4>
          <pre>{{ last_failure.output.stdout }}</pre>
        </div>
        <div v-if="last_failure.output.stderr">
          <h4>stderr</h4>
          <pre>{{ last_failure.output.stderr }}</pre>
        </div>
      </b-tab>
      <b-tab title="Tickets" class="p1">
        <p v-for="issue in linked_issues" :key="issue.name">
          <b-link
            :href="issue.url"
            :class="issue.closed ? 'closed' : 'open'"
            target="_blank"
            rel="noopener noreferrer"
            >{{ issue.name }}</b-link
          >
        </p>
      </b-tab>
      <b-tab title="Full history" class="mt-1 p-0">
        <b-table-lite
          small
          striped
          :items="failed_runs_history"
          :fields="failed_test_runs_fields"
        >
          <template v-slot:cell(build)="data">
            <b-link :title="data.value.build" :href="data.value.url">
              {{ data.value.build }}
            </b-link>
          </template>
        </b-table-lite>
      </b-tab>
    </b-tabs>
    <create-jira-modal
      ref="create_jira"
      @on_success="on_jira_created"
    ></create-jira-modal>
  </div>
</template>

<script>
import CreateJiraModal from "../components/CreateJiraModal";
import { parse_error, format_timestamp, group_by } from "../plugins/helpers";
import { axios } from "../plugins/network";

export default {
  name: "CiFailure",
  components: { CreateJiraModal },
  data: function() {
    return {
      failure: {},
      linked_issues: [],
      failed_runs_history: [],
      grouped_runs: {},
      fields: ["version"],
      failed_test_runs_fields: [
        "timestamp",
        "branch",
        "workflow",
        "build",
        "variant"
      ],
      test_name: "",
      loaded: false,
      issue_link: {},
      last_failure: {},
      non_empty_last_failure: false
    };
  },
  methods: {
    format_timestamp,
    extract(failure) {
      this.failure = failure;
      this.workflow_name = failure.workflow_id
        ? failure.workflow_id.workflow
        : "?";
      this.issue_link = failure.issue_link || {};
      this.versions = failure.failure_details.all_by_versions;
      this.test_name = failure.test.class_name + "." + failure.test.test_name;

      this.last_failure = failure.failure_details.last_failed
        ? failure.failure_details.last_failed
        : {
            output: {
              stderr: null,
              stdout: null,
              error_stack_trace: null,
              error_details: null
            }
          };
      this.non_empty_last_failure =
        this.last_failure.output.stderr ||
        this.last_failure.output.stdout ||
        this.last_failure.output.error_details ||
        this.last_failure.output.error_stack_trace;

      var failure_details = this.failure.failure_details;
      var versions = [];
      for (let v in failure_details.all_by_versions) {
        versions.push(v);
        failure_details.all_by_versions[v].sort(function(a, b) {
          // most recent first
          return b.timestamp - a.timestamp;
        });
      }

      for (var version of versions.sort()) {
        let byVariant = group_by(
          failure_details.all_by_versions[version],
          "variant"
        );
        for (let variant in byVariant) {
          let row = {
            version: { value: version, last_run: "N/A" },
            _cellVariants: {}
          };
          for (let idx in byVariant[variant]) {
            let result = byVariant[variant][idx];
            var item = {
              name: result.id.build_number,
              url: result.url,
              run_at: format_timestamp(result.timestamp)
            };
            if (idx == 0) {
              row.version.last_run = item.run_at;
            }
            row["r" + idx] = item;
            var cv = "success";
            if (result.failed) {
              cv = "danger";
            } else if (result.skipped) {
              cv = "warning";
            }
            row["_cellVariants"]["r" + idx] = cv;
          }
          (this.grouped_runs[variant] = this.grouped_runs[variant] || []).push(
            row
          );
        }
      }
      this.clamp_fields();
      this.loaded = true;
    },
    clamp_fields() {
      let maxlen = 0;
      for (let variant in this.grouped_runs) {
        let lengths = this.grouped_runs[variant].map(
          v => Object.keys(v).filter(key => key.startsWith("r")).length
        );
        maxlen = Math.max(maxlen, ...lengths);
      }
      let numFields = 1 + maxlen; // to include version name and R0..Rn
      if (numFields < this.fields.length) {
        this.fields.splice(-1 * (this.fields.length - numFields));
      }
    },
    on_jira_created(test, ticket) {
      this.issue_link = ticket.value;
      this.load_failure_history();
      this.load_failed_test_runs_history();
    },
    // load view data, so the history of all runs including failed ones for recent history
    // and potentially for requested workflow
    apiUrl() {
      let url_parts = ["api", "upstream"];
      if (this.$route.params.workflow_name) {
        url_parts = url_parts.concat([
          "workflow",
          this.$route.params.workflow_name
        ]);
      }
      if (this.$route.params.job_name) {
        url_parts = url_parts.concat(["job", this.$route.params.job_name]);
      }
      url_parts.push("failure");
      if (this.$route.params.path) {
        url_parts.push(encodeURIComponent(this.$route.params.path));
      }
      url_parts.push(encodeURIComponent(this.$route.params.suite_name));
      url_parts.push(encodeURIComponent(this.$route.params.test_name));

      return "/" + url_parts.join("/");
    },
    // load history of reported "upstream failures" so actually
    // history of reported jira tickets for problems with given test
    // and set it as this.failure_history
    load_failure_history() {
      let url = "/api/upstream/failures/linked_issues";
      if (this.$route.params.path) {
        url += "/" + encodeURIComponent(this.$route.params.path);
      }
      url += "/" + encodeURIComponent(this.$route.params.suite_name);
      url += "/" + encodeURIComponent(this.$route.params.test_name);

      axios()
        .get(url)
        .then(response => {
          this.linked_issues = response.data;
          console.log("linked_issues", this.linked_issues);
        })
        .catch(error => {
          alert("Error loading failure history:\n" + parse_error(error));
        });
    },
    // load history of all failed tests runs for all the workflows for given test
    // and set it as this.failed_runs_history
    load_failed_test_runs_history() {
      let url = "/api/upstream/failed_runs";
      if (this.$route.params.path) {
        url += "/" + encodeURIComponent(this.$route.params.path);
      }
      url += "/" + encodeURIComponent(this.$route.params.suite_name);
      url += "/" + encodeURIComponent(this.$route.params.test_name);
      axios()
        .get(url)
        .then(response => {
          let runs = response.data.failure_details;
          // extract data into list of [timestamp, variant, workflow, branch, build]
          // this.failed_runs_history = response.data;
          for (let v in runs.all_by_versions) {
            for (let r of runs.all_by_versions[v]) {
              let row = {};
              row.timestamp = new Date(r.timestamp * 1000).toISOString();
              row.workflow = r.id.workflow;
              row.branch = v;
              row.build = {
                build: r.id.build_number,
                url: r.url
              };
              row.variant = r.variant;
              this.failed_runs_history.push(row);
            }
          }
          this.failed_runs_history.sort((a, b) =>
            a.timestamp < b.timestamp ? 1 : -1
          );
        })
        .catch(error => {
          let err = parse_error(error);
          alert(`Error loading failed tests runs history from ${url} : ${err}`);
        });
    }
  },
  mounted() {
    for (var i = 0; i < 40; i++) {
      this.fields.push({ key: "r" + i, class: "text-center" });
    }
    axios()
      .get(this.apiUrl())
      .then(response => {
        this.extract(response.data);
      })
      .catch(error => {
        alert("Error loading failure:\n" + parse_error(error));
      });

    this.load_failure_history();
    this.load_failed_test_runs_history();
  }
};
</script>
<style scoped>
.closed {
  text-decoration: line-through;
}
</style>
