# Elevator pitch

Butler is a project test results aggregation tool
collecting results for multiple product branches
and multiple jenkins CI/CD workflows
in a single database providing developers and managers
with view on current test condition across product branches
and ability to link failures with jira tickets.

Based on the history of test runs
butler can approve or reject builds (test suite results)
taking into account existing flaky and failing tests
history on the upstream or target branch;

# Problems we try to attack

TODO: zero-test-failures
TODO: approaches

`Butler` attacks the problem in two dimensions:
- visualisation and transparency for test fitness
- automated test regression analysis

## Visualization of tests fitness

CI/CD systems such as `jenkins` (but also others) are present in every reasonably maintained product. So we can usually have a good view on current condition of the tests being run by single pipeline and on a single branch;

Ticket / task managment systems such as 'jira' are used by multiple software
projects for maintaining information about bugs, features or tasks.

Once product get released software development organizations start to
maintain more than one branch e.g. `1.0` release branch (for patches) and new development candidate for `1.1` or `2.0`. In such case CI/CD is being run on all branches using same (or growing) set of tests.

`Butler` tries to solve following problems that are not solved by CI/CD (jenkins) nor tasks management systems (such as jira) itself:

- providing historical perspective on test condition (flickering, failing, stable) not only on a single branch but across multiple branches of the product being tested;
- automating test regression detection or making it visible;
- selection of the most valuable test fixes;
- automating task creation for fixing problematic tests with one click, so that all important information is present in the ticket;
- linking tasks with test failures;
- providing holistic view on tests duration and stability for all product branches;

So the first major goal is to __visualize current condition of product tests across all branches and enabe effort-less creation of tasks to improve it__;

## Automated test regression analysis

# Major user stories

People in several different roles as using `butler`.

## Build Baron

_Build Baron_ is a rotational role found in several development teams.
Her major responsibility is to ensure there are no new test regression introduced, preliminary investigation if such happened and making sure the team is aware;

- As BB I want to see on single screen test fitness for all the test suites and all the branches of the product so that I know if I should take a deeper look into the failures for particular suite or branch;
- As BB I would like to select most important tests that are failing or flickering and file ticket to fix them with single click so that the team can plan working on it and there is no need for further investigation when test fails for PR;
- As BB I would like to link flickering or failing tests with existing jira tickets so that I know that fixing them is already in the team backlog or there is a known problem in the product that contributes to the failures and there is no need for further investigation when test fails for PR;

## Developer


## Release manager


# FAQ

## Is butler aiming at one or multiple products?

One instance of Butler (application + database) is designed to work for single product, possibly using multiple jenkins jobs and product version branches. Butler exposes REST apis making it possible to build a comprehensive view for multiple products as a separate application;

Keeping multiple products in single butler instance is possible but makes things unnecessary complext e.g. from UI perspective (too much information).

## Is butler secured in any way

No. It is supposed to work in some secure environment (e.g. VPN)
and as it aims at providing transparency for test fitness access to the information is by design not limited in any way.


