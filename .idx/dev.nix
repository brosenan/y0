{pkgs, ...}: {
  packages = [pkgs.leiningen pkgs.perl pkgs.adoptopenjdk-jre-bin];
  env = {};
}

