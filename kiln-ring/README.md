# kiln-ring

Kiln-Ring is a small adaptor library to make it easy to run The Kiln
over a Clojure Ring server.

## Usage

The principle of this is simple:

* You provide the library with a sinle clay that will provide a Ring
  response when asked. It can use the `request` clay (see below).

* The library provides a Ring responder that sets up a Kiln and fires
  a single coal named `request`.

* That's it!

Your code can be as simple as this:

    (use kiln.kiln
         [kiln-ring server response]
         ring.util.response)

    (defclay response-clay
      :value (response (str (?? request-uri))))

    (apply-kiln-handler response-clay)

The function `apply-kiln-handler` is defined in `kiln-ring.server`. It
can take a pair of optional arguments: an `on-error` function and a
list of Ring middleware wrapping functions.

The module `kiln-ring.request` details the various request clays that
are provided.

The module `kiln-ring.uri-utils` provides a URI like datatype that
provides an assoc interface around the `java.net.URI class`.

Like this:

 ````clojure
(def a-uri (as-uri "http://www.customer.com/fred?mary=sue"))

(str a-uri)
=> "http://www.customer.com/fred?mary=sue"

(str (:host a-uri))
=> "www.customer.com"

(str (assoc a-uri :path "/bobcats"))
=> "http://www.customer.com/bobcats?mary=sue"

(as-java-uri a-uri)
=> #<URI http://www.customer.com/fred?mary=sue>
````

It should make a lot of normal URI munging much easier.

A sample application is using this library can be found
[here](http://github.com/straszheimjeffrey/The-Kiln/tree/master/sample).

## License

Copyright (C) 2012 Jeffrey Straszheim

Distributed under the Eclipse Public License, the same as Clojure.
