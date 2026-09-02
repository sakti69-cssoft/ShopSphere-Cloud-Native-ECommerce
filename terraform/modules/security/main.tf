variable "vpc_id" { type = string }
variable "ssh_cidr" {
  type     = string
  nullable = true
}
resource "aws_security_group" "edge" {
  name_prefix = "shopsphere-edge-"
  description = "Public web only; all databases and application ports stay internal"
  vpc_id      = var.vpc_id
}
resource "aws_vpc_security_group_ingress_rule" "web" {
  security_group_id = aws_security_group.edge.id
  description       = "Public HTTP ingress; TLS is not configured yet"
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "tcp"
  from_port         = 80
  to_port           = 80
}
resource "aws_vpc_security_group_ingress_rule" "ssh" {
  count             = var.ssh_cidr == null ? 0 : 1
  security_group_id = aws_security_group.edge.id
  description       = "Single operator SSH address"
  cidr_ipv4         = var.ssh_cidr
  ip_protocol       = "tcp"
  from_port         = 22
  to_port           = 22
}
resource "aws_vpc_security_group_egress_rule" "https" {
  security_group_id = aws_security_group.edge.id
  description       = "HTTPS package registries and SSM"
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
}
output "security_group_id" { value = aws_security_group.edge.id }
output "public_ingress_ports" { value = [80] }
