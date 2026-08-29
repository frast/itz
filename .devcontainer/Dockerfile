FROM mcr.microsoft.com/devcontainers/base:ubuntu-24.04

# Docker copies this non-empty directory into a new named volume on its first
# mount.  Its ownership is therefore retained as vscode rather than root.
RUN mkdir -p /home/vscode/.m2 \
    && touch /home/vscode/.m2/.volume-initialized \
    && chown -R vscode:vscode /home/vscode/.m2

USER vscode
