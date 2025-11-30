# gs-spring-boot-hashi-vault

## Run application

- Refer [vault-k8s-namespace-restriction-setup.md](k8s/vault-k8s-namespace-restriction-setup.md) to setup Vault in Kubernetes
- Set the secrets
```shell
kubectl exec -it vault-0 -n vault -- /bin/sh
vault kv put secret/team-a/github github.oauth2.key=foobar
```

- Run the application
```shell
gradle clean bootRun
```