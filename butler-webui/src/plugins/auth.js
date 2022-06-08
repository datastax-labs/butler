/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */

export function login(token) {
  localStorage.token = token;
}

export function setUserInfo(username, isAdmin) {
  localStorage.usename = username;
  localStorage.isAdmin = isAdmin;
}

export function getToken() {
  return localStorage.token;
}

export function getUsername() {
  return localStorage.usename;
}

export function logout(cb) {
  delete localStorage.token;
  delete localStorage.isAdmin;
  delete localStorage.username;
  if (cb) cb();
}

export function loggedIn() {
  return !!localStorage.token;
}

export function isAdmin() {
  return localStorage.isAdmin;
}
