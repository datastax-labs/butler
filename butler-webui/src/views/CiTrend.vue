<template>
  <div class="container-fluid h-100" id="main">
    <b-row class="h-100">
      <b-col
        cols="6"
        class="p-1"
        v-for="(data, version) in versions_data"
        :key="version"
      >
        <b-card bg-variant="light" text-variant="dark">
          <div class="row py-0">
            <div class="col-12">
              <time-line-chart
                v-if="loaded"
                :chart_data="data"
                :options="chartOptions(version)"
                @created="register_chart"
              >
              </time-line-chart>
            </div>
            <div class="col12 mx-4 small">
              Total passed:
              <b-badge class="mx-1" :variant="data.totalRatio.variant"
                >{{ data.passedBuilds }} / {{ data.numBuilds }}
              </b-badge>
              Recent passed:
              <b-badge class="mx-1" :variant="data.recentRatio.variant"
                >{{ data.recentPassed }} / {{ data.recentBuilds }}
              </b-badge>
              Broken builds:
              <b-badge class="mx-1" :variant="data.recentBrokenVariant"
                >{{ data.recentBroken }} / {{ data.recentBuilds }}
              </b-badge>
              Avg Duration:
              <b-badge class="mx-1" :variant="data.duration.variant"
                >{{ data.duration.minutes }} min.
              </b-badge>
              <b-link
                class="mx-1 text-lg"
                :to="
                  '/ci/upstream/compare/' +
                    version.split('::')[1] +
                    '/' +
                    version.split('::')[0]
                "
                >detailed history</b-link
              >
            </div>
          </div>
        </b-card>
      </b-col>
    </b-row>
  </div>
</template>

<script>
import TimeLineChart from "../components/TimeLineChart";
import { parse_error } from "../plugins/helpers";
import { axios } from "../plugins/network";

export default {
  name: "CiTrend",
  components: { TimeLineChart },
  data: function() {
    return {
      loaded: false,
      versions_data: null,
      charts: {}
    };
  },
  mounted() {
    this.loaded = false;
    axios()
      .get("/api/upstream/trends")
      .then(response => {
        this.versions_data = this.api_to_chart(response.data.data);
        this.loaded = true;
      })
      .catch(error => alert(parse_error(error)));
  },
  methods: {
    chartOptions(version) {
      return {
        plugins: {
          zoom: false
        },
        title: {
          display: true,
          text: version,
          fontSize: 18
        },
        onClick: function() {
          //console.log("Clicked");
          //let runDataset = this.data.datasets[1];
          //let isHiddenMeta = runDataset._meta[Object.keys(runDataset._meta)[0]].hidden;
          //if (isHiddenMeta == null) {
          //  console.log("Runs are shown");
          //} else {
          //  console.log("Runs are hidden");
          //}
        }
      };
    },
    // calculate variant based on ratio
    calculate_ratio(hit, total) {
      if (total == 0) {
        return {
          ratio: null,
          variant: "secondary"
        };
      } else {
        let ratio = (100 * hit) / total;
        let variant =
          ratio > 90 ? "success" : ratio > 70 ? "warning" : "danger";
        return {
          ratio: ratio,
          variant: variant
        };
      }
    },
    // Converts the data coming from the API to proper chartjs datasets
    api_to_chart(api_data) {
      console.log("api_data", api_data);
      const chart_data = {};
      for (let data of api_data) {
        const plot_name = data.version + "::" + data.workflow;
        console.log("building chart data for ", plot_name);
        console.log(data);
        let plot_data = { datasets: [] };
        data = data.data;
        // skip chart if no data visible
        if (data.num_builds == 0) continue;
        // total failures ratio
        plot_data.numBuilds = data.num_builds;
        plot_data.passedBuilds = data.num_builds - data.num_builds_failed;
        plot_data.totalRatio = this.calculate_ratio(
          plot_data.passedBuilds,
          plot_data.numBuilds
        );
        // duration
        const dur = Math.round(data.avg_recent_duration_in_min);
        plot_data.duration = {
          minutes: isNaN(dur) ? "---" : dur,
          variant: isNaN(dur)
            ? "secondary"
            : dur < 45
            ? "success"
            : dur < 90
            ? "warning"
            : "danger"
        };
        // recent failures ratio
        plot_data.recentBuilds = data.num_recent;
        plot_data.recentPassed = data.num_recent - data.num_recent_failed;
        plot_data.recentRatio = this.calculate_ratio(
          plot_data.recentPassed,
          plot_data.recentBuilds
        );
        // recent broken ratio, always marked as danger if >1 broken builds
        plot_data.recentBroken = data.num_recent_broken;
        plot_data.recentBrokenVariant =
          plot_data.recentBroken == 0 ? "success" : "danger";
        // plot series
        const not_crashed = data.test_failures.points.filter(p => p.y < 1000);
        plot_data.datasets.push({
          label: "#Failed",
          backgroundColor: "rgb(255,228,214)",
          borderColor: "rgb(255,94,60)",
          yAxisID: "y-count",
          pointRadius: 4,
          data: not_crashed
        });
        plot_data.datasets.push({
          label: "#Tests",
          backgroundColor: "rgb(217,255,241)",
          borderColor: "rgb(79,255,175)",
          hidden: true,
          yAxisID: "y-count",
          data: data.test_runs.points
        });
        plot_data.datasets.push({
          label: "Duration[min]",
          borderColor: "rgb(115,194,251)", // Maya
          hidden: false,
          yAxisID: "y-duration",
          data: data.duration.points
        });
        // add to charts
        console.log(plot_data);
        chart_data[plot_name] = plot_data;
      }
      return chart_data;
    },
    register_chart(version, chart) {
      this.charts[version] = chart;
    },
    reset_zoom(version) {
      this.charts[version].resetZoom();
    }
  }
};
</script>
