# The Kiln
  
The Kiln is an evaluation strategy for insanely complex functions. It
was designed based on my experience with managing several large,
complex, ever-changing web applications in Clojure, including with
tools like Compojure and Ring. Those tool are nice. They are simple
and well thought out. However, they do little to address the real
complexities of developing big commercial system.

The Kiln can help.

* [Why Use the Kiln]
  (http://github.com/straszheimjeffrey/The-Kiln/wiki/Why)
* [A Sample Application]
  (http://github.com/straszheimjeffrey/The-Kiln/tree/master/sample)
* Kiln Style Guile (comming soon)

## Usage

If you use Leiningen, add this to your project.clj:

````clojure
[kiln "1.0.0"]
````

To start with, you must create some "coal". Coals are your base
values, the ones not computed from other things in the kiln. For a
webapp, we'll use the request object.

````clojure    
(defcoal request "The request object")
````
    
Don't worry that it's blank. We'll assign a value later.
    
So now we can compute stuff from that.

````clojure    
(defclay uri
  "The URI of the request"
  :value (build-uri (?? request)))
````
    
Here we assume you have some function `build-uri` that, when given a
request object, will compute a URI. Also note the `??` syntax. It will
automatically lookup the request.
    
````clojure
(defclay path
  "The path"
  :value (.getPath (?? uri)))
````
    
Dispatching is one of the first things you do in most frameworks. For
now, we just dispatch on path.
    
````clojure
(defclay dispatch
  "What do I do?"
  :value (condp = (?? path)
           "/remove-user" :remove-user
           "/add-user" :add-user
           "/view-user" :view-user))
````
    
Of course, as the application grows, dispatching will get more
complex. No problem, just make the clay look at more stuff.
    
We must match the dispatch to an action. (Ignore the `:glaze` stuff
for now. We will explain it below.)
    
````clojure
(declare log)
(declare security-check)
(declare db-con)
(declare user)

(defclay add-user!
  :glaze [(log :info)
          security-check]
  :value (add-user (?? db-con) (?? user)))

(defclay remove-user!
  :glaze [(log :info)
          security-check]
  :value (remove-user (?? db-con) (?? user)))

(defclay action!
  "Dispatch to the action to perform"
  :glaze [(log :debug)]
  :value (condp = (?? dispatch)
           :remove-user (?? remove-user!)
           :add-user (?? add-user!)
           :view-user nil ; no changes!
           ))
````

Here I'm assuming that the functions `add-user` and `remove-user` are
already defined somewhere.
    
Of course we need a database connection.

````clojure    
(defclay db-con
  :value (get-db-con)
  :cleanup (close-db-con ?self))
````

As above, I assume `get-db-con` and `close-db-con` are functions
defined somewhere. Also, note that `?self` is the value computed by
the clay. You can see it during cleanup.
    
If you wanted to automatically manage transactioning, try this:
    
````clojure
(defclay transactioned-db
  :value (get-transactioned-connection)
  :cleanup-success (do (commit ?self)
                       (close-db-con ?self))
  :cleanup-failure (do (rollback ?self)
                       (close-db-con ?self)))
````
    
I won't detail `user`, but you can figure it out.
    
Let's look at some glaze.
    
````clojure
(defglaze log
  "Log an operation"
  :args [level]
  :operation (let [clay-name (str (:name ?clay))]
               (log level (format "Begin %s" clay-name))
               (let [result (?next)]
                 (log level (format "Complete %s result: %s"
                                    clay-name
                                    (str result)))
                 result)))
````
    
Here, the `(?next)` call is where the magic happens. It computes and
returns the value of the enclosed clay. (In reality it may call
another glaze. They act as a chain.) Notice also that you can access
the enclosed `?clay`. You can also see its `?args` (not shown).

Here is the `security-check` glaze:

````clojure    
(defglaze security-check
  "Throw if user not valid"
  :operation (if-not (valid-user? (?? user))
               (throw+ {:type :security-breach!
                        :user (?? user)
                        :clay ?clay})
               (?next)))
````

These run as wrappers around the various clays that include them. This
allows you to factor out common behavior, much as you would with
aspects or middleware or the like.
    
Now we need a view.
    
````clojure
(defclay template
  :value (condp = (?? dispatch)
           :remove-user "templates/logon"
           :add-user "templates/show-user"
           :view-user "templates/show-user"))
````
    
And so on.
    
What does the driver look like? This should work:

````clojure    
(defn run-kiln
  [req]
  (let [kiln (new-kiln)]
    (stoke-coal kiln request req)
    (let [result (try
                   (fire kiln action!) ; do the stuff
                   (render-template (fire kiln template) ...other kiln data...)
                   (catch Exception e
                     (cleanup-kiln-failure kiln)
                     (throw e)))]
      (cleanup-kiln-success kiln)
      result)))
````

Here, `(new-kiln)` returns an empty kiln, the `(stoke-coal ...)` sets
the value of the request (remember we mentioned that above). And the
`(fire ...)` commands actually compute the values. Since the kiln is
stateful, calling `(fire kiln template)` will *not* recompute the
various intermediate values that were already computed when we called
`(fire kiln action!)`.
    
At this point I expect software engineer types will point out that
some of our clays (namely `dispatch`, `action!`, and `template`) are
too interdependent.  True. Let's try a minor refactor:

````clojure    
(defclay dispatch-structure
  :value (condp = (?? path)
           "/remove-user" {:name :remove-user
                           :action! add-user!
                           :template "templates/logon"}
           "/add-user" {:name :add-user
                        :action! remove-user!
                        :template "templates/show-user"}
           "/view-user" {:name :view-user
                         :action! nil
                         :templates "templates/show-user"}))

(defclay dispatch ; we're redefining this
  :value (:name (?? dispatch-structure)))

(defclay action!
  :glaze [(log :debug)]
  :value (when-let [action-clay! (:action! (?? dispatch-structure))]
           (?? action-clay!)))

(defclay template
  :value (:template (?? dispatch-structure)))
````
    
Nice, eh?


## Clays with arguments

The basic idea of a clay is that it is a single named value that is
available throughout your computation (e.g. a web request). However,
sometimes you will want to define a clay whose behavior can vary on
arguments. Doing this will make a clay behave somewhat like a memoized
function, except the memoization is local to the specific kiln.

It looks like this:

````clojure
(defclay a-clay-with-arguments
   :args [a b]
   :value (+ a b)
   :cleanup (do-something ?self a b))
````

You can use it like this:

````clojure
(fire some-kiln a-clay-with-arguments 1 2)
````

Which will return `3`. Also at cleanup time `(do-something 3 1 2)`
will be called. Note, this will only happen once. If you call it again
with those same arguments, the same value is returned, but it is not
recomputed. The cleanup is only called once.

On the other hand, if you do this

````clojure
(do
  (fire some-kiln a-clay-with-arguments 1 2)
  (fire some-kiln a-clay-with-arguments 2 3))
````

the computation will happen twice, as well as the cleanup.

Note that the kiln must remember each invocation, so if you call a
kiln a very large number of times with different arguments, all of
those values (along with the arguments) are stored in the kiln. If you
must do this, you may be better off with a function.

Glazes can also take arguments. The `log` glaze in the above example
shows how.


## Clays and Threads

Each Kiln should only be used within the thread where it was
created. There is no support for sharing kilns between threads. Note
this does not apply to clays. Clays are entirely stateless. They can
be shared. Many threads can create their own kilns, and use them to
process the same set of clays.

This, of course, is exactly how they should be used for web
applications. Each request creates a kiln. They share your clays.

## License

Copyright (C) 2012 Jeffrey Straszheim

Distributed under the Eclipse Public License, the same as Clojure.
