# gs-spring-boot-hashi-vault

## Run application

- Refer [vault-k8s-namespace-restriction-setup.md](k8s/vault-k8s-namespace-restriction-setup.md) to setup Vault in
  Kubernetes
- Set the secrets

```shell
kubectl exec -it vault-0 -n vault -- /bin/sh
vault kv put secret/team-a/github github.oauth2.key=foobar
```

- Run the application

```shell
gradle clean bootRun
```
Console output
 
```text
2025-12-01T00:17:21.285+05:30  INFO 20540 --- [gs-spring-boot-hashi-vault] [           main] i.a.p.GsSpringBootHashiVaultApplication  : Value of github.oauth2.key
2025-12-01T00:17:21.285+05:30  INFO 20540 --- [gs-spring-boot-hashi-vault] [           main] i.a.p.GsSpringBootHashiVaultApplication  : foobar
2025-12-01T00:17:21.320+05:30  INFO 20540 --- [gs-spring-boot-hashi-vault] [           main] i.a.p.GsSpringBootHashiVaultApplication  : Encrypted value
2025-12-01T00:17:21.320+05:30  INFO 20540 --- [gs-spring-boot-hashi-vault] [           main] i.a.p.GsSpringBootHashiVaultApplication  : vault:v1:hQvviKQxNS8FSOgYTqujxopCg5nrXGWjKwdcmm5xNKJAVby500Ryl18L
2025-12-01T00:17:21.323+05:30  INFO 20540 --- [gs-spring-boot-hashi-vault] [           main] i.a.p.GsSpringBootHashiVaultApplication  : Decrypted value
2025-12-01T00:17:21.323+05:30  INFO 20540 --- [gs-spring-boot-hashi-vault] [           main] i.a.p.GsSpringBootHashiVaultApplication  : Secure message
```

## Reference

- [Spring Boot - Accessing Vault](https://spring.io/guides/gs/accessing-vault)
- [Encrypt Spring application data with Vault](https://developer.hashicorp.com/vault/tutorials/encryption-as-a-service/eaas-spring-demo)