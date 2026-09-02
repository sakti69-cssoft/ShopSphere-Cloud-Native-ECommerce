variable "region" {
  type    = string
  default = "ap-south-1"
}
variable "environment" {
  type    = string
  default = "portfolio"
}
variable "instance_type" {
  type        = string
  default     = "t3.large"
  description = "Full JVM/database stack needs about 8 GiB. t3.small is not recommended for the full stack."
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
