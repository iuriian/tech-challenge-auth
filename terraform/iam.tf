data "google_project" "this" {}

data "google_service_account" "ci" {
  account_id = var.ci_service_account_id
}

resource "google_service_account" "fn" {
  account_id   = "auth-fn"
  display_name = "Runtime da função de autenticação"
}

resource "google_service_account" "gateway" {
  account_id   = "auth-gateway"
  display_name = "API Gateway → função de autenticação"
}

resource "google_service_account" "build" {
  account_id   = "auth-build"
  display_name = "Cloud Build da função de autenticação"
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

resource "google_project_iam_member" "build" {
  for_each = toset([
    "roles/cloudbuild.builds.builder",
    "roles/logging.logWriter",
    "roles/artifactregistry.writer",
    "roles/storage.objectViewer",
  ])
  project = var.project_id
  role    = each.value
  member  = "serviceAccount:${google_service_account.build.email}"
}

resource "google_project_iam_member" "ci" {
  for_each = toset([
    "roles/cloudfunctions.developer",
    "roles/run.admin"
  ])
  project = var.project_id
  role    = each.value
  member  = "serviceAccount:${data.google_service_account.ci.email}"
}

resource "google_service_account_iam_member" "ci_act_as_fn" {
  service_account_id = google_service_account.fn.name
  role               = "roles/iam.serviceAccountUser"
  member             = "serviceAccount:${data.google_service_account.ci.email}"
}

resource "google_service_account_iam_member" "ci_act_as_build" {
  service_account_id = google_service_account.build.name
  role               = "roles/iam.serviceAccountUser"
  member             = "serviceAccount:${data.google_service_account.ci.email}"
}
