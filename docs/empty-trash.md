Emptying a User's Trash Directory
---------------------------------
__URL Path__: /trash

__HTTP METHOD__: DELETE

__Action__: "delete-trash"

__Error Codes__: ERR_NOT_A_USER

__Request Query Parameters__:
* user - The iRODS username of the user making the request.

__Response__:

    {
        "action" : "delete-trash",
        "status" : "success",
        "trash" : "/path/to/user's/trash/dir/",
        "paths" : [
                "/path/to/deleted/file",
        ]
    }

__Curl Command__:

    curl -X DELETE http://127.0.0.1:3000/trash?user=johnw

