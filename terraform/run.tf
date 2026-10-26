# O Terraform cria o serviço uma única vez, só para que o endereço exista e
# possa ser apontado no backend do API Gateway. Toda a configuração de runtime
# (imagem, variáveis, secrets, VPC, escala) pertence ao deploy no GitHub
# Actions — por isso o template inteiro está em ignore_changes, e a primeira
# revisão sobe com a imagem de bootstrap até o primeiro deploy do pipeline.
#
# Essa divisão evita o ciclo ISSUER → hostname do gateway → backend do gateway
# → URL do serviço, que impediria descrever tudo num apply só.
resource "google_cloud_run_v2_service" "auth" {
  name     = var.service_name
  location = var.region

  # Sem acesso anônimo: quem chama é o API Gateway, autenticado por IAM.
  ingress             = "INGRESS_TRAFFIC_ALL"
  deletion_protection = false

  template {
    service_account = google_service_account.fn.email

    containers {
      image = var.bootstrap_image
    }
  }

  lifecycle {
    ignore_changes = [template, client, client_version, labels, annotations]
  }

  depends_on = [google_project_service.apis]
}

resource "google_cloud_run_v2_service_iam_member" "gateway_invoker" {
  name     = google_cloud_run_v2_service.auth.name
  location = google_cloud_run_v2_service.auth.location
  role     = "roles/run.invoker"
  member   = "serviceAccount:${google_service_account.gateway.email}"
}
