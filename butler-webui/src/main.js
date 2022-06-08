import "@babel/polyfill";
import "mutationobserver-shim";
import Vue from "vue";
import "./plugins/bootstrap-vue";
import App from "./App.vue";
import { BootstrapVue, IconsPlugin } from "bootstrap-vue";
import router from "./router";
import VueLodash from "vue-lodash";
import lodash from "lodash";

Vue.config.productionTip = false;

Vue.use(BootstrapVue);
Vue.use(IconsPlugin);
Vue.use(VueLodash, { lodash: lodash });

function pluralize(quantity, unit) {
  let str = `${quantity} ${unit}`;
  if (quantity > 1) {
    str += "s";
  }
  return str;
}

Vue.filter("format_sec_duration", function(durationSec) {
  if (durationSec <= 0) {
    return "-";
  }

  let format = function(d1, u1, d2, u2) {
    let i1 = Math.round(d1);
    let i2 = Math.round(d2);
    let str = pluralize(i1, u1);
    if (d2 > 0) {
      str += " " + pluralize(i2, u2);
    }
    return str;
  };

  if (durationSec < 60) {
    return pluralize(Math.round(durationSec), "second");
  }
  const minutes = durationSec / 60;
  if (minutes < 60) {
    const remainingSeconds = durationSec - minutes * 60;
    return format(minutes, "minute", remainingSeconds, "second");
  }
  const hours = minutes / 60;
  if (hours < 24) {
    const remainingMinutes = minutes - hours * 60;
    return format(hours, "hour", remainingMinutes, "minute");
  }
  const days = hours / 24;
  const remainingHours = hours - days * 24;
  return format(days, "day", remainingHours, "hour");
});

new Vue({
  router,
  render: h => h(App)
}).$mount("#app");
