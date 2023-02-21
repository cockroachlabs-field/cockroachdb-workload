# Cluster Deployment

The `setup-cluster.sh` scripts provisions a CockroachDB cluster in AWS
or GCE by using [roachprod](https://github.com/cockroachdb/cockroach/tree/master/pkg/cmd/roachprod), 
an internal testing tool by Cockroach Labs.

## Steps
    
Run the interactive script:

    ./setup-cluster.sh

Open the Admin UI:

    roachprod admin --open --ips $CLUSTER:1

SSH to client node (4)

    roachprod run $CLUSTER:4
