<!--
  - Copyright DataStax, Inc.
  -
  - Please see the included license file for details.
  -->

<template>
  <b-card bg-variant="light" class="mt-4">
    <b-alert
      :show="alert_text != null"
      variant="danger"
      dismissible
      fade
      @dismissed="alert_text = null"
    >
      {{ alert_text }}
    </b-alert>

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
          :disabled="workflow_disabled || all_disabled"
          @change="onWorkflowSelected"
        >
        </b-form-select>
      </b-form-group>
      <b-form-group
        label="Jobs:"
        label-for="job-names"
        label-cols="3"
        label-cols-lg="3"
        label-size="lg"
        label-align-sm="right"
      >
        <b-form-select
          id="job-names"
          v-model="selected_jobs"
          :options="jobs"
          multiple
          :disabled="jobs_disabled || all_disabled"
          :select-size="Math.min(jobs.length, 20)"
          @change="onJobSelected"
        ></b-form-select>
      </b-form-group>
      <load-button
        text="Load"
        type="submit"
        :loading="loading"
        :disabled="all_disabled || jobs.length === 0"
        variant="outline-primary"
      ></load-button>
    </b-form>
  </b-card>
</template>

<script>
import LoadButton from "./LoadButton";
import { parse_error } from "../plugins/helpers";
import { axios } from "../plugins/network";

export default {
  name: "BuildBulkLoaderForm",
  components: { LoadButton },
  props: {
    all_disabled: { type: Boolean }
  },
  data: function() {
    return {
      alert_text: null,
      loading: false,

      workflow_disabled: true,
      workflows: [],
      selected_workflow: null,

      jobs_of_workflow: null,
      jobs_disabled: true,
      jobs: [],
      selected_jobs: []
    };
  },
  methods: {
    onSubmit() {
      if (this.selected_workflow == null) {
        this.alert_text = "You should select a workflow";
        return;
      }
      if (this.selected_jobs.length === 0) {
        this.alert_text = "You should select at least one job";
        return;
      }

      this.workflow_disabled = true;
      this.jobs_disabled = true;

      let jobs_list = this.selected_jobs.map(value => {
        return { workflow: this.selected_workflow, job_name: value };
      });
      let request = { jobs: jobs_list };
      this.loading = true;
      axios()
        .post("/api/ci/builds/bulkload", request)
        .then(response => {
          console.log("Response submitting bulk load:");
          console.dir(response);
          this.$emit("submitted");
        })
        .catch(error => {
          this.loading = false;
          this.alert_text = parse_error(error);
        });
    },
    onWorkflowSelected() {
      this.alert_text = null;

      if (this.selected_workflow === this.jobs_of_workflow) {
        return;
      }
      if (!this.selected_workflow) {
        this.selected_jobs = [];
        this.jobs_disabled = true;
        return;
      }

      this.workflow_disabled = true;
      this.jobs_disabled = true;
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
        .catch(error => {
          this.loading = false;
          this.alert_text = parse_error(error);
        });
    },
    onJobSelected() {
      this.alert_text = null;
    },
    onLoaded() {
      this.loading = false;
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
      .catch(error => {
        this.loading = false;
        this.alert_text = parse_error(error);
      });
  }
};
</script>

<style scoped></style>
