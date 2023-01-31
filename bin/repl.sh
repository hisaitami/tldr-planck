#!/bin/sh

plk -Sdeps "{:deps {org.clojure/tools.cli {:mvn/version \"1.0.214\"}}}" -n $((RANDOM / 100 + 50000))
