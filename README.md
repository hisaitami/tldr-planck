# tldr-planck

A [TLDR pages](https://tldr.sh/) client written in Planck (Stand-alone ClojureScript REPL)

![tldr screenshot](screenshot.png)

## Requirement

[Planck](https://planck-repl.org/), a stand-alone ClojureScript REPL for macOS and Linux based on JavaScriptCore.

On macOS:

```
brew install planck
```

## Usage

```
usage: ./tldr.cljs [OPTION]... SEARCH

available commands:
  -v, --version                    print version and exit
  -p, --platform PLATFORM  common  select platform, supported are linux / osx / sunos / windows
  -h, --help                       show this help
```

## Related

* [tldr](https://github.com/tldr-pages/tldr) - Simplified and community-driven man pages
* [tldr-cpp-client](https://github.com/tldr-pages/tldr-cpp-client) - Command line client in C for BSD, OS X, Linux
* [tldr-php](https://github.com/BrainMaestro/tldr-php) - PHP Client for tldr

## License

Copyright (c) 2020 hisaitami
Distributed under the terms of the [MIT License](LICENSE)
