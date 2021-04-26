# End to End Tests
Contains all end to end tests that will for the current moment run against our dev environment to validate the correctness of current builds of our services

Some tests (eg, for Discover) take several minutes to run. To only run the fast tests, use the following command in the SBT console:

    testOnly * -- -l Slow

Or invoke SBT from the  command line:

    sbt "testOnly * -- -l Slow"

To only run the tests for a certain tag, use the following invocation:

    testOnly * -- -n Discover

## Environment Variables
In order to run the tests in either CI or locally, you must set the following environment variables:

* __E2E_EMAIL__ = *Email of the development environment user*
* __E2E_PASSWORD__ = *Password of the development environment user*
* __E2E_BLIND_REVIEWER_EMAIL__ = *Email of the development environment blind reviewer*
* __E2E_BLIND_REVIEWER_PASSWORD__ = *Password of the development environment blind reviewer*
* __E2E_TRIAL_USER_EMAIL__ = *Email of the development environment trial user*
* __E2E_TRIAL_USERvPASSWORD__ = *Password of the development environment trial user*
* __BF_ENVIRONMENT__ = *Must be set to PRODUCTION or DEVELOPMENT to select the environment to test*

All the E2E variables are stored in SSM

## Run CI tests
If you wish to run the tests as part of continuous integration, use the following command:

```
sbt docker
docker-compose up --build --exit-code-from e2e-tests
```
