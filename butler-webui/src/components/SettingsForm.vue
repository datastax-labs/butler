<!--
  - Copyright DataStax, Inc.
  -
  - Please see the included license file for details.
  -->

<template>
  <b-card :title="title" bg-variant="light" class="mt-4">
    <b-form @submit.prevent="on_submit" @reset.prevent="on_cancel">
      <slot v-bind:data="slot_data"></slot>

      <div class="float-right">
        <span v-show="has_updates" class="text-warning mr-2">
          There are unsaved changes...
        </span>
        <span v-show="updated" class="text-success mr-2">
          Updated!
        </span>
        <b-button
          type="reset"
          v-if="slot_data.updating && !updated"
          class="mr-2"
        >
          Cancel
        </b-button>
        <b-button type="submit" :disabled="slot_data.updating && !has_updates">
          {{ slot_data.updating ? "Save" : "Update" }}
        </b-button>
      </div>
    </b-form>
  </b-card>
</template>

<script>
export default {
  name: "SettingsForm",
  props: {
    title: { type: String },
    form_data: { type: Object, required: true }
  },
  data: function() {
    return {
      is_ready: false,
      updated: false,
      slot_data: {
        updating: false,
        data: {}
      }
    };
  },
  computed: {
    has_updates() {
      if (!this.is_ready) {
        return false;
      }
      for (let [key, value] of Object.entries(this.form_data)) {
        if (!this._.isEqual(value, this.slot_data.data[key])) {
          return true;
        }
      }
      return false;
    }
  },
  methods: {
    on_ready() {
      this.slot_data.data = { ...this.form_data };
      this.is_ready = true;
    },
    on_updated() {
      this.slot_data.data = { ...this.form_data };
      this.slot_data.updating = false;
      this.updated = true;
    },
    on_submit() {
      if (this.slot_data.updating) {
        // We were updating, so we're asked to save.
        this.$emit("submitted", this.slot_data.data);
      } else {
        this.slot_data.updating = true;
      }
    },
    on_cancel() {
      // Reset to the original data and revert updating.
      this.slot_data.data = { ...this.form_data };
      this.slot_data.updating = false;
    }
  }
};
</script>

<style scoped></style>
