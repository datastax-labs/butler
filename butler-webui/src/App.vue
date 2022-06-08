<template>
  <div id="app">
    <b-navbar type="light" variant="light" fixed="top">
      <b-navbar-brand variant="faded" to="/ci/upstream/trend">
        Butler[{{ brand }}]
      </b-navbar-brand>

      <b-navbar-toggle target="nav-collapse"></b-navbar-toggle>

      <b-collapse id="nav-collapse" is-nav>
        <b-navbar-nav>
          <b-nav-item-dropdown text="Tests">
            <b-dropdown-item to="/ci/upstream/trend">Trend</b-dropdown-item>
          </b-nav-item-dropdown>

          <b-nav-item-dropdown
            v-for="workflow in workflows"
            v-bind:key="workflow"
            :text="workflow"
            @show="loadData"
          >
            <b-dropdown-group header="Upstream">
              <b-dropdown-item
                v-for="job in byCategory(upstream_jobs[workflow], 'UPSTREAM')"
                v-bind:key="job"
                :to="
                  '/ci/upstream/compare/' + job.workflow + '/' + job.job_name
                "
              >
                {{ job.job_name }}
              </b-dropdown-item>
            </b-dropdown-group>
            <b-dropdown-group header="User jobs">
              <b-dropdown-item
                v-for="job in byCategory(user_jobs[workflow], 'USER')"
                v-bind:key="job"
                :to="
                  '/ci/upstream/compare/' + job.workflow + '/' + job.job_name
                "
              >
                {{ job.job_name }}
              </b-dropdown-item>
            </b-dropdown-group>
          </b-nav-item-dropdown>
        </b-navbar-nav>

        <form
          class="form-inline"
          :action="'#/ci/upstream/jobs/branch/' + this.userBranch"
          target="_blank"
        >
          <input
            v-model="userBranch"
            class="form-control form-control-sm ml-3 mr-sm-2 py-1"
            type="search"
            placeholder="User branch e.g. XYZ-1234"
            aria-label="Search"
          />
          <button type="submit" class="btn btn-outline-info py-1 my-sm-0">
            Open
          </button>
        </form>
        <b-navbar-nav class="ml-auto">
          <b-nav-item-dropdown text="Configuration" right v-if="isAdmin">
            <b-dropdown-item to="/admin/settings">Settings</b-dropdown-item>
            <b-dropdown-item to="/ci/builds/load">Build Loader</b-dropdown-item>
            <b-dropdown-item to="/ci/builds/bulkload"
              >Builds Bulk Loader</b-dropdown-item
            >
          </b-nav-item-dropdown>
          <b-nav-item to="/login" v-if="!loggedIn">Log In</b-nav-item>
          <b-nav-item-dropdown :text="username" right v-if="loggedIn">
            <b-dropdown-item to="/logout">Log Out</b-dropdown-item>
          </b-nav-item-dropdown>
        </b-navbar-nav>
      </b-collapse>
    </b-navbar>
    <div class="mt-5 pt-3">
      <router-view />
    </div>
  </div>
</template>

<script>
import { parse_error, group_by } from "./plugins/helpers";
import { axios } from "./plugins/network";
import { getUsername, isAdmin, loggedIn } from "./plugins/auth";

export default {
  name: "App",
  data: function() {
    return {
      brand: "???",
      upstream_jobs: {},
      user_jobs: {},
      workflows: {},
      userBranch: ""
    };
  },
  computed: {
    username() {
      return getUsername();
    },
    isAdmin() {
      return isAdmin();
    },
    loggedIn() {
      return loggedIn();
    }
  },
  created() {
    this.loadData();
  },
  methods: {
    jobDisplayName(job) {
      return job.workflow + "/" + job.job_name;
    },
    // return jobs filtered by workflow and category, so that we can organize menu
    byCategory(jobs, category) {
      return jobs ? jobs.filter(j => j.category == category) : [];
    },
    loadData() {
      this.loadWorkflows();
      this.loadUpstreamJobs();
      this.loadConfig();
    },
    // loads configuration
    loadConfig() {
      axios()
        .get("/api/config/brand")
        .then(response => {
          this.brand = response.data;
        });
    },
    // load known workflows via apis so that we can build menus
    loadWorkflows() {
      const apiUrl = "/api/upstream/workflows/all";
      console.log("loading workflows from " + apiUrl);
      axios()
        .get(apiUrl)
        .then(response => {
          this.workflows = response.data.map(x => x.workflow);
          console.log("workflows", this.workflows);
          this.workflows.forEach(w => this.loadWorkflowUserJobs(w));
        })
        .catch(error => {
          alert(
            "Error loading workflows from " +
              apiUrl +
              ":\n" +
              parse_error(error)
          );
        });
    },
    // load upstream jobs
    loadUpstreamJobs() {
      const apiUrl = "/api/ci/jobs/upstream";
      console.log("loading upstream jobs from " + apiUrl);
      axios()
        .get(apiUrl)
        .then(response => {
          let jobs = group_by(response.data, "workflow");
          for (let job in jobs) {
            jobs[job].sort((a, b) =>
              this.jobDisplayName(a).localeCompare(this.jobDisplayName(b))
            );
          }
          this.upstream_jobs = jobs;
          console.log("upstream jobs", this.upstream_jobs);
        })
        .catch(error => {
          alert(
            "Error loading upstream jobs from " +
              apiUrl +
              "\n" +
              parse_error(error)
          );
        });
    },
    // load user jobs: for each workflow W get recent(W)
    // maybe we should call it from loadWorkflows()
    loadWorkflowUserJobs(workflow) {
      let apiUrl = "/api/ci/workflow/" + workflow + "/jobs/recent/16";
      console.log("loading " + apiUrl);
      axios()
        .get(apiUrl)
        .then(response => {
          let jobs = response.data;
          jobs.sort((a, b) =>
            this.jobDisplayName(a).localeCompare(this.jobDisplayName(b))
          );
          this.user_jobs[workflow] = jobs;
        })
        .catch(error => {
          alert(
            "Error loading user jobs for workflow" +
              workflow +
              ":\n" +
              parse_error(error)
          );
        });
    }
  }
};
</script>

<style>
#app {
  font-family: Avenir, Helvetica, Arial, sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  color: #2c3e50;
}
</style>
