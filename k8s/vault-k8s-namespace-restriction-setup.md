# 🛡️ Vault Setup in Kubernetes — Restrict Secret Access per Namespace

This guide shows how to set up **HashiCorp Vault** in Kubernetes using the **Vault Helm chart** and **Vault Agent Injector (sidecar)** so that **each namespace can only access its own secrets**, and each team can log in to the **Vault UI using credentials created directly in the UI**.

---

## 🎯 Goal

- Deploy Vault with the Vault Agent Injector  
- Enable **Userpass authentication** for Vault UI login per team  
- Create namespace-specific Vault policies and roles  
- Deploy sample pods per namespace  
- Teams using their own login credentials can login in the Vault UI  
- Verify that cross-namespace secret access is blocked

---

## 🧩 Prerequisites

- Kubernetes cluster (Docker Desktop, Kind, or Minikube)
- `kubectl` and `helm` installed
- Internet connectivity to fetch Helm charts

---

## ⚙️ Step 1: Install Vault via Helm

```bash
helm repo add hashicorp https://helm.releases.hashicorp.com
helm repo update

kubectl create namespace vault

helm install vault hashicorp/vault -n vault \
  --set "server.dev.enabled=true" \
  --set "server.dev.devRootToken=root-token@123" \
  --set "injector.enabled=true" \
  --set "ui.enabled=true" \
  --set "ui.serviceType=NodePort" \
  --set "ui.serviceNodePort=30000"
```

> ⚠️ `server.dev.enabled=true` runs Vault in **development mode**.  
> For production, disable this and configure persistent storage and unseal keys.

Check pods

```bash
kubectl get pods -n vault
kubectl get svc -n vault
```

---

## 🧱 Step 2: Create Namespaces and Service Accounts

```bash
kubectl create ns team-a
kubectl create ns team-b

kubectl -n team-a create sa team-a-sa
kubectl -n team-b create sa team-b-sa
```

---

## 🔐 Step 3: Enable and Configure Authentication mechanism

Open a shell in the Vault pod:

```bash
kubectl exec -it vault-0 -n vault -- /bin/sh
```

Enable auth method:

```bash
vault auth enable userpass
vault auth enable kubernetes
```

Verify:

```bash
vault auth list
```

Expected output:

```table
path                type          description
----                ----          -----------
userpass/           userpass      User/Password based login
kubernetes/         kubernetes    K8s Service Account based login
token/              token         token based credentials
```

Configure Kubernetes Auth :

Vault must know how to communicate with your Kubernetes API server.

```bash
vault write auth/kubernetes/config \
    kubernetes_host="https://$KUBERNETES_PORT_443_TCP_ADDR:443" \
    token_reviewer_jwt="$(cat /var/run/secrets/kubernetes.io/serviceaccount/token)" \
    kubernetes_ca_cert=@/var/run/secrets/kubernetes.io/serviceaccount/ca.crt
```

---

## 📜 Step 4: Create Policies per Namespace

Each policy restricts access to only that namespace’s secrets.

```bash
vault policy write team-a-policy - <<EOF
# Allow team-a full access to their own secrets
path "secret/data/team-a/*" {
  capabilities = ["create", "read", "update", "delete", "list"]
}

# Allow metadata operations (needed for UI listing and deletion)
path "secret/metadata/team-a/*" {
  capabilities = ["read", "list", "delete"]
}

# Allow listing the secret mount itself (UI needs this to render the folder)
path "secret/metadata" {
  capabilities = ["list"]
}
EOF

vault policy write team-b-policy - <<EOF
# Allow team-b full access to their own secrets
path "secret/data/team-b/*" {
  capabilities = ["create", "read", "update", "delete", "list"]
}
# Allow metadata operations (needed for UI listing and deletion)
path "secret/metadata/team-b/*" {
  capabilities = ["read", "list", "delete"]
}

# Allow listing the secret mount itself (UI needs this to render the folder)
path "secret/metadata" {
  capabilities = ["list"]
}
EOF
```

---

## **Step 5: Create Kubernetes Roles**\

### Team A Role

```bash
vault write auth/kubernetes/role/team-a-role \
  bound_service_account_names=team-a-sa \
  bound_service_account_namespaces=team-a  \
  policies=team-a-policy  \
  audience="https://kubernetes.default.svc.cluster.local" \
  ttl=24h
```

### Team B Role

```bash
vault write auth/kubernetes/role/team-b-role  \
  bound_service_account_names=team-b-sa  \
  bound_service_account_namespaces=team-b \
  policies=team-b-policy  \
  audience="https://kubernetes.default.svc.cluster.local" \
  ttl=24h
```

Verify:

```bash
vault read auth/kubernetes/role/team-a-role
vault read auth/kubernetes/role/team-b-role
```

## 🗝️ Step 6: Store Secrets in Vault

Inside the Vault pod shell.
This can be done from UI by each team.

```bash
vault kv put secret/team-a/app username="a_user" password="a_pass"
vault kv put secret/team-b/app username="b_user" password="b_pass"
```

---

## 🚀 Step 7: Deploy Example Applications

### 🟢 team-a pod

Save the following as `team-a-pod.yaml`:

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: app
  namespace: team-a
  annotations:
    vault.hashicorp.com/agent-inject: "true"
    vault.hashicorp.com/role: "team-a-role"
    vault.hashicorp.com/agent-inject-secret-config: "secret/data/team-a/app"
spec:
  serviceAccountName: team-a-sa
  containers:
  - name: team-a-app
    image: busybox
    command: ["sleep", "3600"]
```

Apply it:

```bash
kubectl apply -f team-a-pod.yaml
```

---

### 🔵 team-b pod

Save the following as `team-b-pod.yaml`:

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: app
  namespace: team-b
  annotations:
    vault.hashicorp.com/agent-inject: "true"
    vault.hashicorp.com/role: "team-b-role"
    vault.hashicorp.com/agent-inject-secret-config: "secret/data/team-b/app"
spec:
  serviceAccountName: team-b-sa
  containers:
  - name: team-b-app
    image: busybox
    command: ["sleep", "3600"]
```

Apply it:

```bash
kubectl apply -f team-b-pod.yaml
```

---

## ✅ Step 8: Verify Secret Injection

Check the injected secret for each pod:

```bash
kubectl exec -n team-a team-a-app  -c team-a-app -- cat /vault/secrets/config
```

Expected output:

```sh
data: map[password:a_pass username:a_user]
```

And for `team-b`:

```bash
kubectl exec -n team-b team-b-app  -c team-b-app -- cat /vault/secrets/config
```

Expected output:

```sh
data: map[password:b_pass username:b_user]
```

If you modify annotations to point to another namespace’s path  
(e.g., `secret/data/team-b/app` in `team-a-pod.yaml`),
delete the deployment and redeploy again,
`team-a-app` pod will be in `Init` status, will not go to `Running` status,
because it can not access secrets.

---

## **Step 9: Setup Userpass for UI Login**

### Create Team Users

```bash
vault write auth/userpass/users/team-a-user password="TeamA@123" policies="team-a-policy"
vault write auth/userpass/users/team-b-user password="TeamB@123" policies="team-b-policy"
```

### Create Admin User

---

Create an admin user to allow teams to create users via UI:
Create admin policy - should allow user creation:

```bash
vault policy write admin-policy - <<EOF
# Full access to secrets for all teams
path "secret/*" {
  capabilities = ["create", "read", "update", "delete", "list"]
}

# Allow managing auth methods and users (Userpass, etc.)
path "auth/*" {
  capabilities = ["create", "read", "update", "delete", "list", "sudo"]
}

# Allow managing policies (required for editing policies in UI)
path "sys/policies/acl/*" {
  capabilities = ["create", "read", "update", "delete", "list", "sudo"]
}

# Allow viewing and managing system configuration
path "sys/mounts/*" {
  capabilities = ["create", "read", "update", "delete", "list", "sudo"]
}

# Allow listing and reading enabled secrets engines
path "sys/mounts" {
  capabilities = ["read", "list"]
}

# Allow managing authentication backends
path "sys/auth/*" {
  capabilities = ["create", "read", "update", "delete", "list", "sudo"]
}

# Allow listing auth backends
path "sys/auth" {
  capabilities = ["read", "list"]
}

# Allow managing entities and aliases (identity)
path "identity/*" {
  capabilities = ["create", "read", "update", "delete", "list", "sudo"]
}

# Allow admin to read Vault system info
path "sys/*" {
  capabilities = ["read", "list"]
}
EOF
```

Create admin user:

```bash
vault write auth/userpass/users/admin \
    password="AdminPassword123" \
    policies="admin-policy"
```

---

## **Step 10: ✅ Validation

Vault web UI is now accessible at [http://127.0.0.1:30000](http://127.0.0.1:30000) with token `root-token@123`.

- Login to UI(Select userpasss from dropdown) as `team-a-user` and password `TeamA@123` → You can see only secrets under `secret/team-a/`
- Login to UI(Select userpasss from dropdown) as `team-b-user` and password `TeamB@123` → You can see only secrets under `secret/team-b/`
- Login to UI(Select userpasss from dropdown) as `admin` and password `AdminPassword123` → Can manage users and policies

---

## 🔎 Step 11: Vault UI Login for Teams (UI-Created Credentials)

- Log in to Vault UI as admin (Userpass auth).
- Navigate to Access → Authentication → Userpass → Create User.
- Teams login with their own users and passwords

---

## 🧹 Cleanup

```bash
kubectl delete -f team-a-pod.yaml
kubectl delete -f team-b-pod.yaml
helm uninstall vault -n vault
kubectl delete ns vault team-a team-b
```

---

## 📘 Summary

| Namespace | Vault Role | Policy | Secret Path |
|------------|-------------|--------|--------------|
| team-a | `team-a-role` | `team-a-policy` | `secret/data/team-a/*` |
| team-b | `team-b-role` | `team-b-policy` | `secret/data/team-b/*` |

✅ Each namespace is isolated  
🚫 No cross-namespace secret access  
🔒 Least privilege enforced by Vault

---

**Author:** Ashutosh  
**Purpose:** Secure namespace-based secret isolation using Vault + Kubernetes.
