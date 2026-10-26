data "google_compute_network" "vpc" {
  name = var.network
}

resource "google_vpc_access_connector" "auth" {
  name          = "auth-connector"
  region        = var.region
  network       = data.google_compute_network.vpc.name
  ip_cidr_range = var.connector_cidr
  machine_type  = "e2-micro"
  min_instances = 2
  max_instances = 3

  depends_on = [google_project_service.apis]
}
