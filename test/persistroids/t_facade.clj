(ns persistroids.t-facade
  (:require [midje.sweet :refer :all]
            [taoensso.timbre :as logger]
            [persistroids.core :as p]
            [persistroids.connector :as connector]))

(background
  (around :facts
          (logger/with-level :info ?form)))

;persistroids as a powerful, yet simple, facade:
; data fragments may reside across different storage solutions,
; while persistroids at the front gives a unified facade

(defn get-connector [connector-id db & [read-only?]]
  (reify connector/Connector
    (get-id [this] connector-id)
    (read [this args] (get @db args))
    (write [this args value]
      (if read-only?
        (do (swap! db dissoc args) {})
        {args value}))
    (flush [this writes] (swap! db into writes))))

(midje.config/at-print-level
  :print-facts

  (fact
    "data is fragmented across different storage solutions"

    (let [db-a (atom {"key" {:fragment-a "val-1"}})
          db-b (atom {"key" {:fragment-b "val-2"}})
          connector-a (get-connector "connector-a" db-a true)
          connector-b (get-connector "connector-b" db-b true)
          persistroids (p/init :connectors [connector-a connector-b])
          val (p/read persistroids "key")]
      (p/shutdown persistroids)
      val)
    => {:fragment-a "val-1"
        :fragment-b "val-2"})

  (fact
    "migrate data from one storage to another"

    (let [db-a (atom {"key" {:fragment-a "val-1"}})
          db-b (atom {"key" {:fragment-b "val-2"}})
          connector-a (get-connector "connector-a" db-a true)
          connector-b (get-connector "connector-b" db-b)
          persistroids (p/init :connectors [connector-a connector-b])
          val (p/read persistroids "key")
          val (assoc val :fragment-c "val-3")
          val (p/write persistroids "key" val)]
      (p/shutdown persistroids)
      val => {:fragment-a "val-1"
              :fragment-b "val-2"
              :fragment-c "val-3"}
      @db-a => {}
      @db-b => {"key" val}))

  (fact
    "dup writes for redundancy"

    (let [db-a (atom {"key" {:fragment-a "val-1"}})
          db-b (atom {"key" {:fragment-b "val-2"}})
          connector-a (get-connector "connector-a" db-a)
          connector-b (get-connector "connector-b" db-b)
          persistroids (p/init :connectors [connector-a connector-b])
          val (p/read persistroids "key")
          val (assoc val :fragment-c "val-3")
          val (p/write persistroids "key" val)]
      (p/shutdown persistroids)
      val => {:fragment-a "val-1"
              :fragment-b "val-2"
              :fragment-c "val-3"}
      @db-a => {"key" val}
      @db-b => {"key" val}))

  (fact
    "specialized merge op"

    (let [db-a (atom {"key" 1})
          db-b (atom {"key" 2})
          connector-a (get-connector "connector-a" db-a)
          connector-b (get-connector "connector-b" db-b)
          persistroids (p/init :connectors [connector-a connector-b]
                               :merge-query-fn +)
          val (p/read persistroids "key")]
      (p/shutdown persistroids)
      val)
    => 3)

  )
