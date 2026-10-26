resource "google_secret_manager_secret" "jwt" {
  secret_id = "jwt-signing-key"

  replication {
    auto {}
  }

  depends_on = [google_project_service.apis]
}

data "google_secret_manager_secret" "db_auth" {
  secret_id = var.db_auth_secret_id
}