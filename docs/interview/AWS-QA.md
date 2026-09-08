# ShopSphere AWS Interview Q&A

## What AWS resources does ShopSphere use?

The Terraform design tracks a VPC, one public subnet, Internet Gateway and default route, a restrictive edge security group, an EC2 IAM role/profile for SSM, one EC2 instance, its encrypted gp3 root volume, and an Elastic IP. The verified production identifiers are region `ap-south-1`, instance `i-0e2cf0109097385b1`, EBS `vol-0ed9fa47e65c3fd84`, EIP `65.2.225.30`, and security group `sg-037cf17baa6b1070a`.

No NAT Gateway, ALB, EKS, RDS, ElastiCache, Route 53 hosted zone, CloudFront, WAF, or additional EC2 instance is part of this cost-conscious deployment.

## What is the difference between a VPC and a subnet?

A VPC is the regional network boundary and IP address space. A subnet is an Availability Zone-scoped slice of that address space where resources receive addresses. ShopSphere uses one edge subnet with a route to an Internet Gateway. This is economical but not highly available; a commercial design would split public/private tiers across multiple AZs.

## What makes a subnet public?

The subnet's route table has a route such as `0.0.0.0/0` to an Internet Gateway, and the instance has a routable public/EIP address. A route alone does not grant traffic: the security group and host/container listeners also must allow it.

## What does the Internet Gateway do?

It provides a route target between the VPC and the Internet for resources with public IPv4 addressing. It is horizontally available at the VPC level, but it does not perform application routing, TLS termination, or port authorization.

## What is a security group?

A security group is a stateful virtual firewall attached to the instance/network interface. ShopSphere's inbound policy allows TCP 80 and 443 from `0.0.0.0/0`. Return traffic for an allowed connection is automatically permitted. There is no public SSH, database, Docker daemon, microservice, or monitoring ingress.

## Why is port 80 still public after HTTPS?

Nginx uses port 80 for the ACME HTTP-01 challenge and redirects all normal requests to HTTPS. Removing it would break the chosen renewal method unless certificate validation moved to DNS-01 or another termination architecture.

## Why use an Elastic IP?

An automatically assigned public IPv4 can change after stop/start. The EIP keeps the DuckDNS A record and TLS hostname path stable. The trade-off is public IPv4 cost, including while the host is stopped depending on AWS pricing. A stable address is infrastructure, not a substitute for health checks.

## What is EBS, and how is it used here?

EBS is durable block storage attached to EC2. ShopSphere uses an encrypted 40 GiB gp3 root volume. Docker named volumes for MySQL, MongoDB, and Redis live on that filesystem, so they survive container recreation and instance stop/start. EBS remains a single failure domain and is not an off-host backup by itself.

## What happens on EC2 stop/start?

Compute stops and later boots on AWS hardware; RAM and running processes disappear. EBS persists. A normal ephemeral public IPv4 may change, but ShopSphere's associated EIP stays stable. Docker/systemd restart policies should restore services, after which I verify AWS checks, SSM, container health, public HTTPS, and persistent records. EBS and EIP can continue to incur charges while compute is stopped.

## Why `m7i-flex.large`?

The single host runs the frontend, Nginx, Gateway, five services, MySQL, MongoDB, and Redis. The verified host reports about 7.6 GiB RAM, which provides meaningful headroom for several JVMs and three stores. The instance type remains configurable. It is a portfolio sizing choice, not a claim that the workload has been capacity-tested for production scale.

## What is IAM's role?

The EC2 service assumes an instance role through an instance profile. The repository attaches AWS-managed `AmazonSSMManagedInstanceCore` so the agent can register and receive authorized commands. Human/GitHub access should use separate least-privilege principals. The configured future deploy role should allow only OIDC trust from the intended repository/environment and only the SSM actions needed for the fixed instance/document plus command-result reads.

## Why not store AWS keys in GitHub Secrets?

Long-lived access keys create rotation and leakage risk. The configured workflow uses GitHub OIDC to request short-lived role credentials with `id-token: write`. However, the trust policy/role and successful assumption are external prerequisites and have not yet been verified for ShopSphere, so I do not claim OIDC production delivery has run.

## What is SSM and why is it safer than public SSH here?

Systems Manager provides an authenticated control channel through the SSM agent and IAM. ShopSphere can keep port 22 closed and avoids distributing PEM files. SSM Session Manager supports shells/port forwarding; Run Command supports bounded deployment commands. Risks still include overbroad IAM, output containing secrets, and an offline agent, so target and output controls matter.

## What is IMDSv2?

The Instance Metadata Service supplies instance identity and role credentials. IMDSv2 requires a session token before metadata requests, reducing several SSRF-style credential theft risks compared with IMDSv1. Terraform sets `http_tokens = required` and hop limit 1. It is defense in depth, not a reason to trust arbitrary code on the host.

## How do you prove an instance is healthy?

- EC2 state `running` means compute is started.
- System status `ok` covers AWS host/platform reachability.
- Instance status `ok` covers guest network/OS reachability.
- SSM `Online` proves the management agent/channel.
- Docker/container health proves process-level dependencies.
- Public `/`, `/nginx-health`, and `/api/v1/products` prove the external path.
- Business-record checks prove the correct data is mounted.

No single signal proves all layers.

## Why no NAT Gateway or private application subnet?

The current design is one public EC2 edge host to minimize portfolio cost. A NAT Gateway would add a significant fixed/hourly and data-processing cost. The security group and Docker publishing policy limit ingress, but the host is still in a public subnet. A stronger multi-tier design would put application/data nodes in private subnets and justify NAT endpoints/egress architecture from requirements and budget.

## What are the main AWS cost risks?

- Running `m7i-flex.large` compute.
- 40 GiB gp3 EBS retained even while EC2 is stopped.
- Elastic/public IPv4 hourly charges.
- Data transfer and container/OS downloads.
- Snapshots or future off-host backups if added.

The project deliberately avoids ALB, NAT Gateway, managed database/cache, Kubernetes, WAF, CDN, and Route 53 hosted-zone costs. Avoiding cost does not make the single-host design highly available.

## How would you make it highly available?

Use at least two AZs, replace single-host state with managed multi-AZ stores or replicated systems, run stateless services behind an ALB, centralize secrets/logs/metrics, use autoscaling/orchestration, move TLS/DNS to managed production services, and regularly test off-host backup restore. That is an architectural migration, not something to apply casually to a cost-limited portfolio account.

## What should you verify before an AWS change?

Identity/account/region, current state and drift, exact resource IDs, blast radius, Terraform plan actions, replacement triggers, persistence/backup, cost, security exposure, rollback, and post-change checks. For ShopSphere, any unexpected EC2/EBS replacement or destruction is a stop condition.
