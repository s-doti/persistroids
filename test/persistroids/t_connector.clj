(ns persistroids.t-connector
  (:require [midje.sweet :refer :all]
            [taoensso.timbre :as logger]
            [persistroids.core :as p]
            [persistroids.connector :as connector]))

(background
  (around :facts
          (logger/with-level :info ?form)))

;an exploration of the persistroids/connector relationship

(declare verify-connector-read)
(declare verify-connector-write)
(declare verify-connector-flush)

(midje.config/at-print-level
  :print-facts

  (facts
    "an exploration of the persistroids/connector relationship"

    (let [my-connector (reify connector/Connector
                         (get-id [this] "my-connector")
                         (read [this args]
                           (verify-connector-read args))
                         (write [this args value]
                           (verify-connector-write args value))
                         (flush [this writes]
                           (verify-connector-flush (into [] writes))))
          persistroids (p/init :connectors [my-connector])]

      (fact
        "read relationship"
        (p/read persistroids ..my-args..) => ..my-value..
        (provided (verify-connector-read ..my-args..) => ..my-value..))

      (fact
        "write relationship"
        (p/write persistroids ..my-args.. ..my-value..) => ..my-value..
        (provided (verify-connector-write ..my-args.. ..my-value..) => ..my-write-result..))

      (fact
        "write relationship, no buffering"
        (p/write persistroids ..other-args.. ..my-value..) => ..my-value..
        (provided (verify-connector-write ..other-args.. ..my-value..) => nil))

      (fact
        "flush relationship"
        (p/flush-now persistroids) => nil
        (provided (verify-connector-flush [..my-write-result..]) => nil))

      (p/shutdown persistroids)))
  )