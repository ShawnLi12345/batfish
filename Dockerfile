  # Stage 1: Build with Bazel
  FROM eclipse-temurin:17-jdk AS builder

  # Install Bazelisk
  ADD https://github.com/bazelbuild/bazelisk/releases/download/v1.25.0/bazelisk-linux-amd64 /usr/local/bin/bazel
  RUN chmod +x /usr/local/bin/bazel

  RUN apt-get update && apt-get install -y gcc g++ && rm -rf /var/lib/apt/lists/*

  WORKDIR /batfish
  COPY . .

  # Build the allinone target
  RUN bazel build //projects/allinone:allinone_main_deploy.jar

  # Stage 2: Runtime image
  FROM eclipse-temurin:17-jre

  WORKDIR /batfish

  # Copy the built fat jar
  COPY --from=builder /batfish/bazel-bin/projects/allinone/allinone_main_deploy.jar /batfish/
  COPY questions /batfish/questions

  EXPOSE 9996 9997

  ENTRYPOINT ["java", "-cp", "/batfish/allinone_main_deploy.jar", "org.batfish.allinone.Main", "-runclient", "false", "-coordinatorargs", "-templatedirs /batfish/questions"]