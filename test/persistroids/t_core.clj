(ns persistroids.t-core
  (:require [midje.sweet :refer :all]
            [taoensso.timbre :as logger]
            [persistroids.core :as p]))

(background
  (around :facts
          (logger/with-level :info ?form)))

(tabular
  (fact
    "basic read/write ops functionality"

    (let [persistroids (p/init)
          val (?op persistroids)]
      (p/shutdown persistroids)
      val) => ?out)

  ?op ?out

  #(p/read % nil) nil
  #(p/read % "val") nil
  #(p/write % nil nil) nil
  #(p/write % "key" nil) nil
  #(p/write % nil "val") "val"
  #(p/write % "key" "val") "val"
  #(do (p/write % "key" "val")
       (p/read % "key"))
  "val"
  )

(fact
  "init-fn bootstraps non-existing values"
  (let [persistroids (p/init :init-fn (fn [& args] "val"))
        val (p/read persistroids "key")]
    (p/shutdown persistroids)
    val)
  => "val")

(fact
  "cache-key controls cache granularity"
  (let [persistroids (p/init :cache-key first)
        _ (p/write persistroids ["key" "write args"] 1)
        val (p/read persistroids ["key" "read args"])
        _ (p/write persistroids ["key" "other write args"] 2)
        val (p/read persistroids ["key" "other read args"])]
    (p/shutdown persistroids)
    val)
  => 2)

(facts
  "Read/write-through cache metrics"
  (let [{:keys [metrics] :as persistroids} (p/init)]
    (doseq [_ (range 100)
            :let [val (p/read persistroids "key")]]
      (p/write persistroids "key" ((fnil inc 0) val)))
    (p/shutdown persistroids)
    (let [{:keys [lookup read write flush]} (:total @metrics)]
      (fact "100 lookups total" lookup => 100)
      (fact "a single read" read => 1)
      (fact "100 writes total" write => 100)
      (fact "a single flush" flush => 1))))