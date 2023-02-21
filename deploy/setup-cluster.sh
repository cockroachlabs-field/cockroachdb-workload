#!/bin/bash
# Cluster setup using roachprod.
# For setting up roachprod, see:
# https://github.com/cockroachdb/cockroach/tree/master/pkg/cmd/roachprod
#
# To reset without destroying/recreating cluster:
# roachprod run $CLUSTER "sudo killall -9 cockroach"
# roachprod wipe $CLUSTER --preserve-certs

cloud="aws"
nodes="1-3"
client="4"
mode="insecure"

if [ "${cloud}" = "aws" ]; then
region=eu-central-1
roachprod create "$CLUSTER" --clouds=aws --aws-machine-type-ssd=c5d.4xlarge --geo --local-ssd --nodes=4 \
--aws-zones=\
eu-central-1a,\
eu-central-1b,\
eu-central-1c,\
eu-central-1a
fi

if [ "${cloud}" = "gce" ]; then
region=europe-west3
roachprod create "$CLUSTER" --clouds=gce --gce-machine-type=n1-standard-16 --geo --local-ssd --nodes=4 \
--gce-zones=\
europe-west1-a,\
europe-west1-b,\
europe-west1-c,\
europe-west1-a
fi

if [ "${cloud}" = "azure" ]; then
region=westeurope
roachprod create "$CLUSTER" --clouds=azure --azure-machine-type=Standard_DS4_v2 --geo --local-ssd --nodes=4 \
--azure-locations=westeurope
fi

echo "----------------"
echo "Stage Binaries"
echo "----------------"

roachprod stage $CLUSTER release v22.2.5

echo "-----------------"
echo "Start Up Services"
echo "-----------------"

if [ "${mode}" = "secure" ]; then
  roachprod start $CLUSTER:$nodes --sequential --secure
  roachprod admin --open --ips $CLUSTER:1 --secure
else
  roachprod start $CLUSTER:$nodes --sequential
  roachprod admin --open --ips $CLUSTER:1
fi

echo "---------------------"
echo "Installing haproxy..."
echo "---------------------"

roachprod run ${CLUSTER}:$client 'sudo apt-get -qq update'
roachprod run ${CLUSTER}:$client 'sudo apt-get -qq install -y openjdk-17-jre-headless htop dstat haproxy'

if [ "${mode}" = "secure" ]; then
  roachprod run ${CLUSTER}:$client "./cockroach gen haproxy --certs-dir=certs --host $(roachprod ip $CLUSTER:1 --external) --locality=region=$region"
else
  roachprod run ${CLUSTER}:$client "./cockroach gen haproxy --insecure --host $(roachprod ip $CLUSTER:1 --external) --locality=region=$region"
fi

roachprod run ${CLUSTER}:$client 'nohup haproxy -f haproxy.cfg > /dev/null 2>&1 &'
roachprod put ${CLUSTER}:$client ../target/workload.jar

echo "---------------------"
echo "Creating database..."
echo "---------------------"

if [ "${mode}" = "secure" ]; then
  roachprod run ${CLUSTER}:1 './cockroach sql --certs-dir=certs --host=`roachprod ip $CLUSTER:1` -e "CREATE DATABASE workload"'
else
  roachprod run ${CLUSTER}:1 './cockroach sql --insecure --host=`roachprod ip $CLUSTER:1` -e "CREATE DATABASE workload"'
fi

echo "Public admin URLs of nodes:"
roachprod admin --ips $CLUSTER:$nodes

echo "Public admin URL of LB:"
roachprod admin --ips $CLUSTER:$client

echo "Cluster setup complete!"

roachprod admin --open --ips $CLUSTER:1

exit 0
