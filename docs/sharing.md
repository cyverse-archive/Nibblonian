Sharing
-------

Shares a file or directory with another user. The user being shared with is given read-only access to all of the parent directories as well. This allows the user to drill down to the shared file/directory from the "Shared" section of the data management window.

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

    curl -H "Content-Type:application/json" -d '{"path" : "/path/to/shared/file", "user" : "shared-with-user", "permissions" : {"read" : true, "write" : true, "own" : false}}' http://nibblonian.yourhostname.org/share?user=fileowner

The response body:

    {
        "action" : "share",
        "status" : "success",
        "users" : ["users shared with"],
        "paths" : ["the paths that were shared"],
        "permissions" : {
            "read" : true,
            "write" : true,
            "own" : false
        }
    }

Unsharing
------------------------
Unshares a file or directory. All ACLs for the specified user are removed from the file or directory. To simply change existing ACLs, recall the /share end-point with the desired permissions.

Note that "users" and "paths" are always lists, even if only one user or path is specified.

Action: "unshare"

Error codes: ERR_NOT_A_USER, ERR_BAD_OR_MISSING_FIELD, ERR_DOES_NOT_EXIST, ERR_NOT_OWNER

Request body JSON

    {
        "paths" : ["/path/to/shared/file"],
        "users" : ["shared-with-user"]
    }

Curl command:

    curl -H "Content-Type:application/json" -d '{"path" : "/path/to/shared/file", "user" : "shared-with-user"}' http://nibblonian.yourhostname.org/unshare?user=fileowner
