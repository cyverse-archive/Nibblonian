Nibblonian
==========

This is a REST-like HTTP API for iRODS written on top of jargon-core. 

All commands respond to POST requests, unless otherwise noted. A successful call will have a 200 status code, while an error will have either a 400, 404, or 500 status code. You must set the "Content-Type" header to "application/json" for any POST requests that upload JSON. Failing to do so will result in an error message.

All commands take a "user" query string parameter. This is the iRODS user that is requesting the operation. The service runs as an iRODS superuser and performs actions on the behalf of the user specified in the query string.

Also worth noting is that I've pretty-printed the JSON from the response bodies, but they aren't actually returned like that when you make a call.