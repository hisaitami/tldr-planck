#!/usr/bin/env bash
"exec" "plk" "-Sdeps" "{:deps {org.clojure/tools.cli {:mvn/version \"0.3.7\"}}}" "-sf" "$0" "$@"

(ns tldr.core
  "A planck based command-line client for tldr"
  (:require [planck.core :refer [slurp spit exit]]
            [planck.io :as io]
            [planck.environ :refer [env]]
            [clojure.string :as str]
            [clojure.tools.cli :refer [parse-opts]]))

(defn download [platform page]
  (let [base-url "https://raw.githubusercontent.com/tldr-pages/tldr/master/pages/"
        url (str base-url platform "/" page ".md")]
    (slurp url)))

(defn ansi-str [& coll]
  (let [colors {:reset "\u001b[0m"
                :red   "\u001b[31m"
                :green "\u001b[32m"
                :blue  "\u001b[34m"
                :white "\u001b[37m"
                :bright-white "\u001b[37;1m"}]
    (apply str (replace colors coll))))

(defn format [content]
  (-> content
      (str/replace #"^#\s+(.+)" (ansi-str \newline :bright-white "$1" :reset))
      (str/replace #"(?m)^> (.+)" (ansi-str :white "$1" :reset))
      (str/replace #"(?m):\n$" ":")
      (str/replace #"(?m)^(- .+)" (ansi-str :green "$1" :reset))
      (str/replace #"(?m)^`(.+)`$" (ansi-str :red "    $1" :reset))
      (str/replace #"{{(.+?)}}" (ansi-str :reset :blue "$1" :red))))

(defn display [platform page]
  (let [cache-dir (str (:home env) "/.tldrc/tldr-master/pages/" platform)
        page-file (str cache-dir "/" page ".md")]

    (when-not (io/directory? cache-dir)
      (io/make-parents page-file))

    (when-not (io/exists? page-file)
      (if-let [data (download platform page)]
        (spit page-file data)))

    (when (io/exists? page-file)
      (-> (slurp page-file) format print))))

(def cli-options [["-v" "--version" "print version and exit"]
                  ["-p" "--platform PLATFORM"
                   "select platform, supported are linux / osx / sunos / windows"
                   :default "common"
                   :validate [#(contains? #{"common" "linux" "osx" "sunos" "windows"} %)
                              "supported are common / linux / osx / sunos / windows"]]
                  ["-h" "--help" "show this help"]])

(def version "tldr.cljs v0.1.1")

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
      (:version options) ; version => exit OK with version info
      {:exit-message version :ok? true }
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
