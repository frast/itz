FROM mcr.microsoft.com/devcontainers/base:ubuntu-24.04

# Docker copies these non-empty directories into new named volumes on their
# first mount. Their ownership is therefore retained as vscode rather than root.
RUN mkdir -p /home/vscode/.m2 /home/vscode/.codex /commandhistory \
    && touch /home/vscode/.m2/.volume-initialized \
        /home/vscode/.codex/.volume-initialized \
        /commandhistory/.bash_history \
        /commandhistory/.zsh_history \
    && chown -R vscode:vscode /home/vscode/.m2 /home/vscode/.codex /commandhistory \
    && printf '%s\n' \
        'export HISTFILE=/commandhistory/.bash_history' \
        'shopt -s histappend' \
        'PROMPT_COMMAND="history -a${PROMPT_COMMAND:+; ${PROMPT_COMMAND}}"' \
        >> /home/vscode/.bashrc \
    && printf '%s\n' \
        'export HISTFILE=/commandhistory/.zsh_history' \
        'setopt APPEND_HISTORY INC_APPEND_HISTORY SHARE_HISTORY' \
        >> /home/vscode/.zshrc \
    && chown vscode:vscode /home/vscode/.bashrc /home/vscode/.zshrc

USER vscode
