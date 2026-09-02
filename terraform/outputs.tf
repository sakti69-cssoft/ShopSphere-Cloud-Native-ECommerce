output "instance_id" { value = module.compute.instance_id }
output "public_ip" { value = module.compute.public_ip }
output "public_dns" { value = module.compute.public_dns }
output "vpc_id" { value = module.networking.vpc_id }
output "security_group_id" { value = module.security.security_group_id }
