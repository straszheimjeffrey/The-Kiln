
# sample

This is a small web application that demonstrates the basic use of the
Kiln in a RESTful environment. It is an utterly primitive message
board. To logon, enter any name and matching password; e.g., "fred"
"fred" will log you in as fred, "mary" "mary" as mary. Trying "fred"
with a password of "bob" will fail. Guess what "admin" "admin" does?

Once logged in, the application allows you to add and read
message. You can edit your own messages. The admin can edit
anyone's. You cannot delete.

The code is written in a manner that is (I hope) easy to read and
follow. I have used `(declare ...)` aggressively, and the files should
be in a logical order for learning.

Coding with the Kiln is very different from a functional based
approach. In a way, you have to reverse your thinking. Instead of
saying, "I am here. I have this data. What must I do?" you should say,
"I need these things to build my result. I will list them and figure
them out later."

High level concept and artifacts in your code should each have a
unique top-level clay. So there should be a `current-user` clay and a
`current-page-id` clay. Clays are not functions that compute a result
from arguments. (They can be, but that is bad Kiln design.) They are
*values*, and they know how to compute themselves.

The trickiest part of this code, in my opinion, is to understand the
dispatcher: it works backward! In a normal dispatcher, you break apart
the request and then call the business logic code. In a Kiln
dispatcher, you break apart the request, but only to provide data
which will be used elsehwere. The dispatcher returns clays that will
do the work. But they will use other clays that depend on the
dispatcher result.

In short, the dispatcher calls no code. It *chooses* the code, and
sets up the environment where it will run.

I suggest you begin reading this code in the `sample.request`
module. As you proceed, you will want to refer to `sample.dispatcher`
to learn how they interact. The login logic (which is completely
idiotic) lives in the `sample.logon-logoff` module. There are two
messsage modules, one that holds the message based clays, the other
the underlying "database" code.

Enjoy the Kiln! Please let me know your experience.

straszheimjeffrey@gmail.com

## Usage



## License

Copyright (C) 2012 Jeffrey Straszheim

Distributed under the Eclipse Public License, the same as Clojure.
