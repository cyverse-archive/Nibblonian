Sharing
-------

Shares a file or directory with another user. The user being shared with is given read-only access to all of the parent directories as well. This allows the user to drill down to the shared file/directory from the "Shared" section of the data management window.

Some users being shared with may be skipped for various reasons. When this happens, any sharing attempts made for that user will be included in a list of skipped sharing attempts in the response body along with a code indicating the reason the sharing attempt was skipped.

Note that "users" and "paths" are always lists, even if only one user or path is specified.

Action: "share"

Error codes: ERR_NOT_A_USER, ERR_BAD_OR_MISSING_FIELD, ERR_DOES_NOT_EXIST, ERR_NOT_OWNER

Request body JSON:

    {
        "paths" : ["/path/to/shared/file"],
        "users" : ["shared-with-user"],
        "permissions" : {
            "read" : true,
            "write" : true,
            "own" : false
        }
    }

Curl command:

    curl -H "Content-Type:application/json" -d '{"paths" : ["/path/to/shared/file"], "users" : ["shared-with-user1", "fileowner"], "permissions" : {"read" : true, "write" : true, "own" : false}}' http://nibblonian.yourhostname.org/share?user=fileowner

The response body:

    {
        "action" : "share",
        "status" : "success",
        "user" : ["users shared with"],
        "path" : ["the paths that were shared"],
        "permissions" : {
            "read" : true,
            "write" : true,
            "own" : false
        },
        "skipped" : [
            {
                "path" : "/path/to/shared/file",
                "reason" : "share-with-self",
                "user" : "fileowner"
            }
        ]
    }

Unsharing
------------------------
Unshares a file or directory. All ACLs for the specified user are removed from the file or directory. To simply change existing ACLs, recall the /share end-point with the desired permissions.

Some users may be skipped for various reasons.  When this happens, any unsharing attempts made for that user will be included in a list of skipped unsharing attempts in the response body along with a code indicating the reason the unsharing attempt was skipped.

Note that "users" and "paths" are always lists, even if only one user or path is specified.

Action: "unshare"

Error codes: ERR_NOT_A_USER, ERR_BAD_OR_MISSING_FIELD, ERR_DOES_NOT_EXIST, ERR_NOT_OWNER

Request body JSON

    {
        "paths" : ["/path/to/shared/file"],
        "users" : ["shared-with-user"]
    }

Curl command:

    curl -H "Content-Type:application/json" -d '{"paths" : ["/path/to/shared/file"], "users" : ["shared-with-user", "fileowner"]}' http://nibblonian.yourhostname.org/unshare?user=fileowner

The response body:

    {
        "action" : "unshare",
        ":status" : "success",
        "path : ["/path/to/shared/file"],
        "user" : ["shared-with-user"],
        "skipped" : [
            {
                "path" : "/path/to/shared/file",
                "reason" : "unshare-with-self",
                "user" : "fileowner"
            }
        ]
    }
