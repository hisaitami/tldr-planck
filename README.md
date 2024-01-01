# tldr-planck

A [TLDR pages](https://tldr.sh/) client written in Planck (Stand-alone ClojureScript REPL)

![tldr screenshot](screenshot.png)

## Getting Started

### Prerequisites

[Planck](https://planck-repl.org/), a stand-alone ClojureScript REPL for macOS and Linux based on JavaScriptCore.

On macOS:

```
brew install planck
```

### Installation

Copy this command `tldr.cljs` to a directory in your path. (~/bin, /usr/local/bin or somewhere)

```
chmod 755 tldr.cljs
cp tldr.cljs /usr/local/bin
tldr.cljs
```

## Usage

```
usage: tldr.cljs [OPTION]... PAGE

available commands:
  -h, --help                 print this help and exit
  -C, --color                force color display
  -p, --platform PLATFORM    select platform, supported are linux / osx / sunos / windows / common
  -r, --render PATH          render a local page for testing purposes
  -u, --update               update local database
  -v, --version              print version and exit
  -c, --clear-cache          clear local database
  -V, --verbose              display verbose output
  -l, --list                 list all entries in the local database
      --random               show a random command
```

Examples:

```
tldr.cljs tar
tldr.cljs du --platform=osx
tldr.cljs --list
tldr.cljs --random
```

To display pages in the specified language (such as `ja`, `pt_BR`, or `fr`):

```
LANG=ja tldr.cljs less
LANG=fr tldr.cljs --random -p osx
```

To control the cache:

 ```
 tldr.cljs --update
 tldr.cljs --clear-cache
 ```

 To render a local file (for testing):

 ```
 tldr.cljs --render /path/to/file.md
 ```

### Use REPL

Start Planck REPL in a terminal window.

```
plk
```

Load `tldr.cljs` and change ns to `tldr.core`.

```clojure
cljs.user=> (load-file "tldr.cljs")
nil
cljs.user=> (ns tldr.core)
nil
tldr.core=>
```

Call `display` function as follows:

```clojure
;; display page from the osx platform at random
tldr.core=> (display (rand-page "osx"))

;; display specified page (requires .md extension for the page name)
tldr.core=> (display "linux" "tar.md")

;; to change the display language (this may occur warning message)
tldr.core=> (binding [lang-priority-list ["ja" "en"]] (display "linux" "tar.md"))
```

NOTE: Don't call -main. Otherwise, REPL will terminate.

## Configuration

If the local database is older than two weeks, attempting to update it.
To prevent automatic updates, set the environment variable `TLDR_AUTO_UPDATE_DISABLED`

```
TLDR_AUTO_UPDATE_DISABLED=1 tldr.cljs tar
```

## Referenced projects

* [tldr](https://github.com/tldr-pages/tldr) - Simplified and community-driven man pages
* [tldr-c-client](https://github.com/tldr-pages/tldr-c-client) - C command-line client for tldr pages
* [tldr-node-client](https://github.com/tldr-pages/tldr-node-client) - Node.js command-line client for tldr pages
* [tldr-php](https://github.com/BrainMaestro/tldr-php) - PHP Client for tldr

## License

Copyright (c) 2024 hisaitami

Distributed under the terms of the [MIT License](LICENSE)
