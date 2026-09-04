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
  ami_id            = var.ami_id
  subnet_id         = module.networking.public_subnet_id
  security_group_id = module.security.security_group_id
  instance_profile  = module.iam.instance_profile
  instance_type     = var.instance_type
  key_name          = var.key_name
  root_volume_size  = var.root_volume_size
  name              = "shopsphere-${var.environment}"
  user_data = templatefile("${path.module}/user-data.sh.tftpl", {
    repository_url = var.repository_url
    repository_ref = var.repository_ref
  })
}

resource "aws_eip" "shopsphere" {
  domain   = "vpc"
  instance = module.compute.instance_id

  tags = {
    Name = "shopsphere-${var.environment}"
  }

  depends_on = [module.networking]
}
