variable "subnet_id" { type = string }
variable "ami_id" { type = string }
variable "security_group_id" { type = string }
variable "instance_profile" { type = string }
variable "instance_type" { type = string }
variable "root_volume_size" { type = number }
variable "name" { type = string }
variable "user_data" { type = string }
variable "key_name" {
  type     = string
  nullable = true
}
resource "aws_instance" "this" {
  ami                         = var.ami_id
  instance_type               = var.instance_type
  subnet_id                   = var.subnet_id
  vpc_security_group_ids      = [var.security_group_id]
  associate_public_ip_address = true
  iam_instance_profile        = var.instance_profile
  key_name                    = var.key_name
  monitoring                  = false
  user_data                   = var.user_data
  user_data_replace_on_change = true
  metadata_options {
    http_endpoint               = "enabled"
    http_tokens                 = "required"
    http_put_response_hop_limit = 1
  }
  dynamic "credit_specification" {
    for_each = startswith(var.instance_type, "t") ? [1] : []
    content {
      cpu_credits = "standard"
    }
  }
  root_block_device {
    volume_type           = "gp3"
    volume_size           = var.root_volume_size
    encrypted             = true
    delete_on_termination = true
  }
  tags = { Name = var.name }
}
output "instance_id" { value = aws_instance.this.id }
output "public_ip" { value = aws_instance.this.public_ip }
output "public_dns" { value = aws_instance.this.public_dns }
