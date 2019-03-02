(defproject doughamil/threeagent "0.0.1-SNAPSHOT"
  :description "Build Three.js apps in a reagent-like fashion"
  :url "https://github.com/DougHamil/threeagent"

  :dependencies [[org.clojure/clojure "1.9.0"]
                 [medley "1.0.0"]
                 [reagent "0.8.1"]
                 [karma-reporter "3.1.0"]]

  :source-paths ["src/main"]

  :plugins [[lein-cljsbuild "1.1.7"]
            [lein-doo "0.1.10"]
            [lein-figwheel "0.5.18"]]

  :profiles {:dev {:dependencies [[org.clojure/clojurescript "1.10.439"]
                                  [cider/cider-nrepl "0.20.1-SNAPSHOT"]
                                  [cider/piggieback "0.3.10"]
                                  [figwheel "0.5.18"]
                                  [doo "0.1.11"]
                                  [karma-reporter "3.1.0"]]
                   :resource-paths ["target/cljsbuild/demo"]
                   :source-paths ["src/test"]}}

  :npm {:dependencies [[three "0.100.0"]]}

  :doo {:test "test"}

  :figwheel {:http-server-root "public"
             :nrepl-port 7889
             :nrepl-middleware [cider.nrepl/cider-middleware]}

  :cljsbuild
  {:builds [{:id "test"
             :source-paths [ "src/test"]
             :compiler {:optimizations :none
                        :main "threeagent.runner"
                        :output-dir "target/cljsbuild/test/out"
                        :output-to "target/cljsbuild/test/main.js"
                        :foreign-libs [{:file "node_modules/three/build/three.js"
                                        :provides ["THREE"]}]}}
            {:id "demo"
             :source-paths ["demo"]
             :watch-paths ["src/main" "demo"]
             :figwheel {:on-jsload "threeagent.demo.core/on-js-reload"}
             :compiler {:optimizations :none
                        :infer-externs true
                        :main threeagent.demo.core
                        :output-dir "resources/public/js/out"
                        :output-to "resources/public/js/main.js"
                        :asset-path "js/out"
                        :aot-cache false
                        :foreign-libs [{:file "node_modules/three/build/three.js"
                                        :provides ["THREE"]}]
                        :install-deps true
                        :npm-deps {:three "0.100.0"}}}]})
                        

                        
