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
usage: tldr.cljs [-v] [OPTION]... SEARCH

available commands:
  -v                         print verbose output
      --version              print version and exit
  -h, --help                 print this help and exit
  -u, --update               update local database
  -c, --clear-cache          clear local database
  -l, --list                 list all entries in the local database
  -p, --platform PLATFORM    select platform, supported are common / linux / osx / sunos / windows
      --linux                show command page for Linux
      --osx                  show command page for OSX
      --sunos                show command page for SunOS
      --windows              show command page for Windows
  -r, --render PATH          render a local page for testing purposes
  -C, --color                force color display
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
## Configurations

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

Copyright (c) 2023 hisaitami

Distributed under the terms of the [MIT License](LICENSE)
