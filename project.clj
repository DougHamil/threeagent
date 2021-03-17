(defproject doughamil/threeagent "0.0.7-SNAPSHOT"
  :description "Build Three.js apps in a reagent-like fashion"
  :url "https://github.com/DougHamil/threeagent"
  :license {:name "MIT"}

  :deploy-repositories [["releases" {:url "https://clojars.org/repo/"
                                     :signing {:gpg-key "C89350FC"}
                                     :username :env
                                     :password :env}]
                        ["snapshots" {:url "https://clojars.org/repo/"
                                      :signing {:gpg-key "C89350FC"}
                                      :username :env
                                      :password :env}]]

  :dependencies [[org.clojure/clojure "1.9.0"]
                 [medley "1.0.0"]
                 [reagent "0.8.1"]
                 [karma-reporter "3.1.0"]]

  :source-paths ["src/main"]

  :plugins [[lein-cljsbuild "1.1.7"]
            [lein-doo "0.1.10"]]

  :profiles {:dev {:dependencies [[org.clojure/clojurescript "1.10.520"]
                                  [com.bhauman/figwheel-main "0.2.3"]]
                   :resource-paths ["target"]
                   :source-paths ["src/main" "src/test"]}}

  :npm {:dependencies [[three "0.100.0"]]}

  :doo {:paths {:karma "node_modules/.bin/karma"}}

  :cljsbuild
  {:builds [{:id "test"
             :source-paths ["src/main" "src/test"]
             :figwheel true
             :compiler {:optimizations :none
                        :main "threeagent.runner"
                        :output-dir "resources/public/test/js/out"
                        :output-to "resources/public/test/js/main.js"
                        :asset-path "js/out"
                        :install-deps true
                        :npm-deps {:three "0.100.0"}}}
            {:id "render-test"
             :source-paths ["src/render_test"]
             :watch-paths ["src/main" "src/render_test"]
             :figwheel {:on-jsload "threeagent.render-test.core/on-js-reload"}
             :compiler {:optimizations :none
                        :infer-externs true
                        :main threeagent.render-test.core
                        :output-dir "tests/render_test/js/out"
                        :output-to "tests/render_test/js/main.js"
                        :asset-path "js/out"
                        :aot-cache false
                        :install-deps true
                        :npm-deps {:three "0.100.0"}}}
            {:id "dev"
             :source-paths ["src/test" "src/main" "src/dev"]
             :figwheel {:on-jsload "threeagent.dev.core/on-js-reload"}
             :compiler {:optimizations :none
                        :infer-externs true
                        :main threeagent.dev.core
                        :output-dir "resources/public/js/out"
                        :output-to "resources/public/js/main.js"
                        :asset-path "js/out"
                        :aot-cache false
                        :install-deps true
                        :npm-deps {:three "0.100.0"}}}]})
                        

                        
