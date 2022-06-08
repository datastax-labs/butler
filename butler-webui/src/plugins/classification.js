/*
Top level classification function to calculate if based on the branch and upstream results, we have:
regr - regression on branch or vs upstream
fix - fixed vs upstream
new - single new failure,
flaky - flaky test
*/
export const REGRESSION = "rgrsn"; // regression, probably introduced on the branch vs upstream
export const NEW = "new"; // single new failure, we are not sure yet if it is a regression
export const FLAKY = "flaky"; // test is flaky, or flickering
export const FAILING = "failing"; // test is constantly failing
export const FIXED = "fixed"; // test was failing but is fixed, also can be fixed on branch vs upstream
export const CHECK = "check"; // something is not right with the result, suspicious

export function testResultsClassification(branch, upstream) {
  return hasSomeRuns(upstream)
    ? comparisonClassification(branch, upstream)
    : branchClassification(branch);
}

/*
Classification on the branch, when we are not comparing but just looking into the history on one branch
For example on main or master
*/
export function branchClassification(branch) {
  if (isNotRunAnymore(branch)) return null;
  if (notNull(branch).length < 3) return null;
  if (isNew(branch)) return NEW;
  if (isRegression(branch)) return FAILING;
  if (isFlaky(branch)) return FLAKY;
  if (isFailing(branch)) return FAILING;
  // removed to not generate noise
  // if (isFixed(branch) && passedRuns(branch).length < failedRuns(branch).length) return FIXED;
  return null;
}

/*
Classification for comparions between "branch" and "upstream"
*/
export function comparisonClassification(branch, upstream) {
  let branch_has_failures = failedRuns(branch).length > 0;

  // if upstream is good (passing or fixed) any unstability on branch is potenial problem
  if (isFixed(upstream) || isAllPass(upstream)) {
    if (isFlaky(branch)) return REGRESSION;
    if (isFailing(branch)) return REGRESSION;
    if (isNew(branch)) return REGRESSION;
    if (isRegression(branch)) return REGRESSION;
    if (branch_has_failures) return REGRESSION;
  }
  // if it was failing on regression on upstream, but now is passing or fixed, it is fixed
  if (isFailing(upstream) || isRegression(upstream)) {
    // at least 3 runs are required to classified branch as fixed
    if (notNull(branch).length >= 3 && (isFixed(branch) || isAllPass(branch)))
      return FIXED;
  }
  if (isFlaky(upstream)) {
    // at least 4 runs are required to classified branch as fixed
    if (notNull(branch).length >= 4 && (isFixed(branch) || isAllPass(branch)))
      return FIXED;
  }
  // if was failing and is failing we mark it as failing
  if (isFailing(upstream) && isFailing(branch)) return FAILING;
  if (isRegression(upstream) && isFailing(branch)) return FAILING; // this can happen e.g. if we rebase
  if (isRegression(upstream) && isRegression(branch)) return FAILING; // this also can happen after rebasing pr branch
  // if it was flaky but now is constantly failing we should consider it a regression
  if (isFlaky(upstream)) {
    if (notNull(branch).length >= 4 && isFailing(branch)) return REGRESSION;
    if (branch_has_failures) return FLAKY;
  }
  // TODO: analyze below
  if (isBranchRegression(branch, upstream)) return REGRESSION;
  if (hasSomeRuns(upstream) && isAllPass(upstream) && !looksFixed(upstream))
    return NEW;
  return null;
}

/*
All runs that are not empty (so when test was actually run) are failed
*/
function isFailing(classified) {
  let run = notNull(classified);
  return run.length > 0 && run.every(x => x == false);
}

function notNull(classified) {
  return classified.filter(x => x != null);
}

/*
test is considered flaky, if it is failing at least 2 times
and those failures do not create single block, but rather are scattered
special case: no information about the runs in the start of classified
*/
export function isFlaky(classified) {
  let nonEmpty = classified.filter(x => x != null);
  let failCount = nonEmpty.filter(x => !x).length;
  return failCount >= 2 && longestFailPeriod(nonEmpty) != failCount;
}

/*
We consider it a regression if it fails at least once,
and it was passing then start to fail, so it is not spotty
*/
export function isRegression(classified) {
  let nonEmpty = classified.filter(x => x != null);
  let failCount = nonEmpty.filter(x => !x).length;

  return (
    failCount > 1 &&
    nonEmpty[nonEmpty.length - 1] &&
    nonEmpty.slice(0, failCount).every(x => !x)
  );
}

export function isAllPass(classified) {
  return hasSomeRuns(classified) && classified.every(x => x);
}

/*
We consider all failed if we have all results non-empty and all failed
The reason is we do not want to include results that were failing but now are not being run at all
*/
export function isAllFail(classified) {
  return classified.every(x => x == false);
}

/*
Return true if at least 1 recent run does not include this test
*/
export function isNotRunAnymore(classified) {
  return classified[0] == null;
}

/*
New failure is a single new failure, so it is hard to say if this is a regression
*/
export function isNew(classified) {
  return !classified[0] && classified.slice(1).every(x => x);
}

/**
 * We consider results on the branch as "fixed" if it was failing and now is passing,
 * and there are less passes than failures, so that it is relatively fresh
 * @param {*} classified list of results e.g. [true, true, false, null, false]
 */
export function isFixed(classified) {
  let nonEmpty = notNull(classified);
  let numPasses = passedRuns(classified).length;
  let numFailures = failedRuns(classified).length;
  let startstWithPasses = nonEmpty.slice(0, numPasses).every(x => x);
  let endsWithFailures = nonEmpty.slice(-numFailures).every(x => !x);
  let res =
    numPasses > 0 &&
    numFailures > 0 &&
    startstWithPasses &&
    endsWithFailures &&
    numPasses > numFailures;
  return res;
}

export function passedRuns(classified) {
  return notNull(classified).filter(x => x);
}

export function failedRuns(classified) {
  return notNull(classified).filter(x => !x);
}

export function looksFixed(classified) {
  let failCount = classified.filter(x => !x).length;
  let failPct = failCount / classified.length;

  return classified.slice(0, 3).every(x => x) && failPct < 0.25;
}

export function longestFailPeriod(classified) {
  let maxPeriod = 0;
  let period = 0;
  for (let passed of classified) {
    if (passed) {
      period = 0;
    } else {
      period++;
    }
    maxPeriod = Math.max(period, maxPeriod);
  }

  return maxPeriod;
}

export function hasSomeRuns(classified) {
  return classified.filter(x => x != null).length > 0;
}

export function isBranchRegression(branchResults, upstreamResults) {
  let failsOnBranch =
    isAllFail(branchResults) ||
    isNew(branchResults) ||
    isRegression(branchResults);
  let goodOnUpstream = isAllPass(upstreamResults);
  return failsOnBranch && goodOnUpstream;
}

export function isFixedBranchVsUpstream(branchResults, upstreamResults) {
  let failsOnUpstream =
    isAllFail(upstreamResults) || isRegression(upstreamResults);
  let fixedOnBranch = isFixed(branchResults) || isAllPass(branchResults);
  return failsOnUpstream && fixedOnBranch;
}
