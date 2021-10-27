# See here for image contents: https://github.com/microsoft/vscode-dev-containers/tree/v0.166.1/containers/java/.devcontainer/base.Dockerfile

# [Choice] Java version: 11, 15
ARG VARIANT="11"
FROM mcr.microsoft.com/vscode/devcontainers/java:0-${VARIANT}

# Install Maven
ARG MAVEN_VERSION=""
RUN su vscode -c "umask 0002 && . /usr/local/sdkman/bin/sdkman-init.sh && sdk install maven \"${MAVEN_VERSION}\""
