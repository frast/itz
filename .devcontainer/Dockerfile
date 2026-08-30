FROM mcr.microsoft.com/devcontainers/base:ubuntu-24.04

# Docker copies these non-empty directories into new named volumes on their
# first mount. Their ownership is therefore retained as vscode rather than root.
RUN mkdir -p /home/vscode/.m2 /home/vscode/.codex \
    && touch /home/vscode/.m2/.volume-initialized \
        /home/vscode/.codex/.volume-initialized \
    && chown -R vscode:vscode /home/vscode/.m2 /home/vscode/.codex

USER vscode
