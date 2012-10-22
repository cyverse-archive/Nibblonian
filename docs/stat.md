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
                "created": "1339001248000",
                "modified": "1339001248000",
                "type": "dir"
            },
            "/iplant/home/dennis/foo.bar": null,
            "/iplant/home/dennis/foo.txt": {
                "created": "1335289356000",
                "modified": "1335289356000",
                "size": 4,
                "type": "file"
            }
        },
        "status": "success"
    }
