#!/usr/bin/env bash
# Creates services/<name> from tools/service-archetype and wires it into the build and the local infrastructure.
# Usage: tools/new-service.sh <name> <port>      e.g. tools/new-service.sh practice-service 8084
set -euo pipefail

if [[ $# -ne 2 ]]; then
    echo "usage: $0 <name> <port>" >&2
    exit 64
fi

name=$1
port=$2
root=$(cd "$(dirname "$0")/.." && pwd)

[[ $name =~ ^[a-z][a-z0-9]*(-[a-z0-9]+)*$ ]] || { echo "name must be kebab-case: $name" >&2; exit 64; }
[[ $port =~ ^[0-9]{4,5}$ ]] || { echo "port must be a number: $port" >&2; exit 64; }
[[ ! -e "$root/services/$name" ]] || { echo "services/$name already exists" >&2; exit 1; }

base=${name%-service}                          # practice-service -> practice, ai-gateway -> ai-gateway
short=${base//-/}                              # tutor-agent -> tutoragent (Java package, Avro namespace)
db_base=${base//-/_}                           # tutor-agent -> tutor_agent
[[ $name == content-factory ]] && db_base=factory
db="${db_base}_db"
role="${db_base}_app"
app_class="$(echo "$short" | awk '{print toupper(substr($0,1,1)) substr($0,2)}')ServiceApplication"

echo "Installing the archetype and libraries"
"$root/mvnw" -q -B -f "$root/pom.xml" -pl tools/service-archetype,libs/toefly-common-web,libs/toefly-common-security,libs/toefly-common-test -am install -DskipTests

echo "Generating services/$name"
mkdir -p "$root/services"
(cd "$root/services" && "$root/mvnw" -q -B archetype:generate \
    -DarchetypeGroupId=com.toefly -DarchetypeArtifactId=service-archetype -DarchetypeVersion=0.1.0-SNAPSHOT \
    -DgroupId=com.toefly -DartifactId="$name" -Dversion=0.1.0-SNAPSHOT -Dpackage="com.toefly.$short" \
    -DserviceShort="$short" -DtopicSegment="$base" -DservicePort="$port" -DdbName="$db" -DdbRole="$role" \
    -DapplicationClass="$app_class" -DinteractiveMode=false)

# The archetype plugin appends a <modules> entry with a bare name; keep the services/<name> form instead.
perl -0pi -e "s|\n\s*<module>$name</module>||" "$root/pom.xml"
perl -0pi -e "s|(\n(\s*)<module>tools/service-archetype</module>)|\n\$2<module>services/$name</module>\$1|" "$root/pom.xml"

# Line wrapping of the generated code depends on the package name, so the new module is formatted once.
"$root/mvnw" -q -B -f "$root/pom.xml" -pl "services/$name" spotless:apply

connector="$root/infra/compose/connect/connectors/outbox-$name.json"
sed -e "s/__DB__/$db/" -e "s/__SHORT__/$short/" -e "s/__SLOT__/$db_base/" \
    "$root/infra/compose/connect/outbox-connector.json.template" > "$connector"
echo "Created ${connector#"$root/"}"

compose=(docker compose -f "$root/infra/compose/compose.yaml")
if "${compose[@]}" ps --status running postgres 2>/dev/null | grep -q postgres; then
    echo "Creating $db and $role in the running PostgreSQL"
    "${compose[@]}" exec -T postgres psql -U postgres -v ON_ERROR_STOP=0 <<SQL
CREATE ROLE $role LOGIN PASSWORD '$role';
CREATE DATABASE $db OWNER $role;
REVOKE CONNECT ON DATABASE $db FROM PUBLIC;
GRANT CONNECT ON DATABASE $db TO debezium;
SQL
    echo "Creating the sample topic"
    "${compose[@]}" exec -T kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 \
        --create --if-not-exists --topic "toefly.$base.item-created.v1" --partitions 3 --replication-factor 1
    for suffix in retry dlq; do
        "${compose[@]}" exec -T kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 \
            --create --if-not-exists --topic "toefly.$base.item-created.v1.$suffix" --partitions 1 --replication-factor 1
    done
fi

cat <<NEXT

services/$name is ready (port $port, database $db).
  Build and test:  ./mvnw -pl services/$name -am verify
  Run:             ./mvnw -pl services/$name spring-boot:run -Dspring-boot.run.profiles=local
  Register its outbox connector after the first start (Flyway creates the publication):
                   docker compose -f infra/compose/compose.yaml run --rm connect-init
NEXT
