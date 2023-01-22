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

(def base-url "https://raw.github.com/tldr-pages/tldr/main/pages")

(def tldr-home ".tldrc")

(def cache-dir (io/file (:home env) tldr-home "tldr" "pages"))

(def zip-file "main.zip")

(def zip-url (str "https://github.com/tldr-pages/tldr/archive/" zip-file))

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
                :bright-white "\u001b[37;1m"
                :bold  "\u001b[1m"}]
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

(defn display
  ([page]
   (-> page fetch format println))
  ([platform page]
   (let [cache (io/file cache-dir platform page)]
     (when-not (io/exists? cache)
       (create cache platform page))
     (-> cache display))))

(defn mkdtemp [template]
  (let [{:keys [exit out err]} (sh "mktemp" "-d" template)]
    (if (true? err)
      (do (println "Error: Creating Directory:" template) nil)
      (str/trim out))))

(defn download-zip [url path]
  (let [{:keys [exit out err]} (sh "curl" "-sL" url "-o" path)]
    (if (true? err)
      (do (println "Error: Downloading File:" url) nil)
      path)))

(defn update-localdb [verbose]
  (when-let [tmp-dir (mkdtemp "/tmp/tldrXXXXXX")]
    (when-let [file (download-zip zip-url (str (io/file tmp-dir zip-file)))]
      (when verbose (println "Successfully downloaded:" file))
      (shell/with-sh-dir (:home env)
        (sh "unzip" "-u" file "-d" tldr-home)
        (let [old-tldr (str (io/file tldr-home "tldr"))
              new-tldr (str (io/file tldr-home "tldr-main"))]
          (sh "rm" "-rf" old-tldr)
          (sh "mv" new-tldr old-tldr)))
      (when (io/directory? tmp-dir) (sh "rm" "-rf" tmp-dir))
      (println "Successfully updated local database")
      0 ;return ok
      ))
  1 ;return err
  )

(defn clear-localdb [verbose]
  (shell/with-sh-dir (:home env)
    (let [{:keys [exit out err]} (sh "rm" "-rf" tldr-home)]
      (if (true? err) (println err)
        (println "Successfully removed"
                 (if verbose (str (io/file (:home env) tldr-home))
                   "local database")))
      exit)))

(defn list-localdb [platform verbose]
  (let [path (io/file cache-dir platform)]
    (when-not (io/exists? path)
      (update-localdb verbose))

    (println (ansi-str :bold "Pages for " platform :reset))
    (doseq [file (io/list-files path)]
      (let [entry (str/replace (io/file-name file) #".md$" "")]
        (println entry)))))

(def cli-options [["-v" nil "print verbose output"
                   :id :verbose]
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
                   :validate [#(io/exists? %)
                              "file does not exist"]]])

(def version "tldr.cljs v0.4.0-SNAPSHOT")

(defn usage [options-summary]
  (->> ["usage: ./tldr.cljs [-v] [OPTION]... SEARCH\n"
        "available commands:"
        options-summary]
      (str/join \newline)))

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (str/join \newline errors)))

(defn detect-platform [options]
  (cond
    (:linux options) "linux"
    (:osx options) "osx"
    (:sunos options) "sunos"
    :else (:platform options)))

(defn validate-args
  "Validate command line arguments. Either return a map indicating the program
  should exit (with a error message, and optional ok status), or a map
  indicating the action the program should take and the options provided."
  [args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)
        options (assoc options :platform (detect-platform options))]

    (cond
      ;; version => exit OK with version info
      (:version options)
      {:exit-message version :ok? true}

      ;; help => exit OK with usage summary
      (:help options)
      {:exit-message (usage summary) :ok? true}

      (:update options)
      {:options options}

      (:clear-cache options)
      {:options options}

      (:list options)
      {:options options}

      ;; render => ensure that file exists
      (:render options)
      {:page (:render options) :options options}

      ;; errors => exit with description of errors
      errors
      {:exit-message (error-msg errors)}

      ;; custom validation on arguments
      (= 1 (count arguments))
      {:page (io/file-name (str (first arguments) ".md")) :options options}

      ;; failed custom validation => exit with usage summary
      :else
      {:exit-message (usage summary)})))

(defn -main
  "The main entry point of this program."
  [& args]
  (let [{:keys [page options exit-message ok?]} (validate-args args)]

    (when exit-message
      (println exit-message)
      (exit (if ok? 0 1)))

    (cond
      ;; update local database
      (:update options)
      (-> (update-localdb (:verbose options)) exit)

      ;; clear local database
      (:clear-cache options)
      (-> (clear-localdb (:verbose options)) exit)

      ;; list all entries in the local database
      (:list options)
      (list-localdb (:platform options) (:verbose options))

      ;; render a local page for testing purposes
      (:render options)
      (display page)

      ;; otherwise, display the specified page
      :else
      (display (:platform options) page))))

(apply -main *command-line-args*)
