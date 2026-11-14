{
  description = "Heroes of the Brain 2026 - BrainAccess HALO hands-free app";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixpkgs-unstable";
  inputs.flake-utils.url = "github:numtide/flake-utils";

  outputs = { self, nixpkgs, flake-utils }:
    flake-utils.lib.eachSystem [ "aarch64-darwin" "x86_64-darwin" "x86_64-linux" "aarch64-linux" ] (system:
      let
        pkgs = import nixpkgs {
          inherit system;
          overlays = [
            # on darwin liblsl is built as a framework, but nixpkgs' pylsl is patched to dlopen lib/liblsl.dylib
            (final: prev: {
              liblsl = prev.liblsl.overrideAttrs (old: {
                postInstall = (old.postInstall or "") + final.lib.optionalString final.stdenv.hostPlatform.isDarwin ''
                  mkdir -p $out/lib
                  ln -s $out/Frameworks/lsl.framework/Versions/A/lsl $out/lib/liblsl.dylib
                '';
              });
            })
          ];
          config.allowUnfree = true;
          config.android_sdk.accept_license = true;
        };

        # the SDK needs compileSdk 36+, minSdk 24
        androidComposition = pkgs.androidenv.composeAndroidPackages {
          platformVersions = [ "36" ];
          buildToolsVersions = [ "36.0.0" ];
          includeEmulator = false;
          includeSystemImages = false;
          includeNDK = false;
        };
        androidSdk = androidComposition.androidsdk;

        # not packaged in nixpkgs, pure python wheel
        pyxdf = pkgs.python3Packages.buildPythonPackage rec {
          pname = "pyxdf";
          version = "1.18.0";
          format = "wheel";
          src = pkgs.fetchurl {
            url = "https://files.pythonhosted.org/packages/py3/p/pyxdf/pyxdf-${version}-py3-none-any.whl";
            sha256 = "e78ca4c45fdb7f1e0c014f3c4501b5354fe5b8f8fd8ec427cfce577b2592b551";
          };
          dependencies = [ pkgs.python3Packages.numpy ];
          doCheck = false;
        };

        # import check can't load liblsl.dylib inside the darwin build sandbox
        pylsl = pkgs.python3Packages.pylsl.overridePythonAttrs (_: {
          doCheck = false;
          doInstallCheck = false;
          pythonImportsCheck = [ ];
        });

        python = pkgs.python3.withPackages (ps: [
          pylsl
          pyxdf
          ps.numpy
          ps.scipy
          ps.pandas
          ps.matplotlib
          ps.jupyterlab
        ]);

        jdk = pkgs.jdk17;
      in
      {
        devShells.default = pkgs.mkShell {
          packages = [
            python
            jdk
            (pkgs.gradle.override { java = jdk; })
            pkgs.kotlin
            androidSdk
            pkgs.android-tools
            pkgs.liblsl
          ];

          JAVA_HOME = jdk.home;
          ANDROID_HOME = "${androidSdk}/libexec/android-sdk";
          ANDROID_SDK_ROOT = "${androidSdk}/libexec/android-sdk";
          GRADLE_OPTS = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${androidSdk}/libexec/android-sdk/build-tools/36.0.0/aapt2";

          shellHook = ''
            echo "HALO devshell: java $(java -version 2>&1 | head -1), python $(python --version)"
            echo "recordings go to ./recordings (gitignored)"
          '';
        };
      });
}
