<!--
  - Copyright DataStax, Inc.
  -
  - Please see the included license file for details.
  -->

<template>
  <div class="container vh-100" id="main">
    <div v-if="ready">
      <build-bulk-loader-form
        @submitted="onSubmitted"
        :all_disabled="disable_form"
        ref="loader_form"
      >
      </build-bulk-loader-form>
      <div v-if="started">
        <build-bulk-loader-progress @loaded="jobsLoaded">
        </build-bulk-loader-progress>
      </div>
    </div>
    <div v-else-if="error != null">
      <b-alert class="mt-4" variant="danger" show>{{ error }}</b-alert>
    </div>
    <div v-else>
      <b-card bg-variant="light" class="mt-4">
        <div class="text-center my-2">
          <b-spinner class="align-middle"></b-spinner>
        </div>
      </b-card>
    </div>
  </div>
</template>

<script>
import BuildBulkLoaderForm from "../components/BuildBulkLoaderForm";
import BuildBulkLoaderProgress from "../components/BuildBulkLoaderProgress";
import { parse_error } from "../plugins/helpers";
import { axios } from "../plugins/network";

export default {
  name: "BuildBulkLoader",
  components: { BuildBulkLoaderProgress, BuildBulkLoaderForm },
  data: function() {
    return {
      ready: false,
      started: false,
      disable_form: false,
      error: null
    };
  },
  mounted() {
    this.ready = false;
    axios()
      .get("/api/ci/builds/bulkload/status")
      .then(response => {
        console.log("Response for status on initial loading:");
        console.dir(response);
        // We're loading the bulk loader page. So even if we had a previous load but it is finished,
        // we ignore it.
        if (response.data.finished != null && !response.data.finished) {
          this.started = true;
          this.disable_form = true;
        }
        this.ready = true;
      })
      .catch(error => {
        this.error = parse_error(error);
      });
  },
  methods: {
    onSubmitted() {
      axios()
        .get("/api/ci/builds/bulkload/status")
        .then(response => {
          if (response.data.finished == null) {
            // This really shouldn't happen.
            alert("Unexpected response from server; See console for details");
          } else {
            this.started = true;
            this.disable_form = true;
          }
          this.ready = true;
        })
        .catch(error => {
          this.error = parse_error(error);
        });
    },
    onLoaded() {
      this.$refs.loader_form.onLoaded();
    }
  }
};
</script>

<style scoped></style>
