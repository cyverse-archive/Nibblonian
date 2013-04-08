Reading a chunk of a file
-------------------------

__URL Path__: /read-chunk

__HTTP Method__: POST

__Action__: "rename"

__Error Codes___: ERR_DOES_NOT_EXIST, ERR_NOT_READABLE, ERR_NOT_A_USER

__Request Query Parameters___:
* user - the iRODS username of the user making the request.

__Request Body__:
    {
        "path"       : "/iplant/home/wregglej/testfile",
        "position"   : "20",
        "chunk-size" : "7"
    }

__Response__:
    {
        "action"     : "read-chunk",
        "status"     : "success",
        "path"       : "/iplant/home/wregglej/testfile",
        "user"       : "wregglej",
        "start"      : "20",
        "chunk-size" : "7",
        "file-size"  : "33",
        "chunk"      : "0\n12345"
    }

__Curl Command__:
    curl -H "Content-Type:application/json" -d '{"path" : "/iplant/home/wregglej/testfile", "position" : "20", "chunk-size" : "7"}' http://127.0.0.1:31360/read-chunk?user=wregglej

Notes:
* 'position' and 'chunk-size' are both in bytes.
* 'position' and 'chunk-size' must be parseable as longs.
* 'start', 'chunk-size', and 'file-size' in the response are all in bytes.
* 'start', 'chunk-size', amd 'file-size' should all be parseable as longs.
* The byte at 'position' is not included in the response. The chunk begins at position + 1.
