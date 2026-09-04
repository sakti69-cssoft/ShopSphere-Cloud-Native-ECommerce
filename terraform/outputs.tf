output "instance_id" { value = module.compute.instance_id }
output "public_ip" { value = aws_eip.shopsphere.public_ip }
output "elastic_ip" { value = aws_eip.shopsphere.public_ip }
output "public_dns" { value = module.compute.public_dns }
output "public_ports" { value = module.security.public_ingress_ports }
output "vpc_id" { value = module.networking.vpc_id }
output "security_group_id" { value = module.security.security_group_id }
