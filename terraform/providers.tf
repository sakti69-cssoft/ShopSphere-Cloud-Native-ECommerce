provider "aws" {
  region = var.region
  default_tags {
    tags = { Project = "ShopSphere", ManagedBy = "Terraform", Environment = var.environment }
  }
}
