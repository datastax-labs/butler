<!--
  - Copyright DataStax, Inc.
  -
  - Please see the included license file for details.
  -->

<template>
  <request-modal
    id="remove_failure_modal"
    request="/api/upstream/failures/remove"
    title="Remove known failure"
    ref="remove_failure_modal"
    v-on="$listeners"
  >
    <template v-slot:pre-request>
      <div>Remove {{ test_name }} for the known failures?</div>
    </template>
    <template v-slot:post-request>
      <div>
        Removed!
      </div>
    </template>
  </request-modal>
</template>

<script>
import RequestModal from "./RequestModal";

export default {
  name: "RemoveFailureModal",
  components: { RequestModal },
  data: function() {
    return {
      test: null
    };
  },
  computed: {
    test_name() {
      return this.test == null ? "<none>" : this.test.test_name;
    }
  },
  methods: {
    display(test) {
      this.test = test;
      this.$refs.remove_failure_modal.display(test);
    }
  }
};
</script>

<style scoped></style>
