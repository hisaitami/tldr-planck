#!/usr/bin/env bash
"exec" "plk" "-Sdeps" "{:deps {org.clojure/tools.cli {:mvn/version \"0.3.7\"}}}" "-sf" "$0" "$@"

(ns tldr.core
  "A planck based command-line client for tldr"
  (:require [planck.core :refer [slurp spit exit]]
            [planck.io :as io]
            [planck.environ :refer [env]]
            [clojure.string :as str]
            [clojure.tools.cli :refer [parse-opts]]))

(def base-url "https://raw.githubusercontent.com/tldr-pages/tldr/master/pages")

(def cache-dir (io/file (:home env) ".tldrc" "tldr-master" "pages"))

(defn download [platform page]
  (let [url (str/join "/" [base-url platform page])
        ret (slurp url)]
    (if (= ret "404: Not Found\n") nil ret)))

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

(defn create [cache platform page]
  (when-let [data (download platform page)]
    (do (io/make-parents cache)
        (spit cache data))))

(defn fetch [cache]
  (if (io/exists? cache)
    (slurp cache)
    "This page doesn't exist yet!"))

(defn display [platform page]
  (let [cache (io/file cache-dir platform page)]
    (when-not (io/exists? cache)
      (create cache platform page))
    (-> cache fetch format println)))

(def cli-options [["-v" "--version" "print version and exit"]
                  ["-p" "--platform PLATFORM"
                   "select platform, supported are linux / osx / sunos / windows"
                   :default "common"
                   :validate [#(contains? #{"common" "linux" "osx" "sunos" "windows"} %)
                              "supported are common / linux / osx / sunos / windows"]]
                  ["-h" "--help" "show this help"]])

(def version "tldr.cljs v0.2.1")

(defn usage [options-summary]
  (->> ["usage: tldr.cljs [OPTION]... SEARCH"
        ""
        "available commands:"
        options-summary]
      (str/join \newline)))

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (str/join \newline errors)))

(defn die [status msg]
  (println msg)
  (exit status))

(defn validate-args
  "Validate command line arguments. Either return a map indicating the program
  should exit (with a error message, and optional ok status), or a map
  indicating the action the program should take and the options provided."
  [args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    (cond
      ;; version => exit OK with version info
      (:version options)
      {:exit-message version :ok? true }

      ;; help => exit OK with usage summary
      (:help options)
      {:exit-message (usage summary) :ok? true}

      ;; errors => exit with description of errors
      errors
      {:exit-message (error-msg errors)}

      ;; custom validation on arguments
      (= 1 (count arguments))
      {:page (io/file-name (str (first arguments) ".md")) :options options}

      ;; failed custom validation => exit with usage summary
      :else
      {:exit-message (usage summary)})))

(defn -main [& args]
  (let [{:keys [page options exit-message ok?]} (validate-args args)]
    (if exit-message
      (die (if ok? 0 1) exit-message)
      (display (:platform options) page))))

(apply -main *command-line-args*)
