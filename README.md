# persistroids

[![Clojars Project](https://img.shields.io/clojars/v/com.github.s-doti/persistroids.svg)](https://clojars.org/com.github.s-doti/persistroids)

Persistence on steroids.

This library is meant as a light-weight layer between business logic and 
concrete persistence logic.

In turn, it aims to provide superior performance via read/write-through caching; 
as well as a simple facade on top of potentially different storage solutions.

This approach draws a clear line between user/biz logic and persistence code; such 
decoupling promotes persistence-agnostic code, so that the choice of storage 
solution does not weigh heavily on dev's shoulders - not while pre-planning, not during 
dev'ing, nor even much much later when a different solution is desired several years 
down the line ;).

## Usage

In the following snippet, a numeric value is read, incremented, and written, again 
and again. But in fact, it is only once read from storage, and flushed back into 
storage.

```clojure
user=> (require '[persistroids.core :as p])
; nil
user=> (def persistroids (p/init))
; #'user/persistroids
user=> (def val (or (p/read persistroids "key") 0))
user=> (p/write persistroids "key" (inc val))
; 1
user=> (def val (p/read persistroids "key"))
user=> (p/write persistroids "key" (inc val))
; 2
user=> (def val (p/read persistroids "key"))
user=> (p/write persistroids "key" (inc val))
; 3
user=> (p/shutdown persistroids)
; {:total {:lookup 3, :read 1, :write 3, :flush 1}}
=> nil
```

This doesn't really do much, does it?
Lets review what we really stand to gain here.

### Performance boost ###

Check out a more elaborated snippet [here](test/persistroids/t_motivation.clj) which 
demonstrates how persistroids can save orders of magnitude out of a program's running 
time. All you need to do, is provide your persistence code, wrapped in a general 
`Connector` API, to let persistroids do its thing.

*The cache*<br>
Persistroids can be that fast, thanks to its internal cache - a read/write-through thin 
layer which wraps around your persistence connectors, and holding weak references (so it 
doesn't hog your program's memory).

*The writes buffer*<br>
In addition, all write ops are optionally buffered aside. Persistroids correlates the 
flushing of buffered writes with the evacuation of cached items by the GC; yet more 
proactive approach to flushing (outside of the explicit approach) can be taken by the 
user, with explicit count/time thresholds, as is demonstrated 
[here](test/persistroids/t_thresholds.clj). 

### Biz/persistence decoupling ###

Since persistence code is not coupled to your biz logic, your storage solution of choice 
may be easily changed at any time, and you can even mix and match different solutions at
once, as is shown [here](test/persistroids/t_facade.clj). This decoupling still allows 
for powerful/advanced/specialized usage schemes of your specific storage, as you can pass 
any required arguments as needed to your queries. 

### Connectors ###

Your connectors are stateful objects mastering the process of connecting and querying 
your storage solution. They also adhere to the `Connector` API:

`(get-id [connector])`<br>
Returns a string which uniquely identifies your connector.

`(read [connector args])`<br>
Returns the value acquired from issuing a query, based on provided args.

`(write [connector args value])`<br>
Write provided value to storage, and using provided args; return nil.<br>
Optionally, return any non-nil data you'd like persistroids to buffer up, to be flushed 
at a later point in time.

`(flush [connector writes])`<br>
Flush all buffered writes (as produced by previous `write` invocations) to storage.

Take a look at this [exploration](test/persistroids/t_connector.clj) of the relationship 
between persistroids and the connector.

### Extra note-worthy features ###

*init-fn*<br>
Optional fn provided on initialization, for default bootstrapping of items not-yet 
found in store; this prevents repeated round-trips to storage in the case an item 
isn't found there. 
See [example](https://github.com/s-doti/persistroids/blob/test/persistroids/t_core.clj#L32).

*cache-key*<br>
Optional fn provided on initialization, for extracting persistent identity out of 
query args; e.g. in case your args contain non-identity information such as time 
fields, etc.
See [example](https://github.com/s-doti/persistroids/blob/test/persistroids/t_core.clj#L40).

*metrics*<br>
The persistroids stateful instance contains internal metrics tracking of the 
amount of cache lookups, in-mem writes, and actual storage reads/flushes.
See [example](https://github.com/s-doti/persistroids/blob/test/persistroids/t_core.clj#L51).

*checkpointing for DR and the likes*<br>
This is advanced usage, but sometimes your program may possess internal running state 
which it needs to keep track of, in-sync with its persistence data, to be able to 
support DR scenarios. The ability to correlate the flushing to persistence with the 
storing of this internal running state aside, is supported as shown 
[here](test/persistroids/t_checkpoint.clj).

## License

Copyright © 2023 [@s-doti](https://github.com/s-doti)

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
