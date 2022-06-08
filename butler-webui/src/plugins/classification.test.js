const classi = require("./classification");

//
// TESTS FOR VERSION BRANCH CLASSIFICATION SUCH AS 1.0-dev or main
// For those tests there is only a branch and upstream view is empty
//

test("with less than 3 results we cannot tell a thing", () => {
  expect(classi.branchClassification([false, false])).toBe(null);
  expect(classi.branchClassification([false, true])).toBe(null);
  expect(classi.branchClassification([true, false])).toBe(null);
  expect(classi.branchClassification([true, true])).toBe(null);
  expect(classi.branchClassification([false, null, null, false])).toBe(null);
});

test("single recent fail on version branch is classified as new", () => {
  expect(classi.branchClassification([false, true, true])).toBe(classi.NEW);
});

test("few recent fails on version branch is classified as fail", () => {
  expect(classi.branchClassification([false, false, true, true])).toBe(
    classi.FAILING
  );
});

test("all failures is classified as fail", () => {
  expect(classi.branchClassification([false, false, false])).toBe(
    classi.FAILING
  );
});

test("all passes gives empty classification", () => {
  expect(classi.branchClassification([true, true, true])).toBe(null);
});

test("one failure among passes is not classified as flaky", () => {
  expect(classi.branchClassification([true, true, false, true])).toBe(null);
});

test("two dispersed failures are classified as flaky", () => {
  expect(classi.branchClassification([true, false, true, true, false])).toBe(
    classi.FLAKY
  );
  expect(classi.branchClassification([true, false, true, false, true])).toBe(
    classi.FLAKY
  );
  expect(classi.branchClassification([true, false, true, false])).toBe(
    classi.FLAKY
  );
  expect(classi.branchClassification([true, false, true, null, false])).toBe(
    classi.FLAKY
  );
  expect(
    classi.branchClassification([true, false, null, null, true, false, true])
  ).toBe(classi.FLAKY);
});

test("two subsequent failures in the middle are not classified as flaky (it might be fixed intermittent failure)", () => {
  expect(classi.branchClassification([true, false, false, true])).toBe(null);
});

test("new test with some null history", () => {
  expect(classi.branchClassification([false, false, false, null, null])).toBe(
    classi.FAILING
  );
  expect(classi.branchClassification([false, true, false, null, null])).toBe(
    classi.FLAKY
  );
});

//
// TESTS FOR BRANCH vs UPSTREAM COMPARISON CLASSIFICATION
//

test("failing on branch and passing on upstream is a regression", () => {
  expect(
    classi.comparisonClassification([false, false], [true, true, true])
  ).toBe(classi.REGRESSION);
  expect(
    classi.comparisonClassification([false, true], [true, true, true])
  ).toBe(classi.REGRESSION);
  expect(
    classi.comparisonClassification([false, false, false], [true, true, true])
  ).toBe(classi.REGRESSION);
});

test("no failures on branch is not interesting vs upstream", () => {
  expect(
    classi.comparisonClassification([true, true], [true, true, true])
  ).toBe(null);
  expect(
    classi.comparisonClassification([true, true], [true, false, false])
  ).toBe(null);
  expect(
    classi.comparisonClassification(
      [true, true, true, true, true],
      [true, false, false]
    )
  ).toBe(null);
  expect(classi.comparisonClassification([true], [true, true, true])).toBe(
    null
  );
});

test("less than 3 runs on branch will not lead to fixed", () => {
  expect(
    classi.comparisonClassification([true, true], [false, false, false])
  ).toBe(null);
});

test("fixed on branch and was failing on upstream is classified as fixed", () => {
  expect(
    classi.comparisonClassification([true, true, true], [false, false, false])
  ).toBe(classi.FIXED);
  expect(
    classi.comparisonClassification(
      [true, true, true],
      [false, false, false, true, true]
    )
  ).toBe(classi.FIXED);
  expect(
    classi.comparisonClassification(
      [true, true, false],
      [false, false, false, true, true]
    )
  ).toBe(classi.FIXED);
  expect(
    classi.comparisonClassification(
      [true, true, true, false, false],
      [false, false, false, true, true, true, true]
    )
  ).toBe(classi.FIXED);
});

test("misleading fixed for flaky test", () => {
  expect(
    classi.comparisonClassification(
      [true, false],
      [true, true, false, true, false, true, true, true, true, true]
    )
  ).toBe(classi.FLAKY);
});

test("failing on branch and failing on upstream is failing", () => {
  expect(
    classi.comparisonClassification(
      [false, false, false],
      new Array(7).fill(false)
    )
  ).toBe(classi.FAILING);
  expect(
    classi.comparisonClassification(
      [false, false, false],
      [false, false, false, false, true, true]
    )
  ).toBe(classi.FAILING);
});

test("flaky on branch and flaky on upstream is flaky", () => {
  expect(
    classi.comparisonClassification(
      [false, true, false],
      [true, true, false, true, false]
    )
  ).toBe(classi.FLAKY);
});

test("flaky on branch and passing on upstream is regression", () => {
  expect(
    classi.comparisonClassification([false, true, false], [true, true, true])
  ).toBe(classi.REGRESSION);
  expect(
    classi.comparisonClassification(
      [true, false, true, false, true, true],
      [true, true, true]
    )
  ).toBe(classi.REGRESSION);
});

/*
FF|FFFFFFF  still failing
SS|FFFFFFF  fixed if >3 runs
FF|SSSSSSS  regression
SS|SSSSSSS  not interesting
FF|SSFSFSF  still flaky
SS|SSFSFSF  null
FS|FFFFFFF  null
SF|FFFFFFF  null
FS|SSSSSSS  fail
SF|SSSSSSS  fail
FS|SSFSFSF  flaky
SF|SSFSFSF  flaky
*/
test("2 builds on branch vs upstream matrix", () => {
  let passingUpstream = [true, true, true, true, true, true, false, false];
  let failingUpstream = [false, false, false, false, false, true, true];
  let flakyUpstream = [true, false, true, true, false, true, false, true];
  expect(classi.comparisonClassification([false, false], failingUpstream)).toBe(
    classi.FAILING
  );
  expect(classi.comparisonClassification([true, true], failingUpstream)).toBe(
    null
  );
  expect(classi.comparisonClassification([false, false], passingUpstream)).toBe(
    classi.REGRESSION
  );
  expect(classi.comparisonClassification([true, true], passingUpstream)).toBe(
    null
  );
  expect(classi.comparisonClassification([false, false], flakyUpstream)).toBe(
    classi.FLAKY
  );
  expect(classi.comparisonClassification([true, true], flakyUpstream)).toBe(
    null
  );
  //
  expect(classi.comparisonClassification([true, false], failingUpstream)).toBe(
    null
  );
  expect(classi.comparisonClassification([false, true], failingUpstream)).toBe(
    null
  );
  expect(classi.comparisonClassification([true, false], passingUpstream)).toBe(
    classi.REGRESSION
  );
  expect(classi.comparisonClassification([false, true], passingUpstream)).toBe(
    classi.REGRESSION
  );
  expect(classi.comparisonClassification([true, false], flakyUpstream)).toBe(
    classi.FLAKY
  );
  expect(classi.comparisonClassification([false, true], flakyUpstream)).toBe(
    classi.FLAKY
  );
});

test("S_|SSSSSSSSSSF should not be considered regression", () => {
  expect(
    classi.comparisonClassification(
      [true, null],
      [
        true,
        true,
        true,
        true,
        true,
        true,
        true,
        true,
        true,
        true,
        true,
        true,
        true,
        false
      ]
    )
  ).toBe(null);
});

//
// PARTIAL CHECKS TESTS
//

test("is fixed", () => {
  expect(classi.isFixed([true, true, false])).toBe(true);
});

test("is regression", () => {
  expect(classi.isRegression([false, false, false, true, true])).toBe(true);
});
