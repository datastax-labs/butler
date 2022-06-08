<!--
  - Copyright DataStax, Inc.
  -
  - Please see the included license file for details.
  -->

<template>
  <div v-b-tooltip.hover.top.html.v-secondary :title="title">{{ rate }}%</div>
</template>

<script>
import { percent } from "../plugins/helpers";

export default {
  name: "Percentage",
  props: {
    value: {
      type: Number,
      required: true
    },
    value_unit: {
      type: String,
      required: true
    },
    total: {
      type: Number,
      required: true
    },
    total_unit: {
      type: String,
      required: true
    }
  },
  computed: {
    rate() {
      return percent(this.value, this.total);
    },
    title() {
      return `<em>${this.value}</em> ${this.value_unit} out of <em>${this.total}</em> ${this.total_unit}`;
    }
  }
};
</script>

<style scoped></style>
