<!--
  - Copyright DataStax, Inc.
  -
  - Please see the included license file for details.
  -->

<template>
  <percentage
    :value="value.failures"
    value_unit="failures"
    :total="value.runs"
    total_unit="runs"
  >
  </percentage>
</template>

<script>
import Percentage from "./Percentage";
export default {
  name: "FailurePercentage",
  components: { Percentage },
  props: {
    value: {
      type: Object,
      required: true
    }
  }
};
</script>

<style scoped></style>
