(defproject doughamil/threeagent "0.0.9-SNAPSHOT"
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
            [lein-doo "0.1.10"]
            [lein-figwheel "0.5.19"]
            [lein-shell "0.5.0"]]

  :profiles {:dev {:dependencies [[org.clojure/clojurescript "1.10.520"]
                                  [cider/cider-nrepl "0.20.1-SNAPSHOT"]
                                  [cider/piggieback "0.3.10"]
                                  [figwheel "0.5.18"]
                                  [doo "0.1.11"]
                                  [karma-reporter "3.1.0"]]}
             :test {:dependencies [[thheller/shadow-cljs "2.10.15"]]
                    :source-paths ["src/main" "src/test"]}}

  :npm {:dependencies [[three "0.100.0"]]}

  :doo {:paths {:karma "node_modules/.bin/karma"}}

  :figwheel {:http-server-root "public"
             :nrepl-port 7889
             :nrepl-middleware [cider.nrepl/cider-middleware]}

  :cljsbuild
  {:builds [{:id "render-test"
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
             :source-paths ["src/main" "src/dev"]
             :figwheel {:on-jsload "threeagent.dev.core/on-js-reload"}
             :compiler {:optimizations :none
                        :infer-externs true
                        :main threeagent.dev.core
                        :output-dir "resources/public/js/out"
                        :output-to "resources/public/js/main.js"
                        :asset-path "js/out"
                        :aot-cache false
                        :install-deps true
                        :npm-deps {:three "0.118.0"}}}]}

  :release-tasks [["vcs" "assert-committed"]
                  ["change" "version" "leiningen.release/bump-version" "release"]
                  ["shell" "git" "commit" "-am" "Version ${:version} [ci skip]"]
                  ["vcs" "tag" "v" "--no-sign"]
                  ["change" "version" "leiningen.release/bump-version"]
                  ["shell" "git" "commit" "-am" "Version ${:version} [ci skip]"]
                  ["shell" "git" "checkout" "main"]
                  ["shell" "git" "merge" "release"]
                  ["vcs" "push"]])                       
  
