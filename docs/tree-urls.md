Setting a Tree URL for a File
-----------------------------
Multiple tree URLs can be associated with a file. To support this, multiple POSTs of tree-urls for the same file will append the URLs to the list stored in iRODS. Multiple tree URLs can be associated with a file in a single request. You still need to place a URL in a list even if you're only associated one URL with a file.

Something to note is that we're not checking to make sure that the strings in the 'tree-urls' list are actually URLs. This is intentional and will hopefully make migrating to a token based tree retrieval a little simpler.

Action: "set-tree-urls"

Error codes: ERR_DOES_NOT_EXIST, ERR_NOT_WRITEABLE, ERR_NOT_A_USER

Curl command:

    curl -d '{"tree-urls" : [{"label" : "google", "url" : "http://www.google.com"}, {"label" : "yahoo", "url" : "http://www.yahoo.com"}]}' 'http://127.0.0.1:3000/file/tree-urls?user=johnw&path=/iplant/home/johnw/LICENSE.txt'

Response:

    {
        "action":"set-tree-urls",
        "status":"success",
        "path":"\/iplant\/home\/johnw\/LICENSE.txt",
        "user":"johnw"
    }


Getting a Tree URL for a File
-----------------------------

Action: "get-tree-urls"

Error codes: ERR_DOES_NOT_EXIST, ERR_NOT_WRITEABLE, ERR_NOT_A_USER

Curl command:

    curl 'http://127.0.0.1:3000/file/tree-urls?user=rods&path=/iplant/home/johnw/LICENSE.txt'

Response:

    [
        {
            "label" : "google",
            "url" : "http://www.google.com"
        },
        {
            "label" : "yahoo",
            "url" : "http://www.yahoo.com"
        }
    ]


Deleting a Tree URL
-------------------

See the instructions for deleting metadata associated with a file. The attribute name for the tree URLs is 'tree-urls'.