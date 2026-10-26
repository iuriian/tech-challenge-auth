resource "google_api_gateway_api" "auth" {
  provider     = google-beta
  api_id       = "oficina-auth"
  display_name = "Oficina — autenticação"

  depends_on = [google_project_service.apis]
}

resource "google_api_gateway_api_config" "auth" {
  provider             = google-beta
  api                  = google_api_gateway_api.auth.api_id
  api_config_id_prefix = "auth-"

  openapi_documents {
    document {
      path = "openapi.yaml"
      contents = base64encode(templatefile("${path.module}/openapi.yaml.tftpl", {
        service_url = google_cloud_run_v2_service.auth.uri
      }))
    }
  }

  gateway_config {
    backend_config {
      google_service_account = google_service_account.gateway.email
    }
  }

  lifecycle {
    create_before_destroy = true
  }
}

resource "google_api_gateway_gateway" "auth" {
  provider   = google-beta
  region     = var.region
  gateway_id = "oficina-auth-gw"
  api_config = google_api_gateway_api_config.auth.id
}

resource "google_project_service" "gateway_managed_service" {
  service            = google_api_gateway_api.auth.managed_service
  disable_on_destroy = false
}
