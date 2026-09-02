module "networking" {
  source = "./modules/networking"
  name   = "shopsphere-${var.environment}"
}
module "security" {
  source   = "./modules/security"
  vpc_id   = module.networking.vpc_id
  ssh_cidr = var.ssh_cidr
}
module "iam" {
  source = "./modules/iam"
  name   = "shopsphere-${var.environment}"
}
module "compute" {
  source            = "./modules/compute"
  subnet_id         = module.networking.public_subnet_id
  security_group_id = module.security.security_group_id
  instance_profile  = module.iam.instance_profile
  instance_type     = var.instance_type
  key_name          = var.key_name
}
