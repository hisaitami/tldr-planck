#!/usr/bin/env bash
"exec" "plk" "-Sdeps" "{:deps {org.clojure/tools.cli {:mvn/version \"0.3.7\"}}}" "-sf" "$0" "$@"

(ns tldr.core
  "A planck based command-line client for tldr"
  (:require [planck.core :refer [line-seq with-open slurp exit]]
            [planck.io :as io]
            [planck.environ :refer [env]]
            [planck.shell :as shell]
            [clojure.string :as str]
            [clojure.pprint :refer [pprint]]
            [clojure.tools.cli :refer [parse-opts]]))

(defn download [platform page]
  (let [base-url "https://raw.githubusercontent.com/tldr-pages/tldr/master/pages/"
        url (str base-url platform "/" page ".md")]
    (slurp url)))

(defn ansi [color-name text]
  (let [colors {:reset "\u001b[0m"
                :red   "\u001b[31m"
                :green "\u001b[32m"
                :blue  "\u001b[34m"
                :white "\u001b[37m"
                :bright-white "\u001b[37;1m"}]
    (str (color-name colors) text (:reset colors))))

(defn format [content]
  (-> content
      (str/replace #"^#\s+(.+)" (str \newline (ansi :bright-white "$1")))
      (str/replace #"(?m)^> (.+)" (ansi :white "$1"))
      (str/replace #"(?m):\n$" ":")
      (str/replace #"(?m)^(- .+)" (ansi :green "$1"))
      (str/replace #"(?m)^`(.+)`$" (ansi :red "    $1"))
      (str/replace #"{{(.+?)}}" (str "\u001b[0m" (ansi :blue "$1") "\u001b[31m"))))

(defn display [platform page]
  (let [cache-dir (str (:home env) "/.tldrc/tldr-master/pages/" platform)
        page-file (str cache-dir "/" page ".md")]

    (when-not (io/directory? cache-dir)
      (shell/sh "mkdir" "-p" cache-dir))

    (when-not (io/exists? page-file)
      (let [page-data (download platform page)]
        (with-open [wtr (io/writer page-file)]
          (-write wtr page-data)
          (-flush wtr))))

    (when (io/exists? page-file)
      (-> (slurp page-file) format print))))

(def cli-options [["-p" "--platform PLATFORM"
                   "select platform, supported are linux / osx / sunos / common"
                   :default "common"
                   :validate [#(contains? #{"linux" "osx" "sunos" "common"} %)
                              "supported are linux / osx / common"]]
                  ["-h" "--help" "Show this help"]])

(defn usage [options-summary]
  (->> ["usage: tldr.cljs [OPTION]... SEARCH"
        ""
        "available commands:"
        options-summary]
      (str/join \newline)))

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (str/join \newline errors)))

(defn validate-args
  "Validate command line arguments. Either return a map indicating the program
  should exit (with a error message, and optional ok status), or a map
  indicating the action the program should take and the options provided."
  [args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    (cond
      (:help options) ; help => exit OK with usage summary
      {:exit-message (usage summary) :ok? true}
      errors ; errors => exit with description of errors
      {:exit-message (error-msg errors)}
      ;; custom validation on arguments
      (= 1 (count arguments))
      {:page (io/file-name (first arguments)) :options options}
      :else ; failed custom validation => exit with usage summary
      {:exit-message (usage summary)})))

(defn die [status msg]
  (println msg)
  (exit status))

(defn -main [& args]
  (let [{:keys [page options exit-message ok?]} (validate-args args)]
    (if exit-message
      (die (if ok? 0 1) exit-message)
      (display (:platform options) page))))

(apply -main *command-line-args*)
