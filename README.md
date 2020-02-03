# tldr-planck

A [planck](https://planck-repl.org/) based command-line client for [TLDR pages](https://tldr.sh/).

![tldr screenshot](screenshot.png)

### Requirement

[Planck](https://planck-repl.org/), a stand-alone ClojureScript REPL for macOS and Linux based on JavaScriptCore.

On macOS:

```
brew install planck
```

### Usage

* `./tldr.cljs <command>` show examples for this command
* `./tldr.cljs <command> -p <platform>` show command page for the given platform (linux, osx, sunos)

### Related

* [tldr](https://github.com/tldr-pages/tldr) - Simplified and community-driven man pages
* [tldr-php](https://github.com/BrainMaestro/tldr-php) - PHP Client for tldr
