import Vue from "vue";
import VueRouter from "vue-router";
import CiTrend from "../views/CiTrend";

Vue.use(VueRouter);

const routes = [
  {
    path: "/",
    name: "Home",
    component: CiTrend
  },
  {
    path: "/login",
    name: "Login",
    component: () => import("../views/Login.vue")
  },
  {
    path: "/logout",
    name: "Logout",
    component: () => import("../views/Logout.vue")
  },
  {
    path: "/ci/builds/bulkload",
    name: "BulkLoader",
    component: () => import("../views/BuildBulkLoader.vue")
  },
  {
    path: "/ci/builds/load",
    name: "Loader",
    component: () => import("../views/BuildLoader.vue")
  },
  {
    path: "/ci/upstream/trend",
    name: "CiTrend",
    component: () => import("../views/CiTrend.vue")
  },
  {
    path: "/ci/upstream/failures",
    name: "CiFailures",
    component: () => import("../views/CiFailures.vue")
  },
  {
    path: "/ci/upstream/failures/:suite_name",
    name: "CiFailures",
    component: () => import("../views/CiFailures.vue")
  },
  {
    path: "/ci/upstream/failure/:suite_name/:test_name",
    name: "CiFailure",
    component: () => import("../views/CiFailure.vue")
  },
  {
    path: "/ci/upstream/workflow/:workflow_name/failure/:suite_name/:test_name",
    name: "CiFailure",
    component: () => import("../views/CiFailure.vue")
  },
  {
    path:
      "/ci/upstream/workflow/:workflow_name/failure/:path/:suite_name/:test_name",
    name: "CiFailure",
    component: () => import("../views/CiFailure.vue")
  },
  // {
  //   path: '/ci/upstream/workflow/:workflow_name/job/:job_name/failures',
  //   name: 'CiFailures',
  //   component: () => import('../views/CiWorkflowFailures.vue')
  // },
  {
    path:
      "/ci/upstream/workflow/:workflow_name/job/:job_name/failure/:suite_name/:test_name",
    name: "CiFailure",
    component: () => import("../views/CiFailure.vue")
  },
  {
    path: "/ci/upstream/compare/:workflowA/:jobA/to/:workflowB/:jobB",
    name: "TestComparison",
    component: () => import("../views/TestComparison.vue")
  },
  {
    path: "/ci/upstream/compare/:workflowA/:jobA",
    name: "TestComparisonUpstream",
    component: () => import("../views/TestComparison.vue")
  },
  {
    path: "/ci/upstream/jobs/branch/:branch",
    name: "BranchJobs",
    component: () => import("../views/BranchJobs.vue")
  },
  {
    path: "/admin/settings",
    name: "ButlerSettings",
    component: () => import("../views/ButlerSettings.vue")
  }
];

const router = new VueRouter({
  // history mode breaks direct navigation and page refreshing
  // mode: 'history',
  base: process.env.BASE_URL,
  routes
});

export default router;
