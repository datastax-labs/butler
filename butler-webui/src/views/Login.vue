<!--
  - Copyright DataStax, Inc.
  -
  - Please see the included license file for details.
  -->

<template>
  <div class="container" id="main">
    <b-card title="Log In" bg-variant="light" class="mt-4">
      <b-alert
        :show="alert_text != null"
        variant="danger"
        dismissible
        fade
        @dismissed="alert_text = null"
      >
        {{ alert_text }}
      </b-alert>
      <b-form @submit.prevent="onSubmit">
        <b-form-group label="Username" label-for="username">
          <b-form-input id="username" v-model="username"></b-form-input>
        </b-form-group>
        <b-form-group label="Password" label-for="password">
          <b-form-input
            id="password"
            type="password"
            v-model="password"
          ></b-form-input>
        </b-form-group>
        <b-button type="submit">Log In</b-button>
      </b-form>
    </b-card>
  </div>
</template>

<script>
import { axios } from "../plugins/network";
import { parse_error } from "../plugins/helpers";
import { login, setUserInfo } from "../plugins/auth";

export default {
  name: "Login",
  data: function() {
    return {
      username: "",
      password: "",
      alert_text: null
    };
  },
  methods: {
    onSubmit() {
      axios()
        .post("/api/login", { login: this.username, password: this.password })
        .then(response => {
          console.log("Successful login");
          console.dir(response);
          if (!response.headers.authorization) {
            this.alert_text = "Invalid username or password";
            return;
          }

          const token = response.headers.authorization.substring(
            "Bearer ".length
          );
          login(token);
          axios()
            .get("/api/config/user")
            .then(response => {
              console.dir(response);
              setUserInfo(response.data.username, response.data.is_admin);
              this.$router.replace(this.$route.query.redirect || "/");
              location.reload();
            })
            .catch(
              error =>
                (this.alert_text =
                  "While getting user info: " + parse_error(error))
            );
        })
        .catch(error => {
          if (error.response && error.response.status === 401) {
            this.alert_text = "Invalid username or password";
          } else {
            this.alert_text = parse_error(error);
          }
        });
    }
  }
};
</script>

<style scoped></style>
