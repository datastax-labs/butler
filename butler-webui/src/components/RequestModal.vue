<!--
  - Copyright DataStax, Inc.
  -
  - Please see the included license file for details.
  -->

<template>
  <b-modal
    :id="id"
    v-model="show"
    centered
    :title="title"
    size="xl"
    :ok-only="submitted"
    :cancel-disabled="submitted"
    :busy="loading"
    @ok.prevent="on_ok"
  >
    <div v-if="error != null">
      <b-alert variant="danger" show>{{ error }}</b-alert>
    </div>
    <div v-else-if="!submitted">
      <div>
        <slot name="pre-request"></slot>
      </div>
    </div>
    <div v-else-if="data == null">
      <div class="text-center my-2">
        <b-spinner variant="secondary" class="align-middle"></b-spinner>
      </div>
    </div>
    <div v-else>
      <div>
        <slot name="post-request" v-bind:data="data"></slot>
      </div>
    </div>
  </b-modal>
</template>

<script>
import { parse_error } from "../plugins/helpers";
import { axios } from "../plugins/network";

export default {
  name: "RequestModal",
  props: {
    id: { type: String, required: true },
    request: { type: String, required: true },
    title: { type: String, default: "" }
  },
  data: function() {
    return {
      show: false,
      arg: null,
      submitted: false,
      loading: false,
      data: null,
      error: null
    };
  },
  methods: {
    display(arg) {
      // reset the component state
      Object.assign(this.$data, this.$options.data());
      this.arg = arg;
      this.show = true;
    },
    on_ok() {
      console.log("Called on_ok", this.data);

      if (this.submitted) {
        // This is the 2nd ok, just close
        this.show = false;
        return;
      }

      this.submitted = true;
      // Send a POST request
      axios()
        .post(this.request, this.arg)
        .then(response => {
          console.log("Got response: ", response);
          this.data = response.data;
          if (this.data != null) {
            console.log("Emitting on success", this.arg, this.data);
            this.$emit("on_success", this.arg, this.data);
          }
        })
        .catch(error => (this.error = parse_error(error)));
    }
  }
};
</script>

<style scoped></style>
