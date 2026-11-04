data "google_project" "this" {}

data "google_service_account" "ci" {
  account_id = var.ci_service_account_id
}

resource "google_service_account" "fn" {
  account_id   = "auth-fn"
  display_name = "Runtime do serviço de autenticação"
}

resource "google_service_account" "gateway" {
  account_id   = "auth-gateway"
  display_name = "API Gateway → serviço de autenticação"
}

resource "google_secret_manager_secret_iam_member" "fn_jwt" {
  secret_id = google_secret_manager_secret.jwt.id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.fn.email}"
}

resource "google_secret_manager_secret_iam_member" "fn_db" {
  secret_id = data.google_secret_manager_secret.db_auth.id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.fn.email}"
}

resource "google_project_iam_member" "fn_logs" {
  project = var.project_id
  role    = "roles/logging.logWriter"
  member  = "serviceAccount:${google_service_account.fn.email}"
}

resource "google_project_iam_member" "ci" {
  for_each = toset([
    "roles/run.admin",
  ])
  project = var.project_id
  role    = each.value
  member  = "serviceAccount:${data.google_service_account.ci.email}"
}

# Push das imagens no repositório do serviço, sem dar acesso ao projeto inteiro.
resource "google_artifact_registry_repository_iam_member" "ci_push" {
  location   = google_artifact_registry_repository.auth.location
  repository = google_artifact_registry_repository.auth.name
  role       = "roles/artifactregistry.writer"
  member     = "serviceAccount:${data.google_service_account.ci.email}"
}

resource "google_service_account_iam_member" "ci_act_as_fn" {
  service_account_id = google_service_account.fn.name
  role               = "roles/iam.serviceAccountUser"
  member             = "serviceAccount:${data.google_service_account.ci.email}"
}

# Leitura das duas senhas pelo workflow db-grants: a do oficina_app, que roda o
# sql/auth_ro.sql como dono das views, e a do próprio auth_ro, que o script
# grava no CREATE ROLE.
resource "google_secret_manager_secret_iam_member" "ci_db_auth" {
  secret_id = data.google_secret_manager_secret.db_auth.id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${data.google_service_account.ci.email}"
}

resource "google_secret_manager_secret_iam_member" "ci_db_app" {
  secret_id = data.google_secret_manager_secret.db_app.id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${data.google_service_account.ci.email}"
}

# O db-grants liga a instância antes de aplicar os GRANTs e a devolve ao estado
# original depois; sem isso o psql não conecta com o banco desligado.
resource "google_project_iam_member" "ci_cloudsql" {
  project = var.project_id
  role    = "roles/cloudsql.admin"
  member  = "serviceAccount:${data.google_service_account.ci.email}"
}
