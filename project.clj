(defproject guarita "0.1.0-SNAPSHOT"

  :description "FIXME: write description"

  :url "http://example.com/FIXME"

  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url  "https://www.eclipse.org/legal/epl-2.0/"}

  :dependencies [[org.clojure/clojure "1.12.4"]
                 [com.clojure-goes-fast/clj-async-profiler "2.0.0-beta1"]
                 [net.clojars.macielti/service "2.0.0"]
                 [net.clojars.macielti/common-clj "46.2.0"]
                 [io.pedestal/pedestal.service "0.8.1"]
                 [io.pedestal/pedestal.http-kit "0.8.1"]
                 [io.pedestal/pedestal.error "0.8.1"]
                 [io.pedestal/pedestal.interceptor "0.8.1"]
                 [com.taoensso/timbre "6.8.0"]
                 [metosin/jsonista "1.0.0"]

                 ;GraalVM native image building
                 [com.github.clj-easy/graal-build-time "1.0.5"]]

  :jvm-opts ["-Djdk.attach.allowAttachSelf" "-XX:+UnlockDiagnosticVMOptions" "-XX:+DebugNonSafepoints"
             "--add-modules=jdk.incubator.vector"]

  :java-source-paths ["java"]
  :javac-options ["--add-modules=jdk.incubator.vector"]

  :resource-paths ["resources"]

  :aot [guarita.components guarita.diplomat.http-server]

  :profiles {:dev {:plugins        [[lein-shell "0.5.0"]
                                    [com.github.liquidz/antq "RELEASE"]
                                    [com.github.clojure-lsp/lein-clojure-lsp "2.0.14"]]

                   :resource-paths ["test/resources"]

                   :test-paths     ["test/unit" "test/integration" "test/helpers"]

                   :dependencies   [[net.clojars.macielti/common-test-clj "7.1.1"]
                                    [http-kit.fake/http-kit.fake "0.2.2"]
                                    [nubank/matcher-combinators "3.10.0"]
                                    [hashp "0.2.2"]]

                   :injections     [(require 'hashp.core)]

                   :aliases        {"clean-ns"     ["clojure-lsp" "clean-ns" "--dry"] ;; check if namespaces are clean
                                    "format"       ["clojure-lsp" "format" "--dry"] ;; check if namespaces are formatted
                                    "diagnostics"  ["clojure-lsp" "diagnostics"] ;; check if project has any diagnostics (clj-kondo findings)
                                    "lint"         ["do" ["clean-ns"] ["format"] ["diagnostics"]] ;; check all above
                                    "clean-ns-fix" ["clojure-lsp" "clean-ns"] ;; Fix namespaces not clean
                                    "format-fix"   ["clojure-lsp" "format"] ;; Fix namespaces not formatted
                                    "lint-fix"     ["do" ["clean-ns-fix"] ["format-fix"]] ;; Fix both
                                    "native"       ["shell"
                                                    "native-image"
                                                    "-H:+UnlockExperimentalVMOptions"
                                                    "--no-fallback"
                                                    "--enable-url-protocols=http,https"
                                                    "-O3"
                                                    "-march=compatibility"
                                                    "--add-modules=jdk.incubator.vector"
                                                    "--initialize-at-build-time"
                                                    "--initialize-at-run-time=io.prometheus.client.Striped64"

                                                    "-H:ReflectionConfigurationFiles=reflect-config.json"
                                                    "--features=clj_easy.graal_build_time.InitClojureClasses"
                                                    "-Dio.pedestal.log.defaultMetricsRecorder=nil"
                                                    "-jar" "./target/${:uberjar-name:-${:name}-${:version}-standalone.jar}"
                                                    "-H:+UseG1GC"
                                                    "-H:+StaticExecutableWithDynamicLibC"
                                                    "-R:+ExitOnOutOfMemoryError"
                                                    "-H:Name=./target/${:name}"]}}}

  :main guarita.components)
