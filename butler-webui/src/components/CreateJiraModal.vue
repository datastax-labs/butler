<!--
  - Copyright DataStax, Inc.
  -
  - Please see the included license file for details.
  -->

<template>
  <request-modal
    id="create_jira_modal"
    :show="jiraProject"
    :request="requestUrl"
    title="JIRA Operation"
    ref="create_jira_modal"
    v-on="$listeners"
  >
    <template v-slot:pre-request>
      <div v-if="!linkInstead">
        <p>
          Create JIRA <b>{{ jiraProject }}</b> ticket for failures of
        </p>
        <p>{{ test_name }} ?</p>
        <b-alert show variant="info">
          Ticket will be created in jira project {{ jiraProject }} for workflow
          {{ workflow }}
        </b-alert>
        <b-form-checkbox
          id="force-create"
          v-model="forceCreate"
          name="force-create"
          value="true"
          unchecked-value="false"
        >
          Force create (do not search for existing tickets)
        </b-form-checkbox>
      </div>
      <div v-else>
        Link failures of {{ test_name }} to the following issue:
        <b-form-input
          v-model="issue"
          placeholder="Enter jira issue"
        ></b-form-input>
      </div>
    </template>
    <template v-slot:post-request="{ data }">
      <div>
        <span v-if="linkInstead">Linked</span>
        <span v-else>Created></span>
        <b-link :href="data.value.url">{{ data.value.name }}</b-link
        >:
        <pre class="mt-4">{{ data.message }}</pre>
      </div>
    </template>
  </request-modal>
</template>

<script>
import RequestModal from "./RequestModal";
import { axios } from "../plugins/network";

export default {
  name: "CreateJiraModal",
  components: { RequestModal },
  props: {
    linkInstead: { type: Boolean, default: false }
  },
  data: function() {
    return {
      tests: [],
      workflow: null,
      jiraProject: null,
      forceCreate: null,
      issue: null
    };
  },
  computed: {
    test_name() {
      return this.tests.length == 0
        ? "<none>"
        : this.tests
            .map(function(t) {
              return t.class_name + "." + t.test_name;
            })
            .join(", ");
    },
    requestUrl() {
      if (this.linkInstead) {
        return (
          "/api/upstream/failures/link?workflow=" +
          this.workflow +
          "&ticket=" +
          this.issue
        );
      } else {
        var force = this.forceCreate == "true" ? true : false;
        return (
          "/api/upstream/failures/report?workflow=" +
          this.workflow +
          "&force=" +
          force
        );
      }
    }
  },
  methods: {
    setTest(test) {
      this.tests = test instanceof Array ? test : [test];
    },
    display(workflow, test) {
      this.setTest(test);
      this.workflow = workflow;
      this.getJiraProject(workflow);
      this.linkInstead = false;
      this.$refs.create_jira_modal.display(this.tests);
    },
    link(workflow, test) {
      this.setTest(test);
      this.workflow = workflow;
      this.getJiraProject(workflow);
      this.linkInstead = true;
      this.$refs.create_jira_modal.display(this.tests);
    },
    getJiraProject(workflow) {
      axios()
        .get("/api/upstream/workflow/" + workflow + "/jira/main/")
        .then(response => {
          this.jiraProject = response.data;
        })
        .catch(error => {
          console.log("error on get main jira project for workflow", error);
          this.jiraProject = "???";
        });
    }
  }
};
</script>

<style scoped></style>
