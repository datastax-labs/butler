<!--
  - Copyright DataStax, Inc.
  -
  - Please see the included license file for details.
  -->

<script>
import { Line } from "vue-chartjs";
import zoom from "chartjs-plugin-zoom";

export default {
  extends: Line,
  name: "TimeLineChart",
  props: {
    name: { type: String, default: null },
    chart_data: { type: Object, default: null },
    options: { type: Object, default: null }
  },
  mounted() {
    this.addPlugin(zoom);
    this.renderChart(
      this.create_data(this.chart_data),
      this.create_options(this.options)
    );
    this.$emit("created", this.name, this.$data._chart);
  },
  methods: {
    create_options(userOptions) {
      let defaultOptions = {
        responsive: true,
        maintainAspectRatio: false,
        scales: {
          xAxes: [
            {
              display: true,
              type: "time",
              time: {
                unit: "day"
              }
            }
          ],
          yAxes: [
            {
              display: true,
              id: "y-count",
              position: "left",
              ticks: {
                beginAtZero: true,
                suggestedMax: 20
              }
            },
            {
              id: "y-duration",
              display: true,
              position: "right",
              ticks: {
                beginAtZero: true,
                suggestedMax: 120,
                callback: value => value + " min"
              }
            }
          ]
        },
        tooltips: {
          callbacks: {
            afterTitle: function(ts, d) {
              // Show the build in the tooltip.
              const t = ts[0];
              const data_point = d.datasets[t.datasetIndex].data[t.index];
              if ("extra" in data_point)
                return " (" + data_point["extra"] + ")";
              else return "";
            }
          }
        },
        plugins: {
          zoom: {
            pan: {
              enabled: true,
              mode: "x"
            },
            zoom: {
              enabled: true,
              mode: "x",
              drag: false,
              sensitivity: 2
            }
          }
        }
      };
      return Object.assign(defaultOptions, userOptions);
    },
    create_data(userData) {
      let defaultDatasetOptions = {
        pointRadius: 0,
        pointBorderWidth: 1,
        pointHoverRadius: 6,
        pointHoverBorderWidth: 3,
        lineTension: 0,
        borderWidth: 1
      };
      // For all dataset provided by the user, copy it on top of the default options. However,
      // assign copy directly in the target (on top of returning it), so we have to copy our
      // default options first to get a fresh object each time.
      let datasets = userData.datasets.map(s =>
        Object.assign(Object.assign({}, defaultDatasetOptions), s)
      );
      return {
        datasets: datasets
      };
    }
  }
};
</script>
