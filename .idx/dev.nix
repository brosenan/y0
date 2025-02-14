{pkgs, ...}: {
  packages = [pkgs.leiningen pkgs.perl];
  env = {};
}

