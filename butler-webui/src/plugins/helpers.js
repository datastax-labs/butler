/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */

export function compare_arrays(a1, a2, element_compare) {
  for (let i = 0, l = Math.min(a1.length, a2.length); i < l; i++) {
    let cmp = element_compare(a1[i], a2[i]);
    if (cmp !== 0) {
      return cmp;
    }
  }
  return a1.length < a2.length ? -1 : a1.length > a2.length ? 1 : 0;
}

export function percent(value, total) {
  return total === 0 ? 0 : Math.min(Math.round((100 * value) / total), 100);
}

export function parse_error(error) {
  console.dir(error);
  let msg = "Error requesting the server";
  if (error.response) {
    msg = error.response.data.message
      ? `${error.response.data.message} [${error.response.status}]`
      : `Got ${error.response.status} from the butler server`;
  }
  return `${msg} (see js console for details)`;
}

export function format_timestamp(value) {
  if (!value) {
    return "N/A";
  }
  // the history gets a value property. :/
  value = Object.prototype.hasOwnProperty.call(value, "seconds")
    ? value.seconds
    : value;
  let runAt = new Date();
  runAt.setTime(value * 1000);
  return runAt.toUTCString();
}

export function group_by(list, key) {
  return list.reduce(function(rv, x) {
    (rv[x[key]] = rv[x[key]] || []).push(x);
    return rv;
  }, {});
}
