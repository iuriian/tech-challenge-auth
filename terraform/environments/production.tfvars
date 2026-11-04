# Produção precisa de um projeto GCP próprio: o Cloud SQL de homologação vive na
# VPC default deste projeto, e o peering do servicenetworking é único por VPC —
# a mesma restrição documentada no production.tfvars do tech-challenge-database.
#
# PREENCHER: os valores abaixo vêm do repositório de infra aplicado em produção.

project_id            = "PREENCHER-projeto-producao"
region                = "us-central1"
network               = "default"
ci_service_account_id = "PREENCHER-sa-ci-de-producao"

database_state_bucket = "PREENCHER-bucket-de-state-de-producao"
database_state_prefix = "tech-challenge/database/production"
