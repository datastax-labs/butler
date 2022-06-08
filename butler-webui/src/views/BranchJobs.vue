<!--
  - Copyright DataStax, Inc.
  -
  - Please see the included license file for details.
  -->

<template>
  <div class="container-fluid vh-100" id="main">
    <b-alert variant="danger" class="mt-3" :show="error != null">{{
      error
    }}</b-alert>

    <b-card
      :title="'imported jobs for <' + this.branch + '>'"
      sub-title="all jobs that we have imported to butler for this branch/pr"
      bg-variant="info"
      class="mt-2"
      v-if="$route.params.branch"
    >
      <b-row class="p-1" v-for="job in jobs" v-bind:key="job">
        <a :href="'#/ci/upstream/compare/' + job.workflow + '/' + job.job_name">
          {{ "> " + job.workflow + "/" + job.job_name }}
        </a>
      </b-row>
    </b-card>
    <b-card
      :title="'all possible jobs for <' + this.branch + '>'"
      sub-title="jobs on this branch/pr for all workflows configured in butler"
      bg-variant="light"
      class="mt-2"
      v-if="$route.params.branch"
    >
      <b-row class="p-1" v-for="w in all_workflows" v-bind:key="w">
        <a :href="'#/ci/upstream/compare/' + w.workflow + '/' + branch">
          {{ "> " + w.workflow + "/" + branch }}
        </a>
      </b-row>
    </b-card>
  </div>
</template>

<script>
import { parse_error } from "../plugins/helpers";
import { axios } from "../plugins/network";
export default {
  name: "BranchWorkflows",
  data: function() {
    return {
      error: null,
      branch: "some branch",
      jobs: [],
      all_workflows: []
    };
  },
  created() {
    this.branch = this.$route.params.branch;
    console.log("loading jobs for branch " + this.branch);
    axios()
      .get("/api/ci/jobs/branch/" + this.branch)
      .then(response => {
        this.jobs = response.data;
        console.log(this.jobs);
      })
      .catch(error => {
        this.error = parse_error(error);
      });
    axios()
      .get("/api/upstream/workflows/all")
      .then(response => {
        this.all_workflows = response.data;
        console.log("all_workflows", this.all_workflows);
      })
      .catch(error => {
        this.error = parse_error(error);
      });
  }
};
</script>

<style scoped></style>
