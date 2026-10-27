resource "google_artifact_registry_repository" "auth" {
  location      = var.region
  repository_id = "oficina-auth"
  format        = "DOCKER"
  description   = "Imagens do serviço de autenticação"

  # Mantém as 10 imagens mais recentes (para rollback) e descarta o resto
  # depois de 30 dias. A regra KEEP tem precedência sobre a DELETE.
  cleanup_policies {
    id     = "manter-recentes"
    action = "KEEP"
    most_recent_versions {
      keep_count = 10
    }
  }

  cleanup_policies {
    id     = "descartar-antigas"
    action = "DELETE"
    condition {
      older_than = "2592000s"
    }
  }

  depends_on = [google_project_service.apis]
}

locals {
  image_repo = "${var.region}-docker.pkg.dev/${var.project_id}/${google_artifact_registry_repository.auth.repository_id}/${var.service_name}"
}
