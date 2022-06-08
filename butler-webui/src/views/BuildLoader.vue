<!--
  - Copyright DataStax, Inc.
  -
  - Please see the included license file for details.
  -->

<template>
  <div class="container vh-100" id="main">
    <b-card bg-variant="light" class="mt-4">
      <b-form @submit.prevent="onSubmit" class="text-right">
        <b-form-group
          label="Workflow:"
          label-for="jenkins-workflow"
          label-cols="4"
          label-cols-lg="4"
          label-size="lg"
          label-align-sm="right"
        >
          <b-form-select
            id="jenkins-workflow"
            v-model="selected_workflow"
            :options="workflows"
            :disabled="workflow_disabled"
            @change="onWorkflowSelected"
          >
          </b-form-select>
        </b-form-group>

        <b-form-group
          label="Job:"
          label-for="jenkins-job"
          label-cols="4"
          label-cols-lg="4"
          label-size="lg"
          label-align-sm="right"
        >
          <b-form-select
            id="jenkins-job"
            v-model="selected_job"
            :options="jobs"
            :disabled="jobs_disabled"
            @change="onJobSelected"
          >
          </b-form-select>
        </b-form-group>

        <b-form-group
          label="Build:"
          label-for="jenkins-build"
          label-cols="4"
          label-cols-lg="4"
          label-size="lg"
          label-align-sm="right"
        >
          <b-form-select
            id="jenkins-build"
            v-model="selected_build"
            :options="builds"
            :disabled="builds_disabled"
          >
          </b-form-select>
        </b-form-group>

        <load-button
          text="Load"
          type="submit"
          :loading="loading"
          variant="outline-primary"
        ></load-button>
      </b-form>
      <b-alert
        class="mt-4"
        :show="alert_text != null"
        :variant="alert_variant"
        dismissible
        fade
        @dismissed="alert_text = null"
      >
        {{ alert_text }}
      </b-alert>
    </b-card>
  </div>
</template>

<script>
import LoadButton from "../components/LoadButton";
import { parse_error } from "../plugins/helpers";
import { axios } from "../plugins/network";

export default {
  name: "BuildLoader.vue",
  components: { LoadButton },
  data: function() {
    return {
      alert_text: null,
      alert_variant: "danger",

      loading: false,

      workflow_disabled: true,
      workflows: [],
      selected_workflow: null,

      jobs_of_workflow: null,
      jobs_disabled: true,
      jobs: [],
      selected_job: null,

      builds_of_job: null,
      builds_disabled: true,
      builds: [],
      selected_build: null
    };
  },
  methods: {
    handle_error(error) {
      this.loading = false;
      this.alert_variant = "danger";
      this.alert_text = parse_error(error);
    },
    onSubmit() {
      this.workflow_disabled = true;
      this.jobs_disabled = true;
      this.builds_disabled = true;

      let request = {
        workflow: this.selected_workflow,
        job_name: this.selected_job,
        build_number: this.selected_build
      };
      this.loading = true;
      axios()
        .post("/api/ci/builds/load", request)
        .then(response => {
          console.log("Response submitting load:");
          console.dir(response);
          this.loading = false;
          this.alert_variant = response.data.success ? "success" : "warning";
          this.alert_text = response.data.message;
        })
        .catch(error => this.handle_error(error));
    },
    onWorkflowSelected() {
      if (this.selected_workflow === this.jobs_of_workflow) {
        return;
      }

      this.jobs_disabled = true;
      this.builds_disabled = true;

      if (!this.selected_workflow) {
        this.selected_job = null;
        this.selected_build = null;
        return;
      }

      this.workflow_disabled = true;
      axios()
        .get("/api/ci/workflow/" + this.selected_workflow + "/job")
        .then(response => {
          console.dir(response.data);
          this.jobs = response.data.map(w => {
            return { value: w.job_name, text: w.job_name };
          });
          this.workflow_disabled = false;
          this.jobs_disabled = false;
        })
        .catch(error => this.handle_error(error));
    },
    onJobSelected() {
      if (this.selected_job === this.builds_of_job) {
        return;
      }

      this.builds_disabled = true;

      if (!this.selected_job) {
        this.selected_build = null;
        return;
      }

      this.workflow_disabled = true;
      this.jobs_disabled = true;
      axios()
        .get(
          "/api/ci/workflow/" +
            this.selected_workflow +
            "/job/" +
            this.selected_job
        )
        .then(response => {
          console.dir(response.data);
          this.builds = response.data.map(w => {
            return { value: w.build_number, text: w.build_number };
          });
          this.workflow_disabled = false;
          this.jobs_disabled = false;
          this.builds_disabled = false;
        })
        .catch(error => this.handle_error(error));
    }
  },
  mounted() {
    axios()
      .get("/api/ci/workflow")
      .then(response => {
        console.dir(response.data);
        this.workflows = response.data.map(w => {
          return { value: w.workflow, text: w.workflow };
        });
        this.workflow_disabled = false;
      })
      .catch(error => this.handle_error(error));
  }
};
</script>

<style scoped></style>
