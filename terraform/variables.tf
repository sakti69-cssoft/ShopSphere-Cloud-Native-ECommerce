variable "region" {
  type    = string
  default = "ap-south-1"
}
variable "environment" {
  type    = string
  default = "portfolio-demo"
}
variable "instance_type" {
  type        = string
  default     = "m7i-flex.large"
  description = "Full JVM/database stack needs about 8 GiB; keep this configurable for future environments."
}
variable "root_volume_size" {
  type        = number
  default     = 40
  description = "Encrypted gp3 root volume size in GiB."
  validation {
    condition     = var.root_volume_size >= 30 && var.root_volume_size <= 100
    error_message = "Root volume size must be between 30 and 100 GiB."
  }
}
variable "repository_url" {
  type    = string
  default = "https://github.com/sakti69-cssoft/ShopSphere-Cloud-Native-ECommerce.git"
}
variable "repository_ref" {
  type        = string
  description = "Exact reviewed 40-character Git commit SHA deployed by bootstrap."
  validation {
    condition     = can(regex("^[0-9a-fA-F]{40}$", var.repository_ref))
    error_message = "repository_ref must be an exact 40-character Git commit SHA."
  }
}
variable "ssh_cidr" {
  type        = string
  default     = null
  nullable    = true
  description = "Optional operator IPv4 /32. Prefer SSM; never allow world SSH."
  validation {
    condition     = var.ssh_cidr == null ? true : can(regex("^([0-9]{1,3}\\.){3}[0-9]{1,3}/32$", var.ssh_cidr)) && can(cidrhost(var.ssh_cidr, 0))
    error_message = "SSH must be a valid single IPv4 /32, or null."
  }
}
variable "key_name" {
  type     = string
  default  = null
  nullable = true
}
