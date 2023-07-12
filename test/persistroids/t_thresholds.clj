(ns persistroids.t-thresholds
  (:require [midje.sweet :refer :all]
            [taoensso.timbre :as logger]
            [persistroids.core :as p]
            [persistroids.connector :as connector]))

(background
  (around :facts
          (logger/with-level :info ?form)))

(defn get-connector [db]
  (reify connector/Connector
    (get-id [this] "my-connector")
    (read [this args] "val")
    (write [this args value] {args value})
    (flush [this writes] (swap! db into writes))))

(midje.config/at-print-level
  :print-facts

  (facts

    (let [db (atom {})
          persistroids (p/init :connectors [(get-connector db)]
                               :writes-threshold 0)]
      (p/write persistroids "key" "val")
      (fact "no flush" (count @db) => 0)
      (p/read persistroids "another key")
      (fact "flush for surpassing #writes-threshold" (count @db) => 1)
      (p/shutdown persistroids))

    (let [db (atom {})
          persistroids (p/init :connectors [(get-connector db)]
                               :millis-threshold 0)]
      (p/write persistroids "key" "val")
      (fact "no flush" (count @db) => 0)
      (Thread/sleep 1)
      (p/read persistroids "another key")
      (fact "flush for surpassing millis-threshold" (count @db) => 1)
      (p/shutdown persistroids)))

  )
