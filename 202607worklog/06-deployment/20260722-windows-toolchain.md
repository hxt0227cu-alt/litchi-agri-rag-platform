# Windows toolchain installation - 2026-07-22

## Host

- OS: Windows 11 x64
- Physical memory: 16 GB
- Logical processors: 16
- WSL2 resource limit: 8 GB memory, 8 processors, 4 GB swap

## Installed and verified

| Tool | Version | Status |
| --- | --- | --- |
| Git | 2.55.0.windows.3 | Verified |
| Node.js | 22.23.1 | Verified |
| npm | 10.9.8 | Verified |
| Python | 3.11.9 | Verified |
| Temurin JDK | 17.0.19 | Verified |
| Maven | 3.9.11 | Verified |
| kubectl | 1.36.2 | Verified |
| Helm | 4.2.3 | Verified |
| k6 | 1.2.3 | Verified |

`JAVA_HOME` and `MAVEN_HOME` are configured as user environment variables. Tool directories are present in the user or system `PATH`. The running Codex process predates these changes, so it must be restarted before it inherits the updated `PATH`.

k6 was installed from the official portable release after the MSI installer encountered Windows Installer contention. The archive SHA-256 was verified as `2533ffd2aa641c0b92d2129634be6c707fa08a0fd3d7c77aed2dda8a2c062759` before extraction.

## WSL2 and container runtime

The Windows optional features `Microsoft-Windows-Subsystem-Linux` and `VirtualMachinePlatform` were enabled successfully by DISM, which returned exit code `3010`. This code requires a Windows restart before WSL2 can initialize.

Pending after restart:

1. Update WSL and set WSL2 as the default version.
2. Install and initialize Ubuntu 24.04 LTS.
3. Install Docker Engine CE and Docker Compose Plugin inside Ubuntu.
4. Add the Linux user to the `docker` group and verify non-root access.
5. Verify Docker and Compose with a disposable container.

Docker Desktop is intentionally not part of this environment.

## Post-restart continuation

- Hardware virtualization is active (`HypervisorPresent=True`).
- Both `Microsoft-Windows-Subsystem-Linux` and `VirtualMachinePlatform` report enabled.
- Ubuntu 24.04 LTS package `Canonical.Ubuntu.2404` version `2404.0.5.0` was installed successfully through winget with installer hash verification.
- The existing WSL 2.5.10 launcher failed with `Wsl/CallMsi/Install/REGDB_E_CLASSNOTREG`; re-registering the Appx launcher did not restore its missing MSI backend.
- The official WSL 2.7.10 x64 MSI was downloaded and reconstructed from byte ranges. Its length is `258605056`, SHA-256 is `1a62f90a43c03cc5bda47dfd0b6faf496ac70fd4389190518120a4f84fc895cf`, and Authenticode status is valid with signer `Microsoft Corporation`.
- The first two UAC elevation prompts were canceled before `msiexec` started. A later approved attempt installed WSL 2.7.10 successfully with exit code `0`.

After elevation is accepted, continue with WSL verification, Ubuntu initialization, Docker Engine CE, Docker Compose Plugin, non-root Docker access, and container smoke testing.

## Completed environment

- WSL: `2.7.10.0`
- WSL kernel: `6.18.33.2-microsoft-standard-WSL2`
- Distribution: Ubuntu 24.04 LTS (`Ubuntu-24.04`)
- Init system: systemd
- Default Linux user: `hxt02`
- Docker Engine CE: `29.6.2`
- containerd: `2.2.6`
- Docker Compose Plugin: `5.3.1`
- Docker Buildx Plugin: `0.35.0`
- Docker service: enabled and active
- Docker access: non-root user is a member of the `docker` group

Ubuntu package indexes use the Aliyun Ubuntu mirror because direct access to the upstream Ubuntu archive repeatedly timed out. Docker packages use the Aliyun Docker CE mirror with the official Docker repository key scoped through `signed-by`. The verified primary Docker key fingerprint is `9DC858229FC7DD38854AE2D88D81803C0EBFCD88`.

Docker Hub timed out from the current network. No permanent third-party Docker Hub registry mirror was configured. Runtime validation used the Alibaba Cloud Kubernetes mirror image `registry.cn-hangzhou.aliyuncs.com/google_containers/pause:3.10` with digest `sha256:0ca1162b75bf9fc55c4cac99a1ff06f7095c881d5c07acfa07c853e72225c36f`.

The Linux account was created without an automated password to avoid generating or recording credentials. Docker works without sudo through group membership.

## Final account verification - 2026-07-23

- The password was set interactively by the user; no credential was recorded.
- `passwd -S hxt02` reports `P`, confirming that the account password is usable.
- The default Linux identity is `hxt02` and its groups include `sudo` and `docker`.
- Non-root Docker access remains functional after the password change.
- The requested local development environment is fully installed and verified.

Application services were not marked as running by this environment check. The root Compose file currently defines the full nine-service stack but does not yet implement the planned `lite` profile; that orchestration gap must be resolved before treating a constrained local startup as a lite deployment.
