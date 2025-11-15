
# About This Book

This is a stand-alone developer's guide for version 3 of Fulcro.
It is intended to be used by beginners and experienced developers and covers most of the library in detail.
Fulcro has a pretty extensive set of resources on the web tailored to fit your learning style.

There is this book, the [docstrings/Clojure docs](https://cljdoc.org/d/com.fulcrologic/fulcro/3.0.0/doc/readme), and even a series of [YouTube videos](https://www.youtube.com/playlist?list=PLVi9lDx-4C_T7jkihlQflyqGqU4xVtsfi). Even more resources can be reached via the [Fulcro Community site](https://fulcro-community.github.io/).

A lot of time and energy went into creating these libraries and materials and providing them free of charge.
If you find them useful, please consider [contributing to the project](http://patreon.com/fulcro).

Of course, fixes to this guide are also appreciated as pull requests against the [GitHub repository](https://github.com/fulcrologic/fulcro-developer-guide).

## Live Code Examples

This book includes quite a bit of live code. Live code demos with their source look like this in the HTML version:

- An interactive example rendered in the browser
- A "Focus Inspector" button that causes the Fulcro Inspect development tools to focus on that example (see the Fulcro Inspect installation section for details on how to set that up and use it)
- The ClojureScript source code for the example

Each example uses a mock server embedded in the browser to simulate any network interaction, but the source code you'll read for the application is identical to what you'd write for a real server.

**Note:** If you're viewing this directly from the GitHub repository, you won't see the live code. Use [http://book.fulcrologic.com/fulcro3](http://book.fulcrologic.com/fulcro3) instead.

The mock server has a built-in latency to simulate a moderately slow network so you can observe behaviors over time.
You can control the length of this latency in milliseconds using the "Server Controls" in the upper-right corner of this document (if you're reading the HTML version with live examples).

## Common Prefixes and Namespaces

Many of the code examples in this guide assume you've required the proper namespaces in your code.
This book adopts the following general set of requires and aliases as a default:

```clojure
(ns your-ns
  (:require
    [com.fulcrologic.fulcro.components :as comp]
    [com.fulcrologic.fulcro.data-fetch :as df]
    [com.fulcrologic.fulcro.mutations :as m]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]))
```
