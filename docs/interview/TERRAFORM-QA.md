# ShopSphere Terraform Interview Q&A

## Why use Terraform for ShopSphere?

Terraform expresses the network, security, IAM, compute, storage settings, and stable IP as a reviewable dependency graph. It also records which real resource each address owns in state. The strongest project example is not merely that Terraform created resources; it is that plan review detected an unintended EC2 replacement before it happened.

## What do `init`, `fmt`, `validate`, `plan`, and `apply` do?

- `terraform init` installs providers/modules and initializes the selected backend. `-backend=false` is useful for source validation but does not load production state.
- `terraform fmt -check -recursive` checks canonical source formatting.
- `terraform validate` checks configuration and provider-schema consistency; it does not prove AWS permissions, cost, or a safe real-world diff.
- `terraform plan` refreshes/compares configuration and state and previews actions. It is the mandatory safety review.
- `terraform apply` mutates infrastructure. It requires explicit authorization and must apply the exact reviewed plan when change control requires that.

## What is Terraform state?

State maps Terraform resource addresses such as `module.compute.aws_instance.this` to real AWS IDs and stores attributes needed for diffing. Losing or selecting the wrong state can lead Terraform to propose duplicates; stale state can hide or misinterpret drift. State may contain sensitive values, so `.tfstate`, backups, real tfvars, and plan files are Git-ignored and should use a protected remote backend/locking solution in a larger environment.

## What modules does this project have?

- `networking`: VPC, edge subnet, Internet Gateway, route table/association, and closed default SG.
- `security`: edge SG, public HTTP/HTTPS rules, optional single-IPv4 SSH rule, and HTTPS egress.
- `iam`: EC2 role, SSM managed-policy attachment, and instance profile.
- `compute`: pinned AMI, instance type, SG/profile, user data, IMDSv2, encrypted gp3 root disk, and conditional T-family credit settings.
- Root configuration also associates an Elastic IP with the existing instance.

## What was the AMI drift incident?

The compute module originally used an Amazon-owned Amazon Linux 2023 data source with `most_recent = true`. The running instance used `ami-090d68841c2a28756`; later resolution returned a newer AMI. Because EC2 AMI is replacement-forcing, a plan for unrelated HTTPS networking proposed replacing production. The plan was stopped. The source now accepts an `ami_id` variable defaulted to the reviewed current AMI, with format validation. Updating the AMI is therefore a deliberate replacement decision.

## Why was pinning better than `ignore_changes = [ami]`?

Pinning makes desired state explicit and keeps a future AMI upgrade visible. `ignore_changes` would suppress the mismatch indefinitely and could conceal an important security/lifecycle decision. Lifecycle ignores are useful only when another clearly defined owner controls that attribute and the drift contract is intentional.

## Why can user data replace EC2?

The resource sets `user_data_replace_on_change = true`, which is appropriate for immutable bootstrap semantics but dangerous if any rendered input changes unintentionally. ShopSphere's `repository_ref` is therefore a required validated 40-character SHA passed dynamically at plan time. The real SHA is not hardcoded into tracked Terraform, avoiding a circular commit dependency. Before planning, use the value compatible with the current state unless an explicitly approved replacement is intended.

## Explain the circular SHA problem

If the deployment SHA were written into a tracked `.tf` file and committed, that commit would create a new SHA, making the embedded prior SHA stale. Instead, configuration declares `repository_ref`; after pushing, an operator runs `git rev-parse HEAD` and passes that value with `-var`. The example tfvars uses a fake 40-character value only.

## What did the HTTPS infrastructure plan add?

It added an `aws_eip.shopsphere` associated with the existing instance and a TCP 443 ingress rule on the existing edge security group. The safe reviewed plan preserved EC2, root EBS, VPC, subnet, route, IAM, and HTTP. Application certificate/Nginx changes were handled separately on the same host.

## Why can a plan have a change without source edits?

Terraform refresh can discover out-of-band changes, provider normalization, data-source updates, default changes, or a different variable/backend/workspace. That is why “I only edited an output” is not enough. I review every action, replacement marker, and known-after-apply value against actual AWS IDs.

## What is drift?

Drift is a difference between desired configuration/state and the real provider object. Some drift is accidental manual change; some comes from dynamic inputs or provider behavior. The response is to identify ownership and intent—not immediately apply. For ShopSphere, a public port or EC2/EBS replacement is a stop-and-investigate condition.

## Why save a plan file?

A saved plan lets apply execute the exact reviewed actions instead of recomputing a potentially different diff. It can become stale if state changes after planning, and it can contain sensitive values. ShopSphere ignores `*.tfplan`; saved plans are local change artifacts, never source artifacts.

## What does a stale-plan error mean?

The state changed after the plan was created, so Terraform refuses to apply it. That safety feature prevents applying assumptions that no longer match reality. The correct response is to stop, inspect who/what changed state, generate a fresh plan, review it, and seek approval again—not bypass the check.

## How are variables validated?

Examples include a regex for AMI IDs, an exact 40-character SHA for `repository_ref`, a bounded 30–100 GiB root size, and an optional SSH CIDR constrained to a single IPv4 `/32`. Validation catches malformed intent early; it does not prove an AMI exists in the selected region or that an IP belongs to an approved operator.

## How is SSH disabled?

`ssh_cidr` defaults to `null`, and the SSH ingress resource uses `count = 0` in that case. If supplied, validation permits only a `/32`; the project still prefers SSM. `key_name` is also nullable. The production audit confirms no public TCP 22 ingress.

## How is storage protected?

The compute module configures encrypted gp3 with a 40 GiB default. It currently declares `delete_on_termination = true`, so terminating the instance would delete its root disk. That is why replacement/termination is a hard stop and why EBS is not treated as a backup. Docker named volumes persist across normal Compose operations and EC2 stop/start, not across root-volume deletion.

## Why are T3 credits configured conditionally?

The compute module adds `credit_specification { cpu_credits = "standard" }` only for instance types beginning with `t`. That avoids T Unlimited surplus-credit risk for portfolio T-family instances while remaining valid for the current `m7i-flex.large`, which does not use T CPU credits.

## What Terraform outputs are appropriate?

Operational identifiers such as instance ID, stable public IP/DNS, and public ports are useful. Passwords, tokens, private keys, or full rendered user data should not be outputs. Even non-sensitive outputs must be checked against the right state/backend.

## How do you review a ShopSphere plan?

1. Confirm AWS identity, region, directory, branch, SHA, and backend/state.
2. Confirm current real EC2/EBS/EIP/SG identifiers.
3. Pass the state-compatible `repository_ref` dynamically.
4. Inspect the plan summary and every resource action.
5. Search for replacement, destroy, AMI, user data, root block device, and ingress changes.
6. Confirm cost and security effects.
7. Apply only with explicit scope/approval, then verify AWS, containers, public health, and data.

The normal desired steady-state result is `0 to add, 0 to change, 0 to destroy`. A no-op plan is evidence only for the selected state, provider inputs, and time of execution.

## What would you improve?

Use a protected encrypted remote state backend with locking and separate environment/account boundaries; add policy checks for public ingress, encryption, IMDSv2, and replacements; require reviewed plans as CI artifacts; and implement an explicit AMI bake/upgrade process. These are roadmap improvements, not claims about the current account.
