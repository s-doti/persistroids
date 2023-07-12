(ns persistroids.connectors.mem
  (:require [persistroids.connector :refer [Connector]]))

(defrecord MemConnector [config state]

  Connector

  (get-id [this] "mem")

  (connect [this]
    (if state
      this
      (assoc this :state (atom {}))))

  (disconnect [this]
    (if (not state)
      this
      (dissoc this :state)))

  (read [this args]
    (get @state args))

  (write [this args value]
    {args value})

  (flush [this writes]
    (swap! state into writes)))
