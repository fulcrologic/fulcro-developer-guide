# About This Book

## Overview
This is a comprehensive developer's guide for Fulcro 3.x, designed for both beginners and experienced developers. It covers the library in detail and provides practical examples.

## Key Features
- **Stand-alone guide**: Complete reference for Fulcro 3.x
- **Multi-format learning**: Book, documentation, and YouTube videos
- **Live code examples**: Interactive demos with source code
- **Fulcro Inspect integration**: Development tools for debugging

## Prerequisites
- Clojure/ClojureScript knowledge
- Understanding of React concepts helpful but not required
- Browser development tools (Chrome recommended)

## Resources
- **Main documentation**: [Clojure docs](https://cljdoc.org/d/com.fulcrologic/fulcro/3.0.0/doc/readme)
- **Video tutorials**: [YouTube playlist](https://www.youtube.com/playlist?list=PLVi9lDx-4C_T7jkihlQflyqGqU4xVtsfi)
- **Community**: [Fulcro Community site](https://fulcro-community.github.io/)
- **Support**: [Patreon](http://patreon.com/fulcro)

## Common Namespace Prefixes
```clojure
(ns your-ns
  (:require [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
            [com.fulcrologic.fulcro.application :as app]
            [com.fulcrologic.fulcro.data-fetch :as df]
            [com.fulcrologic.fulcro.dom :as dom]
            [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]))
```

## React Compatibility
- **Fulcro 3.5.x**: React 15+ supported, React 17 recommended
- **Fulcro 3.6.0+**: React 17+, React 18 support available
- **React 18 setup**: Use `with-react18` wrapper for non-legacy mode

## Live Examples
All examples include "Focus Inspector" buttons that integrate with Fulcro Inspect Chrome extension for real-time debugging.