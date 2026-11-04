#!/usr/bin/env bash
#
# Uso: ./scripts/bootstrap-ci.sh [staging|production]
#
# Concede a SA do GitHub Actions os papeis que o terraform-cd.yml precisa para
# aplicar esta infraestrutura, mais acesso ao bucket do state. Rode uma vez por
# ambiente, com uma conta owner do projeto.
#
# E o unico passo que o proprio pipeline nao pode fazer: e ele que da ao
# pipeline o direito de rodar. Tudo depois disto esta nos workflows.
#
# E idempotente: add-iam-policy-binding nao duplica binding existente.
set -euo pipefail

AMBIENTE="${1:-staging}"
RAIZ="$(cd "$(dirname "$0")/.." && pwd)"
TFVARS="$RAIZ/terraform/environments/${AMBIENTE}.tfvars"
TFBACKEND="$RAIZ/terraform/environments/${AMBIENTE}.gcs.tfbackend"

[[ -f "$TFVARS" ]] || { echo "ERRO: $TFVARS nao existe" >&2; exit 1; }

ler() { sed -n "s/^$2 *= *\"\(.*\)\"/\1/p" "$1"; }

PROJECT="$(ler "$TFVARS" project_id)"
CI_ID="$(ler "$TFVARS" ci_service_account_id)"
BUCKET="$(ler "$TFBACKEND" bucket)"
REGION="$(ler "$TFVARS" region)"
# A VPC default tem uma subnet de mesmo nome por regiao, que e a que o
# cd-homolog.yml e o cd-prod.yml passam em --subnet.
SUBNET="$(ler "$TFVARS" network)"

case "$PROJECT" in
  ""|PREENCHER*) echo "ERRO: preencha project_id em $TFVARS" >&2; exit 1 ;;
esac
case "$CI_ID" in
  ""|PREENCHER*) echo "ERRO: preencha ci_service_account_id em $TFVARS" >&2; exit 1 ;;
esac

SA="${CI_ID}@${PROJECT}.iam.gserviceaccount.com"

# Um papel por recurso que o apply cria. Nenhum deles e editor nem owner:
# o pipeline so alcanca o que esta descrito em terraform/.
PAPEIS=(
  roles/serviceusage.serviceUsageAdmin   # google_project_service
  roles/run.admin                        # servico do Cloud Run e seu IAM
  roles/artifactregistry.admin           # repositorio de imagens e seu IAM
  roles/secretmanager.admin              # jwt-signing-key, suas versoes e IAM
  roles/apigateway.admin                 # api, api config e gateway
  roles/iam.serviceAccountAdmin          # SAs auth-fn e auth-gateway
  roles/iam.serviceAccountUser           # actAs das duas SAs no deploy
  roles/resourcemanager.projectIamAdmin  # bindings de projeto (logs, run, cloudsql)
  roles/compute.networkViewer            # data google_compute_network
)

echo "Projeto: $PROJECT"
echo "SA:      $SA"
echo

for PAPEL in "${PAPEIS[@]}"; do
  echo "  + ${PAPEL}"
  gcloud projects add-iam-policy-binding "$PROJECT" \
    --member="serviceAccount:${SA}" --role="$PAPEL" \
    --condition=None --quiet --no-user-output-enabled
done

# O state e lido tambem pelo CD (output issuer e db_url) e pelo plan do CI.
if [[ -n "$BUCKET" ]]; then
  echo "  + roles/storage.objectAdmin em gs://${BUCKET}"
  gcloud storage buckets add-iam-policy-binding "gs://${BUCKET}" \
    --member="serviceAccount:${SA}" --role=roles/storage.objectAdmin \
    --quiet --no-user-output-enabled
fi

# Direct VPC egress: o deploy anexa o servico a subnet, e isso exige
# compute.subnetworks.use. Escopo de subnet, nao de projeto -- o pipeline nao
# precisa alcancar o resto da rede. Sem este binding o primeiro deploy falha
# por permissao na subnet.
if [[ -n "$REGION" && -n "$SUBNET" ]]; then
  echo "  + roles/compute.networkUser na subnet ${SUBNET}/${REGION}"
  gcloud compute networks subnets add-iam-policy-binding "$SUBNET"     --project="$PROJECT" --region="$REGION"     --member="serviceAccount:${SA}" --role=roles/compute.networkUser     --quiet --no-user-output-enabled
fi

echo
echo "Pronto. Falta apenas o secret GCP_SA_KEY no Environment ${AMBIENTE} do GitHub:"
echo "  gcloud iam service-accounts keys create chave.json --iam-account=${SA}"
