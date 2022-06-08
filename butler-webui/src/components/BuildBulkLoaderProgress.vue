<!--
  - Copyright DataStax, Inc.
  -
  - Please see the included license file for details.
  -->

<template>
  <b-card bg-variant="light" class="mt-4">
    <div class="float-right" v-show="show_eta">
      ETA: ~{{ etaSec | format_sec_duration }}
    </div>
    <b-card-text>
      <h5>
        {{ error_text != null ? "Error!" : finished ? "Done!" : "Loading..." }}
      </h5>
    </b-card-text>
    <b-progress :value="progress" :max="100" variant="primary" class="mb-3">
    </b-progress>
    <b-alert
      :show="error_text != null"
      variant="danger"
      dismissible
      fade
      @dismissed="error_text = null"
    >
      {{ error_text }}
    </b-alert>
    <div>
      Messages:
      <b-form-textarea
        id="messages"
        rows="8"
        readonly
        :value="messages"
      ></b-form-textarea>
    </div>
  </b-card>
</template>

<script>
import { parse_error } from "../plugins/helpers";
import { axios } from "../plugins/network";

export default {
  name: "BuildBulkLoaderProgress",
  data: function() {
    return {
      task_id: null,
      finished: false,
      error_text: null,
      show_eta: false,
      progress: 0,
      duration: 0,
      interval: null,
      messages: ""
    };
  },
  methods: {
    stopMonitoring() {
      if (this.interval) {
        clearInterval(this.interval);
        this.interval = null;
      }
      setTimeout(() => {
        this.task_id = null;
        this.$emit("loaded");
      }, 1500);
    },
    startMonitoring(task_id) {
      this.task_id = task_id;
      console.log("Monitoring of task " + task_id + " started!");
      this.messages = "";
      this.progress = 0;
      this.duration = 0;
      this.error_text = null;
      this.finished = false;
      this.interval = setInterval(() => {
        console.log("checking status of loading task " + task_id);
        axios()
          .get("/api/ci/builds/bulkload/status/task/" + this.task_id)
          .then(response => {
            if (response.data.finished == null) {
              // Kind of imply we restarted the service behind our back
              this.error_text = "It appears the server has been restarted.";
              this.finished = true;
              this.stopMonitoring();
              return;
            }

            this.finished = response.data.finished;
            this.duration = response.data.duration;
            this.progress = response.data.progress;
            if (this.progress === 0) {
              if (this.duration > 0) {
                this.progress = 1; // Give clue that we've started doing "something"
              }
            } else {
              this.show_eta = true;
            }
            this.error_text = response.data.error;
            this.messages = response.data.messages.join("\n");
            if (this.finished) {
              this.show_eta = false;
              this.stopMonitoring();
            }
          })
          .catch(error => {
            this.error_text =
              "Error fetching bulk-loading status:" + parse_error(error);
            this.finished = true;
            this.stopMonitoring();
          });
      }, 3000);
    }
  },
  computed: {
    etaSec() {
      if (this.progress >= 100) {
        return -1;
      }

      const estimated_duration = (100 * this.duration) / this.progress;
      return estimated_duration - this.duration;
    }
  }
};
</script>

<style scoped></style>
