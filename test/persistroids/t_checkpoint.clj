(ns persistroids.t-checkpoint
  (:require [midje.sweet :refer :all]
            [taoensso.timbre :as logger]
            [persistroids.core :as p]
            [persistroids.connector :as connector]))

(background
  (around :facts
          (logger/with-level :info ?form)))

(midje.config/at-print-level
  :print-facts

  (facts
    "checkpoints - fibonacci example"

    ;fibonacci here is analogous with a process that has both
    ; a persisted state: represented by the position in the
    ; sequence before last - which is persisted in db; and
    ; an interim state: represented by the last position in
    ; the sequence - which is calculated with each iteration
    ; and carried over to the next.
    ;since persistroids keeps the calculation local in mem,
    ; in case of a crash, resuming requires picking up from
    ; last time data was flushed out to db; this is
    ; relevant to both persisted/interim states.
    ;our fibonacci program makes sure to keep checkpoint data
    ; with each iteration, and persistroids knows to persist
    ; this data everytime persisted data is flushed from cache;
    ; the checkpoint-handler is the logic dealing with
    ; persisting the interim state.
    ;
    (let [initial-state {:primary {} :secondary 0}
          db (atom initial-state)
          connector (reify connector/Connector
                      (get-id [this] "my-connector")
                      (read [this args] (get-in @db [:primary args]))
                      (write [this args value] {args value})
                      (flush [this writes] (swap! db update :primary into writes)))
          persistroids (p/init :init-fn (constantly 0)
                               :connectors [connector]
                               :checkpoint-handler #(swap! db assoc :secondary %))
          checkpoint #(assoc %1 :checkpoint %2)

          ;test fn is embedded inside the test execution
          test-fn (fn test-fn [i persistroids]
                    (case i
                      0 (fact "flush cache+checkpoint"
                              (p/flush-now persistroids) => {:primary {"fib" 5}, :secondary 8})
                      (fact "no flush" @db => initial-state)))

          ;the actual test logic - fibonacci
          ;interim state (checkpoint) data is updated with each iteration
          ;test-fn verifies db with each iteration, and triggers a manual
          ; flush once (only done for the sake of testing)
          fibonacci #(loop [i %1 n-1 1]
                       (let [persistroids+ (checkpoint %2 n-1)
                             n-2 (p/read persistroids+ "fib")]
                         (test-fn i persistroids+)
                         (p/write persistroids+ "fib" n-1)
                         (when (pos? i)
                           (recur (dec i) (+ n-2 n-1)))))]

      (fibonacci 5 persistroids)
      (p/shutdown persistroids)
      (get-in @db [:primary "fib"]))
    => 8)

  )
