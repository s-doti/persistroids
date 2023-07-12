(ns persistroids.core
  (:require [taoensso.timbre :as logger]
            [clojure.core.cache.wrapped :as c]
            [seamless-async.core :refer :all]
            [persistroids.connectors.mem :as mem]
            [persistroids.connector :as connector]
            [persistroids.cache :refer :all]))

(defn current-time-ms
  "A convenience wrapper around System/currentTimeMillis
   meant to help with time-sensitive tests (easy mocking)."
  [] (System/currentTimeMillis))

(defn- pending-writes
  "Returns a list of pending writes (identities are
   correlated with the cached identities)."
  [cache-key writes-buf]
  (->> (vals writes-buf)
       (mapcat (comp keys deref))
       (map cache-key)
       (distinct)))

(defn- time-to-flush?
  "Returns true if:
   the amount of pending writes exceeds the configured
   threshold (default:99), OR there are pending writes
   which map to evicted items (removed from cache), OR
   if there are pending writes and enough time elapsed
   since previous flush (defualt:1000ms)."
  [{:keys [cache
           cache-key
           last-flush
           writes-buf
           writes-threshold
           millis-threshold]}]

  (when-let [pending (not-empty (pending-writes cache-key writes-buf))]
    (or (< writes-threshold (count pending))
        (< millis-threshold (- (current-time-ms) @last-flush))
        (not-empty (remove #(cache-lookup cache %) pending)))))

(defn flush-now
  "Flush pending writes, and checkpoint state, if any."
  [{:keys [connectors
           checkpoint-handler
           checkpoint
           cache-key
           last-flush
           writes-buf
           metrics]}]

  (when-let [ids (not-empty (pending-writes cache-key writes-buf))]
    (logger/debug "flush-writes!" (count ids))
    (swap! metrics update-in [:total :flush] (fnil + 0) (count ids))
    (doseq [id ids]
      (swap! metrics update-in [id :f] (fnil inc 0)))
    (doseq [c connectors
            :let [writes (get writes-buf (connector/get-id c))]]
      (connector/flush c (filter some? (vals @writes)))
      (reset! writes {}))
    (reset! last-flush (current-time-ms))
    (when (and checkpoint-handler checkpoint)
      (checkpoint-handler checkpoint))))

(defn- ifnil [f x]
  "Returns x, if x is not nil, otherwise f is invoked, and
   its outcome is returned."
  (if (nil? x) (f) x))

(defn read
  "Reads a single value, identified by args. If the value was not
   cached, it will be, when this call returns."
  [{:keys [cache cache-key metrics] :as persistroids}
   args]

  (let [k (cache-key args)]
    (logger/debug "lookup" k args)
    (swap! metrics update-in [:total :lookup] (fnil inc 0))
    (swap! metrics update-in [k :l] (fnil inc 0))
    (or (cache-lookup cache k)
        (let [{:keys [connectors merge-query-fn init-fn]} persistroids]
          (when (time-to-flush? persistroids)
            (flush-now persistroids))
          (logger/debug "read" k args)
          (swap! metrics update-in [:total :read] (fnil inc 0))
          (swap! metrics update-in [k :r] (fnil inc 0))
          (s->> (smap #(connector/read % args) connectors)
                (apply merge-query-fn)
                (ifnil #(init-fn args))
                (cache-get-or-create cache k))))))

(defn- ifsome [f x]
  "Returns nil, if x is nil, otherwise f(x) is invoked, and
   its outcome is returned."
  (when (some? x) (f x)))

(defn write
  "Writes a single value, identified by args."
  [persistroids
   args
   value]

  (let [{:keys [cache cache-key connectors writes-buf metrics]} persistroids
        k (cache-key args)]
    (logger/debug "write" k args)
    (swap! metrics update-in [:total :write] (fnil inc 0))
    (swap! metrics update-in [k :w] (fnil inc 0))
    (doseq [c connectors
            :let [writes (get writes-buf (connector/get-id c))]]
      (s->> (connector/write c args value)
            (ifsome (partial swap! writes assoc args))))
    (cache-put cache k value)))

(defn init
  "Initializes persistroids.
   Returns a stateful instance with which to perform basic
   read/write ops (supposedly with considerable speed gain).
   connectors: a collection of storage wrappers, adhering
               to the Connector protocol; if not provided
               a default in-mem implementation is used.
   writes-threshold: writes are buffered in memory, and are
                     persisted if this threshold is crossed
                     (default:99).
   millis-threshold: writes are buffered in memory, and are
                     persisted if this threshold is crossed
                     (default:1000ms since last flush).
   init-fn: values bootstrap logic when not found in store
            (default:returns nil).
   cache-key: cache identity determining logic, given args
              (default:identity).
   merge-query-fn: if multiple connectors are given, this logic
                   determines how their returned values are to
                   be merged into one (default:merge).
   checkpoint-handler: this logic may be used for DR scenarios; it
                       is invoked each time writes are flushed to
                       storage, to handle runtime stateful information
                       needed for recovery later; such data may
                       optionally be flagged like so:
                       (assoc persistroids :checkpoint running-state)"
  [& {:keys [connectors
             checkpoint-handler
             init-fn
             merge-query-fn
             cache-key
             writes-threshold
             millis-threshold]
      :or   {init-fn          (constantly nil)
             merge-query-fn   merge
             cache-key        identity
             writes-threshold 99
             millis-threshold 1000}}]
  (let [connectors (or connectors
                       [(connector/connect (mem/map->MemConnector {}))])]
    {:connectors         connectors
     :checkpoint-handler checkpoint-handler
     :init-fn            init-fn
     :merge-query-fn     merge-query-fn
     :cache-key          cache-key
     :writes-threshold   writes-threshold
     :millis-threshold   millis-threshold
     ;internals
     :cache              [(c/lru-cache-factory {} #_#_:threshold 100)
                          (c/soft-cache-factory {})]
     :writes-buf         (->> connectors
                              (map connector/get-id)
                              (reduce #(assoc %1 %2 (atom {})) {}))
     :last-flush         (atom (current-time-ms))
     :metrics            (atom {:total {:lookup 0
                                        :read   0
                                        :write  0
                                        :flush  0}})}))

(defn shutdown
  "Shuts-down persistroids, optionally disconnecting
   connectors (default:false)."
  [{:keys [connectors metrics] :as persistroids}
   & [disconnect-connectors?]]

  (flush-now persistroids)
  (clojure.pprint/pprint
    (cond-> @metrics
            (< 10 (count @metrics)) :total))
  (when disconnect-connectors?
    (doseq [c connectors]
      (connector/disconnect c))))
