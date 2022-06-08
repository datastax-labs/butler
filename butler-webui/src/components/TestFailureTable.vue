<!--
  - Copyright DataStax, Inc.
  -
  - Please see the included license file for details.
  -->

<template>
  <b-container fluid>
    <!-- User Interface controls -->
    <b-row>
      <b-col lg="6" class="my-1">
        <b-form-group
          label="Filter"
          label-cols-sm="3"
          label-align-sm="right"
          label-size="sm"
          label-for="filterInput"
          class="mb-0"
        >
          <b-input-group size="sm">
            <b-form-input
              v-model="filter"
              type="search"
              id="filterInput"
              placeholder="Type to Search"
              debounce="250"
            ></b-form-input>
            <b-input-group-append>
              <b-button :disabled="!filter" @click="filter = ''"
                >Clear</b-button
              >
            </b-input-group-append>
          </b-input-group>
        </b-form-group>
      </b-col>
      <b-col lg="6" class="my-1">
        <b-form-group
          label="Filter On"
          label-cols-sm="3"
          label-align-sm="right"
          label-size="sm"
          description="Leave all unchecked to filter on all data"
          class="mb-0"
        >
          <b-form-checkbox-group v-model="filterOn" class="mt-1">
            <b-form-checkbox value="versions">Version</b-form-checkbox>
            <!-- Test needs to only filter on the link text, not the entire html contents of the cell
            <b-form-checkbox value="test">Test</b-form-checkbox>
            -->
            <b-form-checkbox value="suite">Suite</b-form-checkbox>
          </b-form-checkbox-group>
        </b-form-group>
      </b-col>
    </b-row>
    <b-row>
      <b-col lg="6" class="my-1">
        <slot name="actions" />
      </b-col>
    </b-row>
    <b-row>
      <b-col lg="6" class="my-1">
        <b-icon-question-circle-fill
          id="popover-target-help"
        ></b-icon-question-circle-fill>
        <b-popover
          target="popover-target-help"
          triggers="hover"
          placement="top"
        >
          <template v-slot:title>Link Behavior</template>
          <ul>
            <li>
              <b-icon-link45deg></b-icon-link45deg>: Open the failures dashboard
            </li>
            <li>Suite: Load only this suites failures</li>
            <li>
              Test, Version, Variant: Open the last jenkins failure, in a new
              window
            </li>
          </ul>
        </b-popover>
      </b-col>
    </b-row>
    <b-row>
      <b-col lg="6" class="my-1">
        Displaying {{ tableModel.length }} of {{ data.length }} records
        <span v-if="selected.length > 0">({{ selected.length }} selected)</span>
      </b-col>
    </b-row>
    <b-table
      ref="failure_table"
      class="mt-4"
      :fields="fields"
      :items="data"
      :busy="!loaded"
      sort-by="category"
      :sort-compare="sort_compare"
      :filter="filter"
      :filterIncludedFields="filterOn"
      selectable
      @row-selected="row_selected"
      striped
      small
      v-model="tableModel"
    >
      <!-- Pass all scoped slot of our <test-failure-table> to the <table> -->
      <template v-for="(_, slot) of $scopedSlots" v-slot:[slot]="scope">
        <slot :name="slot" v-bind="scope" />
      </template>

      <template v-slot:table-busy>
        <div class="text-center my-2">
          <b-spinner class="align-middle"></b-spinner>
        </div>
      </template>

      <template v-slot:cell(dash)="data">
        <b-link :to="data.value.url" title="Test Dashboard"
          ><b-icon-link45deg></b-icon-link45deg
        ></b-link>
      </template>

      <template v-slot:cell(suite)="data">
        <b-link :to="'/ci/upstream/failures/' + data.value">{{
          data.value
        }}</b-link>
      </template>

      <template v-slot:cell(test)="data">
        <div v-if="data.value.url">
          <b-link :href="data.value.url" target="_blank"
            ><b-icon-box-arrow-up-right></b-icon-box-arrow-up-right>
            {{ data.value.name }}</b-link
          >
        </div>
        <div v-else>
          {{ data.value.name }}
        </div>
      </template>

      <template v-slot:cell(versions)="data">
        <template v-for="(v, i) in data.value">
          <template v-if="i !== 0">, </template>
          <b-link :href="v.url" :key="i">{{ v.name }}</b-link>
        </template>
      </template>

      <template v-slot:cell(variants)="data">
        <template v-for="(v, i) in data.value">
          <template v-if="i !== 0">, </template>
          <b-link :href="v.url" :key="i">{{ clean_variant(v.name) }}</b-link>
        </template>
      </template>

      <template v-slot:head(last_week_rate)="data">
        <div
          v-b-tooltip.hover.top.v-secondary
          title="Rate of failures for last week"
        >
          {{ data.label }}
        </div>
      </template>
      <template v-slot:cell(last_week_rate)="data">
        <failure-percentage :value="data.value"></failure-percentage>
      </template>

      <template v-slot:head(last_month_rate)="data">
        <div
          v-b-tooltip.hover.top.v-secondary
          title="Rate of failures for last month"
        >
          {{ data.label }}
        </div>
      </template>
      <template v-slot:cell(last_month_rate)="data">
        <failure-percentage :value="data.value"></failure-percentage>
      </template>

      <template v-slot:head(all_time_rate)="data">
        <div
          v-b-tooltip.hover.top.v-secondary
          title="Rate of failures over all known runs"
        >
          {{ data.label }}
        </div>
      </template>
      <template v-slot:cell(all_time_rate)="data">
        <failure-percentage :value="data.value"></failure-percentage>
      </template>
    </b-table>
  </b-container>
</template>

<script>
import FailurePercentage from "../components/FailurePercentage";
import { compare_arrays, percent } from "../plugins/helpers";

export default {
  name: "TestFailureTable",
  components: { FailurePercentage },
  props: {
    data: { type: Array, required: true },
    use_rates: { type: Boolean, default: false },
    extra_fields: { type: Array, default: () => [] },
    loaded: { type: Boolean, default: true }
  },
  data: function() {
    return {
      fields: [
        { key: "category", sortable: true },
        { key: "dash", sortable: false, label: "" },
        { key: "suite", sortable: true },
        { key: "test", sortable: true },
        { key: "versions", sortable: true },
        { key: "variants" }
      ],
      filter: null,
      filterOn: [],
      selected: [],
      tableModel: []
    };
  },
  methods: {
    /* Rates fields, added if 'this.use_rates' */
    rate_fields() {
      return [
        {
          key: "last_week_rate",
          label: "W",
          sortable: true,
          sortDirection: "desc"
        },
        {
          key: "last_month_rate",
          label: "M",
          sortable: true,
          sortDirection: "desc"
        },
        {
          key: "all_time_rate",
          label: "∞",
          sortable: true,
          sortDirection: "desc"
        }
      ];
    },
    sort_compare(
      aRow,
      bRow,
      key,
      sortDesc,
      formatter,
      compareOptions,
      compareLocale
    ) {
      const a = aRow[key];
      const b = bRow[key];
      if (key === "category") {
        // Sort by the category first
        const cmp = this.default_compare(
          aRow,
          bRow,
          key,
          sortDesc,
          formatter,
          compareOptions,
          compareLocale
        );
        if (cmp !== 0) {
          return cmp;
        }
        // But then sort on suite on equality
        return this.sort_compare(
          aRow,
          bRow,
          "suite",
          sortDesc,
          formatter,
          compareOptions,
          compareLocale
        );
      } else if (key === "suite") {
        // Sort by the suite
        const cmp = a.localeCompare(b, compareLocale, compareOptions);
        if (cmp !== 0) {
          return cmp;
        }
        // But then, sort by the test
        return this.sort_compare(
          aRow,
          bRow,
          "test",
          sortDesc,
          formatter,
          compareOptions,
          compareLocale
        );
      } else if (key === "test") {
        return a.name.localeCompare(b.name, compareLocale, compareOptions);
      } else if (key === "versions") {
        return compare_arrays(a, b, this.version_compare);
      } else if (key.endsWith("_rate")) {
        const p1 = percent(a.failures, a.runs);
        const p2 = percent(b.failures, b.runs);
        return p1 < p2 ? -1 : p1 > p2 ? 1 : 0;
      } else {
        return this.default_compare(
          aRow,
          bRow,
          key,
          sortDesc,
          formatter,
          compareOptions,
          compareLocale
        );
      }
    },
    version_compare(v1, v2) {
      if (v1.name === "master") return v2.name === "master" ? 0 : 1;
      if (v2.name === "master") return -1;
      return v1.name.localeCompare(v2.name);
    },
    default_compare(
      aRow,
      bRow,
      key,
      sortDesc,
      formatter,
      compareOptions,
      compareLocale
    ) {
      const a = aRow[key];
      const b = bRow[key];
      if (
        (typeof a === "number" && typeof b === "number") ||
        (a instanceof Date && b instanceof Date)
      ) {
        // If both compared fields are native numbers or both are native dates
        return a < b ? -1 : a > b ? 1 : 0;
      } else {
        // Otherwise stringify the field data and use String.prototype.localeCompare
        return this.stringify(a).localeCompare(
          this.stringify(b),
          compareLocale,
          compareOptions
        );
      }
    },
    stringify(value) {
      if (value === null || typeof value === "undefined") {
        return "";
      } else if (value instanceof Object) {
        return Object.keys(value)
          .sort()
          .map(key => toString(value[key]))
          .join(" ");
      } else {
        return String(value);
      }
    },
    clean_variant(variant) {
      return variant.replace("/<default>/g", "∅");
    },
    row_selected(items) {
      this.selected = items;
      this.$emit("selection_change", this.selected.length);
    },
    clear_selected() {
      this.$refs.failure_table.clearSelected();
    }
  },
  mounted() {
    if (this.use_rates) {
      this.fields = this.fields.concat(this.rate_fields());
    }
    this.fields = this.fields.concat(this.extra_fields);
  }
};
</script>

<style scoped></style>
