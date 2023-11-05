#!/usr/bin/env bash
"exec" "plk" "-Sdeps" "{:deps {org.clojure/tools.cli {:mvn/version \"1.0.219\"}}}" "-sf" "$0" "$@"

(ns tldr.core
  "A planck based command-line client for tldr"
  (:require [planck.core :refer [slurp spit exit]]
            [planck.io :as io]
            [planck.environ :refer [env]]
            [planck.shell :as shell :refer [sh]]
            [clojure.string :as s]
            [clojure.math :as math]
            [clojure.tools.cli :refer [parse-opts]]))

(def ^:dynamic *verbose* false)

(def ^:dynamic *force-color* false)

(def tldr-home ".tldrc")

(def zip-file "tldr.zip")

(def zip-url (str "https://tldr.sh/assets/" zip-file))

(def page-suffix ".md")

(def cache-date (io/file (:home env) tldr-home "date"))

(defn die [& args]
  (binding [*print-fn* *print-err-fn*]
    (println (apply str args)))
  (exit 1))

(defn current-datetime []
  (math/ceil (/ (.now js/Date) 1000)))

(defn pages-dir
  ([]
   (pages-dir (:lang env)))
  ([l]
   (let [prefix "pages"
         lang (->> (str l) (re-matches #"^([a-z]{2}(_[A-Z]{2})*).*$") second)
         cc (subs (str lang) 0 2)]
     (cond
       (empty? lang) prefix
       (= "en" cc) prefix
       (#{"pt_BR" "pt_PT" "zh_TW"} lang) (s/join "." [prefix lang])
       :else (s/join "." [prefix cc])))))

(defn cache-path
  ([]
   (io/file (:home env) tldr-home (pages-dir)))
  ([platform]
   (io/file (cache-path) platform))
  ([platform page]
   (io/file (cache-path) platform page))
  ([lang platform page]
   (io/file (:home env) tldr-home (pages-dir lang) platform page)))

(def lang-priority-list
  (let [lang (str (:lang env))
        language (reverse (s/split (str (:language env)) #":"))
        default (conj '("en") lang)]
    (->> (if (empty? lang) default
           (if (empty? language) default
             (reduce conj default language)))
         (filter (complement empty?))
         distinct)))

(defn lookup [platform page]
  (for [lang lang-priority-list
        platform (distinct (conj '("common") platform))
        :let [path (cache-path lang platform page)]
        :when (io/exists? path)]
    path))

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
  (let [color? (or *force-color* (and (empty? (:no-color env)) (io/tty? *out*)))
        parse (fn [s m r] (s/replace s m (if color? r "$1")))]
    (-> content
        (parse #"^#\s+(.+)" (ansi-str :bright-white "$1" :reset))
        (parse #"(?m)^> (.+)" (ansi-str :white "$1" :reset))
        (s/replace #"(?m):\n$" ":")
        (parse #"(?m)^(- .+)" (ansi-str :green "$1" :reset))
        (parse #"(?m)^`(.+)`$" (ansi-str :red "    $1" :reset))
        (parse #"\{\{(.+?)\}\}" (ansi-str :reset :blue "$1" :red)))))

(defn fetch [cache]
  (if (io/exists? cache) (slurp cache)
    (die "This page doesn't exist yet!")))

(defn display
  ([page]
   (println)
   (println (format (fetch page))))
  ([platform page]
   (let [cache (first (lookup platform page))]
     (display cache))))

(defn rand-page [platform]
  (let [path (cache-path platform)]
    (rand-nth (io/list-files path))))

(defn mkdtemp [template]
  (let [{:keys [out err]} (sh "mktemp" "-d" template)]
    (or (empty? err) (die "Error: Creating Directory:" template))
    (s/trim out)))

(defn download-zip [url path]
  (let [{:keys [err]} (sh "curl" "-sL" url "-o" path)]
    (or (empty? err) (die "Error: Downloading File:" url))
    path))

(defn update-localdb []
  (let [tmp-dir (mkdtemp "/tmp/tldrXXXXXX")
        zip-path (download-zip zip-url (:path (io/file tmp-dir zip-file)))]
    (when *verbose* (println "Successfully downloaded:" zip-path))
    (shell/with-sh-dir (:home env)
      (sh "unzip" "-u" zip-path "-d" tldr-home))
    (when (io/directory? tmp-dir) (sh "rm" "-rf" tmp-dir))
    (spit cache-date (current-datetime))
    (println "Successfully updated local database")))

(defn clear-localdb []
  (shell/with-sh-dir (:home env)
    (let [{:keys [err]} (sh "rm" "-rf" tldr-home)]
      (or (empty? err) (die err))
      (println "Successfully removed"
               (if *verbose* (:path (io/file (:home env) tldr-home))
                 "local database")))))

(defn list-localdb [platform]
  (let [path (cache-path platform)]
    (or (io/exists? path) (die "Can't open cache directory:" path))
    (println (format "# Pages for"))
    (doseq [file (io/list-files path)]
      (let [r (re-pattern (str page-suffix "$"))
            entry (s/replace (io/file-name file) r "")]
        (println entry)))))

(defn check-localdb []
  (when *verbose* (println "Checking local database..."))
  (if (not (io/exists? cache-date)) (update-localdb)
    (let [created (long (slurp cache-date))
          current (current-datetime)
          elapsed (- current created)]
      (when *verbose* (println "*" created current elapsed))
      (when (> elapsed (* 60 60 24 7 2))
        (println "Local database is older than two weeks, attempting to update it..."
                 "\nTo prevent automatic updates, set the environment variable"
                 "TLDR_AUTO_UPDATE_DISABLED")
        (update-localdb)))))

(defn- default-platform []
  (let [{:keys [out err]} (sh "uname" "-s")]
    (or (empty? err) (die "Error: Unknown platform"))
    (let [sysname (s/trim out)]
      (case sysname
        "Linux" "linux"
        "Darwin" "osx"
        "SunOS" "sunos"
        "Windows" "windows"
        "common"))))

(def cli-options [[nil "--verbose" "print verbose output"
                   :id :verbose
                   :default false
                   :default-desc ""]
                  ["-v" "--version" "print version and exit"]
                  ["-h" "--help" "print this help and exit"]
                  ["-u" "--update" "update local database"]
                  ["-c" "--clear-cache" "clear local database"]
                  ["-l" "--list" "list all entries in the local database"]
                  ["-p" "--platform PLATFORM"
                   "select platform, supported are common / linux / osx / sunos / windows"
                   :default-fn default-platform
                   :default-desc ""
                   :validate [#(contains? #{"common" "linux" "osx" "sunos" "windows"} %)
                              "supported are common / linux / osx / sunos / windows"]]
                  [nil, "--linux" "show command page for Linux"]
                  [nil, "--osx" "show command page for OSX"]
                  [nil, "--sunos" "show command page for SunOS"]
                  [nil, "--windows" "show command page for Windows"]
                  ["-r" "--render PATH" "render a local page for testing purposes"
                   :validate [#(io/exists? %) "file does not exist"]]
                  ["-C", "--color" "force color display"
                   :default false
                   :default-desc ""]
                  [nil, "--random" "show a random command"]])

(def version "tldr.cljs v0.7.0 (spec v2.0)")

(defn usage [options-summary]
  (->> ["usage: tldr.cljs [-v] [OPTION]... SEARCH\n"
        "available commands:"
        options-summary]
       (s/join \newline)))

(defn- has-key? [m k]
  (contains? k m))

(defn- select-platform [options]
  (condp has-key? options
    :linux   "linux"
    :osx     "osx"
    :sunos   "sunos"
    :windows "windows"
    (or (:platform options) "common")))

(defn -main
  "The main entry point of this program."
  [& args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)
        platform (select-platform options)]

    (when errors
      (die "The following errors occurred while parsing your command:\n\n"
           (s/join \newline errors)))

    (set! *verbose* (:verbose options))
    (set! *force-color* (:color options))

    (condp has-key? options
      :version ;; show version info
      (println version)

      :help ;; show usage summary
      (println (usage summary))

      :update ;; update local database
      (update-localdb)

      :clear-cache ;; clear local database
      (clear-localdb)

      :list ;; list all entries in the local database
      (list-localdb platform)

      :render ;; render a local page for testing purposes
      (let [page (:render options)]
        (display page))

      :random ;; show a random command
      (let [page (rand-page platform)]
        (display page))

      ;; if no argument is given, show usage and exit as failure,
      ;; otherwise display the specified page
      (if (empty? arguments) (die (usage summary))
        (let [update? (empty? (:tldr-auto-update-disabled env))
              page (-> (s/join "-" arguments) (s/lower-case) (str page-suffix) (io/file-name))]
          (when update? (check-localdb))
          (display platform page))))))

(set! *main-cli-fn* -main)
