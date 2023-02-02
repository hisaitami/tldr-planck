#!/usr/bin/env bash
"exec" "plk" "-Sdeps" "{:deps {org.clojure/tools.cli {:mvn/version \"1.0.214\"}}}" "-sf" "$0" "$@"

(ns tldr.core
  "A planck based command-line client for tldr"
  (:require [planck.core :refer [slurp spit exit]]
            [planck.io :as io]
            [planck.environ :refer [env]]
            [planck.shell :as shell :refer [sh]]
            [clojure.string :as str]
            [clojure.tools.cli :refer [parse-opts]]))

(def ^:dynamic *verbose* false)

(def base-url "https://raw.github.com/tldr-pages/tldr/main")

(def tldr-home ".tldrc")

(def zip-file "main.zip")

(def zip-url (str "https://github.com/tldr-pages/tldr/archive/" zip-file))

(defn pages-dir []
  (let [prefix "pages"
        lang (->> (:lang env) str
                  (re-matches #"^([a-z]{2}(_[A-Z]{2})*).*$") second)]
    (cond
      (nil? lang) prefix
      (= "en" (subs lang 0 2)) prefix
      (contains? #{"pt_BR" "pt_PT" "zh_TW"} lang) (str/join "." [prefix lang])
      :else (str/join "." [prefix (subs lang 0 2)]))))

(defn cache-dir []
  (io/file (:home env) tldr-home "tldr" (pages-dir)))

(defn download [platform page]
  (let [url (str/join "/" [base-url (pages-dir) platform page])
        ret (slurp url)]
    (if (= ret "404: Not Found") nil ret)))

(defn ansi-str [& coll]
  (let [colors {:reset "\u001b[0m"
                :bold  "\u001b[1m"
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
    (io/make-parents cache)
    (spit cache data)))

(defn fetch [cache]
  (if (io/exists? cache)
    (slurp cache)
    "This page doesn't exist yet!"))

(defn display
  ([page]
   (-> page fetch format println))
  ([platform page]
   (let [cache (io/file (cache-dir) platform page)]
     (when-not (io/exists? cache)
       (create cache platform page))
     (-> cache display))))

(defn rand-page [platform]
  (let [path (io/file (cache-dir) platform)]
    (rand-nth (io/list-files path))))

(defn die [& args]
  (binding [*print-fn* *print-err-fn*]
    (println (apply str args)))
  (exit 1))

(defn mkdtemp [template]
  (let [{:keys [out err]} (sh "mktemp" "-d" template)]
    (or (empty? err) (die "Error: Creating Directory:" template))
    (str/trim out)))

(defn download-zip [url path]
  (let [{:keys [err]} (sh "curl" "-sL" url "-o" path)]
    (or (empty? err) (die "Error: Downloading File:" url))
    path))

(defn update-localdb []
  (let [tmp-dir (mkdtemp "/tmp/tldrXXXXXX")
        zip-path (download-zip zip-url (:path (io/file tmp-dir zip-file)))]
      (when *verbose* (println "Successfully downloaded:" zip-path))
      (shell/with-sh-dir (:home env)
        (sh "unzip" "-u" zip-path "-d" tldr-home)
        (let [old (:path (io/file tldr-home "tldr"))
              new (:path (io/file tldr-home "tldr-main"))]
          (sh "rm" "-rf" old)
          (sh "mv" new old)))
      (when (io/directory? tmp-dir) (sh "rm" "-rf" tmp-dir))
      (println "Successfully updated local database")))

(defn clear-localdb []
  (shell/with-sh-dir (:home env)
    (let [{:keys [err]} (sh "rm" "-rf" tldr-home)]
      (or (empty? err) (die err))
      (println "Successfully removed"
               (if *verbose* (:path (io/file (:home env) tldr-home))
                 "local database")))))

(defn list-localdb [platform]
  (let [path (io/file (cache-dir) platform)]
    (or (io/exists? path) (update-localdb))
    (println (ansi-str :bold "Pages for " platform :reset))
    (doseq [file (io/list-files path)]
      (let [entry (str/replace (io/file-name file) #".md$" "")]
        (println entry)))))

(def cli-options [["-v" nil "print verbose output"
                   :id :verbose :default false]
                  [nil "--version" "print version and exit"]
                  ["-h" "--help" "print this help and exit"]
                  ["-u" "--update" "update local database"]
                  ["-c" "--clear-cache" "clear local database"]
                  ["-l" "--list" "list all entries in the local database"]
                  ["-p" "--platform PLATFORM"
                   "select platform, supported are linux / osx / sunos / windows"
                   :default "common"
                   :validate [#(contains? #{"common" "linux" "osx" "sunos" "windows"} %)
                              "supported are common / linux / osx / sunos / windows"]]
                  [nil, "--linux" "show command page for Linux"]
                  [nil, "--osx" "show command page for OSX"]
                  [nil, "--sunos" "show command page for SunOS"]
                  ["-r" "--render PATH" "render a local page for testing purposes"
                   :validate [#(io/exists? %) "file does not exist"]]
                  [nil, "--random" "show a random command"]])

(def version "tldr.cljs v0.6.1")

(defn usage [options-summary]
  (->> ["usage: ./tldr.cljs [-v] [OPTION]... SEARCH\n"
        "available commands:"
        options-summary]
      (str/join \newline)))

(defn detect-platform [options]
  (cond
    (options :linux) "linux"
    (options :osx) "osx"
    (options :sunos) "sunos"
    :else (options :platform)))

(defn str->page [s]
  (io/file-name (str s ".md")))

(defn -main
  "The main entry point of this program."
  [& args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)
        p (detect-platform options)]

    (when errors
      (die "The following errors occurred while parsing your command:\n\n"
           (str/join \newline errors)))

    (set! *verbose* (options :verbose))
    (cond
      ;; show version info
      (options :version) (println version)
      ;; show usage summary
      (options :help) (println (usage summary))
      ;; update local database
      (options :update) (update-localdb)
      ;; clear local database
      (options :clear-cache) (clear-localdb)
      ;; list all entries in the local database
      (options :list) (list-localdb p)
      ;; render a local page for testing purposes
      (options :render) (display (options :render))
      ;; show a random command
      (options :random) (display (rand-page p))
      ;; if there is only one argument, display it
      (= (count arguments) 1) (display p (str->page (first arguments)))
      ;; otherwise show usage and exit as failure
      :else (die (usage summary)))))

(set! *main-cli-fn* -main)
