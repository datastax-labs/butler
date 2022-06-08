# this is a very simple script to run butler
# so that it is confiured

export MYSQL_HOST=localhost
export MYSQL_DB=my_project
export MYSQL_USER=butler
export JAVA_OPTS="-Xms2048m -Xmx4096m -DbuildLoaderMaxBuildsPerJob=3"
export JAVA_OPTS="${JAVA_OPTS} -Dbutler.jira.default.project.enabled=true"
export BUTLER_BRAND=my-project
export BUTLER_HOST=$(hostname --long)

export butlerTrendDays=60

echo $JAVA_OPTS
./gradlew bootRun