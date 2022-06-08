<!--
  - Copyright DataStax, Inc.
  -
  - Please see the included license file for details.
  -->

<template>
  <div></div>
</template>

<script>
import { logout } from "../plugins/auth";

export default {
  name: "Logout",
  mounted() {
    logout(() => {
      this.$router.replace(this.$route.query.redirect || "/");
      location.reload();
    });
  }
};
</script>

<style scoped></style>
