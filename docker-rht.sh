#!/bin/sh
CMD_BASE="$(readlink -f $0)" || CMD_BASE="$0"; CMD_BASE="$(dirname $CMD_BASE)"

IMAGE="m4ucorp/tools:reverse-http-tunnel.latest"

set -x
docker pull "$IMAGE"

if curl -fsL --connect-timeout 1 http://169.254.169.254/latest/meta-data/local-ipv4 >/dev/null; then
  log_config="--log-driver=awslogs --log-opt awslogs-group=/tools/reverse-http-tunnel --log-opt awslogs-stream=$(hostname)"
fi
#--log-opt awslogs-region=sa-east-1 \

exec docker run --name rht \
-p 1080:1080 -p 1081:1081 -p 9910:9910 -p 9911:9911 \
--dns=10.11.64.21 --dns=10.11.64.22 --dns-search=m4u.com.br \
-e JAVA_OPTS="\
-Dcom.sun.management.jmxremote \
-Dcom.sun.management.jmxremote.ssl=false \
-Dcom.sun.management.jmxremote.authenticate=false \
-Dcom.sun.management.jmxremote.port=9910 \
-Dcom.sun.management.jmxremote.rmi.port=9911 \
-Djava.rmi.server.hostname=$(curl -fsL --connect-timeout 1 http://169.254.169.254/latest/meta-data/local-ipv4 || hostname -i)" \
-d --restart=always \
$log_config \
"$IMAGE" -- -- "$@"
