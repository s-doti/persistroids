(ns persistroids.cache
  (:require [clojure.core.cache.wrapped :as c]))

(defn cache-get-or-create
  "If key is found in cache, return found value, otherwise
   cache val and return it."
  [[c1 c2] key val]

  (let [value val]
    ;(c/lookup-or-miss c1 key (constantly value))
    (c/lookup-or-miss c2 key (constantly value))))

(defn cache-lookup
  "Literally lookup key in the cache to return its value."
  [[c1 c2] key]

  (or
    ;(c/lookup c1 key)
    (c/lookup c2 key)
    ))

(defn cache-put
  "Put the pair key-val in the cache, replacing
   previous value if found, and return val."
  [[c1 c2] key val]

  ;(c/evict c1 key)
  (c/evict c2 key)
  ;(c/lookup-or-miss c1 key (constantly val))
  (c/lookup-or-miss c2 key (constantly val)))
