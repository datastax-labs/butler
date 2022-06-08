<template>
  <div class="container-fluid vh-100" id="main">
    <h3>
      Failures for {{ $route.params.workflow_name }}/{{
        $route.params.job_name
      }}
    </h3>
    <b-table-lite small hover :busy="!loaded" :items="runs" :fields="fields">
      <template v-slot:cell(jira)="data">
        <b-link :href="data.value.url">
          {{ data.value.name }}
        </b-link>
      </template>
    </b-table-lite>
    <pre>{{ raw }}</pre>
  </div>
</template>

<script>
import { parse_error } from "../plugins/helpers";
import { axios } from "../plugins/network";

export default {
  name: "CiWorkflowFailures",
  data: function() {
    return {
      loaded: false,
      raw: null,
      fields: [
        "suite",
        "test",
        "variant",
        { name: "jira", class: "text-nowrap" }
      ],
      runs: []
    };
  },
  methods: {
    extract(data) {
      this.raw = data;
      let cls = "light";
      let last = null;
      for (let failure of data.failures) {
        console.log("Got", failure.test.test_name, failure);
        if (last != failure.test.test_name) {
          last = failure.test.test_name;
          cls = cls == "secondary" ? "light" : "secondary";
        }
        for (let variant in failure.failure_details.all_by_variants) {
          console.log("Variant", variant);
          let variants = failure.failure_details.all_by_variants[variant];
          let row = {
            suite: failure.test.class_name,
            test: failure.test.test_name,
            variant: variant,
            jira: failure.jira_issue,
            // _rowVariant: cls,
            _cellVariants: { suite: cls, test: cls, variant: cls, jira: cls }
          };
          for (let i = 0; i < variants.length; i++) {
            console.log("Row", variants[i]);
            row["r" + i] = variants[i].id.build_number;
            row["_cellVariants"]["r" + i] = variants[i].failed
              ? "danger"
              : variants[i].skipped
              ? "warning"
              : "success";
          }
          this.runs.push(row);
        }
      }
      this.loaded = true;
    }
  },
  mounted() {
    for (var i = 0; i < 10; i++) {
      this.fields.push({ key: "r" + i, class: "text-center" });
    }

    axios()
      .get(
        "/api/upstream/interesting/" +
          this.$route.params.workflow_name +
          "/" +
          this.$route.params.job_name
      )
      .then(response => {
        console.log(response.data);
        this.extract(response.data);
      })
      .catch(error => {
        alert("Error loading failure history:\n" + parse_error(error));
      });
  }
};
</script>
