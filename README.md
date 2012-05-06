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
* [A Worked Example]
  (http://github.com/straszheimjeffrey/The-Kiln/wiki/Worked_Example)
* [A Sample Application]
  (http://github.com/straszheimjeffrey/The-Kiln/tree/master/sample)
* Kiln Style Guile (comming soon)

## Usage

If you use Leiningen, add this to your project.clj:

````clojure
[kiln "1.1.0"]
````

Kilns fire clays. A clay is a very simple sort of object. Here is one:

````clojure
(defclay example)
    :value 4)
````

This clay is somethign with a value of 4. To get its value, we fire it
in a kiln.

````clojure
(fire (new-kiln) example)
-> 4
````

Not very exciting. Let's make another clay:

````clojure
(defclay another
    :value (+ 3 (?? example)))
````

We'll fire it:

````clojure
(fire (new-kiln) another)
-> 7
````

So the `(?? ...)` syntax lets us fire that *other* clay also. When a
clay is fired in a kiln, it can ask for the value of another clay
inside that same kiln.

But so what, you ask? Let's make a clay like this:

````clojure
(defclay random
    :value (rand-int 1000))
````

And let's fire it a few times:

````clojure
(fire (new-kiln) random)
-> 704
(fire (new-kiln) random)
-> 671
(fire (new-kiln) random)
-> 443
````

Looks random enough. But what about this:

````clojure
(def kiln (new-kiln))
(fire kiln random)
-> 226
(fire kiln random)
-> 226
(fire kiln random)
-> 226
````

Within the same kiln, a clay remembers its value.

This is the point of kilns and clays. Within a single kiln, a clay is
some *particular* value that was computed. Normally, it will be
computed from the other clays in the kiln. Plus the coals.

A coal is like a clay, except with a coal, you do not compute its
value in the kiln. Instead, you *set* its value. We call that
"stoking" the coal.

````clojure
(defcoal some-coal)
(defclay some-clay
    :value (+ 1 (?? some-coal)))
(defclay another-clay
    :value (+ (?? some-clay) (?? some-coal)))

(def kiln (new-kiln))
(stoke-coal kiln some-coal 5)
(fire kiln another-clay)
-> 11

(def kiln-2 (new-kiln))
(stoke-coal kiln-2 some-coal 6)
(fire kiln-2 another-clay)
-> 13
````

Nice, you say, but what is the point?

The point is this: in an application such as a web server, you compute
a bunch of data for each request, such as the request uri, the request
cookies, the current user, his session, the dispatch data, the search
results, the page header, and so on and so on. During that request,

* The data does not change;

* The data is useful in many places;

* But it is difficult to pass around and manage.

Enter clays and kilns. The kiln gathers all this data into one scoped
mechanism where it is visible but controlled.

For a more detailed example, including how clays cleanup after
themselves (such as a database connection that knows to close) and how
clays can be wrapped by *glaze*, which provides features similar to
middleware/aspects/etc., go [here]
(http://github.com/straszheimjeffrey/The-Kiln/wiki/Worked_Example).


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

Glazes can also take arguments. The `log` glaze in the [worked
example](http://github.com/straszheimjeffrey/The-Kiln/wiki/Worked_Example)
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
