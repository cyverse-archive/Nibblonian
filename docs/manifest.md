File manifest
-------------
Action: "manifest"

Error codes: ERR_DOES_NOT_EXIST, ERR_NOT_A_FILE, ERR_NOT_READABLE, ERR_NOT_A_USER

Curl command:

    curl http://127.0.0.1:3000/file/manifest?user=johnw&path=/iplant/home/johnw/LICENSE.txt

Response:

    {
        "action":"manifest",
        "status" : "success",
        "content-type" : "text/plain",
        "preview":"file\/preview?user=johnw&path=\/iplant\/home\/johnw\/LICENSE.txt",
        "tree-urls" : []
    }

