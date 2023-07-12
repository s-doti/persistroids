(ns persistroids.connector)

(defprotocol Connector
  (get-id [connector])
  (connect [connector])
  (disconnect [connector])
  (read [connector args])
  (write [connector args value])
  (flush [connector writes]))
