File and Directory Status Information
-------------------------------------

The /stat endpoint allows the caller to get serveral pieces of information about a file or directory at once.  For directories, the response includes the created and last-modified timestamps along with a file type of `dir`.  For regular files, the response contains the created and last-modified timestamps, the file size in bytes and a file type of `file`.  The following is an example call to the stat endpoint:

Action(s): stat

Curl command:

    curl -H "Content-Type:application/json" -sd '{"paths":["/iplant/home/dennis/foo","/iplant/home/dennis/foo.txt","/iplant/home/dennis/foo.bar"]}' http://services-2:31360/stat?user=dennis

Response:

    {
        "action": "stat",
        "paths": {
            "/iplant/home/dennis/foo": {
                "share-count" : 0,
                "permissions" : {
                    "read" : true,
                    "write" : true,
                    "own" : true
                },
                "dir-count" : 3,
                "file-count" : 4,
                "created": "1339001248000",
                "modified": "1339001248000",
                "type": "dir"
            },
            "/iplant/home/dennis/foo.bar": null,
            "/iplant/home/dennis/foo.txt": {
                "share-count" : 0,
                "permissions" : {                          
                    "read" : true,
                    "write" : true,
                    "own" : true
                },
                "created": "1335289356000",
                "modified": "1335289356000",
                "size": 4,
                "type": "file"
            }
        },
        "status": "success"
    }

Note that entries in the "paths" map that are directories will include "file-count" and "dir-count" fields, while file entries will not.

The "share-count" field is provided for both files and directories and lists the number of users that a file is shared with.

