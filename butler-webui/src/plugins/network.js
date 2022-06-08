/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
import Axios from "axios";
import { getToken, loggedIn } from "./auth";

export function axios() {
  if (loggedIn()) {
    return Axios.create({
      headers: { Authorization: "Bearer " + getToken() }
    });
  } else {
    return Axios;
  }
}
