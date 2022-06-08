<!--
  - Copyright DataStax, Inc.
  -
  - Please see the included license file for details.
  -->

<template>
  <b-button
    :type="type"
    :variant="variant"
    :disabled="disabled || loading"
    @click="$emit('click')"
  >
    <template v-if="loading">
      <b-spinner small></b-spinner>
    </template>
    <template v-else>
      {{ text }}
    </template>
  </b-button>
</template>

<script>
export default {
  name: "LoadButton",
  props: {
    type: { type: String, default: "button" },
    variant: { type: String, default: "" },
    disabled: { type: Boolean, default: false },
    text: { type: String, required: true },
    loading: { type: Boolean, required: true }
  }
};
</script>

<style scoped></style>
