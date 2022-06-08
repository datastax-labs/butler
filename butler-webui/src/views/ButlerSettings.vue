<!--
  - Copyright DataStax, Inc.
  -
  - Please see the included license file for details.
  -->

<template>
  <div class="container" id="main">
    <b-alert variant="danger" class="mt-3" :show="error != null">{{
      error
    }}</b-alert>

    <settings-form
      title="Workflows"
      :form_data="workflows_data"
      ref="workflows"
      @submitted="on_workflows_submitted"
    >
      <template v-slot="{ data }">
        <b-form-group
          label="Upstream workflows:"
          label-for="workflows"
          label-cols="auto"
        >
          <b-form-tags
            v-model="data.data.workflows"
            separator=" "
            placeholder=""
            :disabled="!data.updating"
          >
          </b-form-tags>
        </b-form-group>
      </template>
    </settings-form>

    <b-card title="Operations" bg-variant="light" class="mt-4">
      <b-row>
        <b-col>
          <load-button
            text="Search JIRA for failure tickets"
            :loading="jira_search_loading"
            @click="on_jira_search_submitted"
          ></load-button>
        </b-col>
        <b-col>
          <load-button
            text="Update linked tickets"
            :loading="linked_update_loading"
            @click="on_linked_update_submitted"
          ></load-button>
        </b-col>
        <b-col cols="12" md="auto"> </b-col>
      </b-row>
    </b-card>
  </div>
</template>

<script>
import SettingsForm from "../components/SettingsForm";
import LoadButton from "../components/LoadButton";
import { parse_error } from "../plugins/helpers";
import { axios } from "../plugins/network";
export default {
  name: "ButlerSettings",
  components: { SettingsForm, LoadButton },
  data: function() {
    return {
      error: null,
      versions_data: {
        versions: [],
        master: null
      },
      workflows_data: {
        workflows: []
      },
      jira_search_loading: false,
      linked_update_loading: false
    };
  },
  mounted() {
    axios()
      .get("/api/upstream/workflows")
      .then(response => {
        console.dir(response.data);
        this.workflows_data.workflows = response.data.map(e => e.workflow);
        this.$refs.workflows.on_ready();
      })
      .catch(error => {
        this.error = parse_error(error);
      });
  },
  methods: {
    on_workflows_submitted(data) {
      console.log("Submitted worflows");
      console.dir(data);
      axios()
        .post(
          "/api/upstream/workflows/set",
          data.workflows.map(e => {
            return { workflow: e };
          })
        )
        .then(() => {
          this.workflows_data.workflows = data.workflows;
          this.$refs.workflows.on_updated();
        })
        .catch(error => {
          this.error = parse_error(error);
        });
    },
    on_jira_search_submitted() {
      console.log("Searching JIRA for failure tickets");
      this.jira_search_loading = true;
      axios()
        .post("/api/upstream/failures/search-jira")
        .then(response => {
          this.jira_search_loading = false;
          alert(response.data);
        })
        .catch(error => {
          this.error = parse_error(error);
          this.jira_search_loading = false;
        });
    },
    on_linked_update_submitted() {
      console.log("Updating any linked tickets");
      this.linked_update_loading = true;
      axios()
        .post("/api/upstream/failures/close-fixed")
        .then(response => {
          this.linked_update_loading = false;
          alert(response.data);
        })
        .catch(error => {
          this.error = parse_error(error);
          this.linked_update_loading = false;
        });
    }
  }
};
</script>

<style scoped></style>
