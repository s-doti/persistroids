(ns persistroids.t-motivation
  (:require [midje.sweet :refer :all]
            [taoensso.timbre :as logger]
            [persistroids.core :as p]
            [persistroids.connector :as connector])
  (:import (java.io File)))

(background
  (around :facts
          (let [fs-db (.getAbsolutePath
                        (File/createTempFile "bogus" ".db"))]
            (logger/with-level :info
                               (spit fs-db "")
                               (time ?form)))))

;The basic premise of persistroids:
; it is the same persistence,
; but on steroids
; (this ns shows persistroids delivers an order of magnitude faster on my machine)

(defn do-some-work
  "Read file contents,
   add some random BS,
   and persist;
   X100"
  [fs-db read-fn write-fn]

  (loop [i 100]
    (let [content (read-fn fs-db)
          data (str content (rand))]
      (if (zero? i)
        (count data)
        (do (write-fn fs-db data)
            (recur (dec i)))))))

(midje.config/at-print-level
  :print-facts

  (fact
    "do-some-work directly to fs, not very fast.."
    (do-some-work fs-db
                  slurp
                  spit)
    (count (slurp fs-db)) => (roughly 2000 500))

  (let [fs-connector (reify connector/Connector
                       (get-id [this] "fs-connector")
                       (read [this fs-db]
                         (slurp fs-db))
                       (write [this fs-db value]
                         [fs-db value])
                       (flush [this writes]
                         (doseq [[fs-db data] writes]
                           (spit fs-db data))))
        persistroids (p/init :connectors [fs-connector])]

    (fact
      "do-some-work with persistroids not only works, but is fast!"
      (do-some-work fs-db
                    (partial p/read persistroids)
                    (partial p/write persistroids))
      (p/flush-now persistroids)
      (count (slurp fs-db)) => (roughly 2000 500))

    (p/shutdown persistroids))
  )