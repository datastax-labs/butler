<!--
  - Copyright DataStax, Inc.
  -
  - Please see the included license file for details.
  -->

<template>
  <div class="container-fluid vh-100" id="main">
    <div v-if="$route.params.suite_name">
      <h3>
        <b-badge variant="light">
          <b-link to="/ci/upstream/failures">
            <b-icon-arrow-left></b-icon-arrow-left>
            back
          </b-link></b-badge
        >
        {{ $route.params.suite_name }}
      </h3>
    </div>
    <b-tabs content-class="mt-3">
      <b-tab title="Unreported Failures" active>
        <test-failure-table
          ref="unreported_failures"
          :data="unreported.data"
          :loaded="loaded"
          :extra_fields="unreported.extra_fields"
          @selection_change="set_report_all_state"
          use_rates
        >
          <template v-slot:actions>
            <div>
              <b-button
                ref="report_all_button"
                variant="success"
                @click="report_all_failures"
                >Report all failure</b-button
              >
              <b-button @click="clear_selected">Clear selection</b-button>
            </div>
          </template>
          <template v-slot:cell(actions)="data">
            <span
              class="mr-1"
              v-b-tooltip.hover.top.html.v-secondary
              title="Create JIRA for that test"
            >
              <b-link
                href="#"
                @click="$refs.create_jira.display(data.item['test'].full_name)"
              >
                <b-icon icon="plus-circle" variant="success"></b-icon>
              </b-link>
            </span>
            <span
              v-b-tooltip.hover.top.html.v-secondary
              title="Remove test as known failure"
            >
              <b-link
                href="#"
                @click="
                  $refs.remove_failure.display(data.item['test'].full_name)
                "
              >
                <b-icon icon="x-circle" variant="danger"></b-icon>
              </b-link>
            </span>
          </template>
        </test-failure-table>
        <create-jira-modal ref="create_jira" @on_success="on_jira_created">
        </create-jira-modal>
        <remove-failure-modal
          ref="remove_failure"
          @on_success="on_failure_removed"
        >
        </remove-failure-modal>
      </b-tab>
      <b-tab title="Reported Failures">
        <test-failure-table
          :data="reported.data"
          :loaded="loaded"
          :extra_fields="reported.extra_fields"
        >
          <template v-slot:cell(jira_ticket)="data">
            <b-link :href="data.value.url">{{ data.value.name }}</b-link>
          </template>
        </test-failure-table>
      </b-tab>
      <b-tab title="All Failures">
        <test-failure-table
          :data="all_data"
          :loaded="loaded"
          :extra_fields="reported.extra_fields"
        >
          <template v-slot:cell(jira_ticket)="data">
            <b-link v-if="data.value" :href="data.value.url">{{
              data.value.name
            }}</b-link>
          </template>
        </test-failure-table>
      </b-tab>
    </b-tabs>
  </div>
</template>

<script>
import TestFailureTable from "../components/TestFailureTable";
import CreateJiraModal from "../components/CreateJiraModal";
import RemoveFailureModal from "../components/RemoveFailureModal";
import { parse_error } from "../plugins/helpers";
import { axios } from "../plugins/network";

export default {
  name: "CiFailures",
  components: { RemoveFailureModal, CreateJiraModal, TestFailureTable },
  data: function() {
    return {
      loaded: false,
      unreported: {
        extra_fields: localStorage.isAdmin
          ? [{ key: "actions", label: "" }]
          : [],
        data: []
      },
      reported: {
        extra_fields: [
          { key: "jira_ticket", class: "text-nowrap", sortable: true }
        ],
        data: []
      },
      all_data: []
    };
  },
  methods: {
    populate_data(api_data) {
      let unreported_data = [];
      let reported_data = [];
      api_data.failures.forEach(f => {
        let fdata = f.failure_details;
        let tname = f.test;
        let data = {
          category: tname.category,
          dash: {
            url:
              "/ci/upstream/failure/" + tname.class_name + "/" + tname.test_name
          },
          suite: tname.class_name,
          test: {
            name: tname.test_name,
            url: fdata.last ? fdata.last.url : null,
            full_name: tname
          },
          versions: Object.entries(fdata.last_by_versions)
            .map(([k, v]) => {
              return { name: k, url: v.url };
            })
            .sort(this.version_compare),
          variants: Object.entries(fdata.last_by_variants).map(([k, v]) => {
            return { name: k.replace(/<default>/g, "∅"), url: v.url };
          }),
          last_week_rate: {
            failures: fdata.last_week_failures,
            runs: f.last_week_runs
          },
          last_month_rate: {
            failures: fdata.last_month_failures,
            runs: f.last_month_runs
          },
          all_time_rate: { failures: fdata.failures, runs: f.runs }
        };
        this.all_data.push(data);
        if (f.jira_issue == null) {
          unreported_data.push(data);
        } else {
          data.jira_ticket = f.jira_issue;
          reported_data.push(data);
        }
      });
      this.unreported.data = unreported_data;
      this.reported.data = reported_data;
    },
    on_jira_created(tests, ticket) {
      console.log("Jira has been created");
      this.clear_selected();
      console.dir(tests);
      console.dir(ticket);
      for (const test of tests) {
        this.move_test_to_reported(test, ticket.value);
      }
    },
    clear_selected() {
      this.$refs.unreported_failures.clear_selected();
    },
    set_report_all_state(count) {
      this.$refs.report_all_button.disabled = count < 1;
    },
    move_test_to_reported(test, ticket) {
      let i = this.find_test_index(test, this.unreported.data);
      if (i < 0) {
        console.log("Could not find test", test);
        return;
      }
      let item = this.unreported.data[i];
      console.dir(item);
      this.unreported.data.splice(i, 1);
      item.jira_ticket = ticket;
      this.reported.data.push(item);
    },
    on_failure_removed(test) {
      console.log("Removing test from list");
      let i = this.find_test_index(test, this.unreported.data);
      if (i < 0) {
        console.log("Could not find test");
        console.dir(test);
        return;
      }
      this.unreported.data.splice(i, 1);
    },
    report_all_failures() {
      console.log("Reporting");
      console.log(this.$refs.unreported_failures.selected);
      this.$refs.create_jira.display(
        this.$refs.unreported_failures.selected.map(function(r) {
          return r.test.full_name;
        })
      );
    },
    find_test_index(test, data) {
      console.log("Looking for ", test);
      for (let i = 0; i < data.length; i++) {
        const fn = data[i].test.full_name;
        if (
          test.path == fn.path &&
          test.class_name == fn.class_name &&
          test.test_name == fn.test_name
        ) {
          return i;
        }
      }
      return -1;
    },
    version_compare(v1, v2) {
      if (v1.name === "master") return v2.name === "master" ? 0 : 1;
      if (v2.name === "master") return -1;
      return v1.name.localeCompare(v2.name);
    },
    doLoad() {
      // IF we set disabled in the default state, when we un-disable it,
      // it keeps the disabled style. :/
      this.$refs.report_all_button.disabled = true;
      axios()
        .get("/api/upstream/failures/" + (this.$route.params.suite_name || ""))
        .then(response => {
          console.dir(response.data);
          this.populate_data(response.data);
          this.loaded = true;
        })
        .catch(error => {
          alert("Error loading failures:\n" + parse_error(error));
        });
    }
  },
  watch: {
    $route(to, from) {
      // react to route changes...
      console.log("Watch:", to, from);
      Object.assign(this.$data, this.$options.data());
      this.doLoad();
    }
  },
  mounted() {
    this.doLoad();
  }
};
</script>

<style scoped></style>
