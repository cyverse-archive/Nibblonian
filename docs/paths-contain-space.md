Checking For Spaces in Paths and/or Directories
-----------------------------------------------

__URL Path__: /paths-contain-space

__HTTP Method__: POST

__Action__: "paths-contain-space"

__Error codes__: ERR_BAD_OR_MISSING_FIELD

__Request Body__:

    {
        "paths" : [
            "/this/is/a/path/",
            "/this/is another/path"
        ]
    }

"paths" can actually contain any string. File or directory existence is not checked. iRODS is never hit during the execution of this endpoint.

__Response__:

    {
        "action" : "paths-contain-space",
        "status" : "success",
        "paths" : {
            "\/this\/is\/a\/path\/" : false,
            "\/this\/is another\/path" : true,
        }
    }

__Curl Command__:

    curl -H "Content-Type:application/json" -d '{"paths":["/this/is/a/path/","/this/is another/path"]}' http://127.0.0.1:3000/paths-contain-space