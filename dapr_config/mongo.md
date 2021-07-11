# Mongo

```shell
# When the repo isn't in your local repo list
helm repo add bitnami https://charts.bitnami.com/bitnami

# Get values
# Apply in a config folder
helm show values bitnami/mongodb > mongodb-values.yml
# Apply 
helm install dapr-mongodb bitnami/mongodb -f mongodb-values.yml --version 10.20.2
```

After installation the following message get shown(namespace might differ):

> NOTES:
> ** Please be patient while the chart is being deployed **
> 
> MongoDB(R) can be accessed on the following DNS name(s) and ports from within your cluster:
> 
>     dapr-mongodb.traffic-generation-apps.svc.cluster.local
> 
> To get the root password run:
> 
>     export MONGODB_ROOT_PASSWORD=$(kubectl get secret --namespace traffic-generation-apps dapr-mongodb -o jsonpath="{.data.mongodb-root-password}" | base64 --decode)
> 
> To connect to your database, create a MongoDB(R) client container:
> 
>     kubectl run --namespace traffic-generation-apps dapr-mongodb-client --rm --tty -i --restart='Never' --env="MONGODB_ROOT_PASSWORD=$MONGODB_ROOT_PASSWORD" --image docker.io/bitnami/mongodb:4.4.6-debian-10-r26 --command -- bash
> 
> Then, run the following command:
> mongo admin --host "dapr-mongodb" --authenticationDatabase admin -u root -p $MONGODB_ROOT_PASSWORD
> 
> To connect to your database from outside the cluster execute the following commands:
> 
>     kubectl port-forward --namespace traffic-generation-apps svc/dapr-mongodb 27017:27017 &
>     mongo --host 127.0.0.1 --authenticationDatabase admin -p $MONGODB_ROOT_PASSWORD
