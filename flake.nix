{
  description = "kc workspace";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs =
    {
      self,
      nixpkgs,
      flake-utils,
    }:
    flake-utils.lib.eachDefaultSystem (
      system:
      let
        pkgs = import nixpkgs {
          inherit system;
          config = {
            allowUnfree = true;
            android_sdk.accept_license = true;
          };
        };

        android = pkgs.androidenv.composeAndroidPackages {
          platformVersions = [ "37" ];
          buildToolsVersions = [
            "36.0.0"
            "37.0.0"
          ];
          abiVersions = [
            "arm64-v8a"
            "armeabi-v7a"
            "x86_64"
          ];
          includeEmulator = false;
          includeNDK = true;
        };

        androidSdk = "${android.androidsdk}/libexec/android-sdk";
        desktopLibs = with pkgs; [
          fontconfig
          glib-networking
          gtk3
          libayatana-appindicator
          libdrm
          libGL
          libsoup_3
          libX11
          libXcursor
          libXext
          libXfixes
          libXi
          libXinerama
          libxkbcommon
          libXrandr
          libXrender
          libXt
          libXtst
          perl
          webkitgtk_4_1
        ];
        desktopLibraryPath = pkgs.lib.makeLibraryPath desktopLibs;
      in
      {
        devShells.default = pkgs.mkShell {
          packages =
            with pkgs;
            [
              git
              imagemagick
              jdk21
              ktfmt
              lefthook
              libicns
              libxml2
              librsvg
              lint-staged
              nodejs
              prettier
              oxipng
              pngquant
              steam-run
              treefmt
            ]
            ++ desktopLibs;

          ANDROID_HOME = androidSdk;
          ANDROID_SDK_ROOT = androidSdk;
          JAVA_HOME = "${pkgs.jdk21}";
          GIO_EXTRA_MODULES = "${pkgs.glib-networking}/lib/gio/modules";
          GDK_SCALE = 2;

          shellHook = ''
            export PATH="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools:$PATH"
            export LD_LIBRARY_PATH="/run/opengl-driver/lib:''${LD_LIBRARY_PATH:+$LD_LIBRARY_PATH:}${desktopLibraryPath}"
            echo "Development shell ready. Use: nix develop"
          '';
        };
      }
    );
}
